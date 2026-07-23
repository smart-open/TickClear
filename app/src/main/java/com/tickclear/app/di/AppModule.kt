package com.tickclear.app.di

import com.tickclear.app.domain.conflict.ConflictChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // SettingsRepository 现由 RepositoryModule 以 @Binds 绑定到 SettingsRepositoryImpl（@Inject 构造）。

    // ConflictChecker 是 Kotlin object（无 @Inject 构造器），需显式提供以供 UseCase 注入。
    @Provides
    @Singleton
    fun provideConflictChecker(): ConflictChecker = ConflictChecker
}
