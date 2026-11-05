package com.ddupg.runtrip.feature.training

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.model.TrainingSubtype
import com.ddupg.runtrip.ui.theme.RunTripTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unboundHasOneDirectConnectionEntryAndNoUpdateOrFilters() {
        var connects = 0
        compose.setContent { RunTripTheme { TrainingScreen(TrainingUiState(loading = false), {}, {}, { connects++ }) } }
        compose.onNodeWithText("前往「我」连接").performClick()
        assertEquals(1, connects)
        compose.onNodeWithText("更新").assertDoesNotExist()
        compose.onNodeWithText("全部").assertDoesNotExist()
    }

    @Test fun recordHasNoDetailActionAndFilterAndUpdateAreAvailable() {
        var selected: TrainingSport? = null
        var updates = 0
        val record = TrainingActivity("synthetic", TrainingSubtype.OUTDOOR_RUN, LocalDate.parse("2026-11-02"), null, 5200.0, 2060)
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = listOf(record)), { selected = it }, { updates++ }, {})
        } }
        compose.onNodeWithText("户外跑").assert(!hasClickAction())
        compose.onNodeWithText("5.20 km").assertExists()
        compose.onNodeWithText("时长 00:34:20").assertExists()
        compose.onNodeWithText("全部").assertIsSelected()
        compose.onNodeWithText("游泳").performClick()
        assertEquals(TrainingSport.SWIMMING, selected)
        compose.onNodeWithText("更新").performClick()
        assertEquals(1, updates)
    }

    @Test fun progressUpdatesReuseProjectionUntilFilterOrZoneChanges() {
        val values = List(2000) { index ->
            TrainingActivity("id-$index", TrainingSubtype.OUTDOOR_RUN, LocalDate.parse("2026-11-02"), 1793579400L, 5000.0, 1800)
        }
        var reads = 0
        val records = object : AbstractList<TrainingActivity>() {
            override val size get() = values.size
            override fun get(index: Int): TrainingActivity { reads++; return values[index] }
        }
        val state = mutableStateOf(TrainingUiState(connected = true, loading = false, records = records))
        val zone = mutableStateOf(java.time.ZoneOffset.UTC as java.time.ZoneId)
        compose.setContent { RunTripTheme { TrainingScreen(state.value, {}, {}, {}, zone.value) } }
        compose.waitForIdle()
        val initialReads = reads
        assertTrue(initialReads >= values.size)
        compose.runOnIdle {
            state.value = state.value.copy(updating = true, progress = com.ddupg.runtrip.data.repository.TrainingImportProgress(
                com.ddupg.runtrip.data.repository.TrainingImportProgress.Phase.HISTORY, LocalDate.parse("2020-01-01"), LocalDate.parse("2025-12-31")))
        }
        compose.waitForIdle()
        assertEquals(initialReads, reads)
        compose.runOnIdle { zone.value = java.time.ZoneOffset.ofHours(9) }
        compose.waitForIdle()
        assertTrue(reads > initialReads)
        val zoneReads = reads
        compose.runOnIdle { state.value = state.value.copy(filter = TrainingSport.SWIMMING) }
        compose.waitForIdle()
        assertTrue(reads > zoneReads)
    }

    @Test fun updateDisabledDuringImportAndMissingDistanceIsVisible() {
        val record = TrainingActivity("synthetic", TrainingSubtype.POOL_SWIM, LocalDate.parse("2026-11-02"), null, null, 2160)
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = listOf(record), updating = true, progress = com.ddupg.runtrip.data.repository.TrainingImportProgress(com.ddupg.runtrip.data.repository.TrainingImportProgress.Phase.RECENT, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"))), {}, {}, {})
        } }
        compose.onNodeWithText("更新").assertIsNotEnabled()
        compose.onNodeWithText("泳池游泳").assertExists()
        compose.onNodeWithText("距离未记录").assertExists()
    }
}
