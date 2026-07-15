package com.frost.dungeon

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * The dungeon's real room names, keyed by a "core hash" fingerprint of the blocks at one
 * fixed position within that room - see [WorldRoomScanner] for how that hash is computed
 * from the live world. This data and the whole identification technique are a direct port
 * of Odin's `RoomData`/`WorldScan` (BSD 3-Clause, see /THIRD_PARTY_NOTICES.md at the repo
 * root) - not reverse-engineered independently.
 */
object RoomDatabase {
    data class RoomInfo(
        val name: String,
        val type: String,
        val shape: String,
        @SerializedName("cores") val cores: List<Int> = emptyList(),
    )

    /** The 6 room-shape categories the bundled database actually uses - the room-weights
     * config screen groups by these. */
    val SHAPES = listOf("1x1", "1x2", "1x3", "1x4", "2x2", "L")

    // Puzzle/entrance/fairy/blood/trap rooms are all a single 1x1 cell in the bundled
    // database, so without this they'd otherwise get silently mixed into the "1x1 Rooms"
    // category alongside ordinary NORMAL/RARE/CHAMPION rooms - these are different enough
    // in kind (puzzles are solved, not cleared; entrance/fairy/blood aren't even part of
    // the normal room-clear credit at all - see Frost.kt's NON_CREDITABLE_ROOM_TYPES) that
    // they get pulled into their own "Misc" category instead.
    private val MISC_TYPES = setOf("PUZZLE", "ENTRANCE", "FAIRY", "BLOOD", "TRAP")
    const val MISC_CATEGORY = "Misc"

    /** Every category the room-weights config screen shows a button for: the 6 shapes
     * plus the [MISC_CATEGORY] bucket. */
    val CATEGORIES = SHAPES + MISC_CATEGORY

    /** Display label for a [CATEGORIES] entry - "L Rooms", "Misc. Rooms", "1x2 Rooms", etc. */
    fun categoryLabel(category: String): String = when (category) {
        MISC_CATEGORY -> "Misc. Rooms"
        "L" -> "L Rooms"
        else -> "$category Rooms"
    }

    private val coreToRoom: Map<Int, RoomInfo> by lazy {
        val gson = Gson()
        val stream = RoomDatabase::class.java.getResourceAsStream("/assets/frost/rooms.json")
            ?: return@lazy emptyMap()
        val rooms: Array<RoomInfo> = stream.use { input ->
            gson.fromJson(input.reader(), Array<RoomInfo>::class.java)
        }
        val map = HashMap<Int, RoomInfo>()
        for (room in rooms) {
            for (core in room.cores) {
                map[core] = room
            }
        }
        map
    }

    // Every distinct room (a room can have several cores - one per map variant - all
    // pointing at the same RoomInfo instance, so distinct() here dedupes back down to one
    // entry per real room), sorted alphabetically for a stable, readable list order.
    val allRooms: List<RoomInfo> by lazy { coreToRoom.values.distinct().sortedBy { it.name } }

    fun lookup(coreHash: Int): RoomInfo? = coreToRoom[coreHash]

    /** Every room of [shape] - excluding the special [MISC_CATEGORY] types even if one
     * happens to share that shape (all of them are 1x1 today, but this holds regardless). */
    fun roomsByShape(shape: String): List<RoomInfo> = allRooms.filter { it.shape == shape && it.type !in MISC_TYPES }

    /** Every room in [category] - one of [SHAPES] or [MISC_CATEGORY]. */
    fun roomsInCategory(category: String): List<RoomInfo> =
        if (category == MISC_CATEGORY) allRooms.filter { it.type in MISC_TYPES } else roomsByShape(category)
}
