package com.frost.dungeon

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The pure angle math behind [AimAssist], kept free of Minecraft types so it's unit-testable.
 * Uses Minecraft's own rotation convention: yaw 0 faces +Z (south) and grows clockwise
 * toward -X, pitch is positive looking DOWN - matching Entity.getViewVector, so a rotation
 * from [rotationTo] can be compared directly against the player's own yRot/xRot.
 */
object AimAssistMath {
    data class Rotation(val yaw: Float, val pitch: Float)

    // Only waypoints within this many degrees of the crosshair get pulled toward at all -
    // "slight" assist means it should never grab the view from across the room, only help
    // settle onto a waypoint the player is already aiming roughly at.
    const val ASSIST_CONE_DEGREES = 8f

    // Pull rate (1/s) at 100% strength, dead center of the cone - at 60fps that closes ~39%
    // of the remaining gap each frame. Lower strengths scale this linearly. Doubled from 15
    // after live testing found the original too weak to notice.
    private const val MAX_PULL_RATE = 30.0

    // Close enough to stop nudging - avoids sub-pixel jitter once already on target.
    private const val DEADZONE_DEGREES = 0.05f

    fun rotationTo(fromX: Double, fromY: Double, fromZ: Double, toX: Double, toY: Double, toZ: Double): Rotation {
        val dx = toX - fromX
        val dy = toY - fromY
        val dz = toZ - fromZ
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat()
        return Rotation(yaw, pitch)
    }

    /** Wraps to [-180, 180) - the player's raw yRot is unbounded (it just keeps counting up
     * as they spin), so yaw differences must be wrapped before comparing. */
    fun wrapDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d >= 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun angularDistance(from: Rotation, to: Rotation): Float =
        hypot(wrapDegrees(to.yaw - from.yaw), to.pitch - from.pitch)

    /**
     * How far to rotate this frame, as a (yaw, pitch) delta in degrees - or null when
     * [target] is outside the assist cone or already inside the deadzone. Exponential
     * smoothing keeps the pull frame-rate independent, and it fades out toward the cone's
     * edge so it's easy to aim away from (the player's own mouse always wins at the edge).
     */
    fun step(current: Rotation, target: Rotation, strengthPercent: Int, frameSeconds: Double): Rotation? {
        val distance = angularDistance(current, target)
        if (distance > ASSIST_CONE_DEGREES || distance < DEADZONE_DEGREES) return null
        val falloff = 1.0 - distance / ASSIST_CONE_DEGREES
        val rate = strengthPercent.coerceIn(0, 100) / 100.0 * MAX_PULL_RATE * falloff
        val fraction = (1.0 - exp(-rate * frameSeconds)).toFloat()
        if (fraction <= 0f) return null
        return Rotation(
            wrapDegrees(target.yaw - current.yaw) * fraction,
            (target.pitch - current.pitch) * fraction,
        )
    }
}
