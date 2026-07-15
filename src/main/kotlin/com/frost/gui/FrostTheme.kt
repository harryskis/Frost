package com.frost.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * Shared look for all of the mod's menu screens: a near-black background with frosted-blue
 * accents, card-style panels, and a big "Frost / by Bdlt" wordmark - modeled directly on a
 * reference screenshot the user provided of a dark blue-themed mod menu.
 *
 * Colors need an explicit alpha byte (0xFF______) - without it the top byte is 0, meaning
 * fully transparent/invisible. Confirmed as the actual bug behind labels "not showing" on
 * a live run: the draw calls were running fine, just invisible.
 */
object FrostTheme {
    val ACCENT = 0xFF5AC8FA.toInt() // bright frost blue - titles, headers, hovered controls
    val ACCENT_DIM = 0xFF2E3B4E.toInt() // desaturated blue - panel/button borders, unhovered
    val TEXT_PRIMARY = 0xFFE6EEF7.toInt() // near-white body/label text
    val HINT = 0xFF7C90AC.toInt() // muted blue-gray - descriptions, secondary/default hints

    private val BG_TOP = 0xFF080B10.toInt()
    private val BG_BOTTOM = 0xFF0E1520.toInt()
    private val PANEL_BG = 0xFF121924.toInt()
    private val PANEL_BG_HOVER = 0xFF182234.toInt()
    private val SUBTITLE = 0xFF5A6B85.toInt() // dimmer than HINT - the "by Bdlt" byline only
    private val DISCORD_BLURPLE = 0xFF5865F2.toInt()
    private val DISCORD_ICON_DETAIL = 0xFFF2F3F5.toInt()

    private const val DISCORD_USERNAME = "harryskis"
    private const val BYLINE_PREFIX = "by "
    private const val BYLINE_NAME = "Bdlt"

