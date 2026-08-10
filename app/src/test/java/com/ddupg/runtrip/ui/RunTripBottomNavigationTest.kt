package com.ddupg.runtrip.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTripBottomNavigationTest {
    @Test
    fun `bottom navigation content uses compact height`() {
        assertEquals(64.dp, RunTripBottomNavigationContentHeight)
    }
}
