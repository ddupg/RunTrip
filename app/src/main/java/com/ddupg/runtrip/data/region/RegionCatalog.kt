package com.ddupg.runtrip.data.region

import com.ddupg.runtrip.data.model.RaceLocation
import org.json.JSONArray
import org.json.JSONObject

enum class RegionLevel { PROVINCE, CITY, DISTRICT }

data class RegionNode(
    val code: String,
    val name: String,
    val level: RegionLevel,
    val isGrouping: Boolean,
    val children: List<RegionNode>,
)

/** 直接读取 @province-city-china/level 的原始格式，不生成或维护自有地区数据。 */
class RegionCatalog private constructor(val provinces: List<RegionNode>, val version: String) {
    fun isUnavailableProvince(node: RegionNode): Boolean =
        node.level == RegionLevel.PROVINCE && node.children.isEmpty() && selection(listOf(node)) == null

    fun pathFor(location: RaceLocation): List<RegionNode> {
        val code = location.districtCode ?: location.cityCode
        fun find(nodes: List<RegionNode>): List<RegionNode>? {
            for (node in nodes) {
                if (node.code == code) return listOf(node)
                find(node.children)?.let { return listOf(node) + it }
            }
            return null
        }
        return find(provinces).orEmpty()
    }

    fun selection(path: List<RegionNode>): RaceLocation? {
        if (path.isEmpty()) return null
        // 只接受目录中的真实父子路径，不能通过编码片段猜测父级。
        var children = provinces
        for (node in path) {
            if (node !in children) return null
            children = node.children
        }
        val province = path.first()
        val last = path.last()
        if (last.isGrouping) return null
        val city = path.firstOrNull { it.level == RegionLevel.CITY && !it.isGrouping }
            ?: province.takeIf { it.code in MUNICIPALITIES_AND_SARS }
        val district = last.takeIf { it.level == RegionLevel.DISTRICT }
        if (city == null && district == null) return null
        return RaceLocation(
            provinceCode = province.code,
            provinceName = province.name,
            cityCode = city?.code,
            cityName = city?.name,
            districtCode = district?.code,
            districtName = district?.name,
            dataVersion = version,
        )
    }

    companion object {
        // 直辖市和特别行政区允许在省级节点确认城市，不伪造“市辖区”父节点。
        private val MUNICIPALITIES_AND_SARS = setOf("110000", "120000", "310000", "500000", "810000", "820000")

        fun parse(json: String, version: String): RegionCatalog {
            fun parseNode(json: JSONObject): RegionNode {
                val level = when {
                    json.has("area") -> RegionLevel.DISTRICT
                    json.has("city") -> RegionLevel.CITY
                    else -> RegionLevel.PROVINCE
                }
                val children = json.optJSONArray("children")
                return RegionNode(
                    code = json.getString("code"),
                    name = json.getString("name"),
                    level = level,
                    isGrouping = level == RegionLevel.CITY && json.getString("city") == "90",
                    children = if (children == null) emptyList() else
                        (0 until children.length()).map { parseNode(children.getJSONObject(it)) },
                )
            }
            val roots = JSONArray(json)
            return RegionCatalog((0 until roots.length()).map { parseNode(roots.getJSONObject(it)) }, version)
        }
    }
}
