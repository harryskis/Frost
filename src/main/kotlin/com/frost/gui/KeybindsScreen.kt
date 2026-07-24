package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

/**
 * /frost's Keybinds screen: a scrollable list of custom "press this key, run this command"
 * bindings ([KeybindListWidget]), an Add button for a new blank row, and a Done button that
 * persists the whole list to [ModConfig] at once.
 */
class KeybindsScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Keybinds")) {
    private val cardWidth = 300
    private lateinit var list: KeybindListWidget

    override fun init() {
        val listTop = FrostTheme.sectionHeaderContentStartY(font) + 8
        val listBottom = height - 58

        list = KeybindListWidget(
            minecraft!!,
            (width - cardWidth) / 2,
            cardWidth,
            listBottom - listTop,
            listTop,
            ModConfig.get().customKeybinds.toMutableList(),
        )
        addRenderableWidget(list)

        addRenderableWidget(
            Button.builder(Component.literal("Add")) { list.addRow(minecraft!!) }
                .bounds((width - 150) / 2, height - 52, 150, 20)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds((width - 150) / 2, height - 26, 150, 20)
                .build(),
        )
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (list.isCapturing()) {
            list.captureKey(input.key())
            return true
        }
        return super.keyPressed(input)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Keybinds", mouseX, mouseY)
    }

    override fun onClose() {
        ModConfig.setCustomKeybinds(list.keybindsSnapshot())
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
