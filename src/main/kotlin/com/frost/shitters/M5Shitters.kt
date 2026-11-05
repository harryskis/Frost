package com.frost.shitters

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.util.TextUtils
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.inventory.Slot
import java.lang.reflect.Field
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.UUID

/**
 * M5 Shitters: players on the list have their names shown in red - in chat, in item tooltips
 * (which is where Party Finder lists each party's members) and in the tab list. Clicking a party
 * in the Party Finder prints why any of its members are on the list, and so does clicking a red
 * name in chat, such as the "joined the dungeon group" message.
 *
 * The list is the database at the configured URL, fetched in the background and every 15
 * minutes after, plus any players added by hand with /frost shitters add.
 */
object M5Shitters {
    private const val REFRESH_MILLIS = 15 * 60 * 1000L
    private const val URL_SETTLE_MILLIS = 2000L
    private const val TAB_INTERVAL_TICKS = 20
    private const val PARTY_FINDER_TITLE = "Party Finder"
    private const val REASON_COMMAND = "/frost shitters reason "

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile private var records: List<ShitterRecord> = emptyList()
    private var remote: Map<String, ShitterEntry> = emptyMap()
    private var remoteRecords: List<ShitterRecord>? = null
    private var remoteNamesVersion = -1
    @Volatile private var fetchedUrl: String? = null
    @Volatile private var fetching = false
    @Volatile private var lastFetchMillis = 0L
    private var urlEditedAt = 0L
    private var tickCounter = 0

    private var cache: Map<String, ShitterEntry> = emptyMap()
    private var cacheRemote: Map<String, ShitterEntry>? = null
    private var cacheLocal: Map<String, String>? = null

    // Tab list names swapped for highlighted ones, by player: the original, and what replaced it.
    private val tabOriginals = HashMap<UUID, Component>()
    private val tabReplacements = HashMap<UUID, Component>()

    // Minecraft 26 ships with its real names, so the hovered slot can be read by name.
    private val hoveredSlotField: Field? = runCatching {
        AbstractContainerScreen::class.java.getDeclaredField("hoveredSlot").apply { isAccessible = true }
    }.onFailure { Frost.logger.warn("M5 Shitters can't read the hovered slot, so Party Finder clicks won't show reasons", it) }.getOrNull()

    /** The database's players by lowercase name, rebuilt as UUIDs' names come in. A player only
     * listed by UUID isn't on it until their name is known; a known current name wins over the
     * one the database has, in case they've changed it. */
    private fun remoteEntries(): Map<String, ShitterEntry> {
        val current = records
        if (current !== remoteRecords || UuidNames.version != remoteNamesVersion) {
            remoteNamesVersion = UuidNames.version
            remoteRecords = current
            remote = current.mapNotNull { record ->
                val name = record.uuid?.let(UuidNames::nameOf) ?: record.name ?: return@mapNotNull null
                name.lowercase() to ShitterEntry(name, record.reason)
            }.toMap()
        }
        return remote
    }

    /** Everyone on the list, by lowercase name: the database's, with the hand-added on top. */
    fun entries(): Map<String, ShitterEntry> {
        val remote = remoteEntries()
        val local = ModConfig.getLocalShitters()
        if (cacheRemote !== remote || cacheLocal != local) {
            cache = remote + local.map { (name, reason) -> name.lowercase() to ShitterEntry(name, reason) }
            cacheRemote = remote
            cacheLocal = HashMap(local)
        }
        return cache
    }

    fun lookup(name: String): ShitterEntry? = entries()[name.lowercase()]

