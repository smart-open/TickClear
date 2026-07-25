package com.tickclear.app.domain.backup

import com.tickclear.app.data.local.AppDatabase
import androidx.room.withTransaction
import javax.inject.Inject

/**
 * 事务运行器抽象：将一段挂起代码包裹在数据库事务中执行。
 *
 * - 生产实现 [RoomTransactionRunner] 走 Room 的 [androidx.room.RoomDatabase.withTransaction]，
 *   保证导入等批量写入原子性（中途失败整体回滚，不留部分导入脏数据）。
 * - [NoOpTransactionRunner] 为非事务回退（或测试用），直接执行 block。
 *
 * 以接口而非 [AppDatabase] 直接注入 [BackupManager]，避免测试需构造真实 RoomDatabase，
 * 与项目「mock 仓储纯 JVM 单测」范式兼容（R5）。
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val db: AppDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

object NoOpTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
