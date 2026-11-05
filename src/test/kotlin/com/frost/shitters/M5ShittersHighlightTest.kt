package com.frost.shitters

import com.frost.util.TextUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M5ShittersHighlightTest {
    private val listed = mapOf("steve" to ShitterEntry("Steve", "Left at P3"))
    private val red = TextColor.fromLegacyFormat(ChatFormatting.RED)

    /** Each piece of [component]'s text with the style it's drawn in. */
    private fun pieces(component: Component): List<Pair<String, Style>> {
        val result = mutableListOf<Pair<String, Style>>()
        component.visit<Unit>({ style, text ->
            result += text to style
            Optional.empty()
        }, Style.EMPTY)
        return result
    }

    private fun namePiece(component: Component) = pieces(component).single { TextUtils.stripFormatting(it.first) == "Steve" }

    @Test
    fun `text without a listed name is left alone`() {
        assertNull(M5Shitters.highlight(Component.literal("Party Finder > Alex joined the dungeon group!"), true, listed))
        assertNull(M5Shitters.highlight(Component.literal("Steve"), true, emptyMap()))
    }

    @Test
    fun `a listed name turns red and explains itself, and the text reads the same`() {
        val message = Component.literal("Party Finder > ").append(Component.literal("Steve").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" joined the dungeon group! (Mage Level 40)"))
        val highlighted = assertNotNull(M5Shitters.highlight(message, true, listed))
        assertEquals(message.string, highlighted.string)
        val (_, style) = namePiece(highlighted)
        assertEquals(red, style.color)
        val hover = assertNotNull(style.hoverEvent as? HoverEvent.ShowText)
        assertTrue("Left at P3" in hover.value().string)
        assertEquals("/frost shitters reason Steve", (style.clickEvent as ClickEvent.RunCommand).command())
    }

    @Test
    fun `legacy color codes around the name keep coloring the rest`() {
        val line = Component.literal(" §5Steve§f: §eMage§b (§e42§b)")
        val highlighted = assertNotNull(M5Shitters.highlight(line, false, listed))
        assertEquals(TextUtils.stripFormatting(line.string), TextUtils.stripFormatting(highlighted.string))
        assertEquals(red, namePiece(highlighted).second.color)
        // The purple the name was in is picked back up straight after it.
        assertTrue(pieces(highlighted).last().first.startsWith("§5"))
    }

    @Test
    fun `a name keeps its own click and hover actions, and its boldness`() {
        val ownClick = ClickEvent.RunCommand("/pv Steve")
        val message = Component.literal("§lSteve").withStyle(Style.EMPTY.withClickEvent(ownClick))
        val highlighted = assertNotNull(M5Shitters.highlight(message, true, listed))
        val (text, style) = namePiece(highlighted)
        assertEquals("§lSteve", text)
        assertEquals(ownClick, style.clickEvent)
    }

    @Test
    fun `tooltips and the tab list get the color but nothing to click`() {
        val (_, style) = namePiece(assertNotNull(M5Shitters.highlight(Component.literal("[302] Steve (Mage 45)"), false, listed)))
        assertEquals(red, style.color)
        assertNull(style.hoverEvent)
        assertNull(style.clickEvent)
    }
}
