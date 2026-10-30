package com.frost.dungeon

import com.frost.Frost
import com.frost.config.ModConfig
import com.frost.gui.FrostTheme
import com.frost.util.SkyblockItems
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.Gizmos
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Gently pulls the crosshair toward the nearest Odin etherwarp waypoint (see
 * [OdinWaypointBridge]) - only while the feature is on, the player is holding an Aspect of
 * the Void, and is sneaking (i.e. actually lined up to etherwarp). Strength comes from the
 * Aim Assist screen's slider; the cone/falloff/smoothing math lives in [AimAssistMath].
 * Which part of each waypoint it aims at is a per-waypoint [AimAnchor], set in [editMode].
 *
 * Runs once per rendered FRAME (LevelRenderEvents.START_MAIN), not per client tick - at 20
 * ticks/sec the pull would visibly step instead of gliding. The rotation is applied through
 * Entity.turn, the same call vanilla's MouseHandler uses for real mouse input, so it updates
 * the previous-rotation fields too and interpolates exactly like the player moving the mouse.
 */
object AimAssist {

    // Etherwarp's max range with a fully-tuned Transmission (57 base + 4) - a waypoint farther
    // than this can't be warped to anyway, so it's not worth pulling toward. Also the reach
    // for picking a waypoint to edit, since they're usually well out of normal click range.
    private const val ETHERWARP_RANGE = 61.0

    // Entity.turn multiplies its input by this (a mouse-sensitivity constant), so degree
    // deltas are divided by it going in.
    private const val TURN_SCALE = 0.15

    // A long hitch (alt-tab, lag spike) shouldn't turn into one giant snap on the next frame.
    private const val MAX_FRAME_SECONDS = 0.1

    private val EDIT_HUD_ID = Identifier.fromNamespaceAndPath(Frost.MOD_ID, "aim_assist_edit")

    /**
     * Edit mode: while on, an "Editing Aim Assist" label stays on screen and each left-click
     * on an Odin etherwarp waypoint cycles its anchor. The click is only OBSERVED, never
     * consumed - whatever left-click normally does still happens. Toggled from the Aim Assist
     * screen or /frost aim edit; deliberately in-memory only (never saved), so a restart
     * always turns it back off.
     */
    var editMode = false

    // How long an anchor/feedback message stays under the crosshair after a click.
    private const val FEEDBACK_NANOS = 2_000_000_000L
    // Gap (GUI pixels) between the crosshair and the edit-mode text above/below it.
    private const val HUD_TEXT_OFFSET = 12
    private val ERROR_COLOR = 0xFFFF5555.toInt()

    // Anchor marker: a yellow disc with a darker gold outline, sized per block of distance
    // (so it reads the same on screen near or far), clamped so it never vanishes or balloons.
    private val MARKER_FILL = 0xE6FFD84A.toInt()
    private val MARKER_OUTLINE = 0xFFB8860B.toInt()
    private const val MARKER_SIZE_PER_BLOCK = 0.006
    private const val MARKER_MIN_RADIUS = 0.06
    private const val MARKER_MAX_RADIUS = 0.3

    private var lastFrameNanos = 0L
    private var attackWasDown = false
    private var feedback: String? = null
    private var feedbackColor = 0
    private var feedbackUntilNanos = 0L

    fun register() {
        LevelRenderEvents.START_MAIN.register { onFrame() }
        HudElementRegistry.addLast(EDIT_HUD_ID) { context, _ -> drawEditHud(context) }
    }

