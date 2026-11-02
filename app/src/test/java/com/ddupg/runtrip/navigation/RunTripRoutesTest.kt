package com.ddupg.runtrip.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTripRoutesTest {
    @Test
    fun `bottom navigation is only visible on top level routes`() {
        assertTrue(RunTripRoutes.isTopLevel(RunTripRoutes.HOME))
        assertTrue(RunTripRoutes.isTopLevel(RunTripRoutes.TRAINING))
        assertTrue(RunTripRoutes.isTopLevel(RunTripRoutes.TOOLS))
        assertTrue(RunTripRoutes.isTopLevel(RunTripRoutes.ME))

        assertFalse(RunTripRoutes.isTopLevel(RunTripRoutes.PACE_CALCULATOR))
        assertFalse(RunTripRoutes.isTopLevel(RunTripRoutes.EDIT_RACE_PATTERN))
        assertFalse(RunTripRoutes.isTopLevel(RunTripRoutes.RACE_DETAIL_PATTERN))
        assertFalse(RunTripRoutes.isTopLevel(null))
    }
}
