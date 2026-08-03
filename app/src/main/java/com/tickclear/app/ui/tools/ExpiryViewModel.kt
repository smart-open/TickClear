package com.tickclear.app.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.ExpiryRepository
import com.tickclear.app.domain.scheduler.ExpiryScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 到期提醒 ViewModel（V2.9++）：维护列表流；增改后按开关续排/取消精确闹钟。
 */
@HiltViewModel
class ExpiryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: ExpiryRepository,
) : ViewModel() {

    val items: StateFlow<List<ExpiryEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 新增或更新：返回落库后的实体（含 id）供调用方续排闹钟。 */
    fun upsert(entity: ExpiryEntity) {
        viewModelScope.launch {
            val withId = if (entity.id == 0L) {
                val id = repo.insert(entity)
                entity.copy(id = id)
            } else {
                repo.update(entity)
                entity
            }
            runCatching { ExpiryScheduler.schedule(appContext, withId) }
                .onFailure { AppLogger.w("ExpiryViewModel", "schedule 失败: ${it.message}") }
        }
    }

    fun delete(entity: ExpiryEntity) {
        viewModelScope.launch {
            repo.deleteById(entity.id)
            runCatching { ExpiryScheduler.cancel(appContext, entity.id) }
        }
    }
}
