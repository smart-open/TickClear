package com.tickclear.app.domain.model

/**
 * 密码保险箱条目（V2.9）。内存态为明文；落库前由 [com.tickclear.app.data.VaultCrypto]
 * 逐字段加密为 blob（见 VaultViewModel）。id 由创建时随机生成，用于列表 key 与增删改定位。
 */
data class VaultEntry(
    val id: Long,
    val title: String,
    val address: String,
    val username: String,
    val password: String,
    val note: String,
)
