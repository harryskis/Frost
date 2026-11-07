package com.frost.dungeon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapFixRulesTest {
    @Test
    fun `a teammate's head in the leap menu is read by the first word of its name`() {
        assertEquals("Steve", LeapFixRules.targetName("§bSteve"))
        assertEquals("alex_2", LeapFixRules.targetName("  §aalex_2 §7(Mage)"))
        assertNull(LeapFixRules.targetName("   "))
    }

    @Test
    fun `it turns the player only for a teleport soon after the click that leaves their rotation alone`() {
        assertTrue(LeapFixRules.shouldTurn(3, 10, relativeRotation = true, rotationChanged = false))
        assertTrue(LeapFixRules.shouldTurn(10, 10, relativeRotation = true, rotationChanged = false))
        assertFalse(LeapFixRules.shouldTurn(11, 10, relativeRotation = true, rotationChanged = false))
        // A teleport that sets the rotation itself isn't the leap, or is the server choosing it.
        assertFalse(LeapFixRules.shouldTurn(3, 10, relativeRotation = false, rotationChanged = false))
        assertFalse(LeapFixRules.shouldTurn(3, 10, relativeRotation = true, rotationChanged = true))
    }
}
