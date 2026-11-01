package com.ddupg.runtrip.feature.form

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollToNode
import com.ddupg.runtrip.BuildConfig
import com.ddupg.runtrip.data.model.RaceLocation
import com.ddupg.runtrip.data.region.RegionCatalog
import com.ddupg.runtrip.ui.theme.RunTripTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class RegionPickerDialogTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = RegionCatalog.parse(
        InstrumentationRegistry.getInstrumentation().targetContext.assets.open("regions/level.json")
            .bufferedReader().use { it.readText() }, BuildConfig.CHINA_REGION_DATA_VERSION,
    )
    private var selected: RaceLocation? = null
    private var dismissed = false

    private fun show(current: RaceLocation? = null, data: RegionCatalog = catalog) {
        compose.setContent {
            RunTripTheme {
                RegionPickerDialog(data, current, onDismiss = { dismissed = true }, onSelect = { selected = it })
            }
        }
    }

    private fun clickOption(name: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }

    @Test
    fun provinceWithoutCityDataShowsReasonAndCannotBeEntered() {
        val emptyProvince = RegionCatalog.parse("""[
            {"code":"710000","name":"台湾省","province":"71","children":[]}
        ]""", "test")
        show(data = emptyProvince)
        val row = compose.onNodeWithText("台湾省")
        row.assertIsNotEnabled()
        compose.onNodeWithText("暂无城市数据").assertExists()
        row.performTouchInput { click() }
        compose.onNodeWithText("请选择省份").assertExists()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        assertNull(selected)
    }

    @Test
    fun cityWithoutCountiesCanStillBeConfirmed() {
        show()
        clickOption("广东省")
        clickOption("东莞市")
        compose.onNodeWithText("该城市没有区县可选").assertExists()
        compose.onNodeWithText("确定").assertIsEnabled().performClick()
        assertEquals("441900", selected?.cityCode)
        assertNull(selected?.districtCode)
    }

    @Test
    fun cityCanBeConfirmedWithoutSelectingDistrict() {
        show()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        clickOption("浙江省")
        compose.onNodeWithText("确定").assertIsNotEnabled()
        clickOption("杭州市")
        compose.onNodeWithText("确定").assertIsEnabled().performClick()
        assertEquals("330100", selected?.cityCode)
        assertNull(selected?.districtCode)
    }

    @Test
    fun countySelectionIsReturned() {
        show()
        clickOption("浙江省")
        clickOption("杭州市")
        clickOption("西湖区")
        compose.onNodeWithText("确定").performClick()
        assertEquals("330106", selected?.districtCode)
    }

    @Test
    fun cancelDoesNotCommitAndChangingProvinceClearsPriorCity() {
        val current = RaceLocation("330000", "浙江省", "330100", "杭州市", "330106", "西湖区", "old-version")
        show(current)
        compose.onNodeWithText("已选：浙江省 / 杭州市 / 西湖区").assertExists()
        compose.onNodeWithText("省份").performClick()
        clickOption("江苏省")
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertNull(selected)
    }

    @Test
    fun municipalitySupportsCityAndDistrictAndGoingBackToCityClearsDistrict() {
        show()
        clickOption("北京市")
        compose.onNodeWithText("确定").assertIsEnabled()
        clickOption("朝阳区")
        compose.onNodeWithText("已选：北京市 / 朝阳区").assertExists()
        compose.onNodeWithText("北京市").performClick()
        compose.onNodeWithText("确定").performClick()
        assertEquals("110000", selected?.cityCode)
        assertNull(selected?.districtCode)
    }
}
