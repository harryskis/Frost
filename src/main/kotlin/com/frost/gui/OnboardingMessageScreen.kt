package com.frost.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text

/**
 * A single wrapped paragraph plus one button - used for the first-launch onboarding flow
 * (see [OnboardingFlow]). Deliberately has no `parent`/`close()` of its own: navigation is
 * entirely up to whatever [onButtonPress] does (advance to the next onboarding step, or
 * close the screen for good), since these screens aren't reached via the normal /frost
 * menu tree.
 */
class OnboardingMessageScreen(
    private val screenTitle: String,
    private val message: String,
    private val buttonLabel: String,
    private val onButtonPress: () -> Unit,
) : Screen(Text.literal("Frost - $screenTitle")) {

    private val wrapWidth = 300
    private lateinit var wrappedLines: List<OrderedText>
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun init() {
        wrappedLines = textRenderer.wrapLines(Text.literal(message), wrapWidth)

        val panelPadding = 16
        val lineHeight = textRenderer.fontHeight + 2
        val buttonHeight = 20
        val gapBeforeButton = 16
        val contentHeight = wrappedLines.size * lineHeight + gapBeforeButton + buttonHeight

        panelWidth = wrapWidth + panelPadding * 2
        panelHeight = contentHeight + panelPadding * 2
        panelX = (width - panelWidth) / 2
        val wordmarkBottom = FrostTheme.sectionHeaderContentStartY(textRenderer)
        panelY = wordmarkBottom + (height - wordmarkBottom - panelHeight) / 2

        val buttonWidth = 150
        addDrawableChild(
            ButtonWidget.builder(Text.literal(buttonLabel)) { onButtonPress() }
                .dimensions((width - buttonWidth) / 2, panelY + panelHeight - panelPadding - buttonHeight, buttonWidth, buttonHeight)
                .build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        FrostTheme.drawPanel(context, panelX, panelY, panelWidth, panelHeight)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, screenTitle, mouseX, mouseY)

        val panelPadding = 16
        val lineHeight = textRenderer.fontHeight + 2
        var y = panelY + panelPadding
        for (line in wrappedLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, FrostTheme.TEXT_PRIMARY)
            y += lineHeight
        }
    }

    override fun shouldPause(): Boolean = false
}
