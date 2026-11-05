package com.frost.gui

import com.frost.config.ModConfig
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** The colors shared across the click GUI. Alpha bytes are required - see [FrostTheme]. */
object ClickGuiColors {
    val ACCENT = 0xFF2E96DC.toInt()
    val ACCENT_HOVER = 0xFF3FA6EC.toInt()
    val HEADER = 0xFF0E131B.toInt()
    val ROW = 0xFF171D27.toInt()
    val ROW_HOVER = 0xFF232C3A.toInt()
    val SETTINGS_BACKGROUND = 0xFF11161E.toInt()
    val SURFACE = 0xFF2A3442.toInt()
    val SURFACE_HOVER = 0xFF364355.toInt()
    val SHADOW = 0x66000000
    val TEXT = 0xFFFFFFFF.toInt()
    val TEXT_DIM = 0xFF8A97A8.toInt()
    val KNOB = 0xFFFFFFFF.toInt()
}

/** One frame's drawing state, passed down the tree. The mouse is off-screen for anything it
 * isn't really over - a column underneath another, or a row scrolled out of view. */
class GuiFrame(
    val context: GuiGraphicsExtractor,
    val font: Font,
    val mouseX: Int,
    val mouseY: Int,
    val dtMillis: Float,
    val screen: ClickGuiScreen,
) {
    fun withoutMouse() = GuiFrame(context, font, OFF_SCREEN, OFF_SCREEN, dtMillis, screen)

    companion object {
        const val OFF_SCREEN = -10000
    }
}

/** A small open/close or on/off transition, advanced once per frame. */
class GuiAnimation(startOpen: Boolean, private val durationMillis: Float) {
    private var linear = if (startOpen) 1f else 0f
    val value: Float get() = ClickGuiLayout.ease(linear)
    val isSettled: Boolean get() = linear == 0f || linear == 1f

    fun update(open: Boolean, dtMillis: Float) {
        linear = ClickGuiLayout.stepAnimation(linear, open, dtMillis, durationMillis)
    }
}

/** A filled box with its corner pixels left out, which reads as softly rounded. */
fun GuiGraphicsExtractor.softFill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
    fill(left + 1, top, right - 1, bottom, color)
    fill(left, top + 1, left + 1, bottom - 1, color)
    fill(right - 1, top + 1, right, bottom - 1, color)
}

/** One row inside a feature's dropdown. Each remembers where it was last drawn, and input is
 * tested against that. */
abstract class GuiSetting(val name: String, val description: String) {
    protected var x = 0
    protected var y = 0
    protected var width = 0

    /** Current height, which can change as a nested dropdown animates. */
    abstract val height: Int

    protected abstract fun render(frame: GuiFrame)

    fun draw(frame: GuiFrame, x: Int, y: Int, width: Int) {
        this.x = x
        this.y = y
        this.width = width
        render(frame)
        val mouseX = frame.mouseX.toDouble()
        val mouseY = frame.mouseY.toDouble()
        if (isOver(mouseX, mouseY)) frame.screen.hoverTooltip(this, tooltipAt(mouseX, mouseY))
    }

