package com.tickclear.app.di

import com.tickclear.app.domain.backup.BackupManager
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.usecase.SeedUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 供 Application / BroadcastReceiver（@HiltAndroidApp 上下文之外）获取所需依赖。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun seedUseCase(): SeedUseCase
    fun backupManager(): BackupManager
    fun settingsRepository(): SettingsRepository
}
