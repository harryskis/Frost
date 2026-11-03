package com.frost.gui

import com.frost.config.ModConfig
import com.frost.dungeon.Croesus
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * "Move HUD" under Croesus in [ClickGuiScreen]: drag the chest count to place it. No background,
 * so it can be judged against the actual game. Saved once on close, like [FunnyMapMoveScreen].
 */
class CroesusHudMoveScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Move Croesus HUD")) {
    private var hudX = ModConfig.getCroesusHudX()
    private var hudY = ModConfig.getCroesusHudY()
    private var dragging = false
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0

    override fun init() {
        clampHud()
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - DONE_WIDTH) / 2, height - DONE_MARGIN, DONE_WIDTH, DONE_HEIGHT)
                .build(),
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOverHud(event.x(), event.y())) return false
        dragging = true
        grabOffsetX = event.x() - hudX
        grabOffsetY = event.y() - hudY
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY)
        hudX = (event.x() - grabOffsetX).toInt()
        hudY = (event.y() - grabOffsetY).toInt()
        clampHud()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = false
        return super.mouseReleased(event)
    }

    private fun isOverHud(x: Double, y: Double): Boolean =
        x >= hudX && x < hudX + font.width(Croesus.hudText()) && y >= hudY && y < hudY + font.lineHeight

    private fun clampHud() {
        val (x, y) = Croesus.clampHud(hudX, hudY, minecraft)
        hudX = x
        hudY = y
    }

    // Deliberately empty - skips the default menu blur so the game stays visible behind the HUD.
    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        Croesus.drawHudAt(context, hudX, hudY)
        super.extractRenderState(context, mouseX, mouseY, delta)
        context.centeredText(font, "Drag to move", width / 2, height - DONE_MARGIN - font.lineHeight - HINT_GAP, FrostTheme.TEXT_PRIMARY)
    }

    override fun onClose() {
        ModConfig.setCroesusHudPosition(hudX, hudY)
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val DONE_WIDTH = 100
        const val DONE_HEIGHT = 20
        const val DONE_MARGIN = 28
        const val HINT_GAP = 4
    }
}