    protected open fun isOver(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    /** What to show on hover at this spot, or nothing if empty. */
    protected open fun tooltipAt(mouseX: Double, mouseY: Double): String = description

    /** Returns true if this row used the click. */
    open fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean = false
    open fun mouseDragged(mouseX: Double, mouseY: Double) {}
    open fun mouseReleased() {}

    /** Names search should match this row by, including anything nested inside it. */
    open fun searchNames(): List<String> = listOf(name)

    protected fun drawLabel(frame: GuiFrame, indent: Int = 0, rowHeight: Int = height) {
        frame.context.text(frame.font, name, x + ClickGuiLayout.PADDING + indent, y + (rowHeight - 8) / 2, ClickGuiColors.TEXT, false)
    }
}

/** A text label with an on/off switch. The whole row is the click target. */
class ToggleSetting(
    name: String,
    description: String,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit,
    private val indent: Int = 0,
    override val height: Int = ClickGuiLayout.ROW_HEIGHT,
) : GuiSetting(name, description) {
    private val anim = GuiAnimation(getter(), SWITCH_MILLIS)

    override fun render(frame: GuiFrame) {
        drawLabel(frame, indent)
        val on = getter()
        anim.update(on, frame.dtMillis)
        val hovered = isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        val offColor = if (hovered) ClickGuiColors.SURFACE_HOVER else ClickGuiColors.SURFACE
        val onColor = if (hovered) ClickGuiColors.ACCENT_HOVER else ClickGuiColors.ACCENT
        val left = x + width - ClickGuiLayout.PADDING - SWITCH_WIDTH
        val top = y + (height - SWITCH_HEIGHT) / 2
        frame.context.softFill(left, top, left + SWITCH_WIDTH, top + SWITCH_HEIGHT, ClickGuiLayout.lerpColor(offColor, onColor, anim.value))
        val knobSize = SWITCH_HEIGHT - KNOB_INSET * 2
        val knobLeft = left + KNOB_INSET + ((SWITCH_WIDTH - KNOB_INSET * 2 - knobSize) * anim.value).toInt()
        frame.context.softFill(knobLeft, top + KNOB_INSET, knobLeft + knobSize, top + KNOB_INSET + knobSize, ClickGuiColors.KNOB)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        setter(!getter())
        screen.playClick()
        return true
    }

    private companion object {
        const val SWITCH_WIDTH = 22
        const val SWITCH_HEIGHT = 12
        const val KNOB_INSET = 2
        const val SWITCH_MILLIS = 120f
    }
}

/** A whole-number slider, with its value on the label's line and the track beneath. Applied
 * on release rather than every drag step, since applying can mean writing the config file. */
class SliderSetting(
    name: String,
    description: String,
    private val min: Int,
    private val max: Int,
    private val suffix: String,
    private val getter: () -> Int,
    private val setter: (Int) -> Unit,
) : GuiSetting(name, description) {
    override val height: Int = ClickGuiLayout.SLIDER_ROW_HEIGHT
    private var dragValue: Int? = null

    private val trackX get() = x + ClickGuiLayout.PADDING
    private val trackWidth get() = width - ClickGuiLayout.PADDING * 2

    override fun render(frame: GuiFrame) {
        val value = dragValue ?: getter()
        val labelY = y + LABEL_OFFSET
        frame.context.text(frame.font, name, trackX, labelY, ClickGuiColors.TEXT, false)
        val valueText = "$value$suffix"
        frame.context.text(frame.font, valueText, trackX + trackWidth - frame.font.width(valueText), labelY, ClickGuiColors.TEXT_DIM, false)

        val trackY = y + height - TRACK_BOTTOM_OFFSET
        val filled = (trackWidth * ClickGuiLayout.sliderFraction(value, min, max)).toInt()
        frame.context.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, ClickGuiColors.SURFACE)
        frame.context.fill(trackX, trackY, trackX + filled, trackY + TRACK_HEIGHT, ClickGuiColors.ACCENT)
        val knobLeft = (trackX + filled - KNOB_SIZE / 2).coerceIn(trackX, trackX + trackWidth - KNOB_SIZE)
        val knobTop = trackY + TRACK_HEIGHT / 2 - KNOB_SIZE / 2
        frame.context.softFill(knobLeft, knobTop, knobLeft + KNOB_SIZE, knobTop + KNOB_SIZE, ClickGuiColors.KNOB)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        mouseDragged(event.x(), event.y())
        screen.beginDrag(this)
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double) {
        dragValue = ClickGuiLayout.sliderValue(mouseX, trackX, trackWidth, min, max)
    }

    override fun mouseReleased() {
        dragValue?.let(setter)
        dragValue = null
    }

    private companion object {
        const val LABEL_OFFSET = 5
        const val TRACK_BOTTOM_OFFSET = 9
        const val TRACK_HEIGHT = 3
        const val KNOB_SIZE = 7
    }
}

/** A button for things that need their own screen, like a long list. */
class ActionSetting(name: String, description: String, private val action: () -> Unit) : GuiSetting(name, description) {
    override val height: Int = ClickGuiLayout.ROW_HEIGHT

