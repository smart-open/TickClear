package com.tickclear.app.domain.backup

/**
 * 备份健康状态（V2.23 备份自愈校验）：
 * - [OK] 解析成功、版本受支持且至少含一类非空数据，可正常恢复；
 * - [CORRUPT] 无法解析、版本号为 0/负数，或高于当前 schema（不支持），不可恢复；
 * - [EMPTY] 结构合法但无任何数据（空备份）；
 * - [NONE] 尚未执行过校验 / 无备份（DataStore 默认值）。
 */
enum class BackupHealth { OK, CORRUPT, EMPTY, NONE }
