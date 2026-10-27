package ai.driverhelper.data

import ai.driverhelper.rule.SchoolZone
import org.json.JSONArray

/**
 * assets/school_zones.json: `[{ "name", "lat", "lon" }]`, converted from the public dataset
 * (전국어린이보호구역표준데이터: 대상시설명, 위도, 경도).
 */
fun parseSchoolZones(json: String): List<SchoolZone> {
    val zones = JSONArray(json)
    return (0 until zones.length()).map { i ->
        val z = zones.getJSONObject(i)
        SchoolZone(z.getString("name"), z.getDouble("lat"), z.getDouble("lon"))
    }
}