    private fun onFrame() {
        val now = System.nanoTime()
        val frameSeconds = if (lastFrameNanos == 0L) 0.0 else ((now - lastFrameNanos) / 1e9).coerceAtMost(MAX_FRAME_SECONDS)
        lastFrameNanos = now

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        detectEditClick(client, player)

        val assistActive = ModConfig.isAimAssistEnabled() && client.gui.screen() == null &&
            player.isShiftKeyDown && SkyblockItems.isAspectOfTheVoid(player.mainHandItem)
        if (!assistActive && !editMode) return
        val room = OdinWaypointBridge.currentRoom() ?: return

        val eye = player.eyePosition
        val current = AimAssistMath.Rotation(player.yRot, player.xRot)
        val best = room.etherwarps
            .filter { !it.clicked }
            .mapNotNull { anchorTarget(room, it, eye, current) }
            .filter { it.point.distanceToSqr(eye) <= ETHERWARP_RANGE * ETHERWARP_RANGE }
            .minByOrNull { it.degreesFromCrosshair }
            ?.takeIf { it.degreesFromCrosshair <= AimAssistMath.ASSIST_CONE_DEGREES }

        // Edit mode marks the waypoint actually under the crosshair (the one a click would
        // edit), falling back to whatever the assist would pick; otherwise it's the assist's
        // own target - the point it's pulling toward right now.
        val marked = if (editMode) {
            waypointUnderCrosshair(player, room)?.let { anchorTarget(room, it, eye, current) } ?: best
        } else {
            best
        }
        marked?.let { drawAnchorMarker(it, eye) }

        if (!assistActive || best == null) return
        val delta = AimAssistMath.step(current, best.rotation, ModConfig.getAimAssistStrength(), frameSeconds) ?: return
        player.turn(delta.yaw / TURN_SCALE, delta.pitch / TURN_SCALE)
    }

    /** One waypoint's resolved aim point: where its anchor sits in the world, and how far
     * that is from the crosshair. */
    private class AnchorTarget(
        val anchor: AimAnchor,
        val point: Vec3,
        val rotation: AimAssistMath.Rotation,
        val degreesFromCrosshair: Float,
    )

