package com.frost.friends

import com.frost.config.FriendTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendsChatFormatterTest {
    private fun clickCommand(component: Component): String? =
        (component.style.clickEvent as? ClickEvent.RunCommand)?.command()

    private fun suggestedCommand(component: Component): String? =
        (component.style.clickEvent as? ClickEvent.SuggestCommand)?.command()

    private fun findByText(component: Component, text: String): Component? =
        component.siblings.find { it.string == text }

    @Test
    fun `page header shows the page and total`() {
        val header = FriendsChatFormatter.pageHeader(1, 15)
        assertTrue(header.string.contains("Page 1 of 15"))
    }

    @Test
    fun `page header has a clickable next-page link pointing at our own paging command`() {
        // Not /friend list directly - see FriendsChatFormatter.pageHeader's doc comment for
        // why a clicked link can't rely on the same outgoing-command detection typed input
        // does.
        val header = FriendsChatFormatter.pageHeader(1, 15)
        val next = findByText(header, "[>>]")
        assertEquals("/frost friends page 2", next?.let(::clickCommand))
    }

    @Test
    fun `page header has a clickable previous-page link pointing at our own paging command`() {
        val header = FriendsChatFormatter.pageHeader(2, 15)
        val prev = findByText(header, "[<<]")
        assertEquals("/frost friends page 1", prev?.let(::clickCommand))
    }

    @Test
    fun `page header has no previous link on the first page`() {
        val header = FriendsChatFormatter.pageHeader(1, 15)
        assertNull(findByText(header, "[<<]"))
    }

    @Test
    fun `page header has no next link on the last page, but keeps the previous link`() {
        val header = FriendsChatFormatter.pageHeader(15, 15)
        assertNull(findByText(header, "[>>]"))
        assertEquals("/frost friends page 14", findByText(header, "[<<]")?.let(::clickCommand))
    }

    @Test
    fun `page header has no next or previous link on page 1 when the total page count is unknown`() {
        val header = FriendsChatFormatter.pageHeader(1, null)
        assertNull(findByText(header, "[>>]"))
        assertNull(findByText(header, "[<<]"))
    }

    @Test
    fun `page header still has a previous link past page 1 even when the total page count is unknown`() {
        val header = FriendsChatFormatter.pageHeader(2, null)
        assertEquals("/frost friends page 1", findByText(header, "[<<]")?.let(::clickCommand))
    }

    @Test
    fun `tab header shows the cached count`() {
        val header = FriendsChatFormatter.tabHeader("Dungeon Team", 4)
        assertTrue(header.string.contains("Dungeon Team"))
        assertTrue(header.string.contains("4 cached"))
    }

    @Test
    fun `tabs line always includes All plus every known tag, with no Untagged option`() {
        val line = FriendsChatFormatter.tabsLine(listOf("Dungeon Team"))
        assertTrue(line.string.contains("[All]"))
        assertTrue(line.string.contains("[Dungeon Team]"))
        assertTrue(!line.string.contains("[Untagged]"))
    }

    @Test
    fun `clicking a tab runs the friendstab command with that tab's name`() {
        val line = FriendsChatFormatter.tabsLine(listOf("Dungeon Team"))
        val tab = findByText(line, "[Dungeon Team]")
        assertEquals("/frost friendstab Dungeon Team", tab?.let(::clickCommand))
    }

    @Test
    fun `friend line shows username and status`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertTrue(line.string.contains("Ducky_"))
        assertTrue(line.string.contains("SkyBlock"))
    }

    @Test
    fun `friend line truncates the status to its first dash-delimited piece`() {
        val entry = FriendEntry("Nightowl3", "SkyBlock - Master Mode The Catacombs - [MM] Floor VII")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertTrue(line.string.contains("SkyBlock"))
        assertTrue(!line.string.contains("Catacombs"))
    }

    @Test
    fun `friend line leaves a status with no dash unchanged`() {
        val entry = FriendEntry("Emberfox", "Limbo")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertTrue(line.string.contains("Limbo"))
    }

    @Test
    fun `friend line carries the username as an insertion for the tag-hotkey hover feature`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertEquals("Ducky_", line.style.insertion)
    }

    @Test
    fun `friend line shows tags when present`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, setOf("Dungeon Team"), null)
        assertTrue(line.string.contains("Dungeon Team"))
    }

    @Test
    fun `friend line hides tags when showTags is false`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, setOf("Dungeon Team"), null, showTags = false)
        assertTrue(!line.string.contains("Dungeon Team"))
    }

    @Test
    fun `recruit icon pastes the built message rather than sending it immediately`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), "Join my M5 party! We are 3/5 missing BERS and ARCH.")
        val icon = findByText(line, "[Recruit]")
        assertEquals("/msg Ducky_ Join my M5 party! We are 3/5 missing BERS and ARCH.", icon?.let(::suggestedCommand))
        // Must be a suggestion, never an instant-send - an accidental click can't invite
        // the wrong person if it still requires pressing Enter to confirm.
        assertNull(icon?.let(::clickCommand))
    }

    @Test
    fun `recruit icon pastes recruit with no message set yet, rather than doing nothing`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        val icon = findByText(line, "[Recruit]")
        assertEquals("/frost recruit", icon?.let(::suggestedCommand))
        val hover = (icon?.style?.hoverEvent as? HoverEvent.ShowText)?.value()?.string
        assertTrue(hover?.contains("recruit") == true)
    }

    @Test
    fun `recruit icon is hidden when showRecruitButton is false`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), "Join my M5 party!", showRecruitButton = false)
        assertNull(findByText(line, "[Recruit]"))
    }

    @Test
    fun `invite icon instantly runs the party invite command`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        val icon = findByText(line, "[Invite]")
        assertEquals("/p Ducky_", icon?.let(::clickCommand))
    }

    @Test
    fun `invite icon still appears even when the recruit button is hidden`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null, showRecruitButton = false)
        val icon = findByText(line, "[Invite]")
        assertEquals("/p Ducky_", icon?.let(::clickCommand))
    }

    @Test
    fun `offline friends get no recruit or invite icon at all`() {
        val entry = FriendEntry("Ducky_", "Offline", isOnline = false)
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), "Join my M5 party! We are 3/5 missing BERS and ARCH.")
        assertNull(findByText(line, "[Recruit]"))
        assertNull(findByText(line, "[Invite]"))
        assertTrue(line.string.contains("Ducky_"))
    }

    @Test
    fun `friend line defaults the username to gray when no rank color was captured`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island")
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertTrue(line.string.startsWith("§7Ducky_"))
    }

    @Test
    fun `friend line renders the username in its captured Hypixel rank color`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island", nameColor = 'b')
        val line = FriendsChatFormatter.friendLine(entry, emptySet(), null)
        assertTrue(line.string.startsWith("§bDucky_"))
    }

    @Test
    fun `friend line renders the tag suffix in tagColor, not the username's own color`() {
        val entry = FriendEntry("Ducky_", "SkyBlock - Private Island", nameColor = 'b')
        val line = FriendsChatFormatter.friendLine(entry, setOf("Dungeon Team"), null, tagColor = '6')
        assertTrue(line.string.contains("§6[Dungeon Team]"))
        assertTrue(line.string.startsWith("§bDucky_ §6[Dungeon Team]"))
    }

    @Test
    fun `friend line places the tag suffix immediately after the username, before the status`() {
        val entry = FriendEntry("Frostwing", "SkyBlock - The Catacombs - Floor VII")
        val line = FriendsChatFormatter.friendLine(entry, setOf("M5"), null)
        assertTrue(line.string.contains("Frostwing §7[M5] §7- §fSkyBlock"))
    }

    @Test
    fun `resolveTagColor defaults to gray when the friend is in no colored tag`() {
        val tags = listOf(FriendTag("Dungeon Team", mutableListOf("Ducky_"), color = null))
        assertEquals('7', FriendsChatFormatter.resolveTagColor(tags, "Ducky_"))
    }

    @Test
    fun `resolveTagColor uses the color of the tag the friend belongs to`() {
        val tags = listOf(FriendTag("Dungeon Team", mutableListOf("Ducky_"), color = "AQUA"))
        assertEquals('b', FriendsChatFormatter.resolveTagColor(tags, "Ducky_"))
    }

    @Test
    fun `resolveTagColor ignores tags the friend is not a member of`() {
        val tags = listOf(FriendTag("Dungeon Team", mutableListOf("SomeoneElse"), color = "AQUA"))
        assertEquals('7', FriendsChatFormatter.resolveTagColor(tags, "Ducky_"))
    }

    @Test
    fun `resolveTagColor prefers the first colored tag when a friend is in more than one`() {
        val tags = listOf(
            FriendTag("Mules", mutableListOf("Ducky_"), color = "GOLD"),
            FriendTag("Dungeon Team", mutableListOf("Ducky_"), color = "AQUA"),
        )
        assertEquals('6', FriendsChatFormatter.resolveTagColor(tags, "Ducky_"))
    }

    @Test
    fun `resolveTagColor skips an uncolored tag in favor of a later colored one`() {
        val tags = listOf(
            FriendTag("Mules", mutableListOf("Ducky_"), color = null),
            FriendTag("Dungeon Team", mutableListOf("Ducky_"), color = "AQUA"),
        )
        assertEquals('b', FriendsChatFormatter.resolveTagColor(tags, "Ducky_"))
    }
}
