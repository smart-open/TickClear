package com.tickclear.app.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R

@Composable
fun TickClearBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    // 底部导航整体高度降低约 1/4（80dp → 60dp），点击热区默认覆盖「图标 + 文字」整块。
    NavigationBar(Modifier.height(60.dp)) {
        topLevelDestinations.forEach { dest ->
            val r = currentRoute?.substringBefore("?")
            NavigationBarItem(
                // 忽略查询参数（如 tasks?openEditor=true）匹配一级目的地；
                // 子路由（settings/about、tasks/recycleBin 等）归属其一级板块，
                // 使对应底部 tab 在进入设置/任务子页面时保持选中高亮。
                selected = r == dest.route || r?.startsWith(dest.route + "/") == true,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

/** Medium/Expanded 左侧常驻导航轨（双栏布局使用）。 */
@Composable
fun TickClearNavRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationRail {
        topLevelDestinations.forEach { dest ->
            val r = currentRoute?.substringBefore("?")
            NavigationRailItem(
                // 与底部栏一致：一级精确匹配或子路由归属对应板块时选中。
                selected = r == dest.route || r?.startsWith(dest.route + "/") == true,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}
