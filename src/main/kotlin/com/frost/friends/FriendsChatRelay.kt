package com.frost.friends

import com.frost.config.FriendTag
import com.frost.config.ModConfig
import com.frost.util.TextUtils
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import org.slf4j.LoggerFactory

/**
 * Replaces Hypixel's own `/friend list` chat output with Frost's reformatted version, in
 * chat - never a GUI screen. The whole thing is keyed off detecting the player's own
 * outgoing `/fl` or `/friend list [page]` command (see [onOutgoingCommand], wired to
 * `ClientSendMessageEvents.COMMAND` in Frost.kt): that command opens a short capture
 * window, during which every recognized line from Hypixel's response is suppressed from
 * chat and collected here instead, then Frost prints its own version once the window
 * closes.
 *
 * This is deliberately NOT "silently intercept any friends-list header, whoever triggered
 * it" - that earlier design suppressed the player's own /fl with nowhere to send the
 * result, which is exactly why it appeared to do nothing. Scoping capture to a command
 * Frost explicitly recognized fixes that at the root, and also means normal chat is never
 * at risk of being swallowed outside that narrow, deliberate window.
 */
object FriendsChatRelay {
    private val logger = LoggerFactory.getLogger("frost.friends")

    // Optional leading "/" defensively - Fabric's convention for what onSendCommandMessage
    // receives (with or without the slash) isn't confirmed from here, so tolerate either
    // rather than risk silently never matching.
    private val TRIGGER_COMMAND = Regex("^/?(fl|friend\\s+list(\\s+\\d+)?)$", RegexOption.IGNORE_CASE)

    // A page is a handful of lines that arrive in a tight burst - quiet detection can be
    // much shorter than a full multi-page fetch would need.
    private const val QUIET_TICKS = 8 // ~0.4s
    private const val MAX_CAPTURE_TICKS = 60 // ~3s hard cap per page

    private var localTick = 0
    private var capturing = false
    private var refreshMode = false
    private var captureStartTick = 0
    private var lastActivityTick = 0
    private var headerPage: Int? = null
    private var headerTotalPages: Int? = null
    private val capturedLines = mutableListOf<FriendEntry>()

    // Everything seen across pages viewed this session, for tag-filtered views (see
    // [showTab]) - deliberately not a guaranteed-complete roster; [refreshAll] is the
    // explicit, opt-in way to walk every page and fill it in fully.
    private val cache = LinkedHashMap<String, FriendEntry>()

    private var refreshTotalPages: Int? = null
    private var refreshNextPage = 1
    private var onRefreshDone: ((Int) -> Unit)? = null

    private var recruitFloorLabel: String? = null
    private val recruitFilledClasses: MutableSet<DungeonClass> = mutableSetOf()

    fun setRecruitState(floorLabel: String, filled: Set<DungeonClass>) {
        recruitFloorLabel = floorLabel
        recruitFilledClasses.clear()
        recruitFilledClasses.addAll(filled)
    }

    fun recruitStateSummary(): String {
        val floor = recruitFloorLabel
            ?: return "No recruiting floor set - use /frost recruit <floor> <classes...> (e.g. /frost recruit m5 mage,heal,tank)"
        val filled = if (recruitFilledClasses.isEmpty()) "none" else recruitFilledClasses.joinToString(", ") { it.abbreviation }
        return "Recruiting for $floor - filled: $filled"
    }

    fun currentFloorLabel(): String? = recruitFloorLabel

    fun currentFilledClasses(): Set<DungeonClass> = recruitFilledClasses.toSet()

    /** Marks one class as filled without touching the floor or any other class - for
     * auto-updating recruit state from a detected party-join message (once that message's
     * real format is confirmed) rather than requiring a full /frost recruit re-entry every
     * time someone joins. */
    fun markClassFilled(cls: DungeonClass) {
        recruitFilledClasses.add(cls)
    }

    // Only re-applies (and re-notifies) when the parsed listing actually differs from last
    // time - Party Finder is polled repeatedly while open (see Frost.kt), and re-announcing
    // an unchanged sync every poll would be spammy.
    private var lastScannedPartyInfo: PartyFinderInfo? = null

    /** If [screen] is the Party Finder inventory screen, scans its item slots for the
     * listing marked "You are in this party!" and, if found and different from last scan,
     * replaces the recruit floor/filled-classes with it wholesale (not additive like
     * [markClassFilled] - Party Finder shows the party's current ground truth at that
     * instant, including anyone who's since left, so a full replace is the correct sync). */
    fun scanPartyFinderScreen(screen: Screen) {
        if (!ModConfig.isRecruitEnabled()) return
        if (screen !is AbstractContainerScreen<*>) return
        if (TextUtils.stripFormatting(screen.title.string) != "Party Finder") return

        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val player = client.player ?: return
        val tooltipContext = Item.TooltipContext.of(level)

        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty) continue
            val lines = stack.getTooltipLines(tooltipContext, player, TooltipFlag.NORMAL)
                .map { TextUtils.stripFormatting(it.string) }
            val info = PartyFinderParser.parseOwnPartyListing(lines) ?: continue
            if (info == lastScannedPartyInfo) return
            lastScannedPartyInfo = info

