package com.ddupg.runtrip.feature.training

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

    @Test fun updateDisabledDuringImportAndMissingDistanceIsVisible() {
        val record = TrainingActivity("synthetic", TrainingSubtype.POOL_SWIM, LocalDate.parse("2026-11-02"), null, null, 2160)
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = listOf(record), updating = true, progress = "正在获取记录…"), {}, {}, {})
        } }
        compose.onNodeWithText("更新").assertIsNotEnabled()
        compose.onNodeWithText("泳池游泳").assertExists()
        compose.onNodeWithText("距离未记录").assertExists()
    }
}
