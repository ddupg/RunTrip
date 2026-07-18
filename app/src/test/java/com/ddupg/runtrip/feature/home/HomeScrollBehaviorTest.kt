package com.ddupg.runtrip.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScrollBehaviorTest {
    @Test
    fun titleCollapsesWhenTopAppBarIsHalfCollapsed() {
        assertFalse(isHomeTitleCollapsed(0.49f))
        assertTrue(isHomeTitleCollapsed(0.5f))
    }
}