    fun register() {
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
            if (overlay || !ModConfig.isM5ShittersEnabled()) message else highlight(message, interactive = true) ?: message
        }
        ItemTooltipCallback.EVENT.register { _, _, _, lines ->
            if (ModConfig.isM5ShittersEnabled()) {
                for (index in lines.indices) highlight(lines[index], interactive = false)?.let { lines[index] = it }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*>) {
                ScreenMouseEvents.beforeMouseClick(screen).register { clicked, _ -> onContainerClick(clicked) }
            }
        }
    }

    /**
     * [component] with every listed name in red, or null if it has none. In chat ([interactive])
     * a name also shows its reason on hover and prints it on click, unless it already does
     * something of its own there. [listed] is by lowercase name. The text is rebuilt piece by piece, so everything else about it
     * - colors, including legacy codes in the text, and click and hover actions - stays as it was.
     */
    fun highlight(component: Component, interactive: Boolean, listed: Map<String, ShitterEntry> = entries()): Component? {
        if (listed.isEmpty()) return null
        val isListed = { name: String -> name.lowercase() in listed }
        // Almost nothing has a listed name in it, so check the whole text before taking it apart.
        if (ShitterText.findNames(component.string, isListed).isEmpty()) return null
        val result = Component.empty()
        var changed = false
        component.visit<Unit>({ style, text ->
            val spans = ShitterText.findNames(text, isListed)
            if (spans.isEmpty()) {
                result.append(Component.literal(text).setStyle(style))
            } else {
                changed = true
                appendHighlighted(result, text, style, spans, listed, interactive)
            }
            Optional.empty()
        }, Style.EMPTY)
        return result.takeIf { changed }
    }

    private fun appendHighlighted(
        result: MutableComponent,
        text: String,
        style: Style,
        spans: List<ShitterText.Span>,
        listed: Map<String, ShitterEntry>,
        interactive: Boolean,
    ) {
        var last = 0
        for (span in spans) {
            // Text after a name picks back up in whatever color codes were in effect there.
            val resume = if (last == 0) "" else ShitterText.activeCodes(text, last)
            if (span.start > last) result.append(Component.literal(resume + text.substring(last, span.start)).setStyle(style))
            val entry = listed.getValue(span.name.lowercase())
            var nameStyle = style.withColor(ChatFormatting.RED)
            if (interactive) {
                if (style.hoverEvent == null) nameStyle = nameStyle.withHoverEvent(HoverEvent.ShowText(reasonTooltip(entry)))
                if (style.clickEvent == null) nameStyle = nameStyle.withClickEvent(ClickEvent.RunCommand(REASON_COMMAND + entry.name))
            }
            // Bold and the like carry over to the name; only its color changes.
            result.append(Component.literal(ShitterText.activeFormats(text, span.start) + span.name).setStyle(nameStyle))
            last = span.end
        }
        if (last < text.length) result.append(Component.literal(ShitterText.activeCodes(text, last) + text.substring(last)).setStyle(style))
    }

    private fun reasonTooltip(entry: ShitterEntry): Component =
        Component.literal("§c§lM5 Shitters\n§7Reason: §f${entry.reason}\n§8Click to show it in chat.")

    private fun onTick(client: Minecraft) {
        if (++tickCounter % TAB_INTERVAL_TICKS == 0) {
            learnNamesInGame(client)
            updateTabList(client)
        }
        if (!ModConfig.isM5ShittersEnabled()) return
        UuidNames.tick()
        val url = ModConfig.getM5ShittersUrl()
        if (url.isBlank()) {
            if (fetchedUrl != null) {
                records = emptyList()
                fetchedUrl = null
            }
            return
        }
        val now = System.currentTimeMillis()
        val newUrl = url != fetchedUrl && now - urlEditedAt > URL_SETTLE_MILLIS
        if (!fetching && (newUrl || now - lastFetchMillis > REFRESH_MILLIS)) fetch(url, report = false)
    }

    /** Called as the database URL is typed, so it's only fetched once typing stops. */
    fun onUrlEdited() {
        urlEditedAt = System.currentTimeMillis()
    }

    /** Fetches the database now, saying in chat how it went. */
    fun reload() {
        val url = ModConfig.getM5ShittersUrl()
        if (url.isBlank()) {
            message("§7No database URL set - only the players added with /frost shitters add are on the list (${ModConfig.getLocalShitters().size}).")
            return
        }
        if (fetching) {
            message("§7Already fetching the list.")
            return
        }
        fetch(url, report = true)
    }

    private fun fetch(url: String, report: Boolean) {
        val request = runCatching {
            HttpRequest.newBuilder(URI.create(ShitterDatabase.directUrl(url)))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Frost (Minecraft mod)")
                .GET()
                .build()
        }.getOrElse {
            fetchedUrl = url
            lastFetchMillis = System.currentTimeMillis()
            if (report) message("§cThat database URL isn't a valid link.")
            return
        }
        fetching = true
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            fetching = false
            fetchedUrl = url
            lastFetchMillis = System.currentTimeMillis()
            val failed = error != null || response.statusCode() !in 200..299
            val outcome = when {
                error != null -> "§cCouldn't reach the database: ${error.message ?: error.javaClass.simpleName}"
                response.statusCode() !in 200..299 -> "§cThe database answered with HTTP ${response.statusCode()}."
                else -> {
                    val parsed = ShitterDatabase.parse(response.body())
                    records = parsed
                    val uuids = parsed.mapNotNull { it.uuid }
                    UuidNames.request(uuids)
                    val unnamed = UuidNames.unresolved(uuids)
                    when {
                        parsed.isEmpty() -> "§eThe database loaded, but no players could be read from it."
                        unnamed > 0 -> "§aLoaded ${parsed.size} players from the database. §7Looking up the names of $unnamed - they'll go red as they come in."
                        else -> "§aLoaded ${parsed.size} players from the database."
                    }
                }
            }
            if (failed) Frost.logger.warn("M5 Shitters fetch from $url failed: $outcome")
            if (report) Minecraft.getInstance().execute { message(outcome) }
        }
    }

    /** Players on the list by UUID who are in this server's player list - the tab list and anyone
     * nearby - have their current name right there, so it's taken from there. */
    private fun learnNamesInGame(client: Minecraft) {
        if (!ModConfig.isM5ShittersEnabled()) return
        val connection = client.connection ?: return
        val listedUuids = records.mapNotNullTo(HashSet()) { it.uuid }
        if (listedUuids.isEmpty()) return
        for (info in connection.onlinePlayers) {
            val profile = info.profile
            val uuid = profile.id.toString().replace("-", "")
            if (uuid in listedUuids && ShitterText.isUsername(profile.name) && UuidNames.nameOf(uuid) != profile.name) UuidNames.learn(uuid, profile.name)
        }
    }

    /** Swaps listed names in the tab list for red ones. Hypixel rewrites these lines all the time,
     * so it's redone every second, and undone when the feature is turned off. */
    private fun updateTabList(client: Minecraft) {
        val connection = client.connection
        if (connection == null) {
            tabOriginals.clear()
            tabReplacements.clear()
            return
        }
        val enabled = ModConfig.isM5ShittersEnabled()
        for (info in connection.listedOnlinePlayers) {
            val id = info.profile.id
            val current = info.tabListDisplayName ?: continue
            val ours = tabReplacements[id]
            val original = if (ours != null && current === ours) tabOriginals.getValue(id) else current
            val highlighted = if (enabled) highlight(original, interactive = false) else null
            if (highlighted == null) {
                if (current !== original) info.setTabListDisplayName(original)
                tabOriginals.remove(id)
                tabReplacements.remove(id)
                continue
            }
            if (ours != null && current === ours && highlighted == ours) continue
            tabOriginals[id] = original
            tabReplacements[id] = highlighted
            info.setTabListDisplayName(highlighted)
        }
    }

    /** Clicking a party in the Party Finder: says why any of its members are on the list. The
     * click itself goes through as normal. */
    private fun onContainerClick(screen: Screen) {
        if (!ModConfig.isM5ShittersEnabled()) return
        if (TextUtils.stripFormatting(screen.title.string) != PARTY_FINDER_TITLE) return
        val slot = runCatching { hoveredSlotField?.get(screen) as? Slot }.getOrNull() ?: return
        val stack = slot.item
        if (stack.isEmpty) return
        val texts = listOf(stack.hoverName) + stack.get(DataComponents.LORE)?.lines().orEmpty()
        val listed = entries()
        val found = texts.flatMap { text -> ShitterText.findNames(text.string) { it.lowercase() in listed } }
            .map { listed.getValue(it.name.lowercase()) }
            .distinct()
        for (entry in found) message(reasonLine(entry))
    }

    private fun reasonLine(entry: ShitterEntry) = "§c${entry.name}§7 is on the M5 Shitters list: §f${entry.reason}"

    private fun message(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(TextUtils.FROST_TAG + text))
    }

    /** /frost shitters: add, remove, reason, list and reload. */
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal("shitters")
        .executes {
            message("§7${entries().size} players on the M5 Shitters list. §8/frost shitters add|remove|reason|list|reload")
            1
        }
        .then(
            ClientCommands.literal("reason").then(
                ClientCommands.argument("name", StringArgumentType.word()).executes { context ->
                    val name = StringArgumentType.getString(context, "name")
                    message(lookup(name)?.let(::reasonLine) ?: "§a$name§7 isn't on the M5 Shitters list.")
                    1
                },
            ),
        )
        .then(
            ClientCommands.literal("add").then(
                ClientCommands.argument("name", StringArgumentType.word()).then(
                    ClientCommands.argument("reason", StringArgumentType.greedyString()).executes { context ->
                        val name = StringArgumentType.getString(context, "name")
                        if (!ShitterText.isUsername(name)) {
                            message("§c$name isn't a valid username.")
                        } else {
                            ModConfig.addLocalShitter(name, StringArgumentType.getString(context, "reason").trim())
                            message("§aAdded §c$name§a to the M5 Shitters list.")
                        }
                        1
                    },
                ),
            ),
        )
        .then(
            ClientCommands.literal("remove").then(
                ClientCommands.argument("name", StringArgumentType.word()).executes { context ->
                    val name = StringArgumentType.getString(context, "name")
                    val fromDatabase = name.lowercase() in remoteEntries()
                    message(
                        when {
                            ModConfig.removeLocalShitter(name) -> "§aRemoved $name from the players you added."
                            fromDatabase -> "§e$name is on the list from the database, so can't be removed here."
                            else -> "§7$name wasn't on the players you added."
                        },
                    )
                    1
                },
            ),
        )
        .then(
            ClientCommands.literal("list").executes {
                val local = ModConfig.getLocalShitters()
                val pending = UuidNames.unresolved(records.mapNotNull { it.uuid })
                val lookingUp = if (pending > 0) ", $pending names still being looked up" else ""
                message("§7${records.size} from the database$lookingUp, ${local.size} added by you.")
                for ((name, reason) in local.toSortedMap(String.CASE_INSENSITIVE_ORDER)) message("§c$name§7: §f$reason")
                1
            },
        )
        .then(ClientCommands.literal("reload").executes { reload(); 1 })
}
