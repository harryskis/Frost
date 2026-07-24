package com.frost.gui

import com.frost.config.CustomKeybind
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.network.chat.Component

/**
 * Scrollable list of the player's custom keybinds - each row is a command text field, a
 * button showing the bound key (click it, then press any key to rebind), and a remove
 * button. Edits happen directly against the backing [keybinds] list (each entry holds a
 * live reference to its own [CustomKeybind]), so the owning screen just needs to persist
 * that list on close - see [KeybindsScreen].
 */
class KeybindListWidget(
    client: Minecraft,
    x: Int,
    width: Int,
    height: Int,
    y: Int,
    private val keybinds: MutableList<CustomKeybind>,
) : ContainerObjectSelectionList<KeybindListWidget.KeybindEntry>(client, width, height, y, 26) {

    // Only one row can be "listening for the next keypress" at a time - the owning
    // screen's keyPressed override checks this before falling through to normal handling,
    // so a capturing row swallows every key (letters included) instead of them typing into
    // whatever text field happens to be focused.
    private var capturingEntry: KeybindEntry? = null

    init {
        this.x = x
        rebuild(client)
    }

    private fun rebuild(client: Minecraft) {
        clearEntries()
        for (keybind in keybinds) {
            addEntry(KeybindEntry(client, keybind))
        }
    }

    fun addRow(client: Minecraft) {
        keybinds.add(CustomKeybind())
        rebuild(client)
    }

    /** A snapshot of the current list, edits included - the owning screen persists this on
     * close. Blank commands are dropped rather than saved as dead rows. */
    fun keybindsSnapshot(): List<CustomKeybind> = keybinds.filter { it.command.isNotBlank() }

    private fun removeRow(client: Minecraft, keybind: CustomKeybind) {
        if (capturingEntry?.keybind == keybind) capturingEntry = null
        keybinds.remove(keybind)
        rebuild(client)
    }

    fun isCapturing(): Boolean = capturingEntry != null

    /** Called by the owning screen's keyPressed override while [isCapturing] - binds the
     * pressed key to whichever row started listening, unless it's Escape (cancels instead
     * of binding to Escape itself, matching vanilla's own control-rebinding convention). */
    fun captureKey(keyCode: Int) {
        val entry = capturingEntry ?: return
        capturingEntry = null
        if (keyCode == InputConstants.KEY_ESCAPE) return
        entry.keybind.keyCode = keyCode
        entry.refreshBindLabel()
    }

    override fun getRowWidth(): Int = 300

    inner class KeybindEntry(private val client: Minecraft, val keybind: CustomKeybind) : Entry<KeybindEntry>() {
        private val commandField = EditBox(minecraft.font, 0, 0, 170, 20, Component.literal("Command"))
        private val bindButton = Button.builder(bindLabel()) { startCapturing() }.bounds(0, 0, 75, 20).build()
        private val removeButton = Button.builder(Component.literal("X")) { removeRow(client, keybind) }.bounds(0, 0, 20, 20).build()

        init {
            commandField.setMaxLength(256)
            commandField.value = keybind.command
            commandField.setResponder { keybind.command = it }
        }

        private fun bindLabel(): Component =
            if (keybind.keyCode < 0) {
                Component.literal("Set key")
            } else {
                InputConstants.Type.KEYSYM.getOrCreate(keybind.keyCode).displayName
            }

        fun refreshBindLabel() {
            bindButton.message = bindLabel()
        }

        private fun startCapturing() {
            capturingEntry = this
            bindButton.message = Component.literal("Press a key...")
        }

        override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            val rowY = y + (height - 20) / 2
            commandField.x = x
            commandField.y = rowY
            commandField.extractRenderState(context, mouseX, mouseY, tickDelta)

            bindButton.x = x + commandField.width + 6
            bindButton.y = rowY
            bindButton.extractRenderState(context, mouseX, mouseY, tickDelta)

            removeButton.x = bindButton.x + bindButton.width + 6
            removeButton.y = rowY
            removeButton.extractRenderState(context, mouseX, mouseY, tickDelta)
        }

        override fun children(): MutableList<out GuiEventListener> = mutableListOf(commandField, bindButton, removeButton)

        override fun narratables(): MutableList<out NarratableEntry> = mutableListOf(commandField, bindButton, removeButton)
    }
}
