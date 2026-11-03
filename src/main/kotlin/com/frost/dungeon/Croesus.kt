package com.frost.dungeon

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.gui.BigAlert
import com.frost.gui.CroesusHudMoveScreen
import com.frost.parsing.TabListParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.Identifier

/**
 * Tracks how many Croesus chests are waiting to be opened. Hypixel only shows the real count in
 * the Dungeon Hub's tab list, so the last one seen is saved, and each run finished away from the
 * hub adds one until the next real reading corrects it - the same approach as Odin's.
 */
object Croesus {
    private val HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "croesus")

    private const val CHEST_LIMIT = 60
    private const val WARNING_THRESHOLD = 55

    // Long enough to notice, without sitting over the crosshair for a whole run.
    private const val WARNING_MILLIS = 5000L

    private val TEXT = 0xFFFFFFFF.toInt()
    private val WARNING_RED = 0xFFFF2020.toInt()

    private var warningUntil = 0L
    private var lastLevel: ClientLevel? = null
    private var runCountedThisLevel = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
        HudElementRegistry.addLast(HUD_ID) { context, _ -> draw(context) }
    }

    /** Fed the visible tab list lines by Frost's own polling. */
    fun onTabList(lines: List<String>) {
        if (!ModConfig.isCroesusEnabled()) return
        lines.firstNotNullOfOrNull(TabListParser::parseUnclaimedChests)?.let(::updateCount)
    }

    /** A finished run leaves one more chest. Only once per dungeon, however often the line that
     * reports it arrives. An unknown count stays unknown, since there's nothing to add to. */
    fun onRunComplete() {
        if (!ModConfig.isCroesusEnabled() || runCountedThisLevel) return
        runCountedThisLevel = true
        val count = ModConfig.getCroesusChests()
        if (count >= 0) updateCount((count + 1).coerceAtMost(CHEST_LIMIT))
    }

    /** Warns on going up rather than on every reading, so it doesn't repeat with no change. */
    private fun updateCount(count: Int) {
        val previous = ModConfig.getCroesusChests()
        if (count == previous) return
        ModConfig.setCroesusChests(count)
        if (count < WARNING_THRESHOLD) warningUntil = 0 else if (count > previous) startWarning()
    }

    private fun startWarning() {
        warningUntil = System.currentTimeMillis() + WARNING_MILLIS
    }

    /** Also warns again on every world change while still over the threshold, so it can't be
     * forgotten about after the first time. */
    private fun onTick(client: Minecraft) {
        val level = client.level
        if (level === lastLevel) return
        lastLevel = level
        runCountedThisLevel = false
        if (level != null && ModConfig.isCroesusEnabled() && ModConfig.getCroesusChests() >= WARNING_THRESHOLD) startWarning()
    }

    /** The HUD's text. The count reads "?" until the Dungeon Hub has been visited once. */
    fun hudText(): String {
        val count = ModConfig.getCroesusChests()
        return "Unopened Chests: ${if (count < 0) "?" else count}/$CHEST_LIMIT"
    }

    private fun hudColor() = if (ModConfig.getCroesusChests() >= WARNING_THRESHOLD) WARNING_RED else TEXT

    /** Keeps the HUD fully on screen, including after the window or GUI scale shrinks. */
    fun clampHud(x: Int, y: Int, client: Minecraft): Pair<Int, Int> {
        val maxX = (client.window.guiScaledWidth - client.font.width(hudText())).coerceAtLeast(0)
        val maxY = (client.window.guiScaledHeight - client.font.lineHeight).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    fun drawHudAt(context: GuiGraphicsExtractor, x: Int, y: Int) {
        context.text(Minecraft.getInstance().font, hudText(), x, y, hudColor())
    }

    private fun draw(context: GuiGraphicsExtractor) {
        if (!ModConfig.isCroesusEnabled()) return
        val client = Minecraft.getInstance()
        // The move screen draws its own copy at the position being dragged.
        if (ModConfig.isCroesusHudEnabled() && client.gui.screen() !is CroesusHudMoveScreen) {
            val (x, y) = clampHud(ModConfig.getCroesusHudX(), ModConfig.getCroesusHudY(), client)
            drawHudAt(context, x, y)
        }
        if (System.currentTimeMillis() < warningUntil) {
            BigAlert.draw(context, "${ModConfig.getCroesusChests()}/$CHEST_LIMIT Unopened Chests!", WARNING_RED)
        }
    }
}
