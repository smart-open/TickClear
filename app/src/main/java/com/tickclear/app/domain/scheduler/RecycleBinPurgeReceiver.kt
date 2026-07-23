package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.data.repositories.RecycleBinRepository
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 回收站物理清理接收器：由 [RecycleBinScheduler] 的每日闹钟触发。
 * 通过 Hilt @EntryPoint 获取 [RecycleBinRepository]，清理 deletedAt 早于
 * 默认保留期（30 天）的任务与分组记录。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecycleBinEntryPoint {
    fun recycleBinRepository(): RecycleBinRepository
}

class RecycleBinPurgeReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "RecycleBinPurge"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != RecycleBinScheduler.ACTION_PURGE) return
        val appCtx = context?.applicationContext ?: return
        // goAsync：在协程完成物理清理前，保持进程存活。
        val pending = goAsync()
        scope.launch {
            try {
                val repo = EntryPointAccessors.fromApplication(
                    appCtx,
                    RecycleBinEntryPoint::class.java,
                ).recycleBinRepository()
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
                repo.purgeExpired(cutoff)
            } catch (e: Exception) {
                Log.e(tag, "回收站物理清理失败", e) // L7：原 runCatching 静默吞异常，改为记录
            } finally {
                pending.finish()
                scope.cancel() // 单次广播完成即回收作用域（L1）
            }
        }
    }
}
