package com.frost.gui

import com.frost.config.FriendTag
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Scrollable list of the player's friend tags - each row is a tag-name text field, a color
 * button (cycles through the 16 standard chat colors plus "None" - see
 * [com.frost.friends.FriendsChatFormatter.resolveNameColor] for how it's applied), a bind-key
 * button (press it, then press any key - see [com.frost.friends.FriendsChatRelay] for how
 * hovering a friend's chat line and pressing that key adds them to this tag directly), a
 * "Members (N)" button opening [FriendTagMembersScreen] to assign friends by typing usernames
 * instead, and a remove button. Edits happen directly against the backing [tags] list (each
 * entry holds a live reference to its own [FriendTag]), so the owning screen just needs to
 * persist that list on close - see [FriendTagsScreen].
 */
class FriendTagListWidget(
    client: Minecraft,
    x: Int,
    width: Int,
    height: Int,
    y: Int,
    private val tags: MutableList<FriendTag>,
    private val parentScreen: Screen,
) : ContainerObjectSelectionList<FriendTagListWidget.TagEntry>(client, width, height, y, 26) {

    // Only one row can be "listening for the next keypress" at a time - the owning screen's
    // keyPressed override checks this before falling through to normal handling, so a
    // capturing row swallows every key (letters included) instead of them typing into
    // whatever text field happens to be focused. Same mechanic as KeybindListWidget.
    private var capturingEntry: TagEntry? = null

    init {
        this.x = x
        rebuild(client)
    }

    private fun rebuild(client: Minecraft) {
        clearEntries()
        for (tag in tags) {
            addEntry(TagEntry(client, tag))
        }
    }

    fun addRow(client: Minecraft) {
        tags.add(FriendTag())
        rebuild(client)
    }

    /** A snapshot of the current list, edits included - the owning screen persists this on
     * close. Blank-named tags are dropped rather than saved as dead rows. */
    fun tagsSnapshot(): List<FriendTag> = tags.filter { it.name.isNotBlank() }

    private fun removeRow(client: Minecraft, tag: FriendTag) {
        if (capturingEntry?.tag == tag) capturingEntry = null
        tags.remove(tag)
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
        entry.tag.keyCode = keyCode
        entry.refreshKeyLabel()
    }

    override fun getRowWidth(): Int = 360

    inner class TagEntry(private val client: Minecraft, val tag: FriendTag) : Entry<TagEntry>() {
        // None (null) first, then the 16 standard colors in their declared order.
        private val colorOptions: List<ChatFormatting?> = listOf(null) + ChatFormatting.values().filter { it.isColor }

        private val nameField = EditBox(minecraft.font, 0, 0, 95, 20, Component.literal("Tag name"))
        private val colorButton = Button.builder(colorLabel()) { cycleColor() }.bounds(0, 0, 70, 20).build()
        private val keyButton = Button.builder(keyLabel()) { startCapturing() }.bounds(0, 0, 65, 20).build()
        private val membersButton = Button.builder(membersLabel()) { openMembers() }.bounds(0, 0, 75, 20).build()
        private val removeButton = Button.builder(Component.literal("X")) { removeRow(client, tag) }.bounds(0, 0, 20, 20).build()

        init {
            nameField.setMaxLength(32)
            nameField.value = tag.name
            nameField.setResponder { tag.name = it }
        }

        private fun membersLabel(): Component = Component.literal("Members (${tag.members.count { it.isNotBlank() }})")

        private fun colorLabel(): Component {
            val current = colorOptions.find { it?.name == tag.color }
            return Component.literal(current?.let { formatColorName(it) } ?: "None")
        }

        private fun formatColorName(color: ChatFormatting): String =
            color.name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

        private fun cycleColor() {
            val currentIndex = colorOptions.indexOfFirst { it?.name == tag.color }
            val next = colorOptions[(currentIndex + 1) % colorOptions.size]
            tag.color = next?.name
            colorButton.message = colorLabel()
        }

        private fun keyLabel(): Component =
            if (tag.keyCode < 0) {
                Component.literal("Set key")
            } else {
                InputConstants.Type.KEYSYM.getOrCreate(tag.keyCode).displayName
            }

        fun refreshKeyLabel() {
            keyButton.message = keyLabel()
        }

        private fun startCapturing() {
            capturingEntry = this
            keyButton.message = Component.literal("Press a key...")
        }

        private fun openMembers() {
            minecraft.setScreen(FriendTagMembersScreen(parentScreen, tag))
        }

        override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            val rowY = y + (height - 20) / 2
            nameField.x = x
            nameField.y = rowY
            nameField.extractRenderState(context, mouseX, mouseY, tickDelta)

            colorButton.x = x + nameField.width + 6
            colorButton.y = rowY
            colorButton.extractRenderState(context, mouseX, mouseY, tickDelta)

            keyButton.x = colorButton.x + colorButton.width + 6
            keyButton.y = rowY
            keyButton.extractRenderState(context, mouseX, mouseY, tickDelta)

            membersButton.x = keyButton.x + keyButton.width + 6
            membersButton.y = rowY
            membersButton.extractRenderState(context, mouseX, mouseY, tickDelta)

            removeButton.x = membersButton.x + membersButton.width + 6
            removeButton.y = rowY
            removeButton.extractRenderState(context, mouseX, mouseY, tickDelta)
        }

        override fun children(): MutableList<out GuiEventListener> =
            mutableListOf(nameField, colorButton, keyButton, membersButton, removeButton)

        override fun narratables(): MutableList<out NarratableEntry> =
            mutableListOf(nameField, colorButton, keyButton, membersButton, removeButton)
    }
}
