package com.frost.gui

import com.frost.config.ModConfig
import com.frost.dungeon.DungeonMapReader
import com.frost.dungeon.FunnyMap
import com.frost.dungeon.FunnyMapLayout
import com.frost.dungeon.FunnyMapSnapshot
import com.frost.dungeon.HeadLook
import com.frost.dungeon.MapGeometry
import com.frost.dungeon.MapStyle
import com.frost.gui.map.GuiMapCanvas
import com.frost.gui.map.MapPaintOptions
import com.frost.gui.map.MapPainter
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws [com.frost.dungeon.FunnyMap]'s layout in the chosen [MapStyle], shared by the HUD and the
 * move screen. Everything is laid out at the style's own size and scaled as a whole, so text and
 * heads grow with the map. The rooms themselves are drawn by [MapPainter]; the heads are here,
 * as they need the game's skins.
 */
object FunnyMapRenderer {
    private val STAR_MOB = 0xFFFFC21A.toInt()
    private val STAR_MOB_OUTLINE = 0xFF000000.toInt()
    private const val STAR_MOB_SIZE = 3
    private const val PERCENT = 100f

    // Odin's own position marker: the green pointer from vanilla maps, 5x7 in an 8x8 texture.
    private val ARROW_TEXTURE = Identifier.withDefaultNamespace("textures/map/decorations/frame.png")
    private const val ARROW_TEXTURE_SIZE = 8
    private const val ARROW_U = 2f
    private const val ARROW_WIDTH = 5
    private const val ARROW_HEIGHT = 7
    private const val ARROW_LEFT = -2
    private const val ARROW_TOP = -4
    private const val ARROW_CENTERING = -0.5f

    fun style(): MapStyle = ModConfig.getMapStyle()

    fun options(): MapPaintOptions = MapPaintOptions(
        bloodRushPath = ModConfig.isBloodRushPathEnabled(),
        totalSecrets = ModConfig.isTotalSecretsEnabled(),
        limitRoomNames = ModConfig.isLimitRoomNamesEnabled(),
        textSize = ModConfig.getFunnyMapTextSize() / PERCENT,
        hiddenNameTypes = FunnyMapLayout.SPECIAL_ROOM_TYPES.map { it.second }.filterNot(ModConfig::isRoomNameShown).toSet(),
        fillCompletedRooms = ModConfig.isFillCompletedRoomsEnabled(),
        filledRoomTypes = ModConfig.getFilledRoomTypes(),
        fillColor = ModConfig.getFillCompletedColor(),
        backgroundColor = ModConfig.getMapBackgroundColor(),
    )

    /** The map's on-screen width at [scale]. */
    fun scaledSize(scale: Float): Int = ceil(style().geometry.size * scale).toInt()

    /** The on-screen height at [scale], which includes the secrets line under the map if it's on. */
    fun scaledHeight(scale: Float): Int = ceil(MapPainter.height(style(), options()) * scale).toInt()

