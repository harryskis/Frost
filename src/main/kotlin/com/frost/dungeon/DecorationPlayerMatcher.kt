package com.frost.dungeon

/**
 * Hypixel's dungeon map decorations carry no player-identifying key at all - just a
 * type/position/rotation (confirmed against the actual client API, which exposes only
 * `type()`/`x()`/`z()`/`rotation()`, no name or UUID). Reference dungeon mods (Odin,
 * Skyblocker) both work around this the same way, confirmed by reading their source: the
 * decoration marked "self" (vanilla's FRAME type) is always the local player, and every
 * OTHER decoration is matched, in iteration order, to the next party member (excluding
 * the local player). This isn't a guarantee - Hypixel could reorder decorations between
 * ticks - but it's the same accepted heuristic those mods ship with, and it's the only
 * way to place a teammate whose entity isn't currently loaded into the client at all
 * (which happens often in a large, spread-out dungeon floor).
 */
object DecorationPlayerMatcher {
    data class Decoration(val isSelf: Boolean, val tile: GridPos)

    /**
     * [partyOrder] should be every party member's username in a stable, consistent order
     * (e.g. insertion order) so a given decoration index maps to the same teammate from
     * tick to tick.
     */
    fun match(decorations: List<Decoration>, localUsername: String, partyOrder: List<String>): Map<String, GridPos> {
        val result = LinkedHashMap<String, GridPos>()
        val others = partyOrder.filter { it != localUsername }.iterator()
        for (deco in decorations) {
            if (deco.isSelf) {
                result[localUsername] = deco.tile
            } else if (others.hasNext()) {
                result[others.next()] = deco.tile
            }
        }
        return result
    }
}
