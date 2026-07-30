package com.frost.gui

import com.frost.config.FriendTag
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * One tag's member list ([TagMemberListWidget]) - opened from [FriendTagListWidget]'s
 * "Members" button. The widget edits its own copy of [tag]'s members while open (same
 * stage-then-commit pattern [KeybindsScreen] uses), and [onClose] writes the final
 * (blank-filtered) result back onto [tag] - the same live object [FriendTagsScreen] holds,
 * so returning there shows the updated member count immediately: Minecraft re-runs
 * FriendTagsScreen.init() when it becomes the active screen again, which rebuilds its row
 * widgets from that same live [tag] reference.
 */
class FriendTagMembersScreen(private val parent: Screen, private val tag: FriendTag) :
    Screen(Component.literal("Frost - Tag Members")) {
    private val cardWidth = 300
    private lateinit var list: TagMemberListWidget

    override fun init() {
        val listTop = FrostTheme.sectionHeaderContentStartY(font) + 8
        val listBottom = height - 58

        list = TagMemberListWidget(
            minecraft!!,
            (width - cardWidth) / 2,
            cardWidth,
            listBottom - listTop,
            listTop,
            tag.members.toMutableList(),
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

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FrostTheme.drawBackground(context, width, height)
        super.extractRenderState(context, mouseX, mouseY, delta)
        val title = tag.name.ifBlank { "(unnamed tag)" }
        FrostTheme.drawSectionHeader(context, font, width, "Tag: $title", mouseX, mouseY)
    }

    override fun onClose() {
        tag.members.clear()
        tag.members.addAll(list.membersSnapshot())
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
