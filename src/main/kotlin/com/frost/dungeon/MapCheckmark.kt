package com.frost.dungeon

/**
 * Room-state colors used on the held dungeon map item. Minecraft map pixels are stored
 * as a single palette-index byte per pixel; these are the index values Hypixel uses for
 * each checkmark state (cross-referenced against known open-source Hypixel dungeon mods'
 * color tables, not reverse-engineered by us from scratch).
 */
enum class MapCheckmark(val colorId: Byte) {
    UNDISCOVERED(-1),
    WHITE(34), // minimum objectives (mobs) complete - the real "room cleared" signal
    GREEN(30), // that room's secrets are ALSO 100% found - a stricter follow-on state
    RED(18), // specifically a FAILED PUZZLE ROOM (confirmed via the Hypixel wiki), not a
             // general "not ready" state - this happens to share a byte value with
             // RoomType.BLOOD, so a Blood room showing RED here is a coincidental color
             // collision, not an actual failed puzzle in that room
    QUESTION_MARK(119),
    ;

    companion object {
        fun fromColorId(id: Byte): MapCheckmark = entries.find { it.colorId == id } ?: UNDISCOVERED
    }

    /**
     * WHITE means the room's mobs are dead ("cleared" in the everyday sense); GREEN means
     * that room's secrets are ALSO 100% found - a stricter, separate condition. Confirmed
     * directly by the user from in-game observation. Room-clear detection should trigger
     * on reaching either (whichever comes first), not GREEN alone - that was the actual
     * bug behind rooms/room-credit never incrementing.
     */
    fun isCleared(): Boolean = this == WHITE || this == GREEN
}

/**
 * A room's fixed type, sampled from a different pixel (the cell's corner) than its
 * cleared/checkmark state (the cell's center) - these are two independent readings of
 * the same map, not the same value, which is why some color IDs are shared between
 * [MapCheckmark] and [RoomType] without conflict. Puzzle rooms are always PUZZLE-colored
 * and always occupy a single 1x1 cell, which is what makes them locatable this way.
 */
enum class RoomType(val colorId: Byte) {
    UNDISCOVERED(-1),
    ENTRANCE(30),
    FAIRY(82),
    NORMAL(63),
    RARE(63),
    BLOOD(18),
    CHAMPION(74),
    UNKNOWN(85),
    PUZZLE(66),
    TRAP(62),
    ;

    companion object {
        fun fromColorId(id: Byte): RoomType = entries.find { it.colorId == id } ?: UNDISCOVERED
    }
}
