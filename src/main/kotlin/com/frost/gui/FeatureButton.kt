package com.frost.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component

/**
 * A toggleable feature entry for the main menu - left-click turns it on/off (lighting up
 * blue when on, via [FrostTheme.drawFeaturePanel]), right-click opens that feature's own
 * settings screen regardless of its current on/off state. Used for every actual feature
 * (dungeon contributions, the better friends list, custom keybinds, Spotify link) so they
 * all share one consistent interaction, rather than each screen inventing its own toggle.
 *
 * A plain [net.minecraft.client.gui.components.Button] can't do this: its default
 * [isValidClickButton] only accepts left-click, so right-click has to be explicitly opted
 * into here.
 */
class FeatureButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: String,
    private var enabled: Boolean,
    private val onToggle: (Boolean) -> Unit,
    private val onOpenSettings: () -> Unit,
) : AbstractWidget(x, y, width, height, Component.literal(label)) {

    // Named constants, not raw numbers: MC 26.3 renumbered mouse buttons (left 0 -> 1, right
    // 1 -> 3), which silently swapped left-click onto the open-settings branch and made
    // right-click invalid while this still used the old raw 0/1.
    override fun isValidClickButton(buttonInfo: MouseButtonInfo): Boolean =
        buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT || buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        when (event.button()) {
            InputConstants.MOUSE_BUTTON_LEFT -> {
                enabled = !enabled
                onToggle(enabled)
            }
            InputConstants.MOUSE_BUTTON_RIGHT -> onOpenSettings()
        }
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawFeaturePanel(context, x, y, width, height, enabled, isHovered)
        val font = Minecraft.getInstance().font
        val textColor = if (enabled) FrostTheme.FEATURE_ON_TEXT else FrostTheme.TEXT_PRIMARY
        context.centeredText(font, message, x + width / 2, y + (height - font.lineHeight) / 2, textColor)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
