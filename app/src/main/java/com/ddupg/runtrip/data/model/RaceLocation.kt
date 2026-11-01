package com.ddupg.runtrip.data.model

/** 行政区编码与选中时的名称快照。名称快照不随地区数据包升级而改写。 */
data class RaceLocation(
    val provinceCode: String,
    val provinceName: String,
    val cityCode: String?,
    val cityName: String?,
    val districtCode: String?,
    val districtName: String?,
    val dataVersion: String,
) {
    init {
        require(provinceCode.isNotBlank() && provinceName.isNotBlank() && dataVersion.isNotBlank())
        require((cityCode == null) == (cityName == null))
        require((districtCode == null) == (districtName == null))
        require(cityCode == null || cityCode.isNotBlank() && !cityName.isNullOrBlank())
        require(districtCode == null || districtCode.isNotBlank() && !districtName.isNullOrBlank())
        require(cityCode != null || districtCode != null) { "Select a city or district" }
    }
}
