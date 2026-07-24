# 点清（TickClear）Release Notes

> 管好每一个时间点，清空每一件烦心事。

---

## v2.0.0（2026-07-24）· 效率与平台集成正式版（封板）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**架构**：纯本地 · 无云端 · 无账号（语音 ASR/LLM 为用户可配置的可选云能力，任务数据不上云）
**版本标识**：versionCode 2 / versionName 2.0.0
**相对 v1.0.0**：21 项增强全部交付（可观测性 / 数据韧性 / 平台集成 / Android 14+ 提醒 / AI 助手深化 / 体验打磨）

### ✨ 核心新增

**可观测性与韧性（Phase 1）**
- Room `exportSchema=true`，各版本 schema 入库，迁移可审计
- 统一日志基础设施 `AppLogger`（环形缓冲 + 文件落盘），替换散落 `Log.w/e`；Debug 页可查看/导出/清空运行日志
- 离线崩溃遥测 `CrashReporter`：未捕获异常持久化并注入日志，重启后仍可导出
- 仪器化测试脚手架（`androidTest`：启动冒烟 + 备份加密往返）

**数据韧性与可移植（Phase 2）**
- 自动备份：`AlarmManager` 每日近似重复触发加密备份（保留最近 7 份），设置页开关 + 立即备份 + 回显
- 备份加密：AndroidKeystore AES/GCM `TCB1` 信封，自动识别加密 / 版本迁移脚手架
- ICS 日历导入导出：手写 RFC5545（任务 ↔ `.ics`，DTSTART/DTEND/RRULE），SAF 导入导出

**平台集成与效率（Phase 3）**
- 桌面小组件：「今日待办」集合（列表展示 + 点按勾选即完成 + 标题点击开应用）
- 动态快捷方式：新建任务 / 语音助手 / 今日（长按图标展开，冷/热启动直达）
- 键盘快捷键：今日列表 `↑↓` 选择、`空格/回车` 完成、`N` 新建（聚焦高亮）
- 通知与权限引导：通知渠道 / 全屏提醒 / 精确闹钟 系统设置跳转

**Android 14+ 提醒增强（Phase 4）**
- 全屏提醒权限前置引导（`canUseFullScreenIntent` 检测）
- 位置提醒韧性：改用前台 `LocationReminderService` 主动轮询，替代厂商省电敏感的 `addProximityAlert`
- 精确闹钟权限：`canScheduleExactAlarms()` 检测 + 引导

**AI 助手深化（Phase 5）**
- 本地规则 NLU 增强：覆盖更多口语表达（每工作日上午 / 提前 15 分钟提醒）
- 离线 ML ASR 评估：确认引入 Vosk/TFLite 会破「零新依赖」红线，维持系统框架 best-effort 并文档化上限
- 小智 Real 模式韧性：断线指数退避自动重连、外部 token 刷新即时生效、WS 层心跳保活
- 多轮任务编辑：建任务后支持「改时间 / 改重复 / 取消」指令本地闭环

**体验与设计打磨（Phase 6）**
- 横屏 / 折叠态：统计、助手、设置 Tab 在 Medium+ 宽屏双栏
- 空状态与首次引导：回收站 / 统计无数据 / 助手未配置 引导插画 + 行动按钮
- 动效与反馈：完成率环过渡、勾选微动效、列表项进入/重排动画

### 🛠 质量与工程（封板前收尾）
- **全量 lint 门禁复跑**：`abortOnError=true` + `checkReleaseBuilds=true`，修复 8 个 lint error（动态快捷方式 `ShortcutInfo.Builder` 的 API 25 守卫、`RemoteViews` 不支持 `CheckBox` → 改 `ImageView`、默认 locale、自动装箱状态、RTL 对称、清理 47 个死字符串与未用色值）
- **深度代码审计修复**（高风险 → 中风险）：
  - 修复 `AssistantViewModel.onCleared` 中 `transport.disconnect()` 因 `super.onCleared()` 取消 `viewModelScope` 而永不执行，导致的 WebSocket / AudioTrack / 内部作用域泄露，且再次进入助手时 `connect()` 因 `connected==true` 直接 return
  - 完成任务跨表写包进 `AppDatabase.withTransaction` 原子事务（实例完成 + CompletionLog + Task 终态），杜绝进程中断导致的不一致
  - 今日键盘监听改用 `rememberUpdatedState` 读取最新 `state`，修复过期闭包误操已变更列表项
  - `GeofenceScheduler` 去除 `runBlocking(Dispatchers.IO)` 主线程阻塞，改用内部协程作用域 + `withContext`
- 单元测试 30 例 + 仪器化测试脚手架全绿；全工程无 `!!`/`GlobalScope`/`fallbackToDestructiveMigration`
- 四维成熟度评估 **98+/100**（产品 / 开发 / 测试 / 配置），详见 `docs/开发计划_v2.0_任务清单.md`

