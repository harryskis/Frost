package com.frost

import com.frost.api.HypixelApiClient
import com.frost.config.ModConfig
import com.frost.dungeon.DecorationPlayerMatcher
import com.frost.dungeon.DungeonGrid
import com.frost.dungeon.GridPos
import com.frost.dungeon.RoomCreditTracker
import com.frost.dungeon.RoomDatabase
import com.frost.dungeon.RoomGrid
import com.frost.dungeon.RoomType
import com.frost.dungeon.WorldRoomScanner
import com.frost.gui.FrostScreen
import com.frost.gui.OnboardingFlow
import com.frost.gui.RoomWeightsScreen
import com.frost.parsing.ChatParser
import com.frost.parsing.PuzzleStatus
import com.frost.parsing.ScoreboardParser
import com.frost.parsing.TabListParser
import com.frost.session.DungeonSession
import com.frost.session.PartyMember
import com.frost.summary.RunSummary
import com.frost.util.TextUtils
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.world.ClientWorld
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.MapIdComponent
import net.minecraft.item.Items
import net.minecraft.item.map.MapDecorationTypes
import net.minecraft.item.map.MapState
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

object Frost : ClientModInitializer {
    const val MOD_ID = "frost"
    val logger = LoggerFactory.getLogger(MOD_ID)!!
    private val VALID_USERNAME = Regex("^\\w{1,16}$")
    // Entrance/Fairy/Blood are exception rooms (no real "clear it out" contest to credit -
    // you start in the entrance, blood is a portal you pay health for, fairy is a bonus
    // room), so they never contribute a cleared-room credit.
    private val NON_CREDITABLE_ROOM_TYPES = setOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)

    private var session = DungeonSession()
    private var roomCreditTracker = RoomCreditTracker()
    private val roomGrid = RoomGrid()
    private var tickCounter = 0
    private var endTriggered = false
    // Printing the room breakdown the instant the boss dies lands in the exact same tick as
    // Hypixel's own boss-victory chat flood (Master Mode score screen, "EXTRA STATS", a
    // loot/xp line per party member) - confirmed on a live run that our message gets
    // buried/scrolled past even though it was actually sent. Delay it a few seconds instead
    // of printing inline from finishSession(), so it lands after that flood has scrolled by.
    private var pendingBreakdownAtTick: Int? = null
    private const val BREAKDOWN_PRINT_DELAY_TICKS = 100 // ~5s at 20 ticks/sec

    // The weighted-score message runs on its own retry timeline, separate from the room
    // breakdown above - confirmed via live-run log analysis that Hypixel's API can return
    // a stale (pre-run) lifetime secrets count immediately after a run ends, with no
    // request error at all, making multiple party members' secrets-this-run look like 0.
    // The first attempt waits a few seconds before ever asking, giving Hypixel's backend a
    // head start rather than racing it the instant the boss dies; up to 3 attempts total,
    // 5 real seconds apart after that, each one re-fetching everyone's final secrets. 2 or
    // more party members reading zero/unknown at once counts as a failure worth retrying -
    // see runWeightAttempt for why the threshold isn't "everyone".
    private var pendingWeightAttempt: Int? = null
    private var weightRetryAtTick: Int? = null
    private const val WEIGHT_FETCH_MAX_ATTEMPTS = 3
    private const val WEIGHT_FETCH_INITIAL_DELAY_TICKS = 200 // ~10s at 20 ticks/sec
    private const val WEIGHT_FETCH_RETRY_DELAY_TICKS = 100 // ~5s at 20 ticks/sec
    private const val WEIGHT_FETCH_SUSPICIOUS_ZERO_THRESHOLD = 2
    // Every PUZZLE-type cell seen this run (a dungeon can have 2+ simultaneously, confirmed
    // on a live run), plus which of those have already been used for a presence-fallback
    // credit - crediting the closest player to the SAME cached room for two DIFFERENT
    // puzzles that happen to complete around the same time was a real bug (both puzzles'
    // fallback landed on the same person instead of each looking at its own room).
    private val puzzleRoomPositions: MutableSet<GridPos> = mutableSetOf()
    private val presenceCreditedPuzzleRooms: MutableSet<GridPos> = mutableSetOf()
    private var cachedMapId: MapIdComponent? = null
    private var lastFloorNumber: Int? = null

    // Which room-grid tiles WorldRoomScanner has identified a real name for this run.
    // Filled in by an active per-tick sweep in pollDungeonMap (any discovered room tile
    // whose chunk is currently loaded gets scanned), and read at end of run to resolve the
    // names of rooms players cleared - which is often only possible after the clear itself,
    // since a fast team clears a room's checkmark before anyone's close enough to scan it.
    private val identifiedRooms: MutableMap<GridPos, RoomDatabase.RoomInfo> = mutableMapOf()

    // Per-run calibration anchor for world position <-> room-grid tile conversion (see
    // DungeonGrid's doc comment). entranceCorner is captured ONCE, from the local player's
    // own position the instant the dungeon-start message fires - they're guaranteed to
    // still be standing in the entrance room at that exact moment. entranceTile is
    // captured the first time the map decode successfully locates the ENTRANCE room type,
    // which may be a few ticks later; both must be known before any position converts.
    private var entranceCorner: Pair<Int, Int>? = null
    private var entranceTile: GridPos? = null

    private fun toRoomGridPos(worldX: Double, worldZ: Double): GridPos? {
        val corner = entranceCorner ?: return null
        val tile = entranceTile ?: return null
        return DungeonGrid.worldToRoomTile(worldX, worldZ, corner, tile)
    }

    /**
     * Every currently-tracked party member's room-grid tile, preferring their live entity
     * position (most accurate, updates every tick) and falling back to the dungeon map's
     * own decoration marker for whoever's entity isn't currently loaded into the client at
     * all - confirmed common in a large, spread-out floor. The decoration data itself has
     * no player-identifying key (just type/position/rotation), so it's matched to a
     * specific teammate positionally, the same way reference dungeon mods (Odin,
     * Skyblocker) do it - see [DecorationPlayerMatcher].
     */
    private fun currentPlayerTiles(localPlayer: ClientPlayerEntity, world: ClientWorld, mapState: MapState, floorNumber: Int?): Map<String, GridPos> {
        val entityTiles = session.party.values.mapNotNull { member ->
            val entity = world.players.firstOrNull { it.uuid == member.uuid } ?: return@mapNotNull null
            val pos = toRoomGridPos(entity.x, entity.z) ?: return@mapNotNull null
            member.username to pos
        }.toMap()

        val decorations = mapState.decorations.map { deco ->
            val isSelf = deco.type().value() == MapDecorationTypes.FRAME.value()
            val pixelX = (deco.x().toInt() / 2.0 + 64).toInt()
            val pixelZ = (deco.z().toInt() / 2.0 + 64).toInt()
            DecorationPlayerMatcher.Decoration(isSelf, roomGrid.pixelToTile(pixelX, pixelZ, floorNumber))
        }
        val decorationTiles = DecorationPlayerMatcher.match(decorations, localPlayer.gameProfile.name, session.party.keys.toList())

        return session.party.keys.mapNotNull { username ->
            (entityTiles[username] ?: decorationTiles[username])?.let { username to it }
        }.toMap()
    }

    override fun onInitializeClient() {
        logger.info("Frost initializing")
        // Hypixel delivers some lines (e.g. NPC dialogue) as "chat" packets and others as
        // "system/game" packets; listen on both so detection doesn't depend on which one.
        ClientReceiveMessageEvents.GAME.register { message, _ -> onGameMessage(message) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onGameMessage(message) }
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
        ClientPlayConnectionEvents.JOIN.register { _, _, client -> onServerJoin(client) }
        registerCommands()
    }

    /**
     * Shows the first-launch welcome/API-key onboarding exactly once ever, the first time
     * the player joins any server with Frost installed - marks it seen immediately (before
     * the screen even opens) so quitting out of it mid-flow can't cause it to reappear on
     * the next join.
     */
    private fun onServerJoin(client: MinecraftClient) {
        if (ModConfig.get().hasSeenWelcome) return
        ModConfig.markWelcomeSeen()
        client.execute {
            try {
                OnboardingFlow.start(client)
            } catch (e: Exception) {
                logger.error("Failed to show Frost onboarding flow", e)
            }
        }
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("frost")
                    .executes {
                        logger.info("/frost invoked - opening menu screen")
                        // Deferred to next tick: running a command from the chat input
                        // closes the chat screen (setScreen(null)) as part of that same
                        // send-message flow, which - if it happens AFTER this callback
                        // opens our screen - immediately clobbers it back to closed in
                        // the same tick. Scheduling it via execute() runs it after that
                        // chat-closing step instead of racing it.
                        val client = MinecraftClient.getInstance()
                        client.execute {
                            try {
                                client.setScreen(FrostScreen(null))
                            } catch (e: Exception) {
                                logger.error("Failed to open Frost menu screen", e)
                            }
                        }
                        1
                    }
                    .then(
                        ClientCommandManager.literal("apikey")
                            .then(
                                ClientCommandManager.argument("key", StringArgumentType.word())
                                    .executes { ctx ->
                                        val key = StringArgumentType.getString(ctx, "key")
                                        ModConfig.setApiKey(key)
                                        ctx.source.sendFeedback(Text.literal("§aHypixel API key saved."))
                                        1
                                    },
                            ),
                    )
                    .then(
                        ClientCommandManager.literal("lastrun")
                            .executes {
                                // On-demand room breakdown for the current/most-recent run -
                                // handy for checking mid-run without waiting for boss death.
                                // Named "lastrun" (not "rooms") to avoid reading as a room-
                                // weights shortcut alongside /frost roomweights.
                                printRoomBreakdown()
                                1
                            },
                    )
                    .then(
                        ClientCommandManager.literal("roomweights")
                            .executes {
                                // Same next-tick deferral as the bare /frost command above -
                                // avoids the chat-close race from opening a screen inline.
                                val client = MinecraftClient.getInstance()
                                client.execute {
                                    try {
                                        client.setScreen(RoomWeightsScreen(null))
                                    } catch (e: Exception) {
                                        logger.error("Failed to open Room Weights screen", e)
                                    }
                                }
                                1
                            },
                    ),
            )
        }
    }

    private fun onGameMessage(message: Text) {
        val text = TextUtils.stripFormatting(message.string)
        if (ChatParser.isDungeonStartMessage(text)) {
            startNewSession()
        } else if (text.contains("Mort") && text.contains("Good luck", ignoreCase = true)) {
            // Regex didn't match but this looks like the start line - log the raw text so a
            // future formatting mismatch is diagnosable instead of silently doing nothing.
            logger.warn("Saw a Mort/Good-luck-like line that didn't match DUNGEON_START: '$text'")
        } else if (ChatParser.isBossDefeatedMessage(text) && session.isActive() && !endTriggered) {
            endTriggered = true
            finishSession()
        } else if (session.isActive()) {
            val quizFinderRaw = ChatParser.parseQuizBuffFinder(text)
            if (quizFinderRaw != null) {
                // Hypixel shows "You found a..." to the finder themselves (not their real
                // name) and "<name> found a..." to everyone else - confirmed on a live run
                // where the local player's own pickup came through as literally "You".
                val quizFinder = if (quizFinderRaw == "You") {
                    MinecraftClient.getInstance().player?.gameProfile?.name
                } else {
                    quizFinderRaw
                }
                if (quizFinder != null) {
                    val credited = session.recordPuzzleSolved(quizFinder)
                    session.lastPuzzleStatus["Quiz"] = PuzzleStatus.COMPLETED
                    if (credited) {
                        logger.info("Credited Quiz puzzle from dungeon buff message: $quizFinder")
                    } else {
                        logger.info("Quiz puzzle already at the run's total puzzle count - not crediting $quizFinder again")
                    }
                }
            }
            ChatParser.parsePuzzleSolvedSolver(text)?.let { solver ->
                // Mark the SPECIFIC puzzle this chat line is about as already completed
                // BEFORE recording the credit, so the tab-list-driven flow can't also
                // presence-credit it. Fall back to marking an arbitrary not-yet-completed
                // tracked puzzle when the name can't be matched in the text (some puzzles'
                // chat completion message doesn't literally contain their tab-list name),
                // rather than leaving nothing marked and risking a second, tab-list-driven
                // credit for this exact same completion (confirmed on a live run: this
                // exact gap let one real puzzle get double-credited).
                val matchedPuzzle = session.lastPuzzleStatus.keys.find { text.contains(it, ignoreCase = true) }
                    ?: session.lastPuzzleStatus.entries.find { it.value != PuzzleStatus.COMPLETED }?.key
                if (matchedPuzzle != null) {
                    session.lastPuzzleStatus[matchedPuzzle] = PuzzleStatus.COMPLETED
                } else {
                    logger.info("Could not identify which tracked puzzle this chat credit belongs to: '$text'")
                }
                val credited = session.recordPuzzleSolved(solver)
                if (credited) {
                    logger.info("Credited puzzle from chat: $solver")
                } else {
                    logger.info("Already at the run's total puzzle count - not crediting $solver again from chat: '$text'")
                }
            }
        }
    }

    // world.players isn't just the party: Hypixel renders some humanoid mobs (e.g.
    // Floor 7's "Crypt Dreadlord"/"Crypt Souleater") using the Player entity type, so they
    // show up here too. Filter to entries that look like real Mojang accounts: a real
    // username never contains a space, and real (online-mode) accounts use version-4
    // UUIDs, which Hypixel's constructed mob/NPC profiles are unlikely to match. Not
    // bulletproof, but should clear out flavor-named mobs reliably.
    private fun looksLikeRealPlayer(entity: AbstractClientPlayerEntity): Boolean {
        val profile = entity.gameProfile
        return VALID_USERNAME.matches(profile.name) && profile.id.version() == 4
    }

    private fun startNewSession() {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return

        val allWorldPlayers = world.players
        val members = allWorldPlayers.filter(::looksLikeRealPlayer)
            .map { entity -> PartyMember(username = entity.gameProfile.name, uuid = entity.uuid) }
        val excluded = allWorldPlayers.size - members.size
        if (excluded > 0) {
            logger.info(
                "Filtered out $excluded non-party world 'player' entities: " +
                    allWorldPlayers.filterNot { members.any { m -> m.uuid == it.uuid } }
                        .joinToString { it.gameProfile.name },
            )
        }
        if (members.isEmpty()) {
            logger.warn("Dungeon start detected but no players were visible in the world yet.")
        }

        session = DungeonSession()
        roomCreditTracker = RoomCreditTracker()
        endTriggered = false
        pendingBreakdownAtTick = null
        pendingWeightAttempt = null
        weightRetryAtTick = null
        puzzleRoomPositions.clear()
        presenceCreditedPuzzleRooms.clear()
        identifiedRooms.clear()
        cachedMapId = null
        lastFloorNumber = null
        entranceTile = null
        // The local player is guaranteed to still be standing in the entrance room right
        // now (this fires the instant the run starts) - anchor the whole run's world<->tile
        // calibration on their position, per-run, rather than trusting a hardcoded constant
        // to generalize across every dungeon instance forever.
        entranceCorner = client.player?.let { DungeonGrid.physicalRoomCorner(it.x, it.z) }
        session.start(members)
        snapshotSecrets(session.secretsBaseline, members)
        logger.info("Dungeon run started with party: ${members.joinToString { it.username }}, entranceCorner=$entranceCorner")
    }

    /**
     * Snapshots [members]' lifetime secrets-found count into [target] via the Hypixel API,
     * keyed by username - called once per member at the moment they're known (baseline: at
     * run start for the initial roster, and again for anyone discovered mid-run - see
     * [discoverNewPartyMembers] for why late joiners need their own call rather than being
     * silently skipped forever) and again for everyone at run end (final);
     * [DungeonSession.secretsFoundThisRun] diffs the two. No-ops silently if no API key is
     * configured (secrets just show as "?" in the summary). Each response is checked
     * against the CURRENT [session] before writing, since a slow request can resolve after
     * a new run has already started - writing into a session that's since moved on would be
     * a stale write into a still-live object, not merely a wasted one. [onAllComplete], if
     * given, fires once every member's request has resolved (success or error) - used by
     * the final-secrets retry loop to know when it's safe to check the aggregate result.
     */
    private fun snapshotSecrets(target: MutableMap<String, Int>, members: Collection<PartyMember>, onAllComplete: (() -> Unit)? = null) {
        val apiKey = ModConfig.get().hypixelApiKey
        if (apiKey.isNullOrBlank()) {
            onAllComplete?.invoke()
            return
        }
        val forSession = session
        val futures = members.mapNotNull { member ->
            val uuid = member.uuid ?: return@mapNotNull null
            HypixelApiClient.fetchSecretsFoundAsync(uuid, apiKey).thenAccept { result ->
                if (session !== forSession) return@thenAccept
                when (result) {
                    is HypixelApiClient.Result.Success -> target[member.username] = result.secretsFound
                    is HypixelApiClient.Result.Error ->
                        logger.warn("Secrets fetch failed for ${member.username}: ${result.message}")
                }
            }
        }
        if (onAllComplete == null) return
        CompletableFuture.allOf(*futures.toTypedArray()).whenComplete { _, _ ->
            MinecraftClient.getInstance().execute {
                if (session === forSession) onAllComplete()
            }
        }
    }

    /**
     * The one-time roster snapshot at dungeon start missed a real party member on a live
     * run (their entity likely hadn't loaded into the client yet at that exact tick), so
     * they never got credited for anything the whole run. Keep discovering valid players
     * throughout the run instead of only capturing the roster once.
     *
     * Confirmed via a live run's logs: a player discovered here never got a baseline
     * secrets snapshot at all (only the one-time call in [startNewSession] took one, for
     * whoever was already visible at that exact instant) - their secretsFoundThisRun()
     * stayed permanently null the whole run, showing "?" no matter what the final snapshot
     * later found. Every newly-discovered member now gets their own baseline snapshot
     * taken right here, at the moment they're actually known.
     */
    private fun discoverNewPartyMembers(world: ClientWorld) {
        val newMembers = world.players
            .filter(::looksLikeRealPlayer)
            .filter { it.gameProfile.name !in session.party }
            .map { entity -> PartyMember(username = entity.gameProfile.name, uuid = entity.uuid) }
        if (newMembers.isEmpty()) return

        logger.info("Discovered party member(s) mid-run who weren't in the initial roster: ${newMembers.map { it.username }}")
        newMembers.forEach { session.party[it.username] = it }
        snapshotSecrets(session.secretsBaseline, newMembers)
    }

    private fun onTick(client: MinecraftClient) {
        // Deliberately NOT gated on session.isActive(): that flips false the instant
        // "Cleared: 100%" fires, which would kill the scoreboard/tab-list diagnostic dumps
        // right as the boss fight starts - exactly the window needed to find a real
        // "dungeon truly complete" signal instead of the current fixed-delay guess. The
        // actual state mutations below (recordPuzzleSolved etc.) already no-op internally
        // when the session isn't active, so this is safe to leave unconditional.
        tickCounter++

        pendingBreakdownAtTick?.let { targetTick ->
            if (tickCounter >= targetTick) {
                pendingBreakdownAtTick = null
                printRoomBreakdown()
            }
        }

        pendingWeightAttempt?.let { attempt ->
            val fireAt = weightRetryAtTick
            if (fireAt == null || tickCounter >= fireAt) {
                pendingWeightAttempt = null
                weightRetryAtTick = null
                runWeightAttempt(attempt)
            }
        }

        if (tickCounter % 5 != 0) return // poll ~4x/sec, no need for every tick

        val handler = client.networkHandler ?: return
        val world = client.world ?: return
        val player = client.player ?: return

        if (session.isActive()) discoverNewPartyMembers(world)
        pollScoreboard(handler.scoreboard)
        pollTabList(handler.listedPlayerListEntries)
        pollDungeonMap(player, world)
    }

    private fun pollScoreboard(scoreboard: Scoreboard) {
        val objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) ?: return

        // Hypixel doesn't populate ScoreboardEntry.display() - entry.name() just returns the
        // raw "owner" key (e.g. "§t"), a legacy trick where the real visible line text lives
        // on that score holder's Team prefix/suffix instead. Resolve through the team, falling
        // back to the raw entry name for anything that isn't team-backed.
        val lines = scoreboard.getScoreboardEntries(objective).map { entry ->
            val team = scoreboard.getScoreHolderTeam(entry.owner())
            val raw = if (team != null) {
                team.prefix.string + team.suffix.string
            } else {
                entry.name().string
            }
            TextUtils.stripFormatting(raw)
        }

        // Dump the raw sidebar every ~5s while active - useful if the real format still
        // doesn't match what ScoreboardParser expects.
        if (tickCounter % 100 == 0) {
            logger.info("Scoreboard sidebar snapshot: ${lines.joinToString(" | ")}")
        }

        for (line in lines) {
            ScoreboardParser.parseFloor(line)?.let { session.floor = it }
            ScoreboardParser.parseSecretsFoundTeamTotal(line)?.let { session.teamSecretsFound = it }
            // Cleared% hits 100 once all rooms are done, which is well before the boss
            // fight even starts (confirmed on a live run - it led the real boss-defeated
            // chat message by ~30s on a floor with a ~30s boss fight). No longer used to
            // trigger the summary; see onGameMessage's BOSS_DEFEATED handling for that.
            ScoreboardParser.parseClearedPercent(line)
        }
    }

    private fun pollTabList(entries: Collection<PlayerListEntry>) {
        val lines = entries.mapNotNull { entry -> entry.displayName?.string?.let(TextUtils::stripFormatting) }

        // Same idea as the scoreboard dump: the puzzle-line regex has never been confirmed
        // against a live tab list, so log the raw content periodically to check it.
        if (tickCounter % 100 == 0) {
            logger.info("Tab list snapshot: ${lines.joinToString(" | ")}")
        }

        for (line in lines) {
            TabListParser.parsePuzzleCount(line)?.let { session.totalPuzzles = it }
            TabListParser.parsePuzzleLine(line)?.let { update ->
                val result = session.handlePuzzleUpdate(update)
                if (result is DungeonSession.PuzzleUpdateResult.NeedsPresenceFallback) {
                    creditPuzzleByPresence()
                }
            }
            TabListParser.parsePlayerClassLine(line)?.let { update ->
                session.playerClass[update.username] = update.className
            }
        }
    }

    /**
     * Fallback for puzzle types Hypixel doesn't attach a solver name to. Prefers checking
     * who's actually standing in an unclaimed puzzle room (from [puzzleRoomPositions],
     * sampled from the map's PUZZLE-colored cells) - this works even if the rest of the
     * party has split up elsewhere, unlike a "who's away from the group" guess. Falls back
     * to that guess only if no puzzle room has been located yet (e.g. map not currently
     * held).
     *
     * A dungeon can have more than one puzzle room, and two can finish around the same
     * moment (confirmed on a live run) - each call here claims and consumes ONE room from
     * [presenceCreditedPuzzleRooms] rather than always using a single cached position, so
     * two simultaneous completions don't both get attributed via the same room.
     */
    private fun creditPuzzleByPresence() {
        val world = MinecraftClient.getInstance().world ?: return
        val localPlayer = MinecraftClient.getInstance().player ?: return
        val mapState = cachedMapId?.let { world.getMapState(it) }
        val playerTiles = mapState?.let { currentPlayerTiles(localPlayer, world, it, lastFloorNumber) } ?: emptyMap()
        val unclaimedRooms = puzzleRoomPositions - presenceCreditedPuzzleRooms

        val credited: Set<String>
        val claimedRoom: GridPos?
        when {
            unclaimedRooms.isNotEmpty() && playerTiles.values.any { it in unclaimedRooms } -> {
                claimedRoom = playerTiles.values.first { it in unclaimedRooms }
                credited = playerTiles.filterValues { it == claimedRoom }.keys
            }
            unclaimedRooms.isNotEmpty() -> {
                // Nobody's exactly in any unclaimed room - likely they already walked out
                // (e.g. Higher Or Lower has a long trailing animation that's easy to skip
                // past before the room shows as complete). Credit whoever's tile-distance
                // closest to the nearest unclaimed room instead of leaving it uncredited -
                // but only within DungeonGrid.MAX_FALLBACK_TILE_DISTANCE_SQ (mirrors
                // RoomCreditTracker's own cap, for the same reason: an unbounded "closest
                // of whoever we have a position for" degenerates into "whoever's
                // trackable," which kept picking the local player even when they were
                // several rooms away from the puzzle in question).
                var bestRoom: GridPos? = null
                var bestPlayer: String? = null
                var bestDistSq = Int.MAX_VALUE
                for (room in unclaimedRooms) {
                    for ((username, tile) in playerTiles) {
                        val distSq = DungeonGrid.tileDistanceSq(tile, room)
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq
                            bestRoom = room
                            bestPlayer = username
                        }
                    }
                }
                if (bestDistSq > DungeonGrid.MAX_FALLBACK_TILE_DISTANCE_SQ) {
                    bestPlayer = null
                    bestRoom = null
                }
                claimedRoom = bestRoom
                if (bestPlayer != null) {
                    logger.info("Nobody was standing in an unclaimed puzzle room - crediting the closest player instead: $bestPlayer")
                    credited = setOf(bestPlayer)
                } else {
                    logger.warn("Puzzle room(s) known but no player was plausibly nearby - falling back to away-from-group guess.")
                    credited = DungeonGrid.playersApartFromMainGroup(playerTiles)
                }
            }
            else -> {
                claimedRoom = null
                logger.warn("No unclaimed puzzle room location known - falling back to the away-from-group guess.")
                credited = DungeonGrid.playersApartFromMainGroup(playerTiles)
            }
        }
        if (claimedRoom != null) presenceCreditedPuzzleRooms.add(claimedRoom)
        val actuallyCredited = credited.filter { session.recordPuzzleSolved(it) }
        if (actuallyCredited.isNotEmpty()) {
            logger.info("Credited puzzle by presence (no solver name given): $actuallyCredited")
        }
    }

    private fun pollDungeonMap(player: ClientPlayerEntity, world: ClientWorld) {
        // The map item isn't reliably present in the inventory scan every tick (confirmed
        // on a live run: found once, then absent on every check afterward, even though the
        // user says it lives in a fixed hotbar slot). Rather than depend on finding the
        // ItemStack every tick, cache its MapIdComponent the first time it IS found, then
        // query the world's map storage directly by ID from then on - vanilla keeps that
        // storage updated from server packets regardless of where the item currently is.
        val mapStack = player.inventory.mainStacks.firstOrNull { it.item == Items.FILLED_MAP }
        if (mapStack != null) {
            mapStack.get(DataComponentTypes.MAP_ID)?.let { foundId ->
                if (foundId != cachedMapId) {
                    // If Hypixel periodically regenerates the map item with a new ID, this
                    // is where we'd see it - the room grid staying frozen on stale data
                    // would mean we kept the OLD id instead of picking up a newer one.
                    logger.info("Map ID changed: $cachedMapId -> $foundId")
                }
                cachedMapId = foundId
            }
        }

        val mapState = cachedMapId?.let { world.getMapState(it) }
        if (mapState == null) {
            if (tickCounter % 100 == 0) {
                val inventoryDump = player.inventory.mainStacks.withIndex()
                    .filter { (_, stack) -> !stack.isEmpty }
                    .joinToString(", ") { (slot, stack) -> "[$slot]=${stack.item}" }
                logger.info(
                    "Room grid: no map data yet (cachedMapId=$cachedMapId). Inventory: $inventoryDump",
                )
            }
            return
        }
        val floorNumber = RoomGrid.parseFloorNumber(session.floor)
        val cells = roomGrid.decode(mapState.colors, floorNumber)
        lastFloorNumber = floorNumber

        if (entranceTile == null) {
            roomGrid.findRoomOfType(cells, RoomType.ENTRANCE)?.let {
                entranceTile = it
                logger.info("Calibrated entrance tile: $it (entranceCorner=$entranceCorner)")
            }
        }

        // Actively scan every discovered-but-not-yet-named room tile whose chunk is loaded
        // right now - this catches rooms whose chunks were already loaded before the run
        // began, and rooms cleared before anyone got close, both of which a one-shot
        // chunk-load listener misses (see WorldRoomScanner.scanTileIfLoaded). Uses Odin's
        // own fixed world-coordinate formula directly, uncalibrated - see
        // WorldRoomScanner's doc comment for why per-run calibration here was a mistake.
        for (tile in cells.keys) {
            if (tile in identifiedRooms) continue
            val room = WorldRoomScanner.scanTileIfLoaded(world, tile) ?: continue
            identifiedRooms[tile] = room
            logger.info("World-scan identified room at $tile: ${room.name} (${room.type})")
        }

        if (tickCounter % 100 == 0) {
            // Show the raw checkmark byte alongside the mapped name - if a room that should
            // clearly be cleared by now still maps to UNDISCOVERED, the raw byte tells us
            // what the real "cleared" color actually is instead of guessing again.
            val grid = (0 until 6).joinToString(" / ") { z ->
                (0 until 6).joinToString(",") { x ->
                    cells[GridPos(x, z)]?.let { "${it.type}:${it.checkmark}(${it.rawCheckmarkByte})" } ?: "-"
                }
            }
            logger.info("Room grid (floor=$floorNumber, size=${RoomGrid.roomPixelSize(floorNumber)}px, mapId=$cachedMapId): $grid")

            // A single sampled point hasn't found a real "cleared" transition yet - dump the
            // full pixel block for one interesting cell (prefer CHAMPION, since it's shown
            // its center pixel actually changing; fall back to any NORMAL room) so we can see
            // the true pattern instead of guessing another single offset.
            val target = roomGrid.findRoomOfType(cells, RoomType.CHAMPION)
                ?: cells.entries.find { it.value.type == RoomType.NORMAL }?.key
            if (target != null) {
                logger.info(
                    "Full pixel block for cell $target (${cells[target]?.type}): " +
                        roomGrid.dumpCellPixels(mapState.colors, floorNumber, target),
                )
            }

            // Scan every discovered cell for pixels matching a known checkmark value and
            // report exactly where they sit - a single sampled "center" point missed a real
            // WHITE(34) pixel near the top of a live cell last run, so find every occurrence
            // directly instead of guessing another offset.
            for (pos in cells.keys) {
                val hits = roomGrid.findCheckmarkPixels(mapState.colors, floorNumber, pos)
                if (hits.isNotEmpty()) {
                    logger.info("Checkmark-colored pixels in cell $pos (${cells[pos]?.type}): $hits")
                }
            }

            // Log every tracked party member's tile alongside the full room grid above, so
            // a bad room-credit run can be cross-checked against exactly where the mod
            // thought everyone was standing (entity position or decoration fallback).
            val allPlayerTiles = currentPlayerTiles(player, world, mapState, floorNumber)
            val entranceCell = roomGrid.findRoomOfType(cells, RoomType.ENTRANCE)
            logger.info("Coordinate cross-check (ENTRANCE=$entranceCell): $allPlayerTiles")

            logger.info("World-scan identified rooms so far: $identifiedRooms")
        }

        cells.filterValues { it.type == RoomType.PUZZLE }.keys.forEach { puzzleRoomPositions.add(it) }

        val creditableCells = cells.filterValues { it.type !in NON_CREDITABLE_ROOM_TYPES }
        val checkmarks = creditableCells.mapValues { it.value.checkmark }
        val roomTypes = creditableCells.mapValues { it.value.type }
        val playerPositions = currentPlayerTiles(player, world, mapState, floorNumber)

        for (event in roomCreditTracker.update(checkmarks, roomTypes, playerPositions)) {
            // Record the room's TILE SET now; its real name is resolved lazily at end of
            // run (it's often not identified yet at the moment of the clear).
            session.recordRoomCleared(event.playersPresent, event.room)
            val identified = event.room.firstNotNullOfOrNull { identifiedRooms[it] }?.name
            logger.info("Room cleared: ${event.room} (${identified ?: "name pending"}) - credited: ${event.playersPresent}")
        }
    }

    private fun finishSession() {
        session.end()
        // Room names were resolved live during the run into identifiedRooms, so there's no
        // dedicated end-of-run wait for THEM - but don't print immediately: see
        // BREAKDOWN_PRINT_DELAY_TICKS above for why this is deferred a few seconds.
        pendingBreakdownAtTick = tickCounter + BREAKDOWN_PRINT_DELAY_TICKS
        // The weighted-score message runs on its own, separate retry timeline. Even attempt
        // 1 waits WEIGHT_FETCH_INITIAL_DELAY_TICKS rather than firing the instant the boss
        // dies - confirmed directly against the live API on a real run that Hypixel's own
        // dungeons.secrets counter can still be stale well past a first few-second wait
        // (its last_dungeon_run field updates quickly, but the aggregate secrets counter
        // lags further behind) - giving it a longer head start before the very first ask
        // reduces how often a retry is even needed.
        pendingWeightAttempt = 1
        weightRetryAtTick = tickCounter + WEIGHT_FETCH_INITIAL_DELAY_TICKS
    }

    private fun printRoomBreakdown() {
        val tileNames = identifiedRooms.mapValues { it.value.name }
        val lines = RunSummary.formatRoomBreakdown(session, tileNames)
        // Always log it too, in case the client can't display in chat (e.g. disconnected
        // in the window after the boss dies). getString() strips the hover tooltip.
        logger.info("Room breakdown: ${lines.joinToString(" | ") { it.string }}")
        printLines(lines)
    }

    /** The floor's own secret count is a rough floor - real runs vary, but a combined
     * party total below this for the floor's difficulty tier is unlikely to be honest and
     * far more likely means Hypixel's API just hasn't caught up yet. Unknown/unparseable
     * floor labels enforce no minimum (0) rather than blocking on a run we can't classify. */
    private fun minimumExpectedSecrets(floorNumber: Int?): Int = when (floorNumber) {
        1, 2 -> 15
        3, 4 -> 20
        5, 6, 7 -> 25
        else -> 0
    }

    /**
     * Runs one attempt at fetching everyone's final secrets snapshot, then decides what to
     * do with the result. Confirmed via live-run log analysis, including a direct check
     * against the live API for two real players, that Hypixel's own dungeons.secrets
     * counter can lag well behind the run actually finishing - every fetch comes back a
     * real HTTP 200 with a real number, no error ever logged, it's just not caught up yet.
     * Two independent signals catch this: [WEIGHT_FETCH_SUSPICIOUS_ZERO_THRESHOLD] or more
     * party members reading zero/unknown at once (a single "0" is plausible on its own, a
     * real run where nobody happened to find one - several at once isn't), or the whole
     * party's combined secrets-this-run total falling short of [minimumExpectedSecrets] for
     * the floor's difficulty - catching the case where nobody shows an outright zero, but
     * the aggregate is still clearly incomplete.
     */
    private fun runWeightAttempt(attempt: Int) {
        val forSession = session
        snapshotSecrets(forSession.secretsFinal, forSession.party.values) {
            if (session !== forSession) return@snapshotSecrets
            val zeroOrUnknownCount = forSession.party.keys.count { username ->
                val found = forSession.secretsFoundThisRun(username)
                found == null || found == 0
            }
            val totalSecrets = forSession.party.keys.sumOf { forSession.secretsFoundThisRun(it) ?: 0 }
            val minimumExpected = minimumExpectedSecrets(RoomGrid.parseFloorNumber(forSession.floor))
            val looksSuspicious = zeroOrUnknownCount >= WEIGHT_FETCH_SUSPICIOUS_ZERO_THRESHOLD || totalSecrets < minimumExpected
            if (!looksSuspicious) {
                val tileNames = identifiedRooms.mapValues { it.value.name }
                val weightedLine = RunSummary.formatWeightedScores(forSession, tileNames) { name -> ModConfig.getRoomWeight(name) }
                logger.info("Weighted scores: ${weightedLine.string}")
                announceToChatChannel(TextUtils.stripFormatting(weightedLine.string))
                return@snapshotSecrets
            }
            val reason = "$zeroOrUnknownCount party member(s) unknown/zero secrets, total $totalSecrets < minimum $minimumExpected for ${forSession.floor}"
            if (attempt >= WEIGHT_FETCH_MAX_ATTEMPTS) {
                logger.warn("Weight calculation failed after $WEIGHT_FETCH_MAX_ATTEMPTS attempts - $reason.")
                announceToChatChannel("[Frost] Hypixel API did not respond; could not calculate weights.")
                return@snapshotSecrets
            }
            logger.warn("Weight calculation attempt $attempt looks wrong ($reason) - retrying in 5s.")
            announceToChatChannel("[Frost] Hypixel API did not respond, re-attempting weight calculation in 5 seconds.")
            pendingWeightAttempt = attempt + 1
            weightRetryAtTick = tickCounter + WEIGHT_FETCH_RETRY_DELAY_TICKS
        }
    }

    /**
     * Sends [plainText] to chat - as a REAL message via the configured channel (e.g. "/pc",
     * "/ac" from ChatChannelScreen), exactly as if the player had typed it themselves, or as
     * a local-only display otherwise. Left unconfigured (blank/null), this never guesses or
     * defaults to broadcasting into whatever chat channel happens to be active - only
     * actually sends once the player has explicitly opted in. Real chat can't carry
     * §-formatting codes, so callers pass already-plain text either way (a real send would
     * otherwise show raw codes as garbage characters to every other reader).
     */
    private fun announceToChatChannel(plainText: String) {
        val channel = ModConfig.get().chatAnnounceChannel?.trim()
        val player = MinecraftClient.getInstance().player

        if (channel.isNullOrBlank()) {
            if (player == null) {
                logger.warn("Could not display in chat - no local player (disconnected?). Message: $plainText")
                return
            }
            player.sendMessage(Text.literal(plainText), false)
            return
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
        if (networkHandler == null) {
            logger.warn("Could not send to chat - not connected. Message: $plainText")
            return
        }
        val command = channel.removePrefix("/").trim()
        networkHandler.sendChatCommand("$command $plainText")
    }

    private fun printLines(lines: List<Text>) {
        val player = MinecraftClient.getInstance().player
        if (player == null) {
            logger.warn("Could not display in chat - no local player (disconnected?). See the log line above for the results.")
            return
        }
        for (line in lines) {
            player.sendMessage(line, false)
        }
    }
}
