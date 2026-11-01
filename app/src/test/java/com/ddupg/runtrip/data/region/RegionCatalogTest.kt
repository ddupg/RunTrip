package com.ddupg.runtrip.data.region

import com.ddupg.runtrip.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RegionCatalogTest {
    private val catalog = RegionCatalog.parse(
        requireNotNull(javaClass.classLoader?.getResourceAsStream("regions/level.json"))
            .bufferedReader().use { it.readText() },
        BuildConfig.CHINA_REGION_DATA_VERSION,
    )

    @Test
    fun emptyProvinceIsUnavailableButMunicipalityAndCityRemainSelectable() {
        val sample = RegionCatalog.parse("""[
            {"code":"710000","name":"台湾省","province":"71","children":[]},
            {"code":"110000","name":"北京市","province":"11","children":[]},
            {"code":"440000","name":"广东省","province":"44","children":[
                {"code":"441900","name":"东莞市","province":"44","city":"19","children":[]}
            ]}
        ]""", "test")
        val taiwan = sample.provinces.single { it.code == "710000" }
        val beijing = sample.provinces.single { it.code == "110000" }
        val guangdong = sample.provinces.single { it.code == "440000" }
        val dongguan = guangdong.children.single()
        assertTrue(sample.isUnavailableProvince(taiwan))
        assertNull(sample.selection(listOf(taiwan)))
        assertFalse(sample.isUnavailableProvince(beijing))
        assertNotNull(sample.selection(listOf(beijing)))
        assertFalse(sample.isUnavailableProvince(guangdong))
        assertFalse(sample.isUnavailableProvince(dongguan))
        assertNotNull(sample.selection(listOf(guangdong, dongguan)))
    }

    @Test
    fun provinceBecomesAvailableWhenUpstreamAddsCityData() {
        val sample = RegionCatalog.parse("""[
            {"code":"710000","name":"台湾省","province":"71","children":[
                {"code":"710100","name":"台北市","province":"71","city":"01"}
            ]}
        ]""", "test")
        val province = sample.provinces.single()
        assertFalse(sample.isUnavailableProvince(province))
        assertNotNull(sample.selection(listOf(province, province.children.single())))
    }

    @Test
    fun realUpstreamDataSupportsCityDistrictAndCountyLevelCity() {
        val province = catalog.provinces.single { it.code == "330000" }
        assertNull(catalog.selection(listOf(province)))
        val hangzhou = province.children.single { it.code == "330100" }
        val city = requireNotNull(catalog.selection(listOf(province, hangzhou)))
        assertEquals("杭州市", city.cityName)
        assertNull(city.districtCode)
        assertEquals(BuildConfig.CHINA_REGION_DATA_VERSION, city.dataVersion)

        val xihu = hangzhou.children.single { it.code == "330106" }
        val district = requireNotNull(catalog.selection(listOf(province, hangzhou, xihu)))
        assertEquals("西湖区", district.districtName)
        assertEquals(listOf(province, hangzhou, xihu), catalog.pathFor(district))

        val jinhua = province.children.single { it.code == "330700" }
        val yiwu = jinhua.children.single { it.code == "330782" }
        assertEquals("义乌市", catalog.selection(listOf(province, jinhua, yiwu))?.districtName)
        assertNull(catalog.selection(listOf(province, hangzhou, yiwu)))
    }

    @Test
    fun municipalityCanBeSelectedWithoutInventingCityNode() {
        val beijing = catalog.provinces.single { it.code == "110000" }
        val city = requireNotNull(catalog.selection(listOf(beijing)))
        assertEquals("110000", city.cityCode)
        assertNull(city.districtCode)
        assertEquals(listOf(beijing), catalog.pathFor(city))
        val chaoyang = beijing.children.single { it.code == "110105" }
        val district = requireNotNull(catalog.selection(listOf(beijing, chaoyang)))
        assertEquals("北京市", district.cityName)
        assertEquals("朝阳区", district.districtName)
    }

    @Test
    fun cityWithoutCountiesIsSelectableAndDoesNotExposeTowns() {
        val guangdong = catalog.provinces.single { it.code == "440000" }
        val dongguan = guangdong.children.single { it.code == "441900" }
        assertTrue(dongguan.children.isEmpty())
        val location = requireNotNull(catalog.selection(listOf(guangdong, dongguan)))
        assertEquals("东莞市", location.cityName)
        assertNull(location.districtCode)
    }

    @Test
    fun provinceAdministeredCountyUsesRealCountyAndCannotSelectContainer() {
        val henan = catalog.provinces.single { it.code == "410000" }
        val grouping = henan.children.single { it.isGrouping }
        assertNull(catalog.selection(listOf(henan, grouping)))
        val jiyuan = grouping.children.single { it.code == "419001" }
        val location = requireNotNull(catalog.selection(listOf(henan, grouping, jiyuan)))
        assertNull(location.cityCode)
        assertEquals("济源市", location.districtName)
        assertEquals(listOf(henan, grouping, jiyuan), catalog.pathFor(location))
    }

    @Test
    fun removedRegionKeepsSnapshotButCannotBeResolvedIntoWrongRegion() {
        val beijing = catalog.provinces.single { it.code == "110000" }
        val location = requireNotNull(catalog.selection(listOf(beijing)))
        assertTrue(catalog.pathFor(location.copy(cityCode = "removed-code")).isEmpty())
        assertNull(catalog.selection(emptyList()))
    }
}