### ⚠️ 已知限制（系统级，非缺陷）
- 含 v1.0.0 全部已知限制（Android 14+ 全屏提醒降级、位置提醒受厂商省电影响、本地语音识别 best-effort）
- 位置提醒（V2.13 前台轮询）较系统围栏更耗电，无位置任务时自动停止服务
- 离线 ML ASR 仍依赖系统框架兜底（引入离线模型会破零依赖红线）

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包（local.properties 配 release.* 签名，缺失回退 debug 签名）
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 全量 lint（error 级阻断构建）
```

环境最小路径见 `docs/SETUP_MINIMAL.md`（JDK 21 + 命令行 SDK，无需 Android Studio）。

---

## v1.0.0（2026-07-23）· 首个正式版（封板）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**架构**：纯本地 · 无云端 · 无账号（语音 ASR/LLM 为用户可配置的可选云能力，任务数据不上云）
**版本标识**：versionCode 1 / versionName 1.0.0

### ✨ 核心功能

**任务与任务组**
- 任务组 + 任务两级管理：新建/编辑/软删除（回收站可恢复/彻底清除）、分组颜色与图标
- 重复任务：每日 / 每周 / 每月 / 自定义间隔（每 N 天、**每 N 小时** 子日级多实例）
- 组级定时提醒：任务组可设重复频率与默认提醒时间，新任务入组自动继承
- 时间冲突检测：新建/编辑时提示与既有任务的时间重叠

**今日与提醒**
- 今日视图：按时段问候语、完成率环（可跳统计）、已完成下沉排序、一键清空（含确认）
- 精确提醒：AlarmManager 定点送达；通知栏直接「完成 / 稍后提醒(5/15/30min) / 跳过本次」
- 全屏提醒（高优先级任务）+ 静音时段自动降级低优先级通知
- 位置提醒：系统原生 `addProximityAlert` 地理围栏，零 Play 服务依赖
- 开机自动恢复排程（BootReceiver）

**统计与激励**
- 统计页：完成率环、连续/最长连续天数、本周/本月完成数、热力图、趋势图
- 勋章墙：解锁进度条、详情弹窗（解锁条件/日期）；打卡记录（天数/连击/最近）
- 随机鼓励语（下拉刷新轮换）

**AI 助手（小智）**
- 聊天为主、建任务为辅：语音/文字对话，提及任务时经 MCP `create_task` 自动落库
- MOCK（离线本地规则）/ REAL（WebSocket + Opus + Function Calling）双模式
- 多服务商 ASR：小智 / OpenAI 兼容 / 腾讯云 / 阿里云 / 系统框架兜底（含离线唤醒词 best-effort）
- 多服务商 LLM：OpenAI / 豆包(火山 Ark) / 千问(DashScope)，OpenAI 兼容协议
- 语音解析确认卡：任务草稿二次确认后才落库；危险写操作强制确认（可开信任模式）
- 全部密钥经 `SecureStore`（EncryptedSharedPreferences）存储，不入库不上云

**数据与安全**
- Room + SQLCipher 全库加密；显式迁移链 v1→v5（禁破坏性迁移）
- JSON 备份/恢复（SAF 导出导入，按主键合并，含全部任务字段）

**平板 / 大屏适配**
- WindowSizeClass 自适应：Compact 底栏 / Medium+Expanded NavigationRail
- 今日与任务 Tab 宽屏主从双栏；旋转/折叠全状态保持（rememberSaveable）
- 无障碍全路径审计（contentDescription / Role / mergeDescendants）

### 🛠 质量与工程

- 单元测试 30 例全绿（调度 15 / ViewModel+UseCase 14 / 备份往返 1）
- GitHub Actions CI：`assembleDebug` + `testDebugUnitTest` 提交门禁
- lint `abortOnError=true`，0 errors；全工程无 `!!`/`TODO`/`GlobalScope`
- release：minify + shrinkResources + ProGuard；签名经 `local.properties` 注入（密钥不入库）
- 封板前深度审计修复 9 类问题（子日级实例完成/跳过脱节、备份字段丢失、语音识别器泄漏、旋转丢对话框、除零守卫等），四维成熟度评估 98+/100（详见 `docs/开发计划_任务清单.md` Phase 9）

### ⚠️ 已知限制（系统级，非缺陷）

- **Android 14+ 全屏提醒**：仅应用前台或已授予「全屏通知」权限时真正全屏，否则降级为高优先级通知
- **位置提醒**：`addProximityAlert` 精度与后台触发受厂商省电策略影响，极端情况下可能延迟
- **本地语音识别/唤醒词**：基于系统 `SpeechRecognizer` 框架 best-effort，受厂商识别服务与联网状态影响，非神经网络离线模型
- 语音功能未配置 ASR/LLM 服务商时不可用（符合 PRD §7.5.9 设计）

### 📦 构建

```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包（local.properties 配 release.* 签名，缺失回退 debug 签名）
./gradlew :app:testDebugUnitTest      # 单元测试
```

环境最小路径见 `docs/SETUP_MINIMAL.md`（JDK 21 + 命令行 SDK，无需 Android Studio）。

---

*未实现需求与遗留事项已全部归纳至 v2.0 计划：见 `docs/开发计划_v2.0_任务清单.md`。*
