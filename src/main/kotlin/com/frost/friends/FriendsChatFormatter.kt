package com.frost.friends

import com.frost.config.FriendTag
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Builds the actual chat lines [com.frost.friends.FriendsChatRelay] sends in place of
 * Hypixel's own `/friend list` output - kept pure (no Minecraft client/network calls) so
 * the exact text and click/hover behavior of each line is unit-testable without a running
 * game, the same way [com.frost.summary.RunSummary] is.
 */
object FriendsChatFormatter {
    // Gray - Hypixel's own default rank color for a non-donor username, used whenever a
    // friend's line carries no captured nameColor (see FriendEntry) and as the tag bracket's
    // own default when no tag color is set.
    private const val DEFAULT_GRAY = '7'

    /** Which legacy color code the "[tag1, tag2]" suffix after a friend's name should render
     * in, based on [tags] (the full defined tag list, not just the ones this friend belongs
     * to - needed to look up each candidate tag's own color). The first tag (in list order)
     * that both has a color set AND includes this friend wins; a friend in no colored tag
     * gets the plain gray the tag suffix always used to render in, rather than an arbitrary
     * fallback. There's no tag ordering UI yet, so "first in the list" is simply whichever
     * order they were created in. Deliberately doesn't touch the username's own color
     * anymore - that now always shows Hypixel's own rank color for that player (see
     * [friendLine]/[FriendEntry.nameColor]), not a tag color. */
    fun resolveTagColor(tags: List<FriendTag>, username: String): Char {
        val tag = tags.firstOrNull { username in it.members && it.color != null } ?: return DEFAULT_GRAY
        // ChatFormatting is a Java enum, not Kotlin - .entries isn't generated for it, only
        // the static .values() method is available.
        val formatting = ChatFormatting.values().find { it.name == tag.color } ?: return DEFAULT_GRAY
        // MC 26.3 made ChatFormatting's code char private (no getChar() anymore), so it's
        // recovered by reverse lookup through the still-public getByCode.
        return LEGACY_FORMAT_CODES.firstOrNull { ChatFormatting.getByCode(it) == formatting } ?: DEFAULT_GRAY
    }

    // Every legacy formatting code character, colors (0-f) then styles (k-o) and reset (r).
    private const val LEGACY_FORMAT_CODES = "0123456789abcdefklmnor"
    /** A plain divider line, printed above and below the whole friends block so it reads as
     * clearly separate from surrounding chat - same visual language as RunSummary's own
     * room-breakdown borders. */
    fun divider(): Component = Component.literal("§6§l-----------------------------------------------------")

