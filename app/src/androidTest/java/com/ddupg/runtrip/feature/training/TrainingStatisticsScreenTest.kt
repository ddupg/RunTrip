package com.ddupg.runtrip.feature.training

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.model.TrainingSubtype
import com.ddupg.runtrip.ui.theme.RunTripTheme
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingStatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-11-06")
    private fun activity(id: String, date: String, subtype: TrainingSubtype, km: Double?) =
        TrainingActivity(id, subtype, LocalDate.parse(date), null, km?.times(1000), 3600)

    @Test fun combinedStatisticsRecalculateWhenPreferencesChangeAndKeepTrendWindowOnSelection() {
        val preferences = mutableStateOf(TrainingPreferences())
        val records = listOf(
            activity("run", "2026-11-02", TrainingSubtype.OUTDOOR_RUN, 30.0),
            activity("bike", "2026-11-03", TrainingSubtype.OUTDOOR_RIDE, 80.0),
            activity("swim", "2026-11-04", TrainingSubtype.POOL_SWIM, 2.0),
            activity("previous", "2026-10-26", TrainingSubtype.TRAIL_RUN, 50.0),
        )
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = records, historyComplete = true),
                {}, {}, {}, ZoneOffset.UTC, preferences.value, today)
        } }
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("58.0").assertExists()
        compose.onNodeWithText("较上周整周 +16.0%").assertExists()
        compose.runOnIdle { preferences.value = preferences.value.copy(cyclingFactor = 0.5) }
        compose.onNodeWithText("78.0").assertExists()
        compose.onNodeWithText("较上周整周 +56.0%").assertExists()
        compose.onNodeWithTag("training-statistics").performScrollToNode(hasText("周趋势"))
        compose.onNodeWithContentDescription("2026-10-26，50.0 km").performClick()
        compose.onNodeWithContentDescription("2026-11-02，78.0 km，进行中").assertExists()
        compose.onNodeWithContentDescription("2026-10-26，50.0 km").assertIsSelected()
        compose.onNodeWithTag("training-statistics").performScrollToIndex(2)
        compose.onNodeWithText("50.0").assertExists()
    }

    @Test fun monthlyPointSelectionShowsGrowthAndRunningReferenceForTheSelectedMonth() {
        val records = listOf(
            activity("september", "2026-09-20", TrainingSubtype.OUTDOOR_RUN, 100.0),
            activity("october", "2026-10-20", TrainingSubtype.OUTDOOR_RUN, 112.0),
            activity("november", "2026-11-02", TrainingSubtype.OUTDOOR_RUN, 31.2),
        )
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = records, historyComplete = true),
                {}, {}, {}, ZoneOffset.UTC, today = today)
        } }
        compose.onNodeWithText("统计").performClick()
        compose.onNode(hasText("跑步") and hasClickAction()).performClick()
        compose.onNodeWithText("月").performClick()
        compose.onNodeWithTag("training-statistics").performScrollToNode(hasText("月趋势"))
        compose.onNodeWithContentDescription("2026-10-01，112.0 km").performClick()
        compose.onNodeWithTag("training-statistics").performScrollToIndex(2)
        compose.onNodeWithText("112.0").assertExists()
        compose.onNodeWithText("较上月 +12.0%").assertExists()
        compose.onNodeWithTag("training-statistics").performScrollToNode(hasText("超出参考线 2.0 km"))
        compose.onNodeWithText("超出参考线 2.0 km").assertExists()
    }

    @Test fun missingDistanceIsNotReportedAsCompleteGrowthAndHelpExplainsConversion() {
        val records = listOf(activity("swim", "2026-11-02", TrainingSubtype.POOL_SWIM, null))
        compose.setContent { RunTripTheme {
            TrainingScreen(TrainingUiState(connected = true, loading = false, records = records, historyComplete = true),
                {}, {}, {}, ZoneOffset.UTC, today = today)
        } }
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("1 次训练未记录距离，统计不完整").assertExists()
        compose.onNodeWithText("较上周整周 — · 数据不完整").assertExists()
        compose.onNodeWithContentDescription("等效跑量说明").performClick()
        compose.onNodeWithText("等效跑量怎么算？").assertExists()
    }

    @Test fun largeTextTrendKeepsSelectedMonthVisibleInsideAndAcrossWindows() {
        val index = trainingStatistics(emptyList(), TrainingPeriod.MONTH, TrainingPreferences(), ZoneOffset.UTC, today, true)
        val selected = mutableStateOf(LocalDate.parse("2026-11-01"))
        val points = mutableStateOf(index.window(selected.value, null))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                RunTripTheme { Box(Modifier.width(300.dp)) {
                    TrainingTrendChart(points.value, TrainingPeriod.MONTH, null, selected.value) { selected.value = it }
                } }
            }
        }
        compose.onNodeWithContentDescription("2026-11-01，0.0 km，进行中").assertIsDisplayed()
        compose.runOnIdle { selected.value = LocalDate.parse("2025-12-01") }
        compose.onNodeWithContentDescription("2025-12-01，0.0 km").assertIsDisplayed()
        compose.runOnIdle {
            selected.value = LocalDate.parse("2025-11-01")
            points.value = index.window(LocalDate.parse("2026-10-01"), null)
        }
        compose.onNodeWithContentDescription("2025-11-01，0.0 km").assertIsDisplayed()
    }
}
