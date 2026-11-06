package com.ddupg.runtrip.feature.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.repository.TrainingPreferencesRepository
import com.ddupg.runtrip.ui.theme.RunTripTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingPreferencesScreenTest {
    @get:Rule val compose = createComposeRule()

    private class Repository : TrainingPreferencesRepository {
        override val preferences = MutableStateFlow(TrainingPreferences())
        override suspend fun update(value: TrainingPreferences) { preferences.value = value }
    }

    @Test fun coefficientCanBeCancelledThenSavedAndHelpDoesNotStartEditing() {
        val repository = Repository()
        compose.setContent { RunTripTheme { TrainingPreferencesRoute(repository, {}) } }
        compose.onNodeWithContentDescription("等效跑量换算说明").performScrollTo().performClick()
        compose.onNodeWithText("等效跑量怎么算？").assertIsDisplayed()
        compose.onNodeWithText("换算比例").assertDoesNotExist()
        compose.onNodeWithText("知道了").performClick()

        compose.onNodeWithText("骑行换算比例").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0.4")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0.25, repository.preferences.value.cyclingFactor, 0.0) }

        compose.onNodeWithText("骑行换算比例").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0.4")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("×0.4").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0.4, repository.preferences.value.cyclingFactor, 0.0) }
    }
}