    /** Null when [waypoint]'s anchor is [AimAnchor.OFF] - it's neither aimed at nor marked. */
    private fun anchorTarget(
        room: OdinWaypointBridge.CurrentRoom,
        waypoint: OdinWaypointBridge.Waypoint,
        eye: Vec3,
        current: AimAssistMath.Rotation,
    ): AnchorTarget? {
        val anchor = worldAnchor(room, waypoint)
        if (anchor == AimAnchor.OFF) return null
        val box = waypoint.box
        val (x, y, z) = anchor.aimPoint(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        val rotation = AimAssistMath.rotationTo(eye.x, eye.y, eye.z, x, y, z)
        return AnchorTarget(anchor, Vec3(x, y, z), rotation, AimAssistMath.angularDistance(current, rotation))
    }

    /**
     * The small yellow disc on the anchor point. It lies flat on the anchor's face; a Center
     * anchor has no face, so it turns to face the player. Always-on-top so a Center anchor
     * (inside the block) and anything partly behind Odin's waypoint box still shows.
     * Re-added every frame - vanilla drains per-frame gizmos after drawing them.
     */
    private fun drawAnchorMarker(target: AnchorTarget, eye: Vec3) {
        val normal = if (target.anchor == AimAnchor.CENTER) {
            eye.subtract(target.point)
        } else {
            Vec3(target.anchor.dx.toDouble(), target.anchor.dy.toDouble(), target.anchor.dz.toDouble())
        }
        // Scaled with distance so it stays about the same size on screen, near or far.
        val radius = (target.point.distanceTo(eye) * MARKER_SIZE_PER_BLOCK).coerceIn(MARKER_MIN_RADIUS, MARKER_MAX_RADIUS)
        // Explicitly scoped to the level renderer's per-frame collector: Gizmos.addGizmo throws
        // if none is active, and this doesn't rely on exactly when Fabric's render event fires
        // relative to vanilla opening one. The scope restores whatever was active before.
        Minecraft.getInstance().levelRenderer.collectPerFrameRenderThreadGizmos().use {
            Gizmos.addGizmo(AnchorMarkerGizmo(target.point, normal, radius, MARKER_FILL, MARKER_OUTLINE)).setAlwaysOnTop()
        }
    }

    private fun waypointUnderCrosshair(player: LocalPlayer, room: OdinWaypointBridge.CurrentRoom): OdinWaypointBridge.Waypoint? {
        val hit = player.pick(ETHERWARP_RANGE, 1f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return room.etherwarps.firstOrNull { it.pos == hit.blockPos }
            ?: room.etherwarps.firstOrNull { it.box.inflate(0.01).contains(hit.location) }
    }

    /**
     * Fires [cycleAnchorAtCrosshair] on the frame the attack key goes down. Polled per frame
     * as a plain up/down edge on KeyMapping.isDown rather than via consumeClick, which would
     * steal the click from vanilla - and per frame rather than per tick so a quick tap
     * (shorter than one 50ms tick) still registers.
     */
    private fun detectEditClick(client: Minecraft, player: LocalPlayer) {
        val down = editMode && client.gui.screen() == null && client.options.keyAttack.isDown
        if (down && !attackWasDown) cycleAnchorAtCrosshair(player)
        attackWasDown = down
    }

    private fun cycleAnchorAtCrosshair(player: LocalPlayer) {
        val room = OdinWaypointBridge.currentRoom() ?: return showFeedback("No Odin room detected here", ERROR_COLOR)
        val waypoint = waypointUnderCrosshair(player, room)
            ?: return showFeedback("Look at an Odin etherwarp waypoint", ERROR_COLOR)
        val relative = room.toRelative(waypoint.pos) ?: return

        val next = worldAnchor(room, waypoint).next()
        // Saved room-relative, so "North" means the same face of this block however the
        // room is rotated next run.
        val stored = next.convert { dx, dz ->
            room.toRelative(waypoint.pos.offset(dx, 0, dz))?.let { (it.x - relative.x) to (it.z - relative.z) } ?: (0 to 0)
        }
        ModConfig.setAimAssistAnchor(anchorKey(room.name, relative), stored.takeIf { it != AimAnchor.CENTER }?.name)
        showFeedback("Anchor: ${next.label}", FrostTheme.TEXT_PRIMARY)
    }

    private fun showFeedback(message: String, color: Int) {
        feedback = message
        feedbackColor = color
        feedbackUntilNanos = System.nanoTime() + FEEDBACK_NANOS
    }

    /** [waypoint]'s saved anchor, converted from room-relative back to world directions. */
    private fun worldAnchor(room: OdinWaypointBridge.CurrentRoom, waypoint: OdinWaypointBridge.Waypoint): AimAnchor {
        val relative = room.toRelative(waypoint.pos) ?: return AimAnchor.CENTER
        val stored = AimAnchor.fromName(ModConfig.getAimAssistAnchor(anchorKey(room.name, relative)))
        return stored.convert { dx, dz ->
            room.toWorld(relative.offset(dx, 0, dz))?.let { (it.x - waypoint.pos.x) to (it.z - waypoint.pos.z) } ?: (0 to 0)
        }
    }

    private fun anchorKey(roomName: String, relative: BlockPos): String =
        "$roomName|${relative.x},${relative.y},${relative.z}"

    /** "Editing Aim Assist" just above the crosshair, and the latest anchor/feedback message
     * just below it for [FEEDBACK_NANOS] - drawn as Frost's own HUD text rather than the
     * action bar, which Hypixel overwrites constantly (health/mana/etc.). */
    private fun drawEditHud(context: GuiGraphicsExtractor) {
        if (!editMode) return
        val client = Minecraft.getInstance()
        val font = client.font
        val centerX = client.window.guiScaledWidth / 2
        val centerY = client.window.guiScaledHeight / 2
        context.centeredText(font, "Editing Aim Assist", centerX, centerY - HUD_TEXT_OFFSET - font.lineHeight, FrostTheme.ACCENT)
        val message = feedback
        if (message != null && System.nanoTime() < feedbackUntilNanos) {
            context.centeredText(font, message, centerX, centerY + HUD_TEXT_OFFSET, feedbackColor)
        }
    }
}
