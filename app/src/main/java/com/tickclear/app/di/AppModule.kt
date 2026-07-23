package com.tickclear.app.di

import android.content.Context
import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
    ): SettingsRepository = SettingsRepository(context)

    // ConflictChecker 是 Kotlin object（无 @Inject 构造器），需显式提供以供 UseCase 注入。
    @Provides
    @Singleton
    fun provideConflictChecker(): ConflictChecker = ConflictChecker
}
