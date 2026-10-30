package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals

class AimAnchorTest {
    @Test
    fun `cycles center, top, bottom, north, south, east, west, off, then back to center`() {
        val order = generateSequence(AimAnchor.CENTER) { it.next() }.take(9).toList()
        assertEquals(
            listOf(
                AimAnchor.CENTER, AimAnchor.TOP, AimAnchor.BOTTOM, AimAnchor.NORTH, AimAnchor.SOUTH,
                AimAnchor.EAST, AimAnchor.WEST, AimAnchor.OFF, AimAnchor.CENTER,
            ),
            order,
        )
    }

    @Test
    fun `aim points sit on the matching face of the box`() {
        fun point(anchor: AimAnchor) = anchor.aimPoint(10.0, 20.0, 30.0, 11.0, 21.0, 31.0)
        assertEquals(Triple(10.5, 20.5, 30.5), point(AimAnchor.CENTER))
        assertEquals(Triple(10.5, 21.0, 30.5), point(AimAnchor.TOP))
        assertEquals(Triple(10.5, 20.0, 30.5), point(AimAnchor.BOTTOM))
        assertEquals(Triple(10.5, 20.5, 30.0), point(AimAnchor.NORTH)) // north = -Z
        assertEquals(Triple(10.5, 20.5, 31.0), point(AimAnchor.SOUTH))
        assertEquals(Triple(11.0, 20.5, 30.5), point(AimAnchor.EAST)) // east = +X
        assertEquals(Triple(10.0, 20.5, 30.5), point(AimAnchor.WEST))
    }

    @Test
    fun `horizontal faces rotate with the frame, everything else passes through`() {
        // A 90-degree clockwise turn (seen from above): north -> east -> south -> west.
        val rotateClockwise = { dx: Int, dz: Int -> -dz to dx }
        assertEquals(AimAnchor.EAST, AimAnchor.NORTH.convert(rotateClockwise))
        assertEquals(AimAnchor.SOUTH, AimAnchor.EAST.convert(rotateClockwise))
        assertEquals(AimAnchor.WEST, AimAnchor.SOUTH.convert(rotateClockwise))
        assertEquals(AimAnchor.NORTH, AimAnchor.WEST.convert(rotateClockwise))
        for (anchor in listOf(AimAnchor.CENTER, AimAnchor.TOP, AimAnchor.BOTTOM, AimAnchor.OFF)) {
            assertEquals(anchor, anchor.convert(rotateClockwise))
        }
    }

    @Test
    fun `an unmappable direction falls back to center`() {
        assertEquals(AimAnchor.CENTER, AimAnchor.NORTH.convert { _, _ -> 0 to 0 })
    }

    @Test
    fun `unknown or missing stored names default to center`() {
        assertEquals(AimAnchor.CENTER, AimAnchor.fromName(null))
        assertEquals(AimAnchor.CENTER, AimAnchor.fromName("SIDEWAYS"))
        assertEquals(AimAnchor.WEST, AimAnchor.fromName("WEST"))
    }
}
