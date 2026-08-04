package com.tickclear.app.domain.tools

/**
 * 到站提醒站点（V2.9++，公交/地铁避免坐过站）。
 * 纯数据 + 与 DataStore 字符串（"id|name|lat|lng|radius" 每行一个）互转，零新依赖。
 */
data class ArrivalStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** 提醒半径（米）。 */
    val radius: Int,
) {
    fun encode(): String = "$id|$name|$lat|$lng|$radius"

    companion object {
        fun decode(line: String): ArrivalStation? {
            val p = line.split("|")
            if (p.size < 5) return null
            return runCatching {
                ArrivalStation(
                    id = p[0],
                    name = p[1],
                    lat = p[2].toDouble(),
                    lng = p[3].toDouble(),
                    radius = p[4].toInt(),
                )
            }.getOrNull()
        }
    }
}

fun encodeStations(list: List<ArrivalStation>): String =
    list.joinToString("\n") { it.encode() }

fun decodeStations(text: String): List<ArrivalStation> =
    text.lineSequence().mapNotNull { ArrivalStation.decode(it) }.toList()
