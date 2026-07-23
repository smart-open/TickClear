package com.tickclear.app.di

import com.tickclear.app.domain.usecase.SeedUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 供 Application（@HiltAndroidApp 上下文之外）获取 SeedUseCase。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun seedUseCase(): SeedUseCase
}
