package com.frost.gui

import com.frost.config.ModConfig
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

/**
 * /frost's Keybinds screen: a scrollable list of custom "press this key, run this command"
 * bindings ([KeybindListWidget]), an Add button for a new blank row, and a Done button that
 * persists the whole list to [ModConfig] at once.
 */
class KeybindsScreen(private val parent: Screen?) : Screen(Text.literal("Frost - Keybinds")) {
    private val cardWidth = 300
    private lateinit var list: KeybindListWidget

    override fun init() {
        val listTop = FrostTheme.sectionHeaderContentStartY(textRenderer) + 8
        val listBottom = height - 58

        list = KeybindListWidget(
            client!!,
            (width - cardWidth) / 2,
            cardWidth,
            listBottom - listTop,
            listTop,
            ModConfig.get().customKeybinds.toMutableList(),
        )
        addDrawableChild(list)

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Add")) { list.addRow(client!!) }
                .dimensions((width - 150) / 2, height - 52, 150, 20)
                .build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) { close() }
                .dimensions((width - 150) / 2, height - 26, 150, 20)
                .build(),
        )
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (list.isCapturing()) {
            list.captureKey(input.key())
            return true
        }
        return super.keyPressed(input)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        super.render(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, textRenderer, width, "Keybinds", mouseX, mouseY)
    }

    override fun close() {
        ModConfig.setCustomKeybinds(list.keybindsSnapshot())
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
