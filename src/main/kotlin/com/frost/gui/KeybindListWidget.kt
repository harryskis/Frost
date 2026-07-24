package com.frost.gui

import com.frost.config.CustomKeybind
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text

/**
 * Scrollable list of the player's custom keybinds - each row is a command text field, a
 * button showing the bound key (click it, then press any key to rebind), and a remove
 * button. Edits happen directly against the backing [keybinds] list (each entry holds a
 * live reference to its own [CustomKeybind]), so the owning screen just needs to persist
 * that list on close - see [KeybindsScreen].
 */
class KeybindListWidget(
    client: MinecraftClient,
    x: Int,
    width: Int,
    height: Int,
    y: Int,
    private val keybinds: MutableList<CustomKeybind>,
) : ElementListWidget<KeybindListWidget.KeybindEntry>(client, width, height, y, 26) {

    // Only one row can be "listening for the next keypress" at a time - the owning
    // screen's keyPressed override checks this before falling through to normal handling,
    // so a capturing row swallows every key (letters included) instead of them typing into
    // whatever text field happens to be focused.
    private var capturingEntry: KeybindEntry? = null

    init {
        this.x = x
        rebuild(client)
    }

    private fun rebuild(client: MinecraftClient) {
        clearEntries()
        for (keybind in keybinds) {
            addEntry(KeybindEntry(client, keybind))
        }
    }

    fun addRow(client: MinecraftClient) {
        keybinds.add(CustomKeybind())
        rebuild(client)
    }

    /** A snapshot of the current list, edits included - the owning screen persists this on
     * close. Blank commands are dropped rather than saved as dead rows. */
    fun keybindsSnapshot(): List<CustomKeybind> = keybinds.filter { it.command.isNotBlank() }

    private fun removeRow(client: MinecraftClient, keybind: CustomKeybind) {
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
        if (keyCode == InputUtil.GLFW_KEY_ESCAPE) return
        entry.keybind.keyCode = keyCode
        entry.refreshBindLabel()
    }

    override fun getRowWidth(): Int = 300

    inner class KeybindEntry(private val client: MinecraftClient, val keybind: CustomKeybind) : Entry<KeybindEntry>() {
        private val commandField = TextFieldWidget(client.textRenderer, 0, 0, 170, 20, Text.literal("Command"))
        private val bindButton = ButtonWidget.builder(bindLabel()) { startCapturing() }.dimensions(0, 0, 75, 20).build()
        private val removeButton = ButtonWidget.builder(Text.literal("X")) { removeRow(client, keybind) }.dimensions(0, 0, 20, 20).build()

        init {
            commandField.setMaxLength(256)
            commandField.text = keybind.command
            commandField.setChangedListener { keybind.command = it }
        }

        private fun bindLabel(): Text =
            if (keybind.keyCode < 0) {
                Text.literal("Set key")
            } else {
                InputUtil.Type.KEYSYM.createFromCode(keybind.keyCode).localizedText
            }

        fun refreshBindLabel() {
            bindButton.message = bindLabel()
        }

        private fun startCapturing() {
            capturingEntry = this
            bindButton.message = Text.literal("Press a key...")
        }

        override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            val rowY = y + (height - 20) / 2
            commandField.x = x
            commandField.y = rowY
            commandField.render(context, mouseX, mouseY, tickDelta)

            bindButton.x = x + commandField.width + 6
            bindButton.y = rowY
            bindButton.render(context, mouseX, mouseY, tickDelta)

            removeButton.x = bindButton.x + bindButton.width + 6
            removeButton.y = rowY
            removeButton.render(context, mouseX, mouseY, tickDelta)
        }

        override fun children(): MutableList<out Element> = mutableListOf(commandField, bindButton, removeButton)

        override fun selectableChildren(): MutableList<out Selectable> = mutableListOf(commandField, bindButton, removeButton)
    }
}
