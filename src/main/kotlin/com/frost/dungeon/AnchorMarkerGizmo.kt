package com.frost.dungeon

import net.minecraft.gizmos.Gizmo
import net.minecraft.gizmos.GizmoPrimitives
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small filled disc with an outline, lying flat in the plane facing [normal] - drawn on a
 * waypoint's aim anchor so the player can see exactly which point [AimAssist] pulls toward.
 *
 * A custom gizmo rather than vanilla's Gizmos.circle, which is hardcoded flat on the X/Z
 * plane: fine for a Top/Bottom anchor, but edge-on (nearly invisible) for a North/South/
 * East/West face. The fan is emitted with both windings so it shows from either side
 * regardless of backface culling.
 */
class AnchorMarkerGizmo(
    private val center: Vec3,
    private val normal: Vec3,
    private val radius: Double,
    private val fillColor: Int,
    private val outlineColor: Int,
) : Gizmo {
    override fun emit(primitives: GizmoPrimitives, alpha: Float) {
        val n = normal.normalize()
        val helper = if (abs(n.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val u = n.cross(helper).normalize()
        val v = n.cross(u).normalize()

        val ring = Array(SEGMENTS + 1) { i ->
            val angle = 2.0 * Math.PI * i / SEGMENTS
            center.add(u.scale(cos(angle) * radius)).add(v.scale(sin(angle) * radius))
        }
        val fill = withAlpha(fillColor, alpha)
        primitives.addTriangleFan(arrayOf(center, *ring), fill)
        primitives.addTriangleFan(arrayOf(center, *ring.reversedArray()), fill)

        val outline = withAlpha(outlineColor, alpha)
        for (i in 0 until SEGMENTS) primitives.addLine(ring[i], ring[i + 1], outline, OUTLINE_WIDTH)
    }

    private fun withAlpha(argb: Int, alpha: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)).toInt()
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    private companion object {
        const val SEGMENTS = 20
        const val OUTLINE_WIDTH = 2f
    }
}