            if (info.floorLabel != null) {
                setRecruitState(info.floorLabel, info.members.map { it.dungeonClass }.toSet())
            } else {
                recruitFilledClasses.clear()
                recruitFilledClasses.addAll(info.members.map { it.dungeonClass })
            }
            val filledText = if (recruitFilledClasses.isEmpty()) "none" else recruitFilledClasses.joinToString(", ") { it.abbreviation }
            player.sendSystemMessage(
                Component.literal("${TextUtils.FROST_TAG}§aSynced from Party Finder: §f${recruitFloorLabel ?: "?"} §7- filled: §f$filledText"),
            )
            return
        }
    }

    private fun currentInviteMessage(): String? {
        val floor = recruitFloorLabel ?: return null
        return PartyInviteMessage.build(floor, recruitFilledClasses)
    }

    /** Updates [cache] from a live "Friend > X joined/left" notification (see
     * [FriendPresenceParser]) - keeps tag-filtered views ([showTab]) accurate between /fl
     * views instead of only ever refreshing when a page is actually viewed, and can grow
     * the cache with friends never seen on a viewed page yet. Always replaces with a fresh
     * minimal entry rather than preserving whatever status/location was cached before -
     * that's necessarily stale the moment presence flips (a location seen while they were
     * last online tells you nothing once they've gone offline and come back). Respects the
     * Better Friends List feature toggle like everything else here. */
    fun handlePresenceChange(change: FriendPresenceChange) {
        if (!ModConfig.isBetterFriendsListEnabled()) return
        cache[change.username] = FriendEntry(change.username, if (change.online) "Online" else "Offline", change.online)
    }

    /** Called from ClientSendMessageEvents.COMMAND for every command the player sends -
     * opens a capture window only if it looks like a friends-list command AND the Better
     * Friends List feature is turned on (its FeatureButton, under Quality of Life) - off
     * means /fl shows Hypixel's own unmodified output, untouched. */
    fun onOutgoingCommand(commandText: String) {
        if (!ModConfig.isBetterFriendsListEnabled()) return
        if (!capturing && !refreshMode && TRIGGER_COMMAND.matches(commandText.trim())) beginCapture()
    }

    /** /frost friends - equivalent to typing /fl yourself, for anyone who'd rather not
     * remember Hypixel's own command name. */
    fun requestFriendsList() = requestSpecificPage(1)

    /** /frost friends page <n> - what the reconstructed "next page" link in
     * FriendsChatFormatter.pageHeader actually points at, rather than the raw
     * `/friend list <n>` command: a live test showed that clicking a chat link's
     * RunCommand doesn't reliably fire ClientSendMessageEvents.COMMAND the way typed input
     * does, so [onOutgoingCommand] never saw it and the capture window never opened for
     * page 2 onward. Explicitly starting the capture here instead sidesteps that entirely -
     * this doesn't depend on the SEND event firing at all. */
    fun requestSpecificPage(page: Int) {
        if (capturing || refreshMode) return
        beginCapture()
        requestPage(page)
    }

    private fun beginCapture() {
        capturing = true
        captureStartTick = localTick
        lastActivityTick = localTick
        headerPage = null
        headerTotalPages = null
        capturedLines.clear()
    }

    /** Called from the ALLOW_GAME/ALLOW_CHAT listeners for every incoming message, with
     * [text] the RAW (not formatting-stripped) message string - unlike everywhere else in
     * this mod, [FriendsParser.parseMessage] needs the original §-codes intact to read each
     * friend's rank color off their username; it strips per-line internally for the actual
     * structural matching. Returns true if anything recognizable was found and should be
     * suppressed from chat - see [FriendsParser.parseMessage] for why this handles a whole
     * message rather than assuming one chat line per call. */
    fun handleIncomingLine(text: String): Boolean {
        if (!capturing) return false
        val (header, entries) = FriendsParser.parseMessage(text)
        if (header == null && entries.isEmpty()) return false
        if (header != null) {
            headerPage = header.first
            headerTotalPages = header.second
        }
        capturedLines.addAll(entries)
        lastActivityTick = if (header != null) {
            // The header showing up confirms this one message already contained the whole
            // page (Hypixel sends it that way, not one message per line - see
            // FriendsParser.parseMessage) - there's nothing left to wait for, so let the
            // very next tick finish immediately instead of sitting through the full
            // QUIET_TICKS delay every time. That delay was noticeably sluggish in practice
            // (close to a second, once network latency is added on top) for a wait that
            // was never actually needed once the confirmed message shape is accounted for.
            localTick - QUIET_TICKS
        } else {
            // No header in this particular message - either it's an unrelated line that
            // still matched the strict online-format pattern (see parseMessage), or a
            // header genuinely hasn't arrived yet. Keep waiting normally rather than
            // assuming this was everything.
            localTick
        }
        return true
    }

    /** Call every client tick - ends the capture window once it's gone quiet or hit the
     * hard cap. */
    fun tick() {
        localTick++
        if (!capturing) return
        val quiet = localTick - lastActivityTick >= QUIET_TICKS
        val expired = localTick - captureStartTick >= MAX_CAPTURE_TICKS
        if (quiet || expired) finishCapture()
    }

    private fun finishCapture() {
        capturing = false
        val page = headerPage
        for (entry in capturedLines) cache[entry.username] = entry

        if (page != null && capturedLines.isEmpty()) {
            // A header came through but nothing looked like a friend line - almost
            // certainly a format mismatch worth knowing about rather than silently
            // relaying an empty page.
            logger.warn("Friends page $page header recognized but 0 friend lines matched - the chat format may not match what FriendsParser expects.")
        }

        if (refreshMode) {
            continueRefresh(page)
            return
        }
        if (page == null || capturedLines.isEmpty()) {
            // Nothing recognizable came back (e.g. zero friends, or the format didn't
            // match) - nothing to relay; whatever Hypixel actually sent (if it wasn't
            // suppressed) speaks for itself.
            return
        }
        sendRelayMessage(page, headerTotalPages, capturedLines)
    }

    private fun sendRelayMessage(page: Int, totalPages: Int?, entries: List<FriendEntry>) {
        val player = Minecraft.getInstance().player ?: return
        val inviteMessage = currentInviteMessage()
        val allTags = ModConfig.getFriendTags()
        val knownTags = ModConfig.allFriendTagNames().sorted()
        val showRecruit = ModConfig.isRecruitEnabled()
        player.sendSystemMessage(FriendsChatFormatter.divider())
        player.sendSystemMessage(FriendsChatFormatter.pageHeader(page, totalPages))
        player.sendSystemMessage(FriendsChatFormatter.tabsLine(knownTags))
        for (entry in entries) {
            val tags = ModConfig.tagsForFriend(entry.username)
            val tagColor = FriendsChatFormatter.resolveTagColor(allTags, entry.username)
            player.sendSystemMessage(
                FriendsChatFormatter.friendLine(entry, tags, inviteMessage, tagColor, showTags = true, showRecruitButton = showRecruit),
            )
        }
        player.sendSystemMessage(FriendsChatFormatter.divider())
    }

    /** /frost friendstab <tag> - reprints from [cache], filtered and sorted alphabetically by
     * username (unlike the raw /fl page, which mirrors whatever order Hypixel's pages come
     * in). "All" shows everyone (online and offline) with tags still shown inline, since it
     * mixes friends from every tag together; any other (custom) tag hides offline friends
     * (nothing to do with an offline entry in a tag-filtered view but clutter it) AND hides
     * the tag suffix - there's no reason to repeat "this friend is in m5" on the m5 page
     * itself. */
    fun showTab(tabName: String) {
        val player = Minecraft.getInstance().player ?: return
        val filtered = if (tabName == "All") {
            cache.values.toList()
        } else {
            val members = ModConfig.getFriendTags().find { it.name == tabName }?.members ?: emptyList()
            cache.values.filter { it.isOnline && it.username in members }
        }.sortedBy { it.username.lowercase() }
        if (filtered.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("${TextUtils.FROST_TAG}§7No cached friends match '$tabName' yet - view a friends page first, or /frost friends refresh."),
            )
            return
        }
        val inviteMessage = currentInviteMessage()
        val allTags = ModConfig.getFriendTags()
        val knownTags = ModConfig.allFriendTagNames().sorted()
        val showTags = tabName == "All"
        val showRecruit = ModConfig.isRecruitEnabled()
        player.sendSystemMessage(FriendsChatFormatter.divider())
        player.sendSystemMessage(FriendsChatFormatter.tabHeader(tabName, filtered.size))
        player.sendSystemMessage(FriendsChatFormatter.tabsLine(knownTags))
        for (entry in filtered) {
            val tags = ModConfig.tagsForFriend(entry.username)
            val tagColor = FriendsChatFormatter.resolveTagColor(allTags, entry.username)
            player.sendSystemMessage(
                FriendsChatFormatter.friendLine(entry, tags, inviteMessage, tagColor, showTags = showTags, showRecruitButton = showRecruit),
            )
        }
        player.sendSystemMessage(FriendsChatFormatter.divider())
    }

    /** /frost friends refresh - walks every page once (sequentially, waiting for each
     * page's response the same way a single page normally would) to build a complete
     * cache, for tag views that need every friend rather than just whichever pages have
     * been viewed recently. [onDone] fires with the total number of friends cached. */
    fun refreshAll(onDone: (Int) -> Unit) {
        if (capturing || refreshMode) return
        onRefreshDone = onDone
        refreshTotalPages = null
        refreshNextPage = 1
        refreshMode = true
        beginCapture()
        requestPage(1)
    }

    private fun continueRefresh(page: Int?) {
        if (page != null) refreshTotalPages = headerTotalPages ?: refreshTotalPages
        val total = refreshTotalPages
        if (total == null) {
            // Never even got page 1's header back (e.g. zero friends) - stop here rather
            // than looping forever with nothing to learn from.
            finishRefresh()
            return
        }
        if (refreshNextPage >= total) {
            finishRefresh()
            return
        }
        refreshNextPage++
        beginCapture()
        requestPage(refreshNextPage)
    }

    private fun finishRefresh() {
        refreshMode = false
        val callback = onRefreshDone
        onRefreshDone = null
        callback?.invoke(cache.size)
    }

    private fun requestPage(page: Int) {
        Minecraft.getInstance().connection?.sendCommand("friend list $page")
    }

    // Keycodes for tag hotkeys currently held down - tracked separately from
    // Frost.kt's own custom-keybind held-set so the two features can't interfere with
    // each other, and so a key bound to both fires each independently.
    private val heldTagHotkeys: MutableSet<Int> = mutableSetOf()

    /** Call every client tick. While the chat screen is open, a bound tag's key going from
     * up to down looks at whatever friend's line is currently under the mouse (see
     * [resolveHoveredUsername]) and adds them to that tag - no menu needed. Only runs while
     * [ChatScreen] is open (that's the only time the mouse cursor is actually free to hover
     * chat at all - normal gameplay keeps it locked for camera control), mirroring how
     * clicking the [Invite]/tab links already only works with chat open too.
     *
     * Known limitation: this polls raw key-down state rather than intercepting the
     * keystroke, so a bound key that's also a printable character will still get typed into
     * the chat input box if it's focused - there's no mixin here to suppress that. Binding a
     * function key (F1-F12) or similar avoids the collision entirely. */
    fun checkTagHotkeys(client: Minecraft) {
        if (client.screen !is ChatScreen || !ModConfig.isBetterFriendsListEnabled()) {
            if (heldTagHotkeys.isNotEmpty()) heldTagHotkeys.clear()
            return
        }
        val tags = ModConfig.getFriendTags()
        val boundKeyCodes = tags.mapNotNullTo(mutableSetOf()) { it.keyCode.takeIf { code -> code >= 0 } }
        heldTagHotkeys.retainAll(boundKeyCodes)

        val window = client.window
        for (tag in tags) {
            if (tag.keyCode < 0 || tag.name.isBlank()) continue
            val pressed = InputConstants.isKeyDown(window, tag.keyCode)
            val wasHeld = tag.keyCode in heldTagHotkeys
            if (pressed && !wasHeld) {
                heldTagHotkeys.add(tag.keyCode)
                tagHoveredFriend(client, tag)
            } else if (!pressed && wasHeld) {
                heldTagHotkeys.remove(tag.keyCode)
            }
        }
    }

    private fun tagHoveredFriend(client: Minecraft, tag: FriendTag) {
        val username = resolveHoveredUsername(client)
        val player = client.player
        if (username == null) {
            player?.sendSystemMessage(Component.literal("${TextUtils.FROST_TAG}§7Hover a friend's line in chat first."))
            return
        }
        ModConfig.addMemberToTag(tag, username)
        player?.sendSystemMessage(Component.literal("${TextUtils.FROST_TAG}§aTagged $username as ${tag.name}."))
    }

    /** Finds which friend's line (if any) is currently under the mouse cursor, by asking the
     * chat HUD the same way vanilla does for its own hover tooltips/click handling
     * (ActiveTextCollector.ClickableStyleFinder), then reading back the username each
     * friend line carries via Style.insertion (see FriendsChatFormatter.friendLine) - insertion
     * has no visible effect on its own, it's purely a place to stash identifying data on a
     * run of text, the same trick vanilla uses for shift-click-to-insert-a-name. */
    private fun resolveHoveredUsername(client: Minecraft): String? {
        val window = client.window
        val mouseX = client.mouseHandler.getScaledXPos(window).toInt()
        val mouseY = client.mouseHandler.getScaledYPos(window).toInt()
        val finder = ActiveTextCollector.ClickableStyleFinder(client.font, mouseX, mouseY).includeInsertions(true)
        client.gui.chat.captureClickableText(finder, window.guiScaledHeight, client.gui.guiTicks, ChatComponent.DisplayMode.FOREGROUND)
        return finder.result()?.insertion
    }
}
