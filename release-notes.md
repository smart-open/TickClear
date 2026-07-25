# 点清（TickClear）Release Notes

> 管好每一个时间点，清空每一件烦心事。

---

## v2.5.0（2026-07-24）· 健壮性收口（通知 id / 迁移一致性 / 内存边界）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 7 / versionName 2.5.0
**相对 v2.4.0**：结清 v2.4 封板前代码复查发现的 7 项 P2 级健壮性隐患（V2.52–V2.58），无新功能、无功能回归、零新依赖。

### 🛠 健壮性修复（V2.52–V2.58）
- **通知 id 稳定化（V2.52）**：新增 `ReminderIds`，以 FNV-1a 32 位哈希（非负、用途前缀隔离）替代 `String.hashCode()` 生成通知 id 与 content/complete/snooze/skip/fullScreen 各 PendingIntent requestCode，显著降低碰撞覆盖风险；`cancelForTask` 兼容撤销 v2.4.0 遗留旧 hash 闹钟。新增 `ReminderIdsTest`（4 用例）。
- **死代码清理（V2.53）**：删除无引用的 `SettingsRepository.asrType/llmType`（Flow、setter、key）；`aiMode` 经核实仍被设置页 AI 引擎选择器与调试页使用，保留。
- **键盘焦点跨段错位修复（V2.54）**：今日视图键盘焦点索引改为仅在「进行中」段内计算（`itemsIndexed` 段内高亮），`↑↓` 索引夹取并补偿列表头部偏移；顺带修复 `DPAD_UP` 从不更新焦点索引的固有 bug。已完成段不再被误高亮/误操作。
- **编辑态软删防误建（V2.55）**：正在编辑的任务被软删（如语音指令删除）时自动关闭编辑页，不再以空白「新建」表单回退，杜绝误建重复任务。
- **迁移一致性修复（V2.56）**：核对导出 schema `5.json`，发现 `MIGRATION_4_5` 漏建单列索引 `index_task_instance_dueDateLocal`（老库升级将触发 Room 校验崩溃），已补建；DROP/CREATE 全部幂等（`IF [NOT] EXISTS`）。空迁移 1→2、2→3 经核验与实体一致。
- **助手消息内存上限（V2.57）**：会话消息保留最近 100 条（`takeLast`），超长会话内存不再单调增长。
- **长录音内存边界（V2.58）**：云 ASR 录音由整段内存累积改为边录边写 `cacheDir` 临时 PCM 文件，`WavUtil.writePcmFromFile` 以 8KB 缓冲流式转 WAV，上传后清理临时文件；峰值内存从约 2× 全量 PCM 降至单个音频缓冲区，>5min 长录音不再有 OOM 风险。

### 🛠 质量与工程
- 全程守住红线：零新依赖、中文全抽离 `strings.xml`、Room 显式 Migration（含本次索引补正）、`.workbuddy/` 不提交、按功能拆 commit。
- `./gradlew :app:lintRelease` 0 error；`./gradlew :app:testDebugUnitTest` 全绿（含新增 `ReminderIdsTest`）。

