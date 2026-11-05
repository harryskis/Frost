package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyPickupTimingTest {
    @Test
    fun `the same key announced again moments later is the same pickup`() {
        assertFalse(KeyPickupTiming.isSamePickup(null, 10_000))
        assertTrue(KeyPickupTiming.isSamePickup(10_000, 10_200))
        assertTrue(KeyPickupTiming.isSamePickup(10_000, 10_000 + KeyPickupTiming.SAME_PICKUP_MILLIS - 1))
        assertFalse(KeyPickupTiming.isSamePickup(10_000, 10_000 + KeyPickupTiming.SAME_PICKUP_MILLIS))
        assertFalse(KeyPickupTiming.isSamePickup(10_000, 60_000))
    }

    @Test
    fun `the notification is solid, then fades over its last moments, then is gone`() {
        val duration = 2000L
        assertEquals(1f, KeyPickupTiming.opacity(0, duration))
        assertEquals(1f, KeyPickupTiming.opacity(duration - KeyPickupTiming.FADE_MILLIS, duration))
        assertEquals(0.5f, KeyPickupTiming.opacity(duration - KeyPickupTiming.FADE_MILLIS / 2, duration), 0.01f)
        assertEquals(0f, KeyPickupTiming.opacity(duration, duration))
        assertEquals(0f, KeyPickupTiming.opacity(-5, duration))
    }

    @Test
    fun `fading scales the color's own alpha and keeps its color`() {
        assertEquals(0x80FF5555.toInt(), KeyPickupTiming.withOpacity(0xFFFF5555.toInt(), 128 / 255f))
        assertEquals(0x40123456, KeyPickupTiming.withOpacity(0x80123456.toInt(), 0.5f))
        assertEquals(0x00FF5555, KeyPickupTiming.withOpacity(0xFFFF5555.toInt(), 0f))
    }
}
