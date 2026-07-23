package com.tickclear.app

import com.tickclear.app.domain.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份加密往返（V2.6 仪器化测试）：[BackupCrypto] 依赖 AndroidKeystore，只能在设备/模拟器执行。
 * 覆盖「明文→加密信封→解密」一致性，以及「明文 JSON 自动兼容导入」识别。
 */
class BackupCryptoInstrumentedTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val plain = "{\"app\":\"TickClear\",\"schemaVersion\":1}".toByteArray(Charsets.UTF_8)
        val enc = BackupCrypto.encrypt(plain)
        assertTrue("应输出加密信封", BackupCrypto.isEncrypted(enc))
        assertArrayEquals("解密应还原明文", plain, BackupCrypto.decrypt(enc))
    }

    @Test
    fun plaintext_passthrough() {
        val plain = "{\"app\":\"TickClear\"}".toByteArray(Charsets.UTF_8)
        assertFalse("明文不应识别为加密", BackupCrypto.isEncrypted(plain))
        assertArrayEquals("明文应原样返回", plain, BackupCrypto.decrypt(plain))
    }
}
