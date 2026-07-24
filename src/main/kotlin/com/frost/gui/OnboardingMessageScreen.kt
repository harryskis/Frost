package com.frost.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.util.FormattedCharSequence
import net.minecraft.network.chat.Component

/**
 * A single wrapped paragraph plus one button - used for the first-launch onboarding flow
 * (see [OnboardingFlow]). Deliberately has no `parent`/`onClose()` of its own: navigation is
 * entirely up to whatever [onButtonPress] does (advance to the next onboarding step, or
 * close the screen for good), since these screens aren't reached via the normal /frost
 * menu tree.
 */
class OnboardingMessageScreen(
    private val screenTitle: String,
    private val message: String,
    private val buttonLabel: String,
    private val onButtonPress: () -> Unit,
) : Screen(Component.literal("Frost - $screenTitle")) {

    private val wrapWidth = 300
    private lateinit var wrappedLines: List<FormattedCharSequence>
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        wrappedLines = font.split(Component.literal(message), wrapWidth)

        val panelPadding = 16
        val lineHeight = font.lineHeight + 2
        val buttonHeight = 20
        val gapBeforeButton = 16
        val contentHeight = wrappedLines.size * lineHeight + gapBeforeButton + buttonHeight

        panelWidth = wrapWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(font)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val buttonWidth = 150
        addRenderableWidget(
            Button.builder(Component.literal(buttonLabel)) { onButtonPress() }
                .bounds((width - buttonWidth) / 2, panelY + panelHeight - panelPadding - buttonHeight, buttonWidth, buttonHeight)
                .build(),
        )
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, screenTitle, mouseX, mouseY)

        val panelPadding = 16
        val lineHeight = font.lineHeight + 2
        var y = panelY + panelPadding
        for (line in wrappedLines) {
            context.centeredText(font, line, width / 2, y, FrostTheme.TEXT_PRIMARY)
            y += lineHeight
        }
    }

    override fun isPauseScreen(): Boolean = false
}
