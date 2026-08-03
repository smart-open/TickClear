package com.tickclear.app.di

import android.content.Context
import com.tickclear.app.data.SecureStore
import com.tickclear.app.data.local.AppDatabase
import com.tickclear.app.domain.backup.RoomTransactionRunner
import com.tickclear.app.domain.backup.TransactionRunner
import com.tickclear.app.data.local.dao.CheckInDao
import com.tickclear.app.data.local.dao.CompletionLogDao
import com.tickclear.app.data.local.dao.HabitCheckInDao
import com.tickclear.app.data.local.dao.HabitDao
import com.tickclear.app.data.local.dao.MedalUnlockDao
import com.tickclear.app.data.local.dao.TaskDao
import com.tickclear.app.data.local.dao.TaskGroupDao
import com.tickclear.app.data.local.dao.TaskInstanceDao
import com.tickclear.app.data.local.dao.VoiceHistoryDao
import com.tickclear.app.data.local.dao.VoiceMemoDao
import com.tickclear.app.data.local.dao.ExpiryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = SecureStore.getDbPassphrase(context)
        return AppDatabase.create(context, passphrase)
    }

    @Provides fun provideTaskGroupDao(db: AppDatabase) = db.taskGroupDao()
    @Provides fun provideTaskDao(db: AppDatabase) = db.taskDao()
    @Provides fun provideTaskInstanceDao(db: AppDatabase) = db.taskInstanceDao()
    @Provides fun provideCompletionLogDao(db: AppDatabase) = db.completionLogDao()
    @Provides fun provideMedalUnlockDao(db: AppDatabase) = db.medalUnlockDao()
    @Provides fun provideCheckInDao(db: AppDatabase) = db.checkInDao()
    @Provides fun provideVoiceHistoryDao(db: AppDatabase): VoiceHistoryDao = db.voiceHistoryDao()
    @Provides fun provideVoiceMemoDao(db: AppDatabase): VoiceMemoDao = db.voiceMemoDao()
    @Provides fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()
    @Provides fun provideHabitCheckInDao(db: AppDatabase): HabitCheckInDao = db.habitCheckInDao()
    @Provides fun provideExpiryDao(db: AppDatabase): ExpiryDao = db.expiryDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner = RoomTransactionRunner(db)
}
