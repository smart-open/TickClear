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
    /** 换行是记录分隔符，名称里的换行必须压平，否则整条记录会被拆成两行而丢失。 */
    fun encode(): String =
        "$id|${name.replace('\n', ' ').replace('\r', ' ')}|$lat|$lng|$radius"

    companion object {
        /**
         * 仅 name 允许含 `|`（用户可能输入「人民广场|1号线」这类名字）。
         * 因此固定从两端取字段：首段是 id，末三段是 lat/lng/radius，中间整体还原为 name。
         * 早期实现按 `p[1]` 取名，遇到含 `|` 的名称会把字段整体错位 → `toDouble()` 失败 → 站点被静默丢弃。
         */
        fun decode(line: String): ArrivalStation? {
            if (line.isBlank()) return null
            val p = line.split("|")
            if (p.size < 5) return null
            return runCatching {
                ArrivalStation(
                    id = p[0],
                    name = p.subList(1, p.size - 3).joinToString("|"),
                    lat = p[p.size - 3].toDouble(),
                    lng = p[p.size - 2].toDouble(),
                    radius = p[p.size - 1].toInt(),
                )
            }.getOrNull()
        }
    }
}

fun encodeStations(list: List<ArrivalStation>): String =
    list.joinToString("\n") { it.encode() }

fun decodeStations(text: String): List<ArrivalStation> =
    text.lineSequence().mapNotNull { ArrivalStation.decode(it) }.toList()