    override fun render(frame: GuiFrame) {
        val hovered = isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        val left = x + ClickGuiLayout.PADDING
        val right = x + width - ClickGuiLayout.PADDING
        frame.context.softFill(left, y + BUTTON_INSET, right, y + height - BUTTON_INSET, if (hovered) ClickGuiColors.SURFACE_HOVER else ClickGuiColors.SURFACE)
        frame.context.text(frame.font, name, x + (width - frame.font.width(name)) / 2, y + (height - 8) / 2, ClickGuiColors.TEXT, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        screen.playClick()
        action()
        return true
    }

    private companion object {
        const val BUTTON_INSET = 2
    }
}

/** A text label with a typing box. Typing is handled by a vanilla [EditBox] for its cursor,
 * selection and pasting, but drawn borderless to match the rest of the GUI. */
class TextSetting(
    name: String,
    description: String,
    private val maxLength: Int,
    private val initial: () -> String,
    private val onChange: (String) -> Unit,
) : GuiSetting(name, description) {
    override val height: Int = ClickGuiLayout.ROW_HEIGHT
    private var box: EditBox? = null

    private fun box(font: Font): EditBox = box ?: EditBox(font, 0, 0, 0, TEXT_HEIGHT, Component.literal(name)).also {
        it.setBordered(false)
        it.setMaxLength(maxLength)
        it.value = initial()
        it.setResponder(onChange)
        box = it
    }

    override fun render(frame: GuiFrame) {
        drawLabel(frame)
        val box = box(frame.font)
        val labelWidth = frame.font.width(name)
        val left = x + ClickGuiLayout.PADDING + labelWidth + LABEL_GAP
        val right = x + width - ClickGuiLayout.PADDING
        val focused = box.isFocused
        frame.context.fill(left, y + BOX_INSET, right, y + height - BOX_INSET, ClickGuiColors.SURFACE)
        frame.context.fill(left, y + height - BOX_INSET - 1, right, y + height - BOX_INSET, if (focused) ClickGuiColors.ACCENT else ClickGuiColors.SURFACE_HOVER)
        box.x = left + TEXT_INSET
        box.y = y + (height - 8) / 2
        box.width = right - left - TEXT_INSET * 2
        box.extractRenderState(frame.context, frame.mouseX, frame.mouseY, 0f)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        val box = box ?: return false
        screen.focusTextBox(box)
        box.mouseClicked(event, false)
        return true
    }

    private companion object {
        const val TEXT_HEIGHT = 10
        const val BOX_INSET = 3
        const val TEXT_INSET = 3
        const val LABEL_GAP = 6
    }
}

/**
 * Odin's color picker: a label and swatch that click open to a saturation/brightness square, hue
 * and alpha bars, a hex box and the favorite slots, which every picker shares.
 *
 * The color is written to the config in memory on every change, so whatever it colors updates
 * live while dragging, and to the file on release. Hue, saturation and brightness are kept here
 * rather than read back from the color, which would lose the hue whenever it goes gray or black.
 */
class ColorSetting(
    name: String,
    description: String,
    private val default: Int,
    private val getter: () -> Int,
    private val setter: (Int) -> Unit,
) : GuiSetting(name, description) {
    private enum class Part { SQUARE, HUE, ALPHA }

    private var open = false
    private val anim = GuiAnimation(false, OPEN_MILLIS)
    private var box: EditBox? = null
    private var dragging: Part? = null

    private var hue = 0f
    private var saturation = 0f
    private var brightness = 0f
    private var alpha = 0xFF
    private var shownColor: Int? = null

    override val height: Int get() = ClickGuiLayout.ROW_HEIGHT + (PICKER_AREA_HEIGHT * anim.value).toInt()

    private val pickerTop get() = y + ClickGuiLayout.ROW_HEIGHT
    private val squareLeft get() = x + ClickGuiLayout.PADDING
    private val squareWidth get() = width - ClickGuiLayout.PADDING * 2 - (BAR_WIDTH + GAP) * 2
    private val hueLeft get() = squareLeft + squareWidth + GAP
    private val alphaLeft get() = hueLeft + BAR_WIDTH + GAP
    private val fieldTop get() = pickerTop + PICKER_HEIGHT + GAP
    private val slotsLeft get() = x + width - ClickGuiLayout.PADDING - FAVORITES_WIDTH

    private fun inPicker(mouseX: Double, mouseY: Double, left: Int, barWidth: Int) =
        mouseX >= left && mouseX < left + barWidth && mouseY >= pickerTop && mouseY < pickerTop + PICKER_HEIGHT

    private fun inHexBox(mouseX: Double, mouseY: Double) =
        mouseX >= squareLeft && mouseX < squareLeft + HEX_WIDTH && mouseY >= fieldTop && mouseY < fieldTop + FIELD_HEIGHT

    private fun slotAt(mouseX: Double, mouseY: Double): Int? {
        if (mouseY < fieldTop || mouseY >= fieldTop + FIELD_HEIGHT) return null
        return (0 until ModConfig.FAVORITE_COLOR_SLOTS).firstOrNull { slot ->
            val left = slotsLeft + slot * (FIELD_HEIGHT + GAP)
            mouseX >= left && mouseX < left + FIELD_HEIGHT
        }
    }

    private fun box(font: Font): EditBox = box ?: EditBox(font, 0, 0, HEX_WIDTH, BOX_TEXT_HEIGHT, Component.literal(name)).also {
        it.setBordered(false)
        it.setMaxLength(HEX_MAX_LENGTH)
        it.setResponder(::onHexTyped)
        box = it
    }

    /** Clearing the box goes back to the default. Anything else waits until it's a whole color. */
    private fun onHexTyped(text: String) {
        if (box?.isFocused != true) return
        val typed = if (text.isBlank()) default else ClickGuiLayout.parseHexColor(text) ?: return
        if (typed == getter()) return
        setter(typed)
        ModConfig.saveCurrent()
    }

    /** Catches up with a color changed by anything other than dragging here. */
    private fun syncFromConfig() {
        val color = getter()
        if (color == shownColor) return
        shownColor = color
        val (newHue, newSaturation, newBrightness) = ClickGuiLayout.rgbToHsv(color)
        hue = newHue
        saturation = newSaturation
        brightness = newBrightness
        alpha = color ushr 24
    }

    private fun applyPicked() {
        val color = ClickGuiLayout.hsvToArgb(hue, saturation, brightness, alpha)
        shownColor = color
        setter(color)
    }

    override fun render(frame: GuiFrame) {
        syncFromConfig()
        anim.update(open, frame.dtMillis)
        val context = frame.context
        val swatchRight = x + width - ClickGuiLayout.PADDING
        val swatchTop = y + (ClickGuiLayout.ROW_HEIGHT - SWATCH_HEIGHT) / 2
        drawLabel(frame, rowHeight = ClickGuiLayout.ROW_HEIGHT)
        context.softFill(swatchRight - SWATCH_WIDTH, swatchTop, swatchRight, swatchTop + SWATCH_HEIGHT, ClickGuiColors.SURFACE)
        context.softFill(swatchRight - SWATCH_WIDTH, swatchTop, swatchRight, swatchTop + SWATCH_HEIGHT, getter())

        if (anim.value <= 0f) return
        context.enableScissor(x, pickerTop, x + width, y + height)
        drawSquare(context)
        drawHueBar(context)
        drawAlphaBar(context)
        drawHexBox(frame)
        drawFavorites(frame)
        context.disableScissor()
    }

    /** One vertical gradient per pixel column: each runs from that saturation at full brightness
     * down to black, which works out to the exact color at every point of the square. */
    private fun drawSquare(context: GuiGraphicsExtractor) {
        val width = squareWidth
        for (column in 0 until width) {
            val top = ClickGuiLayout.hsvToArgb(hue, column / (width - 1f), 1f)
            context.fillGradient(squareLeft + column, pickerTop, squareLeft + column + 1, pickerTop + PICKER_HEIGHT, top, BLACK)
        }
        val markerX = squareLeft + (saturation * (width - 1)).toInt()
        val markerY = pickerTop + ((1f - brightness) * (PICKER_HEIGHT - 1)).toInt()
        val half = MARKER_SIZE / 2
        context.softFill(markerX - half, markerY - half, markerX - half + MARKER_SIZE, markerY - half + MARKER_SIZE, ClickGuiColors.KNOB)
        context.softFill(markerX - half + 1, markerY - half + 1, markerX - half + MARKER_SIZE - 1, markerY - half + MARKER_SIZE - 1, ClickGuiLayout.opaque(getter()))
    }

    private fun drawHueBar(context: GuiGraphicsExtractor) {
        for (segment in 0 until HUE_SEGMENTS) {
            val top = pickerTop + PICKER_HEIGHT * segment / HUE_SEGMENTS
            val bottom = pickerTop + PICKER_HEIGHT * (segment + 1) / HUE_SEGMENTS
            val topColor = ClickGuiLayout.hsvToArgb(segment.toFloat() / HUE_SEGMENTS, 1f, 1f)
            val bottomColor = ClickGuiLayout.hsvToArgb((segment + 1f) / HUE_SEGMENTS, 1f, 1f)
            context.fillGradient(hueLeft, top, hueLeft + BAR_WIDTH, bottom, topColor, bottomColor)
        }
        drawBarKnob(context, hueLeft, hue)
    }

    private fun drawAlphaBar(context: GuiGraphicsExtractor) {
        val rgb = getter() and 0xFFFFFF
        context.fill(alphaLeft, pickerTop, alphaLeft + BAR_WIDTH, pickerTop + PICKER_HEIGHT, ClickGuiColors.SURFACE)
        context.fillGradient(alphaLeft, pickerTop, alphaLeft + BAR_WIDTH, pickerTop + PICKER_HEIGHT, rgb, ClickGuiLayout.opaque(rgb))
        drawBarKnob(context, alphaLeft, alpha / MAX_ALPHA)
    }

    private fun drawBarKnob(context: GuiGraphicsExtractor, left: Int, fraction: Float) {
        val center = pickerTop + (fraction * (PICKER_HEIGHT - 1)).toInt()
        context.softFill(left - 1, center - 1, left + BAR_WIDTH + 1, center + 2, ClickGuiColors.KNOB)
    }

    private fun drawHexBox(frame: GuiFrame) {
        val context = frame.context
        val box = box(frame.font)
        context.fill(squareLeft, fieldTop, squareLeft + HEX_WIDTH, fieldTop + FIELD_HEIGHT, ClickGuiColors.SURFACE)
        context.outline(squareLeft, fieldTop, HEX_WIDTH, FIELD_HEIGHT, ClickGuiColors.ACCENT)
        // Left alone while it's being typed in, or the cursor would jump around mid-word.
        if (!box.isFocused) {
            val hex = ClickGuiLayout.formatHexColor(getter())
            if (box.value != hex) box.value = hex
        }
        box.x = squareLeft + HEX_TEXT_INSET
        box.y = fieldTop + (FIELD_HEIGHT - 8) / 2
        box.width = HEX_WIDTH - HEX_TEXT_INSET * 2
        box.extractRenderState(context, frame.mouseX, frame.mouseY, 0f)
    }

    private fun drawFavorites(frame: GuiFrame) {
        val hovered = slotAt(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        for (slot in 0 until ModConfig.FAVORITE_COLOR_SLOTS) {
            val left = slotsLeft + slot * (FIELD_HEIGHT + GAP)
            val right = left + FIELD_HEIGHT
            frame.context.softFill(left, fieldTop, right, fieldTop + FIELD_HEIGHT, if (slot == hovered) ClickGuiColors.SURFACE_HOVER else ClickGuiColors.SURFACE)
            ModConfig.getFavoriteColor(slot)?.let { frame.context.softFill(left + 1, fieldTop + 1, right - 1, fieldTop + FIELD_HEIGHT - 1, it) }
        }
    }

    /** Only the label and the slots have anything to explain, and a tooltip in the way of the
     * square or bars would hide the color being picked. */
    override fun tooltipAt(mouseX: Double, mouseY: Double): String = when {
        mouseY < pickerTop -> description
        slotAt(mouseX, mouseY) != null -> FAVORITE_TOOLTIP
        inHexBox(mouseX, mouseY) -> HEX_TOOLTIP
        else -> ""
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        if (!isOver(mouseX, mouseY)) return false
        if (mouseY < pickerTop) {
            if (!screen.isLeft(event)) return false
            open = !open
            screen.playClick()
            return true
        }
        if (!open || !anim.isSettled) return false

        slotAt(mouseX, mouseY)?.let { slot -> return clickFavorite(slot, event, screen) }
        if (!screen.isLeft(event)) return false
        if (inHexBox(mouseX, mouseY)) {
            val box = box ?: return false
            screen.focusTextBox(box)
            box.mouseClicked(event, false)
            return true
        }
        dragging = when {
            inPicker(mouseX, mouseY, squareLeft, squareWidth) -> Part.SQUARE
            inPicker(mouseX, mouseY, hueLeft, BAR_WIDTH) -> Part.HUE
            inPicker(mouseX, mouseY, alphaLeft, BAR_WIDTH) -> Part.ALPHA
            else -> return false
        }
        mouseDragged(mouseX, mouseY)
        screen.beginDrag(this)
        return true
    }

    /** Odin's controls: left-click saves the current color to a slot, right-click loads it. */
    private fun clickFavorite(slot: Int, event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        when (event.button()) {
            InputConstants.MOUSE_BUTTON_LEFT -> ModConfig.setFavoriteColor(slot, getter())
            InputConstants.MOUSE_BUTTON_RIGHT -> {
                setter(ModConfig.getFavoriteColor(slot) ?: return false)
                ModConfig.saveCurrent()
            }
            else -> return false
        }
        screen.playClick()
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double) {
        val down = ClickGuiLayout.barFraction(mouseY, pickerTop, PICKER_HEIGHT)
        when (dragging) {
            Part.SQUARE -> {
                saturation = ClickGuiLayout.barFraction(mouseX, squareLeft, squareWidth)
                brightness = 1f - down
            }
            Part.HUE -> hue = down
            Part.ALPHA -> alpha = (down * MAX_ALPHA).roundToInt()
            null -> return
        }
        applyPicked()
    }

    override fun mouseReleased() {
        dragging = null
        ModConfig.saveCurrent()
    }

    private companion object {
        const val OPEN_MILLIS = 160f
        const val GAP = 3
        const val BAR_WIDTH = 10
        const val PICKER_HEIGHT = 84
        const val FIELD_HEIGHT = 16
        const val PICKER_AREA_HEIGHT = PICKER_HEIGHT + GAP + FIELD_HEIGHT + ClickGuiLayout.PADDING
        const val HEX_WIDTH = 60
        const val HEX_TEXT_INSET = 4
        const val HEX_MAX_LENGTH = 9
        const val BOX_TEXT_HEIGHT = 10
        const val FAVORITES_WIDTH = 73
        const val SWATCH_WIDTH = 22
        const val SWATCH_HEIGHT = 14
        const val MARKER_SIZE = 7
        const val HUE_SEGMENTS = 6
        const val MAX_ALPHA = 255f
        const val BLACK = 0xFF shl 24
        const val FAVORITE_TOOLTIP = "Left-click to save the current color here. Right-click to use the saved color."
        const val HEX_TOOLTIP = "RRGGBB or RRGGBBAA. Clear it to go back to the default."
    }
}

/** A text label with a box showing its bound key. Clicking starts listening for a key, which
 * [ClickGuiScreen] passes to [capture]; Escape unbinds, and a click anywhere else cancels. A
 * key code below 0 means unbound. */
class KeybindSetting(
    name: String,
    description: String,
    private val getter: () -> Int,
    private val setter: (Int) -> Unit,
) : GuiSetting(name, description) {
    override val height: Int = ClickGuiLayout.ROW_HEIGHT
    var capturing = false

    override fun render(frame: GuiFrame) {
        drawLabel(frame)
        val key = getter()
        val text = when {
            capturing -> Component.literal("Press a key")
            key < 0 -> Component.literal("None")
            else -> InputConstants.Type.KEYBOARD.getOrCreate(key).displayName
        }
        val hovered = isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        val right = x + width - ClickGuiLayout.PADDING
        val left = right - frame.font.width(text) - TEXT_INSET * 2
        val boxColor = if (capturing) ClickGuiColors.ACCENT else if (hovered) ClickGuiColors.SURFACE_HOVER else ClickGuiColors.SURFACE
        frame.context.softFill(left, y + BOX_INSET, right, y + height - BOX_INSET, boxColor)
        frame.context.text(frame.font, text, left + TEXT_INSET, y + (height - 8) / 2, ClickGuiColors.TEXT, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        screen.playClick()
        screen.beginKeyCapture(this)
        return true
    }

    fun capture(keyCode: Int) = setter(if (keyCode == InputConstants.KEY_ESCAPE) -1 else keyCode)

    private companion object {
        const val BOX_INSET = 3
        const val TEXT_INSET = 4
    }
}

/** A text label with the current choice beside it, which clicking moves on to the next one. */
class ChoiceSetting(
    name: String,
    description: String,
    private val choices: List<String>,
    private val getter: () -> Int,
    private val setter: (Int) -> Unit,
) : GuiSetting(name, description) {
    override val height: Int = ClickGuiLayout.ROW_HEIGHT

    override fun render(frame: GuiFrame) {
        drawLabel(frame)
        val choice = choices[getter()]
        val hovered = isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble())
        val left = x + width - ClickGuiLayout.PADDING - frame.font.width(choice)
        frame.context.text(frame.font, choice, left, y + (height - 8) / 2, if (hovered) ClickGuiColors.TEXT else ClickGuiColors.TEXT_DIM, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        setter((getter() + 1) % choices.size)
        screen.playClick()
        return true
    }
}

/** A text label with the current choice beside it, which clicks open to the full list of
 * [choices] beneath. Clicking one picks it and closes the list again. */
class SelectorSetting(
    name: String,
    description: String,
    private val choices: List<String>,
    private val getter: () -> Int,
    private val setter: (Int) -> Unit,
) : GuiSetting(name, description) {
    private var open = false
    private val anim = GuiAnimation(false, OPEN_MILLIS)

    private val listHeight get() = choices.size * ClickGuiLayout.SUB_ROW_HEIGHT
    override val height: Int get() = ClickGuiLayout.ROW_HEIGHT + (listHeight * anim.value).toInt()

    private fun choiceAt(mouseY: Double): Int? {
        val index = ((mouseY - y - ClickGuiLayout.ROW_HEIGHT) / ClickGuiLayout.SUB_ROW_HEIGHT).toInt()
        return index.takeIf { mouseY >= y + ClickGuiLayout.ROW_HEIGHT && it in choices.indices }
    }

    override fun render(frame: GuiFrame) {
        anim.update(open, frame.dtMillis)
        val context = frame.context
        drawLabel(frame, rowHeight = ClickGuiLayout.ROW_HEIGHT)
        val chevronX = x + width - ClickGuiLayout.PADDING - CHEVRON_WIDTH
        drawChevron(context, chevronX, y + ClickGuiLayout.ROW_HEIGHT / 2 - 1, open, ClickGuiColors.TEXT_DIM)
        val current = choices[getter().coerceIn(choices.indices)]
        val headerHovered = isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble()) && frame.mouseY < y + ClickGuiLayout.ROW_HEIGHT
        val currentLeft = chevronX - CHEVRON_GAP - frame.font.width(current)
        context.text(frame.font, current, currentLeft, y + (ClickGuiLayout.ROW_HEIGHT - 8) / 2, if (headerHovered) ClickGuiColors.TEXT else ClickGuiColors.TEXT_DIM, false)

        if (anim.value <= 0f) return
        val top = y + ClickGuiLayout.ROW_HEIGHT
        context.enableScissor(x, top, x + width, y + height)
        val hovered = if (anim.isSettled && isOver(frame.mouseX.toDouble(), frame.mouseY.toDouble())) choiceAt(frame.mouseY.toDouble()) else null
        choices.forEachIndexed { index, choice ->
            val rowTop = top + index * ClickGuiLayout.SUB_ROW_HEIGHT
            val left = x + ClickGuiLayout.PADDING + ClickGuiLayout.SUB_INDENT
            val right = x + width - ClickGuiLayout.PADDING
            val selected = index == getter()
            if (selected || index == hovered) {
                context.softFill(left - OPTION_INSET, rowTop + 1, right, rowTop + ClickGuiLayout.SUB_ROW_HEIGHT - 1, if (index == hovered) ClickGuiColors.SURFACE_HOVER else ClickGuiColors.SURFACE)
            }
            if (selected) context.fill(left - OPTION_INSET, rowTop + 1, left - OPTION_INSET + MARKER_WIDTH, rowTop + ClickGuiLayout.SUB_ROW_HEIGHT - 1, ClickGuiColors.ACCENT)
            context.text(frame.font, choice, left + MARKER_WIDTH, rowTop + (ClickGuiLayout.SUB_ROW_HEIGHT - 8) / 2, if (selected) ClickGuiColors.TEXT else ClickGuiColors.TEXT_DIM, false)
        }
        context.disableScissor()
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y()) || !screen.isLeft(event)) return false
        if (event.y() < y + ClickGuiLayout.ROW_HEIGHT) {
            open = !open
            screen.playClick()
            return true
        }
        if (!open || !anim.isSettled) return false
        val index = choiceAt(event.y()) ?: return false
        setter(index)
        open = false
        screen.playClick()
        return true
    }

