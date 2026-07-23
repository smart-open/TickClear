package com.tickclear.app.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tickclear.app.R

@Composable
fun TickClearBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        topLevelDestinations.forEach { dest ->
            NavigationBarItem(
                // 忽略查询参数（如 tasks?openEditor=true）匹配一级目的地
                selected = currentRoute?.substringBefore("?") == dest.route,
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
            NavigationRailItem(
                selected = currentRoute?.substringBefore("?") == dest.route,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}