    fun drawBackground(context: DrawContext, width: Int, height: Int) {
        context.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM)
    }

    const val WORDMARK_TOP_Y = 14
    const val WORDMARK_SCALE = 2.4f
    const val SECTION_WORDMARK_TOP_Y = 10
    const val SECTION_WORDMARK_SCALE = 1.5f

    /** Where content below the big wordmark+byline may start - callers use this in
     * [net.minecraft.client.gui.screen.Screen.init] to lay out widgets (no [DrawContext]
     * exists yet there), and [drawWordmark] returns the same value at render time so the
     * two never drift apart. */
    fun wordmarkContentStartY(textRenderer: TextRenderer, topY: Int = WORDMARK_TOP_Y, scale: Float = WORDMARK_SCALE): Int {
        val bylineY = topY + (textRenderer.fontHeight * scale).toInt() + 4
        return bylineY + textRenderer.fontHeight + 6
    }

    /** As [wordmarkContentStartY], but for the smaller sub-screen header (wordmark plus a
     * section label like "Room Weights" underneath). */
    fun sectionHeaderContentStartY(textRenderer: TextRenderer): Int =
        wordmarkContentStartY(textRenderer, SECTION_WORDMARK_TOP_Y, SECTION_WORDMARK_SCALE) + textRenderer.fontHeight + 8

    /** The big "Frost" wordmark plus a smaller "by Bdlt" byline beneath it, scaled up via
     * a matrix push rather than a bigger font (this renderer has no font-size parameter) -
     * [drawCenteredTextWithShadow] draws at (0,0) inside the scaled/translated space so the
     * scale multiplies out from the text's own center, not the screen's corner. The "Bdlt"
     * part of the byline is underlined and, on hover, opens a small "Contact me" card with
     * a Discord badge + username - [mouseX]/[mouseY] are needed for that hover check. */
    fun drawWordmark(
        context: DrawContext,
        textRenderer: TextRenderer,
        width: Int,
        mouseX: Int,
        mouseY: Int,
        topY: Int = WORDMARK_TOP_Y,
        scale: Float = WORDMARK_SCALE,
    ) {
        context.matrices.pushMatrix()
        context.matrices.translate(width / 2f, topY.toFloat())
        context.matrices.scale(scale, scale)
        context.drawCenteredTextWithShadow(textRenderer, "Frost", 0, 0, ACCENT)
        context.matrices.popMatrix()

        val bylineY = topY + (textRenderer.fontHeight * scale).toInt() + 4
        drawByline(context, textRenderer, width, bylineY, mouseX, mouseY)
    }

    /** As [drawWordmark], but for the smaller sub-screen header (wordmark plus a section
     * label like "Room Weights" underneath). */
    fun drawSectionHeader(context: DrawContext, textRenderer: TextRenderer, width: Int, section: String, mouseX: Int, mouseY: Int) {
        drawWordmark(context, textRenderer, width, mouseX, mouseY, topY = SECTION_WORDMARK_TOP_Y, scale = SECTION_WORDMARK_SCALE)
        val labelY = wordmarkContentStartY(textRenderer, SECTION_WORDMARK_TOP_Y, SECTION_WORDMARK_SCALE)
        context.drawCenteredTextWithShadow(textRenderer, section, width / 2, labelY, TEXT_PRIMARY)
    }

    /** Draws "by Bdlt" centered at [y], with just the name (not "by ") underlined, and
     * shows a "Contact me" card underneath it while the mouse is over the name - the same
     * hover-to-reveal-contact-info pattern as a profile card. */
    private fun drawByline(context: DrawContext, textRenderer: TextRenderer, width: Int, y: Int, mouseX: Int, mouseY: Int) {
        val prefixWidth = textRenderer.getWidth(BYLINE_PREFIX)
        val nameWidth = textRenderer.getWidth(BYLINE_NAME)
        val startX = width / 2 - (prefixWidth + nameWidth) / 2
        val nameX = startX + prefixWidth

        context.drawTextWithShadow(textRenderer, BYLINE_PREFIX, startX, y, SUBTITLE)
        val underlinedName = Text.literal(BYLINE_NAME).styled { it.withUnderline(true) }
        context.drawTextWithShadow(textRenderer, underlinedName, nameX, y, SUBTITLE)

        val hovered = mouseX in nameX..(nameX + nameWidth) && mouseY in y..(y + textRenderer.fontHeight)
        if (hovered) {
            drawContactCard(context, textRenderer, nameX + nameWidth / 2, y + textRenderer.fontHeight + 4)
        }
    }

    /** The "Contact me" hover card: a label line, then a small Discord badge next to the
     * Discord username - drawn as plain filled shapes rather than a bundled texture asset,
     * since a custom image resource can't be visually verified without launching the game. */
    private fun drawContactCard(context: DrawContext, textRenderer: TextRenderer, centerX: Int, topY: Int) {
        val label = "Contact me"
        val padding = 8
        val iconSize = 10
        val iconGap = 4
        val usernameWidth = textRenderer.getWidth(DISCORD_USERNAME)
        val rowWidth = iconSize + iconGap + usernameWidth

        val boxWidth = maxOf(textRenderer.getWidth(label), rowWidth) + padding * 2
        val labelRowHeight = textRenderer.fontHeight + 6
        val boxHeight = labelRowHeight + iconSize + padding
        val boxX = centerX - boxWidth / 2

        drawPanel(context, boxX, topY, boxWidth, boxHeight)
        context.drawCenteredTextWithShadow(textRenderer, label, centerX, topY + padding / 2, TEXT_PRIMARY)

        val rowY = topY + labelRowHeight
        val rowX = centerX - rowWidth / 2
        drawDiscordBadge(context, rowX, rowY, iconSize)
        context.drawTextWithShadow(textRenderer, DISCORD_USERNAME, rowX + iconSize + iconGap, rowY + (iconSize - textRenderer.fontHeight) / 2, HINT)
    }

    /** A small stylized badge in Discord's brand color evoking its logo (rounded square,
     * simple face-like detailing) - not a reproduction of Discord's actual logo artwork. */
    private fun drawDiscordBadge(context: DrawContext, x: Int, y: Int, size: Int) {
        context.fill(x, y, x + size, y + size, DISCORD_BLURPLE)
        // Knock out the corners against the card background for a softened, rounded look.
        context.fill(x, y, x + 2, y + 1, PANEL_BG)
        context.fill(x, y, x + 1, y + 2, PANEL_BG)
        context.fill(x + size - 2, y, x + size, y + 1, PANEL_BG)
        context.fill(x + size - 1, y, x + size, y + 2, PANEL_BG)
        context.fill(x, y + size - 1, x + 2, y + size, PANEL_BG)
        context.fill(x, y + size - 2, x + 1, y + size, PANEL_BG)
        context.fill(x + size - 2, y + size - 1, x + size, y + size, PANEL_BG)
        context.fill(x + size - 1, y + size - 2, x + size, y + size, PANEL_BG)
        // Two small "eyes".
        val eyeY = y + size / 2 - 1
        context.fill(x + size / 3 - 1, eyeY, x + size / 3 + 1, eyeY + 2, DISCORD_ICON_DETAIL)
        context.fill(x + size * 2 / 3 - 1, eyeY, x + size * 2 / 3 + 1, eyeY + 2, DISCORD_ICON_DETAIL)
    }

    /** A card-style panel background: a filled rect with a 1px accent-dim border, matching
     * the boxed "COMBAT"/"MOVEMENT"-style sections in the reference screenshot. Draw this
     * BEFORE any widgets that sit inside (x, y, width, height). */
    fun drawPanel(context: DrawContext, x: Int, y: Int, width: Int, height: Int, hovered: Boolean = false) {
        context.fill(x, y, x + width, y + height, ACCENT_DIM)
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, if (hovered) PANEL_BG_HOVER else PANEL_BG)
    }
}
