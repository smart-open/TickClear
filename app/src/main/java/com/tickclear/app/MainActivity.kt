package com.tickclear.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tickclear.app.ui.navigation.ShortcutHelper
import com.tickclear.app.ui.navigation.TickClearApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // V2.9：动态快捷方式携带的启动动作，冷启动经 onCreate、热启动经 onNewIntent 更新。
    private var shortcutAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutHelper.register(this)
        shortcutAction = intent?.getStringExtra(ShortcutHelper.EXTRA_SHORTCUT_ACTION)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                TickClearApp(
                    startAction = shortcutAction,
                    onStartActionConsumed = { shortcutAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutAction = intent.getStringExtra(ShortcutHelper.EXTRA_SHORTCUT_ACTION)
    }
}
