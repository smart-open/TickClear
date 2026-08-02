package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 语音备忘录（V2.9 工具箱）：仅保存录音元数据，真实音频文件存于
 * 应用私有目录 filesDir/voice_memos/ 下，路径记录在 [filePath]。
 * 列表按创建时间倒序展示；删除条目时同步删除磁盘文件（见 VoiceMemoViewModel）。
 */
@Entity(tableName = "voice_memos")
data class VoiceMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
