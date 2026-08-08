package com.tickclear.app.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到站提醒站点序列化契约测试（V2.9++）。
 *
 * 站点以 "id|name|lat|lng|radius" 单行文本存 DataStore，多站点以 \n 分隔。
 * 这里锁定三类历史/边界缺陷，防回归：
 *  1. 名称含 `|`（如「人民广场|1号线」）不得导致字段错位、站点静默丢失；
 *  2. 名称含换行不得把一条记录撑成两行；
 *  3. 脏数据行必须被安全跳过，而不是让整份配置解析失败。
 */
class ArrivalStationTest {

    private fun roundTrip(st: ArrivalStation): ArrivalStation? =
        ArrivalStation.decode(st.encode())

    @Test
    fun `普通站点 编解码往返一致`() {
        val st = ArrivalStation("id-1", "西二旗", 40.0501, 116.3061, 300)
        assertEquals(st, roundTrip(st))
    }

    @Test
    fun `名称含竖线 不再错位丢站`() {
        val st = ArrivalStation("id-2", "人民广场|1号线", 31.2336, 121.4692, 500)
        val back = roundTrip(st)
        assertNotNull("含 | 的名称过去会被解析成 null（静默丢站）", back)
        assertEquals("人民广场|1号线", back!!.name)
        assertEquals(31.2336, back.lat, 1e-9)
        assertEquals(121.4692, back.lng, 1e-9)
        assertEquals(500, back.radius)
    }

    @Test
    fun `名称含多个竖线 仍能完整还原`() {
        val st = ArrivalStation("id-3", "a|b|c|d", 1.5, 2.5, 80)
        assertEquals("a|b|c|d", roundTrip(st)!!.name)
    }

    @Test
    fun `名称含换行 编码时被压平 不破坏行分隔`() {
        val st = ArrivalStation("id-4", "上海\n虹桥", 31.19, 121.32, 200)
        val line = st.encode()
        assertTrue("编码结果不得包含换行", !line.contains('\n') && !line.contains('\r'))
        assertEquals("上海 虹桥", ArrivalStation.decode(line)!!.name)
    }

    @Test
    fun `脏数据行被安全跳过`() {
        assertNull(ArrivalStation.decode(""))
        assertNull(ArrivalStation.decode("   "))
        assertNull(ArrivalStation.decode("only|three|parts"))
        assertNull(ArrivalStation.decode("id|name|not-a-number|121.0|300"))
        assertNull(ArrivalStation.decode("id|name|31.0|121.0|not-int"))
    }

    @Test
    fun `含enabled标志 编解码往返一致`() {
        val st = ArrivalStation("id-5", "望京西", 39.9962, 116.4709, 350, enabled = false)
        val back = roundTrip(st)
        assertNotNull(back)
        assertEquals("望京西", back!!.name)
        assertEquals(39.9962, back.lat, 1e-9)
        assertEquals(116.4709, back.lng, 1e-9)
        assertEquals(350, back.radius)
        assertEquals(false, back.enabled)
    }

    @Test
    fun `旧格式无enabled字段 默认启用`() {
        // 兼容升级前 "id|name|lat|lng|radius" 五段格式：解析后默认 enabled = true
        val line = "id-old|西直门|39.94|116.35|300"
        val back = ArrivalStation.decode(line)
        assertNotNull(back)
        assertEquals(true, back!!.enabled)
        assertEquals(300, back.radius)
    }

    @Test
    fun `名称含竖线且关闭 字段不错位 enabled正确`() {
        val st = ArrivalStation("id-6", "人民广场|1号线", 31.2336, 121.4692, 500, enabled = false)
        val back = roundTrip(st)
        assertNotNull(back)
        assertEquals("人民广场|1号线", back!!.name)
        assertEquals(31.2336, back.lat, 1e-9)
        assertEquals(121.4692, back.lng, 1e-9)
        assertEquals(500, back.radius)
        assertEquals(false, back.enabled)
    }

    @Test
    fun `多站点整体编解码 且脏行不影响其余站点`() {
        val list = listOf(
            ArrivalStation("a", "站A", 1.0, 2.0, 100),
            ArrivalStation("b", "站B|支线", 3.0, 4.0, 200),
        )
        assertEquals(list, decodeStations(encodeStations(list)))

        val withGarbage = encodeStations(list) + "\n" + "坏行" + "\n" + "x|y|z|1|2"
        val parsed = decodeStations(withGarbage)
        assertEquals("脏行应被跳过，正常站点保留", 2, parsed.size)
        assertEquals(list, parsed)
    }

    @Test
    fun `空文本解码为空列表`() {
        assertTrue(decodeStations("").isEmpty())
        assertTrue(decodeStations("\n\n").isEmpty())
    }
}
