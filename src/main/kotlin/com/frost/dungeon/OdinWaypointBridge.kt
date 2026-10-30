package com.frost.dungeon

import com.frost.Frost
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.lang.reflect.Method

/**
 * Reads Odin's etherwarp waypoints for the room the player is currently in, straight out of
 * Odin's own live state via reflection - no compile-time dependency, so Frost still builds
 * and runs without Odin installed (this just returns nothing).
 *
 * Odin's waypoint packs store each position RELATIVE to its room (see
 * config/odin/dungeon-waypoints/), and Odin rotates them into real world coordinates
 * itself whenever the player enters a room (`DungeonWaypointPacksKt.setWaypoints`, which
 * maps every entry through `DungeonRoom.getRealCoords`). So `DungeonUtils.currentRoom
 * .waypoints` is already exactly the set Odin is drawing, in world space - reading that
 * avoids re-deriving room rotation here, or anything as fragile as scanning the screen for
 * the waypoint color. [CurrentRoom.toRelative]/[CurrentRoom.toWorld] expose Odin's own
 * conversion the other way too, so per-waypoint aim anchors can be saved room-relative.
 *
 * Confirmed against Odin 0.3.1 (MC 26.1.2). If a future Odin renames any of this, resolution
 * fails once, gets logged, and the bridge quietly returns nothing from then on.
 */
object OdinWaypointBridge {
    private const val ODIN_MOD_ID = "odin"
    private const val DUNGEON_UTILS = "com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils"
    private const val DUNGEON_ROOM = "com.odtheking.odin.features.impl.dungeon.map.tile.DungeonRoom"
    private const val DUNGEON_WAYPOINTS = "com.odtheking.odin.features.impl.dungeon.dungeonwaypoints.DungeonWaypoints"
    private const val DUNGEON_WAYPOINT = "$DUNGEON_WAYPOINTS\$DungeonWaypoint"
    private const val ETHERWARP_TYPE = "ETHERWARP"

    /** One Odin etherwarp waypoint: [pos] in world space, [box] its world-space bounds
     * (Odin stores box size relative to the block, already offset here), and whether Odin
     * has marked it [clicked] (Odin's renderer hides those). */
    data class Waypoint(val pos: BlockPos, val box: AABB, val clicked: Boolean)

    /** The player's current Odin room: its name, its etherwarp waypoints, and Odin's own
     * world <-> room-relative coordinate conversion for it. */
    class CurrentRoom internal constructor(val name: String, val etherwarps: List<Waypoint>, private val room: Any) {
        fun toRelative(world: BlockPos): BlockPos? = convert(handles?.getRelativeCoords, world)
        fun toWorld(relative: BlockPos): BlockPos? = convert(handles?.getRealCoords, relative)

        private fun convert(method: Method?, pos: BlockPos): BlockPos? = try {
            method?.invoke(room, pos) as? BlockPos
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    private class Handles(
        val dungeonUtils: Any,
        val getCurrentRoom: Method,
        val getRoomWaypoints: Method,
        val getRoomName: Method,
        val getRelativeCoords: Method,
        val getRealCoords: Method,
        val waypointsModule: Any,
        val isModuleEnabled: Method,
        val getBlockPos: Method,
        val getAabb: Method,
        val getType: Method,
        val isClicked: Method,
    )

    private var failed = false

    private val handles: Handles? by lazy {
        if (!FabricLoader.getInstance().isModLoaded(ODIN_MOD_ID)) return@lazy null
        try {
            val utilsClass = Class.forName(DUNGEON_UTILS)
            val roomClass = Class.forName(DUNGEON_ROOM)
            val waypointsModuleClass = Class.forName(DUNGEON_WAYPOINTS)
            val waypointClass = Class.forName(DUNGEON_WAYPOINT)
            Handles(
                dungeonUtils = utilsClass.getField("INSTANCE").get(null),
                getCurrentRoom = utilsClass.getMethod("getCurrentRoom"),
                getRoomWaypoints = roomClass.getMethod("getWaypoints"),
                getRoomName = roomClass.getMethod("getName"),
                getRelativeCoords = roomClass.getMethod("getRelativeCoords", BlockPos::class.java),
                getRealCoords = roomClass.getMethod("getRealCoords", BlockPos::class.java),
                waypointsModule = waypointsModuleClass.getField("INSTANCE").get(null),
                isModuleEnabled = waypointsModuleClass.getMethod("getEnabled"),
                getBlockPos = waypointClass.getMethod("getBlockPos"),
                getAabb = waypointClass.getMethod("getAabb"),
                getType = waypointClass.getMethod("getType"),
                isClicked = waypointClass.getMethod("isClicked"),
            )
        } catch (e: ReflectiveOperationException) {
            Frost.logger.warn("Odin is installed but its waypoint API didn't match - Aim Assist disabled", e)
            null
        }
    }

    /**
     * The room the player is in plus every etherwarp waypoint Odin has for it, or null when
     * Odin isn't installed, its Dungeon Waypoints module is off, or the player isn't in a
     * known room. Clicked waypoints are included (flagged) - editing should still reach them.
     */
    fun currentRoom(): CurrentRoom? {
        if (failed) return null
        val h = handles ?: return null
        return try {
            if (h.isModuleEnabled.invoke(h.waypointsModule) != true) return null
            val room = h.getCurrentRoom.invoke(h.dungeonUtils) ?: return null
            val name = h.getRoomName.invoke(room) as? String ?: return null
            val waypoints = (h.getRoomWaypoints.invoke(room) as? Collection<*>)?.toList().orEmpty()
            val etherwarps = waypoints.mapNotNull { waypoint ->
                if (waypoint == null) return@mapNotNull null
                if ((h.getType.invoke(waypoint) as? Enum<*>)?.name != ETHERWARP_TYPE) return@mapNotNull null
                val pos = h.getBlockPos.invoke(waypoint) as? BlockPos ?: return@mapNotNull null
                val box = (h.getAabb.invoke(waypoint) as? AABB ?: AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
                    .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                Waypoint(pos, box, h.isClicked.invoke(waypoint) == true)
            }
            CurrentRoom(name, etherwarps, room)
        } catch (e: ConcurrentModificationException) {
            // Odin swapped this room's waypoint set mid-read (e.g. a pack reload) - transient,
            // just try again next frame.
            null
        } catch (e: ReflectiveOperationException) {
            failed = true
            Frost.logger.warn("Reading Odin's waypoints failed - Aim Assist disabled until restart", e)
            null
        }
    }
}