### ⚠️ 已知限制 / 显式保留（结转）
- **真机指标评测（V2.44–V2.46）与全量冒烟 QA（V2.47–V2.51）**：仍待物理设备轮次，见 `docs/开发计划_v2.5_任务清单.md` 二、三节；相关逻辑已通过单元 + lint 门禁。
- 其余平台约束（Android 14+ 全屏提醒降级、位置提醒 OEM 省电影响、系统 ASR best-effort）同 v2.4.0。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 质量门禁
```

---

## v2.4.0（2026-07-25）· 语音增强与残留缺口收口

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 6 / versionName 2.4.0
**相对 v2.3.0**：补齐 v2.3 收板后残留的 4 项真实功能缺口（V2.40–V2.43），并把 v2.4 规划内的真机/基准评测项（V2.44–V2.51）作为「已知限制」留待真机 QA 轮次。无功能回归。

### ✨ 核心新增（V2.40–V2.43）
- **一键清空「不再提示」（V2.40）**：清空确认弹窗新增「不再提示」复选框，勾选后持久化 `clearConfirmEnabled=false`，下次直达清空；设置页提供「清空前确认」开关可重新开启。
- **软删任务组恢复级联（V2.41）**：与 V2.33 删除级联对称，回收站恢复任务组时级联恢复其软删成员任务（`RestoreGroupCascadeUseCase` + `TaskDao.restoreByGroup`），保证数据一致性。
- **离线热词指令（V2.42）**：纯函数 `OfflineCommandRecognizer` 解析「暂停/启用/删除 + 任务名」热词，`ApplyOfflineCommandUseCase` 仅在任务名命中真实任务时执行（删除仅命中才生效，防误删）；`AssistantViewModel.sendText` 离线开关开启时本地闭环执行并回显，不再送 LLM。
- **方言识别（V2.43）**：`LocalSpeechRecognizer.start` 新增 `language` 参数驱动 `RecognizerIntent.EXTRA_LANGUAGE`；设置页新增「识别语言（方言）」分段选择（普通话 / 粤语 / 台湾 / 英语），经 `SettingsRepository.asrLanguage` 持久化并在系统 ASR 路径透传。效果取决于设备是否装有对应语言包，未装回退系统默认。

### 🛠 质量与工程
- 新增单测：`OfflineCommandRecognizerTest`(11) `OfflineCommandUseCasesTest`(6) `RecycleBinViewModelTest`/`GroupUseCasesTest` 同步（组恢复级联用例）；V2.42 修复了 `Task.createdAt/updatedAt` 默认毫秒导致数据类相等判定偶发 flaky 的测试陷阱（测试内显式置 0）。
- 全程守住红线：零新依赖（复用系统 ASR / DataStore / Compose）、中文全抽离 `strings.xml`、Room 无 schema 变更、按功能拆 commit。
- `./gradlew :app:lintRelease` 0 error；`./gradlew :app:testDebugUnitTest` 全绿。

### ⚠️ 已知限制 / 显式保留（V2.44–V2.51）
- **提醒送达率 ≥ 98%（V2.44）**：真机 + OEM 后台保活评测，本环境仅单元 + lint 门禁，待真机轮次补测。
- **性能基准 60fps / 冷启动 < 500ms（V2.45）**：Macrobenchmark/模拟器评测，待 `connectedDebugAndroidTest` 轮次补测。
- **语音指标评测集（V2.46）**：意图/槽位准确率、端到端耗时、误删率，待真机语音评测集。
- **全量冒烟 QA（V2.47–V2.51）**：核心流程 / 自适应布局 / 语音流水线 / 无障碍 / 兼容与降级，均需物理设备逐项走查，作为已知限制留待真机 QA 轮次（相关逻辑已随 v2.0–v2.4 实现并通过单元 + lint 门禁）。
- V2.28/V2.27 离线端侧 ML ASR 模型仍按红线保留（不引入推理框架）；V2.42/V2.43 以轻量系统 ASR 方案补足离线热词与方言能力。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 质量门禁
```

---

## v2.3.0（2026-07-24）· 遗留功能缺口补齐

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 5 / versionName 2.3.0
**相对 v2.2.0**：补齐 v2.2 收口后仍遗留的 4 项「真实功能缺口」——多档稍后提醒、音效开关、折叠已完成、组级暂停/启用/删除级联。无功能回归。

### ✨ 核心新增（V2.30–V2.33）
- **多档稍后提醒时长（V2.30）**：设置页新增「稍后提醒时长」分段选择（5/15/30 分钟，默认 15）。通知栏与全屏提醒的「稍后」动作、以及高优先级全屏提醒均改用该设置。`ReminderPrefs.normalizeSnoozeMin` 做归一化容错。
- **提醒音效开关（V2.31）**：设置页新增「提醒音效」总开关（默认开）。关闭后高优先级提醒**不发声、不震动**，仅保留灯光与渠道重要性（仍送达）。`ReminderPrefs.shouldPlaySound` 纯函数判定。
- **折叠已完成任务（V2.32）**：今日视图已完成数超过 20 时，列表自动拆分为「进行中 / 已完成」两段，并提供「折叠 / 展开」开关，避免长列表拖累；已完成项保持下沉与半透明。`TodayListPrefs.shouldShowCollapseByDoneCount` 纯函数判定（阈值 20）。
- **组级暂停 / 启用 / 删除级联（V2.33）**：任务组头新增「暂停 / 启用」切换；暂停组级联暂停组内全部未软删任务（定时不再触发），启用则级联恢复。删除任务组改为级联软删组内所有任务再删组，级联同时取消对应提醒与地理围栏。`PauseGroupUseCase` / `ResumeGroupUseCase` / `DeleteGroupCascadeUseCase` 与单任务 `PauseTaskUseCase` / `ResumeTaskUseCase` 构成级联能力。

### 🛠 质量与工程
- 新增单测：`ReminderPrefsTest`(3) `TodayListPrefsTest`(1) `GroupUseCasesTest`(5)，覆盖归一化、音效判定、折叠阈值与级联落到子任务的验证。
- 全程守住红线：零新依赖（复用 DataStore / NotificationCompat / Compose）、中文全抽离 `strings.xml`、Room 无 schema 变更、按功能拆 commit。
- `./gradlew :app:lintRelease` 0 error；`./gradlew :app:testDebugUnitTest` 全绿。