    override fun searchNames(): List<String> = listOf(name) + choices

    private companion object {
        const val OPEN_MILLIS = 160f
        const val CHEVRON_GAP = 5
        const val OPTION_INSET = 4
        const val MARKER_WIDTH = 2
    }
}

/** A row that opens a group of smaller rows beneath it, nested inside a feature's dropdown. */
class DropdownSetting(name: String, description: String, private val children: List<GuiSetting>) : GuiSetting(name, description) {
    private var open = false
    private val anim = GuiAnimation(false, OPEN_MILLIS)

    private val childrenHeight get() = children.sumOf { it.height }
    override val height: Int get() = ClickGuiLayout.ROW_HEIGHT + (childrenHeight * anim.value).toInt()

    override fun render(frame: GuiFrame) {
        anim.update(open, frame.dtMillis)
        drawLabel(frame, rowHeight = ClickGuiLayout.ROW_HEIGHT)
        drawChevron(frame.context, x + width - ClickGuiLayout.PADDING - CHEVRON_WIDTH, y + ClickGuiLayout.ROW_HEIGHT / 2 - 1, open, ClickGuiColors.TEXT_DIM)

        if (anim.value <= 0f) return
        val top = y + ClickGuiLayout.ROW_HEIGHT
        frame.context.enableScissor(x, top, x + width, y + height)
        var childY = top
        for (child in children) {
            child.draw(frame, x, childY, width)
            childY += child.height
        }
        frame.context.disableScissor()
    }

