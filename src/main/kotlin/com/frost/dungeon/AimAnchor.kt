package com.frost.dungeon

/**
 * Which part of an etherwarp waypoint's box [AimAssist] aims at, set per waypoint in Aim
 * Assist's edit mode (left-click a waypoint to step to the [next] one). [OFF] removes that
 * waypoint from the assist entirely. Kept free of Minecraft types so it's unit-testable.
 *
 * (dx, dy, dz) is the face's outward direction - zero on an axis means "centered on that
 * axis". Directions use Minecraft's world axes: north is -Z, east is +X.
 */
enum class AimAnchor(val label: String, val dx: Int, val dy: Int, val dz: Int) {
    CENTER("Center", 0, 0, 0),
    TOP("Top", 0, 1, 0),
    BOTTOM("Bottom", 0, -1, 0),
    NORTH("North", 0, 0, -1),
    SOUTH("South", 0, 0, 1),
    EAST("East", 1, 0, 0),
    WEST("West", -1, 0, 0),
    OFF("Off", 0, 0, 0),
    ;

    fun next(): AimAnchor = entries[(ordinal + 1) % entries.size]

    /** North/South/East/West - the only anchors whose meaning changes with room rotation. */
    val isHorizontalFace: Boolean get() = dy == 0 && (dx != 0 || dz != 0)

    /** The point on box (min..max) this anchor aims at: the box's center, pushed out to the
     * min or max edge on whichever axis the face points along. */
    fun aimPoint(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Triple<Double, Double, Double> =
        Triple(pick(dx, minX, maxX), pick(dy, minY, maxY), pick(dz, minZ, maxZ))

    /**
     * Re-expresses a horizontal face in another frame (world <-> room-relative), using
     * [mapOffset] to carry a horizontal (dx, dz) direction across. Center/Top/Bottom/Off don't
     * depend on which way the room faces, so they pass through unchanged. Falls back to
     * [CENTER] if the mapped direction isn't a clean cardinal one.
     */
    fun convert(mapOffset: (dx: Int, dz: Int) -> Pair<Int, Int>): AimAnchor {
        if (!isHorizontalFace) return this
        val (mappedX, mappedZ) = mapOffset(dx, dz)
        return entries.firstOrNull { it.isHorizontalFace && it.dx == mappedX && it.dz == mappedZ } ?: CENTER
    }

    private fun pick(direction: Int, min: Double, max: Double): Double = when {
        direction > 0 -> max
        direction < 0 -> min
        else -> (min + max) / 2.0
    }

    companion object {
        fun fromName(name: String?): AimAnchor = entries.firstOrNull { it.name == name } ?: CENTER
    }
}
