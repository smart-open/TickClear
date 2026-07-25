package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 语音历史记录（V2.65）：保存用户与助手的语音对话文本。
 * 默认关闭（voiceHistoryEnabled=false 时不写入），开启后在助手收发指令时落库。
 */
@Entity(tableName = "voice_history")
data class VoiceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "role") val role: String,     // "user" | "assistant"
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "kind") val kind: String = "utterance", // "utterance" | "result"
)