    override fun mouseClicked(event: MouseButtonEvent, screen: ClickGuiScreen): Boolean {
        if (!isOver(event.x(), event.y())) return false
        if (event.y() < y + ClickGuiLayout.ROW_HEIGHT) {
            if (!screen.isLeft(event)) return false
            open = !open
            screen.playClick()
            return true
        }
        return anim.isSettled && open && children.any { it.mouseClicked(event, screen) }
    }

    override fun searchNames(): List<String> = listOf(name) + children.flatMap { it.searchNames() }

    private companion object {
        const val OPEN_MILLIS = 160f
    }
}

const val CHEVRON_WIDTH = 5

/** A small arrow built from pixel rows: pointing down when open, right when closed. */
fun drawChevron(context: GuiGraphicsExtractor, x: Int, y: Int, open: Boolean, color: Int) {
    if (open) {
        context.fill(x, y, x + 5, y + 1, color)
        context.fill(x + 1, y + 1, x + 4, y + 2, color)
        context.fill(x + 2, y + 2, x + 3, y + 3, color)
    } else {
        context.fill(x + 1, y - 1, x + 2, y + 4, color)
        context.fill(x + 2, y, x + 3, y + 3, color)
        context.fill(x + 3, y + 1, x + 4, y + 2, color)
    }
}
