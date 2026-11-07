package com.frost.dungeon

import com.frost.util.TextUtils

/** Leap Fix's decisions, kept free of Minecraft types so they can be tested. */
object LeapFixRules {
    /** The menus a spirit leap opens: the Spirit Leap's own, and the Infinileap's. */
    val MENU_TITLES = setOf("Spirit Leap", "Teleport to Player")

    /** Where the teammates' heads sit in those menus. */
    val TEAMMATE_SLOTS = 11..16

    const val DEFAULT_TIMEOUT_TICKS = 10
    const val MIN_TIMEOUT_TICKS = 1
    const val MAX_TIMEOUT_TICKS = 20

    /** The teammate a head in the leap menu is for: the first word of its name, as RSM reads it. */
    fun targetName(itemName: String): String? =
        TextUtils.stripFormatting(itemName).trim().split(' ').firstOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Whether to turn the player as a teleport arrives [ticksSinceClick] after they clicked: only
     * soon enough after, and only for a teleport that leaves their rotation as it was - one that
     * sets a rotation of its own isn't the leap, or is the server saying where to look.
     */
    fun shouldTurn(ticksSinceClick: Long, timeoutTicks: Int, relativeRotation: Boolean, rotationChanged: Boolean): Boolean =
        ticksSinceClick in 0..timeoutTicks && relativeRotation && !rotationChanged
}
