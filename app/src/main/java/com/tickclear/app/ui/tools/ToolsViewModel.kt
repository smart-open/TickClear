package com.tickclear.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工具列表 ViewModel：暴露常用工具置顶列表，支持 toggle 增删。
 * 持久化走 [SettingsRepository]（DataStore `stringSetPreferencesKey`），无需新增依赖。
 */
@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 当前置顶的工具路由列表（按显示顺序）。 */
    val favorites: StateFlow<List<String>> = settings.favoriteToolRoutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 切换置顶状态：已置顶则移除（保持其余项顺序不变），未置顶则追加到末尾。
     * 写入失败由 ViewModelScope 兜底（不抛给 UI）。
     *
     * 空字符串路由视为无效，直接忽略，避免误污染持久化列表。
     */
    fun toggleFavorite(route: String) {
        if (route.isBlank()) return
        viewModelScope.launch {
            val current = favorites.value
            val next = if (route in current) current - route else current + route
            runCatching { settings.setFavoriteToolRoutes(next) }
        }
    }
}