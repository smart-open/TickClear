package com.tickclear.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * 密码保险箱加密原语单测（纯 JVM，无 Android 依赖）。
 *
 * [VaultCrypto] 是「设备被物理取出也不可解密」这条安全承诺的唯一实现，
 * 此前零测试覆盖 —— 一旦 IV 复用、salt 失效、GCM 校验被绕过，属于静默的安全事故，
 * 不会有任何功能报错暴露。本测试即该承诺的回归屏障。
 */
class VaultCryptoTest {

    private val pass = "correct horse battery staple".toCharArray()

    @Test
    fun `加解密 roundtrip 还原原文`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        val plain = "支付宝支付密码 8891 / 备注：含中文与 emoji 🔐"
        assertEquals(plain, VaultCrypto.decrypt(key, VaultCrypto.encrypt(key, plain)))
    }

    @Test
    fun `空字符串也可 roundtrip`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        assertEquals("", VaultCrypto.decrypt(key, VaultCrypto.encrypt(key, "")))
    }

    @Test
    fun `错误口令解密必须失败而非返回乱码`() {
        val salt = VaultCrypto.randomSalt()
        val good = VaultCrypto.deriveKey(pass, salt)
        val bad = VaultCrypto.deriveKey("wrong passphrase".toCharArray(), salt)
        val blob = VaultCrypto.encrypt(good, "银行卡 6222 0000 1111 2222")
        // GCM 认证标签校验失败 → 抛异常；绝不能静默返回乱码明文
        assertThrows(AEADBadTagException::class.java) { VaultCrypto.decrypt(bad, blob) }
    }

    @Test
    fun `密文被篡改必须校验失败`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        val blob = VaultCrypto.encrypt(key, "tamper-me-please")
        val raw = VaultCrypto.b64ToBytes(blob)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte() // 翻转末位
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(key, VaultCrypto.bytesToB64(raw))
        }
    }

    @Test
    fun `相同明文两次加密密文不同（IV 必须随机）`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        val a = VaultCrypto.encrypt(key, "same-plaintext")
        val b = VaultCrypto.encrypt(key, "same-plaintext")
        // IV 复用是 GCM 的致命误用（可恢复明文异或），必须每次随机
        assertNotEquals("GCM IV 复用会导致明文可恢复", a, b)
        assertEquals("same-plaintext", VaultCrypto.decrypt(key, a))
        assertEquals("same-plaintext", VaultCrypto.decrypt(key, b))
    }

    @Test
    fun `同口令同 salt 派生出等价密钥`() {
        val salt = VaultCrypto.randomSalt()
        val k1 = VaultCrypto.deriveKey(pass, salt)
        val k2 = VaultCrypto.deriveKey(pass, salt)
        assertArrayEquals(k1.encoded, k2.encoded)
        // 跨密钥实例互通，保证「重启后用同口令能解开旧数据」
        assertEquals("cross", VaultCrypto.decrypt(k2, VaultCrypto.encrypt(k1, "cross")))
    }

    @Test
    fun `不同 salt 派生出不同密钥（salt 必须生效）`() {
        val k1 = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        val k2 = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        assertNotEquals(
            "salt 未参与派生会让彩虹表攻击可行",
            VaultCrypto.bytesToB64(k1.encoded),
            VaultCrypto.bytesToB64(k2.encoded),
        )
    }

    @Test
    fun `派生密钥为 AES-256`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomSalt())
        assertEquals("AES", key.algorithm)
        assertEquals("必须是 256 位密钥", 32, key.encoded.size)
    }

    @Test
    fun `randomSalt 长度正确且不重复`() {
        assertEquals(16, VaultCrypto.randomSalt().size)
        assertEquals(32, VaultCrypto.randomSalt(32).size)
        val seen = (1..64).map { VaultCrypto.bytesToB64(VaultCrypto.randomSalt()) }.toSet()
        assertEquals("randomSalt 出现重复说明未使用 SecureRandom", 64, seen.size)
    }

    @Test
    fun `hashAnswer 确定性且随 salt 变化`() {
        val salt = VaultCrypto.randomSalt()
        val a1 = VaultCrypto.hashAnswer("我的第一只猫".toCharArray(), salt)
        val a2 = VaultCrypto.hashAnswer("我的第一只猫".toCharArray(), salt)
        assertEquals("同答案同 salt 必须稳定，否则忘记口令永远校验不过", a1, a2)
        assertNotEquals(a1, VaultCrypto.hashAnswer("我的第一只猫".toCharArray(), VaultCrypto.randomSalt()))
        assertNotEquals(a1, VaultCrypto.hashAnswer("别的答案".toCharArray(), salt))
        assertTrue("答案哈希不得包含明文", !a1.contains("猫"))
    }

    @Test
    fun `Base64 编解码 roundtrip`() {
        val bytes = VaultCrypto.randomSalt(24)
        assertArrayEquals(bytes, VaultCrypto.b64ToBytes(VaultCrypto.bytesToB64(bytes)))
    }
}
