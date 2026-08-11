package com.tickclear.app.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.tickclear.app.domain.repository.PermissionIntroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 独立 DataStore `tickclear_intro`：与 SettingsRepository 完全解耦。
 * 不参与「备份导入/导出」（[com.tickclear.app.domain.repository.SettingsRepository.exportSettingsJson]
 * 仅导出 `tickclear_settings` 命名空间下的键），避免跨设备恢复时把已完成的引导
 * 状态意外重置回未完成——这是 v2.13.2 引入独立存储的根本原因。
 */
private val Context.introDataStore: DataStore<Preferences> by preferencesDataStore(name = "tickclear_intro")

@Singleton
class PermissionIntroRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionIntroRepository {

    private val keyIntroDone = booleanPreferencesKey("intro_done")

    override val introDone: Flow<Boolean> =
        context.introDataStore.data.map { it[keyIntroDone] ?: false }

    override suspend fun setIntroDone(done: Boolean) {
        context.introDataStore.edit { it[keyIntroDone] = done }
    }
}