    /** The page header, with recreated clickable "previous"/"next page" links where
     * Hypixel's own ones would have been (those are part of the suppressed original
     * message, so they have to be rebuilt here rather than reused). Both point at our own
     * `/frost friends page <n>` rather than the raw `/friend list <n>` - a clicked chat link
     * doesn't reliably fire the outgoing-command event our capture window normally opens
     * from, so this needs a command that starts capture itself instead (see
     * FriendsChatRelay.requestSpecificPage). "[<<]" appears whenever [page] isn't the first
     * one - unlike Hypixel's own header (next-page only), so paging backwards doesn't
     * require remembering/retyping the page number by hand. "[>>]" appears whenever
     * [totalPages] is known and [page] isn't the last one. */
    fun pageHeader(page: Int, totalPages: Int?): Component {
        val line = Component.literal("")
        if (page > 1) {
            line.append(
                Component.literal("[<<]").withStyle {
                    it.withClickEvent(ClickEvent.RunCommand("/frost friends page ${page - 1}"))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("Previous page")))
                },
            )
            line.append(Component.literal(" "))
        }
        line.append(Component.literal("§b§lFriends §7(Page $page${totalPages?.let { " of $it" } ?: ""})"))
        if (totalPages != null && page < totalPages) {
            line.append(Component.literal(" "))
            line.append(
                Component.literal("[>>]").withStyle {
                    it.withClickEvent(ClickEvent.RunCommand("/frost friends page ${page + 1}"))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("Next page")))
                },
            )
        }
        return line
    }

    /** Header for a cached, tag-filtered view (see /frost friendstab) - not paginated, since
     * it's a filter over everything seen so far rather than one live page. */
    fun tabHeader(tabName: String, count: Int): Component =
        Component.literal("§b§lFriends §7- $tabName ($count cached)")

    /** Clickable "[All] [tag1] [tag2] ..." row - clicking one reprints the roster filtered
     * to it via /frost friendstab, from whatever's been cached so far. No "Untagged" entry -
     * just "All" plus whatever custom tags actually exist. */
    fun tabsLine(knownTags: List<String>): Component {
        val allTabs = listOf("All") + knownTags
        val line = Component.literal("")
        allTabs.forEachIndexed { index, tab ->
            if (index > 0) line.append(Component.literal(" "))
            line.append(
                Component.literal("[$tab]").withStyle {
                    it.withClickEvent(ClickEvent.RunCommand("/frost friendstab $tab"))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("View: $tab")))
                },
            )
        }
        return line
    }

    /** Cuts the leading "SkyBlock" piece out of a friend's status entirely and returns just
     * their actual location - confirmed live status text is things like "SkyBlock - Private
     * Island" (-> "Private Island") or "SkyBlock - Master Mode The Catacombs - [MM] Floor
     * VII" (-> "Master Mode The Catacombs", the first piece of what's left once "SkyBlock"
     * itself is dropped). A status with no "SkyBlock" prefix at all - "Limbo" - passes
     * through unchanged, since there's nothing to cut. */
    private fun extractLocation(status: String): String {
        val parts = status.split(" - ")
        return if (parts.firstOrNull() == "SkyBlock" && parts.size > 1) parts[1] else parts[0]
    }

    // Hypixel's own gold - queueing up in the Dungeon Hub is the moment a friend's most
    // recruitable, so it's worth standing out from the plain white every other location
    // renders in.
    private const val DUNGEON_HUB_COLOR = '6'
    private const val DEFAULT_LOCATION_COLOR = 'f'

    private fun locationColor(location: String): Char =
        if (location == "Dungeon Hub") DUNGEON_HUB_COLOR else DEFAULT_LOCATION_COLOR

    /** One friend's line, e.g. "§7Frostwing §7[M5] §7- §fPrivate Island" - the username always
     * renders in [FriendEntry.nameColor] (Hypixel's own rank color for that player, captured
     * off the raw chat line - see [FriendsParser.parseMessage]), falling back to gray when it
     * couldn't be captured (e.g. a caller that built a [FriendEntry] directly, without going
     * through the real parser). [tags] render immediately after the username - right where
     * this example shows "[M5]" - only when [showTags] is true (the main /fl page and the
     * "All" tab show them since they mix friends from different tags together; a specific
     * tag's page hides them since you already know which tag you're looking at - see
     * FriendsChatRelay.showTab), in [tagColor] (see [resolveTagColor]) rather than the
     * username's own color. The location itself (see [extractLocation]) renders gold when
     * it's "Dungeon Hub" (see [locationColor]) - the moment a friend's most recruitable is
     * worth standing out from the plain white every other location renders in.
     *
     * Two trailing icons appear for [entry]'s that are online (there's no one to actually
     * message or invite otherwise): [showRecruitButton] gates "[Recruit]" (only meaningful
     * while the separate Recruit feature is on) - it pastes `/msg <username> <inviteMessage>`
     * into the chat box (SuggestCommand, not RunCommand - it still requires pressing Enter to
     * actually send) rather than sending it the instant it's clicked, so an accidental click
     * can't invite the wrong person. If [inviteMessage] is null (no recruiting floor set yet
     * via /frost recruit), clicking it instead pastes /frost recruit with no arguments, which
     * prints current state/usage as a reminder rather than silently doing nothing. "[Invite]"
     * always appears (part of Better Friends List itself, not Recruit) and instantly runs
     * `/p <username>` (RunCommand) - a real party invite is a much lower-stakes accidental
     * click than pinging a friend with a recruit message, so it doesn't need the
     * paste-and-confirm treatment. */
    fun friendLine(
        entry: FriendEntry,
        tags: Set<String>,
        inviteMessage: String?,
        tagColor: Char = DEFAULT_GRAY,
        showTags: Boolean = true,
        showRecruitButton: Boolean = true,
    ): Component {
        val nameColor = entry.nameColor ?: DEFAULT_GRAY
        val tagSuffix = if (!showTags || tags.isEmpty()) "" else " §$tagColor[${tags.sorted().joinToString(", ")}]"
        val location = extractLocation(entry.status)
        // The insertion carries no visible/clickable effect on its own - it's read back out
        // by FriendsChatRelay.resolveHoveredUsername to identify which friend is under the
        // mouse when a tag hotkey is pressed, the same "invisible metadata riding on a
        // Style" trick vanilla uses for shift-click-to-insert-a-name.
        val line = Component.literal("§$nameColor${entry.username}$tagSuffix §7- §${locationColor(location)}$location")
            .withStyle { it.withInsertion(entry.username) }
        if (!entry.isOnline) return line

        var result = line
        if (showRecruitButton) {
            val hoverText = inviteMessage ?: "Set your recruiting floor first - click to open /frost recruit"
            val command = if (inviteMessage != null) "/msg ${entry.username} $inviteMessage" else "/frost recruit"
            val recruitIcon = Component.literal("[Recruit]").withStyle {
                it.withClickEvent(ClickEvent.SuggestCommand(command))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(hoverText)))
            }
            result = result.append(Component.literal(" ")).append(recruitIcon)
        }
        val inviteIcon = Component.literal("[Invite]").withStyle {
            it.withClickEvent(ClickEvent.RunCommand("/p ${entry.username}"))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Invite ${entry.username} to your party")))
        }
        result = result.append(Component.literal(" ")).append(inviteIcon)
        return result
    }
}
