package com.frost.gui

import com.frost.config.ModConfig
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * One feature row: left-click toggles it, right-click drops its settings open beneath it. A
 * feature with no [getter] has nothing to toggle, so either click opens it.
 */
class GuiFeature(
    val name: String,
    val description: String,
    val getter: (() -> Boolean)?,
    val setter: ((Boolean) -> Unit)?,
    // Can be swapped while the menu is open, for a feature whose rows come and go, like Chat Regex's rules.
    var settings: List<GuiSetting>,
) {
    var expanded = false
    private var rowY = 0
    private var rowX = 0
    private val enabledAnim = GuiAnimation(getter?.invoke() == true, ENABLE_MILLIS)
    private val hoverAnim = GuiAnimation(false, HOVER_MILLIS)
    private val openAnim = GuiAnimation(false, OPEN_MILLIS)

    private val settingsHeight get() = settings.sumOf { it.height }
    val height: Int get() = ClickGuiLayout.ROW_HEIGHT + (settingsHeight * openAnim.value).toInt()

    fun matches(query: String) = ClickGuiLayout.matchesSearch(query, name, settings.flatMap { it.searchNames() })

    fun draw(frame: GuiFrame, x: Int, y: Int) {
        rowX = x
        rowY = y
        val width = ClickGuiLayout.PANEL_WIDTH
        val hovered = isOverRow(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        enabledAnim.update(getter?.invoke() == true, frame.dtMillis)
        hoverAnim.update(hovered, frame.dtMillis)
        openAnim.update(expanded, frame.dtMillis)

        val offColor = ClickGuiLayout.lerpColor(ClickGuiColors.ROW, ClickGuiColors.ROW_HOVER, hoverAnim.value)
        val onColor = ClickGuiLayout.lerpColor(ClickGuiColors.ACCENT, ClickGuiColors.ACCENT_HOVER, hoverAnim.value)
        frame.context.fill(x, y, x + width, y + ClickGuiLayout.ROW_HEIGHT, ClickGuiLayout.lerpColor(offColor, onColor, enabledAnim.value))
        frame.context.text(frame.font, name, x + (width - frame.font.width(name)) / 2, y + (ClickGuiLayout.ROW_HEIGHT - 8) / 2, ClickGuiColors.TEXT, false)
        if (settings.isNotEmpty()) {
            val chevronColor = if (enabledAnim.value > 0.5f) ClickGuiColors.TEXT else ClickGuiColors.TEXT_DIM
            drawChevron(frame.context, x + width - ClickGuiLayout.PADDING - CHEVRON_WIDTH, y + ClickGuiLayout.ROW_HEIGHT / 2 - 1, expanded, chevronColor)
        }
        if (hovered) frame.screen.hoverTooltip(this, tooltip())

        if (openAnim.value <= 0f) return
        val top = y + ClickGuiLayout.ROW_HEIGHT
        val bottom = y + height
        frame.context.enableScissor(x, top, x + width, bottom)
        frame.context.fill(x, top, x + width, bottom, ClickGuiColors.SETTINGS_BACKGROUND)
        // Hidden rows during the opening animation shouldn't react to the mouse yet.
        val settingFrame = if (openAnim.isSettled) frame else frame.withoutMouse()
        var settingY = top
        for (setting in settings) {
            setting.draw(settingFrame, x, settingY, width)
            settingY += setting.height
        }
        frame.context.disableScissor()
    }

    private fun tooltip(): String = when {
        settings.isEmpty() -> description
        getter == null -> "$description\n\nClick for settings."
        else -> "$description\n\nLeft-click to toggle, right-click for settings."
    }

    private fun isOverRow(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= rowX && mouseX < rowX + ClickGuiLayout.PANEL_WIDTH && mouseY >= rowY && mouseY < rowY + ClickGuiLayout.ROW_HEIGHT

    fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (isOverRow(event.x(), event.y())) {
            val toggles = getter != null && setter != null
            when {
                event.button() == InputConstants.MOUSE_BUTTON_LEFT && toggles -> setter!!(!getter!!())
                event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT -> {
                    if (settings.isEmpty()) return false
                    expanded = !expanded
                }
                else -> return false
            }
            screen.playClick()
            return true
        }
        return openAnim.isSettled && expanded && settings.any { it.mouseClicked(event, screen) }
    }

    /** Gives a setting under the mouse, like a list, the scroll before the column gets it. */
    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean =
        openAnim.isSettled && expanded && settings.any { it.mouseScrolled(mouseX, mouseY, scrollY) }

    private companion object {
        const val ENABLE_MILLIS = 120f
        const val HOVER_MILLIS = 80f
        const val OPEN_MILLIS = 180f
    }
}

/**
 * One column: a header to drag it by (right-click collapses it) above its feature rows, scrolled
 * with the wheel if they don't fit on screen. Position and collapsed state are saved.
 */
class GuiPanel(val name: String, private val features: List<GuiFeature>) {
    private var x = 0
    private var y = 0
    private var extended = true
    private var hasPosition = false
    private var scroll = 0
    private var bodyHeight = 0
    private var dragging = false
    private var grabX = 0.0
    private var grabY = 0.0
    private val openAnim = GuiAnimation(true, OPEN_MILLIS)

    fun restoreOrPlace(index: Int, panelCount: Int, screenWidth: Int) {
        if (hasPosition) return
        hasPosition = true
        val saved = ModConfig.getClickGuiPanel(name)
        x = saved?.x ?: ClickGuiLayout.defaultPanelX(index, panelCount, screenWidth)
        y = saved?.y ?: ClickGuiLayout.defaultPanelY
        extended = saved?.extended ?: true
        if (!extended) openAnim.update(false, OPEN_MILLIS)
    }

    private fun save() = ModConfig.setClickGuiPanel(name, x, y, extended)

    fun draw(frame: GuiFrame, query: String, screenWidth: Int, screenHeight: Int) {
        x = ClickGuiLayout.clampPanelX(x, screenWidth)
        y = ClickGuiLayout.clampPanelY(y, screenHeight)
        openAnim.update(extended, frame.dtMillis)
        val width = ClickGuiLayout.PANEL_WIDTH
        val visible = features.filter { it.matches(query) }
        val contentHeight = visible.sumOf { it.height }
        val fitted = ClickGuiLayout.visibleBodyHeight(contentHeight, y, screenHeight)
        bodyHeight = (fitted * openAnim.value).toInt()
        scroll = ClickGuiLayout.clampScroll(scroll, contentHeight, fitted)
        val bodyTop = y + ClickGuiLayout.HEADER_HEIGHT
        val capBottom = bodyTop + bodyHeight + ClickGuiLayout.CAP_HEIGHT

        val context = frame.context
        context.fill(x - 1, y - 1, x + width + 1, capBottom + 1, ClickGuiColors.SHADOW)
        context.fill(x, y, x + width, bodyTop, ClickGuiColors.HEADER)
        context.fill(x, y, x + width, y + ACCENT_LINE, ClickGuiColors.ACCENT)
        val title = Component.literal(name).withStyle(Style.EMPTY.withBold(true))
        context.text(frame.font, title, x + (width - frame.font.width(title)) / 2, y + (ClickGuiLayout.HEADER_HEIGHT - 8) / 2 + 1, ClickGuiColors.TEXT, false)

        if (bodyHeight > 0) {
            val mouseInBody = frame.mouseX in x until x + width && frame.mouseY in bodyTop until bodyTop + bodyHeight
            val bodyFrame = if (mouseInBody && openAnim.isSettled) frame else frame.withoutMouse()
            context.enableScissor(x, bodyTop, x + width, bodyTop + bodyHeight)
            var rowY = bodyTop - scroll
            for (feature in visible) {
                feature.draw(bodyFrame, x, rowY)
                rowY += feature.height
            }
            context.disableScissor()
        }
        context.fill(x, bodyTop + bodyHeight, x + width, capBottom, ClickGuiColors.HEADER)
    }

    fun isOver(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + ClickGuiLayout.PANEL_WIDTH &&
            mouseY >= y && mouseY < y + ClickGuiLayout.HEADER_HEIGHT + bodyHeight + ClickGuiLayout.CAP_HEIGHT

    private fun isOverHeader(mouseY: Double) = mouseY < y + ClickGuiLayout.HEADER_HEIGHT
    private fun isOverBody(mouseY: Double) = mouseY >= y + ClickGuiLayout.HEADER_HEIGHT && mouseY < y + ClickGuiLayout.HEADER_HEIGHT + bodyHeight

    fun mouseClicked(event: MouseButtonEvent, query: String, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y())) return false
        if (isOverHeader(event.y())) {
            when (event.button()) {
                InputConstants.MOUSE_BUTTON_LEFT -> {
                    dragging = true
                    grabX = event.x() - x
                    grabY = event.y() - y
                }
                InputConstants.MOUSE_BUTTON_RIGHT -> {
                    extended = !extended
                    screen.playClick()
                    save()
                }
            }
            return true
        }
        if (isOverBody(event.y())) features.filter { it.matches(query) }.any { it.mouseClicked(event, screen) }
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        if (!dragging) return false
        x = (mouseX - grabX).toInt()
        y = (mouseY - grabY).toInt()
        return true
    }

    fun mouseReleased() {
        if (!dragging) return
        dragging = false
        save()
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, query: String, scrollY: Double) {
        if (isOverBody(mouseY) && features.filter { it.matches(query) }.any { it.mouseScrolled(mouseX, mouseY, scrollY) }) return
        if (extended) scroll -= (scrollY * ClickGuiLayout.SCROLL_STEP).toInt()
    }

    private companion object {
        const val OPEN_MILLIS = 180f
        const val ACCENT_LINE = 2
    }
}
