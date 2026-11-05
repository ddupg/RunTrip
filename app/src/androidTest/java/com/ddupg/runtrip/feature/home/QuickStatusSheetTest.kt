package com.ddupg.runtrip.feature.home

import android.view.KeyEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ddupg.runtrip.data.model.*
import com.ddupg.runtrip.data.repository.RaceMutationResult
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.ui.theme.RunTripTheme
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickStatusSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savingSurvivesDismissGesturesAndFailureCanBeRetried() {
        val race = Race(
            "race", "测试比赛", "杭州", LocalDate.of(2027, 1, 1),
            RoadRunningDetails(RoadRunningCategory.MARATHON), RaceStatus.WATCHING,
            null, HotelBookingStatus.NOT_BOOKED, null, null, null, null, null, 1000L, 2000L, 1,
        )
        val firstSave = CompletableDeferred<Unit>()
        var attempts = 0
        val repository = object : RaceRepository {
            override fun observeRaces() = flowOf(listOf(race))
            override fun observeRace(id: String) = flowOf(race)
            override suspend fun create(input: RaceInput): String = error("Unexpected create")
            override suspend fun update(id: String, input: RaceInput): RaceMutationResult = error("Unexpected update")
            override suspend fun delete(id: String): RaceMutationResult = error("Unexpected delete")
            override suspend fun updateStatus(id: String, status: RaceStatus): RaceMutationResult {
                attempts++
                if (attempts == 1) {
                    firstSave.await()
                    throw IllegalStateException("save failed")
                }
                return RaceMutationResult.APPLIED
            }
        }
        val viewModel = HomeViewModel(repository, DaySource { flowOf(LocalDate.of(2026, 1, 1)) })
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            RunTripTheme {
                state.quickStatusRace?.let {
                    QuickStatusSheet(it, state.quickStatusUpdate, viewModel::dismissQuickStatus, viewModel::updateQuickStatus)
                }
            }
        }
        compose.waitUntil(5_000) { viewModel.uiState.value.monthGroups.isNotEmpty() }
        compose.runOnIdle { viewModel.openQuickStatus(race.id) }
        compose.onNodeWithText("已中签").performClick()
        compose.waitUntil(5_000) { viewModel.uiState.value.quickStatusUpdate is QuickStatusUpdate.Saving }

        compose.onNodeWithText("更新参赛状态").performTouchInput { swipeDown() }
        compose.onNodeWithText("更新参赛状态").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("更新参赛状态").assertIsDisplayed()
        compose.runOnIdle { firstSave.complete(Unit) }
        compose.waitUntil(5_000) { viewModel.uiState.value.quickStatusUpdate is QuickStatusUpdate.Failed }
        compose.onNodeWithText("更新参赛状态失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("已中签").performClick()
        compose.waitUntil(5_000) { viewModel.uiState.value.quickStatusRace == null }
        compose.onNodeWithText("更新参赛状态").assertDoesNotExist()
        assertEquals(2, attempts)
    }
}
