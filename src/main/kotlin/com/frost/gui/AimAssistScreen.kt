package com.frost.gui

import com.frost.config.MAX_AIM_ASSIST_STRENGTH
import com.frost.config.MIN_AIM_ASSIST_STRENGTH
import com.frost.config.ModConfig
import com.frost.dungeon.AimAssist
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * Aim Assist's settings, nested under [DungeonsScreen]: a strength slider, and an Edit toggle
 * for [AimAssist.editMode] (left-click waypoints in-world to cycle their aim anchor). The
 * strength is saved once on close rather than on every drag step, matching the other
 * settings screens' save-once-on-close pattern (see ModConfig.setCustomKeybinds).
 */
class AimAssistScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Aim Assist")) {
    private var strength = ModConfig.getAimAssistStrength()

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var sliderY = 0

    override fun init() {
        val sliderWidth = 200
        val buttonHeight = 20
        val spacing = 6
        val hintHeight = 15
        val panelPadding = 16
        val contentHeight = hintHeight + buttonHeight * 3 + spacing * 2

        panelWidth = sliderWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val x = (width - sliderWidth) / 2
        sliderY = panelY + panelPadding + hintHeight
        addRenderableWidget(StrengthSlider(x, sliderY, sliderWidth, buttonHeight))
        var y = sliderY + buttonHeight + spacing

        // A FeatureButton so it lights up while on, like every other toggle in the menu -
        // there are no deeper settings for edit mode, so right-click does nothing.
        addRenderableWidget(
            FeatureButton(
                x, y, sliderWidth, buttonHeight,
                "Edit",
                AimAssist.editMode,
                onToggle = { AimAssist.editMode = it },
                onOpenSettings = {},
            ),
        )
        y += buttonHeight + spacing

        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(x, y, sliderWidth, buttonHeight)
                .build(),
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Aim Assist", mouseX, mouseY)
        context.centeredText(font, "Sneak + Aspect of the Void, Odin etherwarps", width / 2, sliderY - 15, FrostTheme.HINT)
    }

    override fun onClose() {
        ModConfig.setAimAssistStrength(strength)
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private inner class StrengthSlider(x: Int, y: Int, width: Int, height: Int) :
        AbstractSliderButton(x, y, width, height, Component.empty(), toSliderValue(strength)) {
        init {
            updateMessage()
        }

        override fun updateMessage() {
            message = Component.literal("Strength: $strength%")
        }

        override fun applyValue() {
            strength = (MIN_AIM_ASSIST_STRENGTH + value * (MAX_AIM_ASSIST_STRENGTH - MIN_AIM_ASSIST_STRENGTH)).roundToInt()
        }
    }

    private companion object {
        fun toSliderValue(strength: Int): Double =
            (strength - MIN_AIM_ASSIST_STRENGTH).toDouble() / (MAX_AIM_ASSIST_STRENGTH - MIN_AIM_ASSIST_STRENGTH)
    }
}
