package com.frost.gui

import com.frost.config.FriendTag
import com.frost.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

/**
 * /frost's Tags screen: a scrollable list of friend tags ([FriendTagListWidget]), each with
 * its own "Members" button to assign friends to it ([FriendTagMembersScreen]), an Add
 * button for a new blank tag, and a Done button that persists everything to [ModConfig] at
 * once.
 *
 * [tags] is seeded once from [ModConfig] at construction and never re-read afterward - it
 * has to survive round-trips to [FriendTagMembersScreen] and back (Minecraft re-runs
 * [init] every time this screen becomes active again), so re-reading from disk in [init]
 * would silently wipe out any unsaved in-session edits.
 */
class FriendTagsScreen(private val parent: Screen?) : Screen(Component.literal("Frost - Tags")) {
    private val cardWidth = 360
    private val tags: MutableList<FriendTag> = ModConfig.getFriendTags().map { it.copy(members = it.members.toMutableList()) }.toMutableList()
    private lateinit var list: FriendTagListWidget

    override fun init() {
        val listTop = FrostTheme.sectionHeaderContentStartY(font) + 8
        val listBottom = height - 58

        list = FriendTagListWidget(
            minecraft!!,
            (width - cardWidth) / 2,
            cardWidth,
            listBottom - listTop,
            listTop,
            tags,
            this,
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
        // A translucent dim, not the opaque FrostTheme.drawBackground other Frost screens
        // use - keeps chat (and your friends list output) visible behind this one, so you
        // don't have to close it just to glance at who's on your list while tagging.
        FrostTheme.drawDimOverlay(context, width, height)
        super.extractRenderState(context, mouseX, mouseY, delta)
        FrostTheme.drawSectionHeader(context, font, width, "Tags", mouseX, mouseY)
    }

    override fun onClose() {
        ModConfig.setFriendTags(list.tagsSnapshot())
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
