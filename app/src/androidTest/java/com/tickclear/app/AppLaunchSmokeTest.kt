package com.tickclear.app

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.tickclear.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 应用启动冒烟（V2.4 仪器化测试）：仅用已有 `androidx.test.ext.junit`（ActivityScenarioRule），
 * 不引入 ui-test-junit4 等新依赖。验证主 Activity 在设备/模拟器上可正常进入 RESUMED 状态，
 * 作为「关键路径冒烟」基线（需 `adb` 连接设备执行）。
 */
class AppLaunchSmokeTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesToResumed() {
        assertEquals(Lifecycle.State.RESUMED, scenarioRule.scenario.state)
    }
}