    /** Keeps the map fully on screen, including after the window or GUI scale shrinks. */
    fun clampToScreen(x: Int, y: Int, scale: Float, client: Minecraft): Pair<Int, Int> {
        val maxX = (client.window.guiScaledWidth - scaledSize(scale)).coerceAtLeast(0)
        val maxY = (client.window.guiScaledHeight - scaledHeight(scale)).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    private inline fun inMapSpace(context: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, draw: () -> Unit) {
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(scale, scale)
        draw()
        context.pose().popMatrix()
    }

    fun draw(context: GuiGraphicsExtractor, font: Font, x: Int, y: Int, scale: Float, map: FunnyMapSnapshot) {
        inMapSpace(context, x, y, scale) { MapPainter.paint(GuiMapCanvas(context, font), style(), map, options()) }
    }

    /** A small gold square at each of [mobs]' world (x, z), outlined so it shows on any room color. */
    fun drawStarMobs(context: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, mobs: List<Pair<Double, Double>>) {
        val geometry = style().geometry
        inMapSpace(context, x, y, scale) {
            for ((worldX, worldZ) in mobs) {
                val left = geometry.worldToMap(worldX).toInt() - STAR_MOB_SIZE / 2
                val top = geometry.worldToMap(worldZ).toInt() - STAR_MOB_SIZE / 2
                context.fill(left - 1, top - 1, left + STAR_MOB_SIZE + 1, top + STAR_MOB_SIZE + 1, STAR_MOB_OUTLINE)
                context.fill(left, top, left + STAR_MOB_SIZE, top + STAR_MOB_SIZE, STAR_MOB)
            }
        }
    }

    /** Who's on the map and how to show them: everyone's [classes] by name, and whether to write
     * their names under their heads. */
    class Party(
        val names: Set<String>,
        val markers: Map<String, DungeonMapReader.Marker>,
        val classes: Map<String, String>,
        val showNames: Boolean,
    )

    /** Each party member's head, turned to face the way they're looking, in the style's own look.
     * A teammate too far away for the client to have loaded is placed by their marker on the
     * in-game map instead, which is coarser but always there. */
    fun drawPlayers(context: GuiGraphicsExtractor, font: Font, x: Int, y: Int, scale: Float, level: ClientLevel, self: LocalPlayer, party: Party) {
        val style = style()
        val head = Head(context, font, style.geometry, ModConfig.getFunnyMapHeadSize() / PERCENT)
        inMapSpace(context, x, y, scale) {
            val loaded = level.players().filter { it !== self && it.gameProfile.name in party.names }
            for (entity in loaded) {
                val name = entity.gameProfile.name
                head.draw(entity.skin, entity.x, entity.z, entity.yHeadRot, style.headLook(false, party.classes[name]), name.takeIf { party.showNames })
            }
            val loadedNames = loaded.map { it.gameProfile.name }
            for ((name, marker) in party.markers) {
                if (name in loadedNames) continue
                head.draw(FunnyMap.skinOf(name), marker.worldX, marker.worldZ, marker.yaw, style.headLook(false, party.classes[name]), name.takeIf { party.showNames })
            }
            // The local player's own yRot rather than yHeadRot: it updates with the mouse every
            // frame, so their head turns smoothly instead of stepping at the tick rate. dtMap
            // names everyone, Odin only teammates.
            val selfName = self.gameProfile.name
            val selfLabel = selfName.takeIf { party.showNames && style == MapStyle.LCM }
            head.draw(self.skin, self.x, self.z, self.yRot, style.headLook(true, party.classes[selfName]), selfLabel)
        }
    }

    private class Head(val context: GuiGraphicsExtractor, val font: Font, val geometry: MapGeometry, val sizeFactor: Float) {
        fun draw(skin: PlayerSkin, worldX: Double, worldZ: Double, yaw: Float, look: HeadLook, name: String?) {
            val centerX = geometry.worldToMap(worldX)
            val centerZ = geometry.worldToMap(worldZ)
            val onMap = 0.0..geometry.size.toDouble()
            if (centerX !in onMap || centerZ !in onMap) return
            val pose = context.pose()
            pose.pushMatrix()
            pose.translate(centerX.toInt().toFloat(), centerZ.toInt().toFloat())
            if (name != null) {
                pose.pushMatrix()
                pose.scale(MapStyle.LEAP_NAME_SCALE, MapStyle.LEAP_NAME_SCALE)
                context.centeredText(font, name, 0, MapStyle.LEAP_NAME_OFFSET, MapStyle.LEAP_NAME_COLOR)
                pose.popMatrix()
            }
            pose.rotate(Math.toRadians(FunnyMapLayout.headRotationDegrees(yaw).toDouble()).toFloat())
            if (look.arrow) {
                pose.scale(sizeFactor, sizeFactor)
                pose.translate(ARROW_CENTERING, ARROW_CENTERING)
                context.blit(RenderPipelines.GUI_TEXTURED, ARROW_TEXTURE, ARROW_LEFT, ARROW_TOP, ARROW_U, 0f, ARROW_WIDTH, ARROW_HEIGHT, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE)
            } else {
                // The face is sized in whole pixels so it stays crisp; the border around it keeps
                // its width whatever the size.
                val face = (look.faceSize * sizeFactor).roundToInt()
                val half = face / 2
                val border = (look.backdropSize - look.faceSize) / 2
                if (look.backdropSize > 0) context.fill(-half - border, -half - border, face - half + border, face - half + border, look.backdropColor)
                PlayerFaceExtractor.extractRenderState(context, skin, -half, -half, face)
            }
            pose.popMatrix()
        }
    }
}