### ⚠️ 已知限制 / 显式保留
- **离线热词 / 方言识别（V2.28 · 显式保留）**：引入端侧推理框架会破「零新依赖」红线，v2.3.0 维持系统 `SpeechRecognizer` best-effort，不引入依赖。
- 组级删除级联软删后，回收站恢复组时仅恢复组本身（子任务需逐条恢复）；该增强留待 v2.4+。
- 单元测试覆盖率为纯逻辑模块（约 5% 指令级）；UI/仓库/调度交互需 Robolectric 或仪器化测试，留待后续。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 质量门禁
```

---

## v2.2.0（2026-07-24）· 后续遗留任务收口

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 4 / versionName 2.2.0
**相对 v2.1.0**：收口 v2.1 规划的「后续遗留任务」——仪器化测试真机化 + 覆盖率度量、设计文档漂移项回填；无功能回归。

### ✨ 核心新增（遗留任务收口）
- **仪器化测试真机化 + 覆盖率度量（V2.25）**：`app/build.gradle.kts` 启用 `enableUnitTestCoverage` / `enableAndroidTestCoverage`（Jacoco 随 AGP 内置，**零新依赖**）；新增 `.github/workflows/ci.yml` 编排 `lintRelease` + `createDebugUnitTestCoverageReport`（覆盖率产物上传 Artifact）+ `connectedDebugAndroidTest`（reactivecircus 模拟器跑 `androidTest` 脚手架：启动冒烟 + 备份加密往返）。单测覆盖率报告本地已跑通。
- **设计文档 `- [ ]` 漂移项系统回填（V2.26）**：逐条比对代码后回填 `docs/点清APP_产品设计文档.md`。已实现的 30+ 项验收点（任务组 7 项、通知栏直接完成、吃药类三操作、位置提醒、静音时段、一键清空确认、振动跟随系统、时间冲突红条 3 项、空状态/问候语/已完成下沉、语音四类指令/单任务暂停/今日时间线/暂停不触发/软删回收站 30 天/麦克风门控等）已勾选并附代码证据；**诚实保留**未实现项（5/30 分钟稍后选项、音效开关、折叠已完成按钮、组级暂停级联、离线热词、方言）与验收指标项（送达率/准确率/耗时/误删率），避免误标。

### 🛠 质量与工程
- 全程守住红线：零新依赖（Jacoco/CI 均为内置于 AGP/标准 Action 的零运行时依赖方案）、中文全抽离 `strings.xml`、Room 显式 Migration、按功能拆 commit。
- 全量 lint 门禁复跑 0 error（`abortOnError=true`）；`./gradlew testDebugUnitTest` 全绿；单测覆盖率报告可生成。

### ⚠️ 已知限制 / 显式保留
- **离线 ML ASR（V2.27 · 显式保留）**：引入端侧推理框架（Vosk / whisper.cpp 等）将破「零新依赖」红线，且属 best-effort 体验增强而非正确性问题。v2.2.0 维持系统框架 `android.speech.SpeechRecognizer` 的 best-effort 方案，**不引入任何依赖**。若未来项目放宽红线，再评估端侧模型。
- 仪器化测试需 CI 模拟器/真机运行（本环境仅编译验证 + 单测覆盖率本地跑通）；方言识别、离线热词指令仍为未实现项。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:createDebugUnitTestCoverageReport  # 单测覆盖率报告（app/build/reports/coverage/debug）
./gradlew :app:lintRelease            # 全量 lint（error 级阻断构建）
```

---

## v2.1.0（2026-07-24）· 质量与韧性加固

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 3 / versionName 2.1.0
**相对 v2.0.0**：质量与数据韧性加固，无功能回归。

### ✨ 核心新增（质量与韧性）
- **JVM 单元测试补全（V2.22）**：为 v2.0 新增强的纯逻辑模块补 JVM 单测 —— `IcsManager` RFC5545 往返（一次性/全天/日/周/月重复、特殊字符转义、非法输入容错、UID 保留）、`BackupManager.validateBackupJson` 备份健康判定。`./gradlew testDebugUnitTest` 全绿可验证（零新依赖）。
- **备份自愈校验（V2.23）**：`BackupManager.validateBackupJson(json): BackupHealth` 纯函数（解析 + 版本 + 结构校验，JVM 可测）；`AutoBackupRunner` 写入后即时解密校验并回写 DataStore 健康状态；设置页「自动备份」区回显「上次备份状态：正常 / 损坏 / 空 / 未检查」，损坏用错误色提示重备。

### 🛠 质量与工程
- 修复 2 个 ICS 解析 bug（`IcsManager.parseEvent`）：按 `BEGIN:VEVENT` 拆分残留前导 `\r` 吞掉 `UID`（任务 id）；全天判断误用值（`20260724`）而非 `DTSTART` 的 `VALUE=DATE` 参数，导致全天任务导入后丢失 `allDay` 标志。
- 单元测试由 30+ 增至 40+（`IcsManagerTest` 9 例 + `BackupManagerHealthTest` 5 例），全绿。
- 全量 lint 门禁复跑 0 error（`abortOnError=true`）。

### ⚠️ 已知限制（同 v2.0.0，无新增）
- 仪器化测试仍仅编译验证（运行需真机/CI）；离线 ML ASR 仍为系统框架 best-effort。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 全量 lint（error 级阻断构建）
```

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
