package com.frost.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Scrollable list of one tag's member usernames - each row is a username text field plus a
 * remove button. Edits happen directly against the backing [members] list, indexed by
 * position (unlike [KeybindListWidget], whose rows hold a reference to a stable
 * [com.frost.config.CustomKeybind] object - a plain username string has no such identity to
 * hold onto, so rows address their own slot in the list instead) - the owning screen just
 * needs to persist that list on close - see [FriendTagMembersScreen].
 */
class TagMemberListWidget(
    client: Minecraft,
    x: Int,
    width: Int,
    height: Int,
    y: Int,
    private val members: MutableList<String>,
) : ContainerObjectSelectionList<TagMemberListWidget.MemberEntry>(client, width, height, y, 26) {

    init {
        this.x = x
        rebuild(client)
    }

    private fun rebuild(client: Minecraft) {
        clearEntries()
        for (index in members.indices) {
            addEntry(MemberEntry(client, index))
        }
    }

    fun addRow(client: Minecraft) {
        members.add("")
        rebuild(client)
    }

    /** A snapshot of the current list, edits included - the owning screen persists this on
     * close. Blank usernames are dropped rather than saved as dead rows. */
    fun membersSnapshot(): List<String> = members.filter { it.isNotBlank() }

    private fun removeRow(client: Minecraft, index: Int) {
        members.removeAt(index)
        rebuild(client)
    }

    override fun getRowWidth(): Int = 300

    inner class MemberEntry(private val client: Minecraft, private val index: Int) : Entry<MemberEntry>() {
        private val usernameField = EditBox(minecraft.font, 0, 0, 220, 20, Component.literal("Username"))
        private val removeButton = Button.builder(Component.literal("X")) { removeRow(client, index) }.bounds(0, 0, 20, 20).build()

        init {
            usernameField.setMaxLength(16)
            usernameField.value = members[index]
            usernameField.setResponder { members[index] = it }
        }

        override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            val rowY = y + (height - 20) / 2
            usernameField.x = x
            usernameField.y = rowY
            usernameField.extractRenderState(context, mouseX, mouseY, tickDelta)

            removeButton.x = x + usernameField.width + 6
            removeButton.y = rowY
            removeButton.extractRenderState(context, mouseX, mouseY, tickDelta)
        }

        override fun children(): MutableList<out GuiEventListener> = mutableListOf(usernameField, removeButton)

        override fun narratables(): MutableList<out NarratableEntry> = mutableListOf(usernameField, removeButton)
    }
}
