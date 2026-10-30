package com.frost.dungeon

import com.frost.dungeon.AimAssistMath.Rotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimAssistMathTest {
    private val tolerance = 0.01f

    @Test
    fun `rotationTo matches Minecraft's yaw and pitch convention`() {
        // yaw 0 = +Z (south), 90 = -X (west), -90 = +X (east); pitch positive = looking down.
        assertEquals(0f, AimAssistMath.rotationTo(0.0, 0.0, 0.0, 0.0, 0.0, 10.0).yaw, tolerance)
        assertEquals(90f, AimAssistMath.rotationTo(0.0, 0.0, 0.0, -10.0, 0.0, 0.0).yaw, tolerance)
        assertEquals(-90f, AimAssistMath.rotationTo(0.0, 0.0, 0.0, 10.0, 0.0, 0.0).yaw, tolerance)
        assertEquals(45f, AimAssistMath.rotationTo(0.0, 0.0, 0.0, 0.0, -10.0, 10.0).pitch, tolerance)
        assertEquals(-45f, AimAssistMath.rotationTo(0.0, 0.0, 0.0, 0.0, 10.0, 10.0).pitch, tolerance)
    }

    @Test
    fun `angular distance wraps yaw across the 180 seam`() {
        assertEquals(2f, AimAssistMath.angularDistance(Rotation(179f, 0f), Rotation(-179f, 0f)), tolerance)
        // The player's raw yRot is unbounded - 720 + 5 is still 5 degrees from 0.
        assertEquals(5f, AimAssistMath.angularDistance(Rotation(725f, 0f), Rotation(0f, 0f)), tolerance)
    }

    @Test
    fun `step ignores targets outside the assist cone`() {
        val far = Rotation(AimAssistMath.ASSIST_CONE_DEGREES + 1f, 0f)
        assertNull(AimAssistMath.step(Rotation(0f, 0f), far, 100, 1 / 60.0))
    }

    @Test
    fun `step does nothing once already on target`() {
        assertNull(AimAssistMath.step(Rotation(10f, 5f), Rotation(10f, 5f), 100, 1 / 60.0))
    }

    @Test
    fun `step moves part of the way toward the target, never past it`() {
        val delta = assertNotNull(AimAssistMath.step(Rotation(0f, 0f), Rotation(2f, -1f), 50, 1 / 60.0))
        assertTrue(delta.yaw > 0f && delta.yaw < 2f)
        assertTrue(delta.pitch < 0f && delta.pitch > -1f)
    }

    @Test
    fun `higher strength pulls harder`() {
        val weak = assertNotNull(AimAssistMath.step(Rotation(0f, 0f), Rotation(3f, 0f), 10, 1 / 60.0))
        val strong = assertNotNull(AimAssistMath.step(Rotation(0f, 0f), Rotation(3f, 0f), 100, 1 / 60.0))
        assertTrue(strong.yaw > weak.yaw)
    }

    @Test
    fun `step takes the short way around the yaw seam`() {
        val delta = assertNotNull(AimAssistMath.step(Rotation(179f, 0f), Rotation(-179f, 0f), 100, 1 / 60.0))
        assertTrue(delta.yaw > 0f, "should turn +2 through 180, not -358 the long way")
    }
}
