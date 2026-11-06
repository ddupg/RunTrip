package com.ddupg.runtrip.feature.me

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceAccount
import com.ddupg.runtrip.data.source.SourceConnection
import com.ddupg.runtrip.ui.theme.RunTripTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceHeadingIsNotClickableAndCorosRowOpensDirectly() {
        var selected: DataSourceId? = null
        compose.setContent {
            RunTripTheme {
                MeScreen(MeUiState(listOf(SourceRowState(DataSourceId.COROS))), { selected = it }, {}, {})
            }
        }
        compose.onNodeWithText("数据来源").assert(!hasClickAction())
        compose.onNodeWithText("高驰").assertHasClickAction().performClick()
        assertEquals(DataSourceId.COROS, selected)
        compose.onNodeWithText("跑步、骑行、游泳").assertDoesNotExist()
    }

    @Test fun boundRowShowsOnlyNameWhenPresent() {
        compose.setContent {
            RunTripTheme {
                MeScreen(
                    MeUiState(listOf(SourceRowState(DataSourceId.COROS,
                        SourceConnection.Bound(SourceAccount("12345678", "示例跑者"))))), {}, {}, {},
                )
            }
        }
        compose.onNodeWithText("示例跑者").assertExists()
        compose.onNodeWithText("ID 12345678").assertDoesNotExist()
    }

    @Test fun trainingPreferencesCanBeOpenedWithoutConnectingASource() {
        var opened = false
        compose.setContent { RunTripTheme { MeScreen(MeUiState(emptyList()), {}, {}, { opened = true }) } }
        compose.onNodeWithText("训练偏好").performClick()
        assertEquals(true, opened)
    }
}
