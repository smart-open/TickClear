# AGENT.md — 点清 TickClear 项目规范

> 本文件为 AI 协作与人工开发共用的项目规范。改动架构/约定时同步更新本文件。

## 1. 项目概览
- **点清 TickClear**：Android 个人任务清理工具（手机 + 平板自适应）。
- **定位**：纯本地、无云端账号；任务数据经 **SQLCipher** 加密存储。
- **包名**：`com.tickclear.app`，单模块 `:app`（MVP 优先，未拆 :core/:data）。
- **基线**：Kotlin 2.0.21 / AGP 8.5.2 / Gradle 8.9 / minSdk 24 / targetSdk 34 / compileSdk 34。
- **UI**：Jetpack Compose + Material3；底部 6 Tab：今日 / 任务 / 习惯 / 统计 / 助手 / 设置。
- **DI**：Hilt。注解处理器 **Room 与 Hilt 均已统一走 KSP**（`ksp(libs.androidx.room.compiler)` / `ksp(libs.hilt.compiler)`），工程内无 KAPT。
- **本地存储**：Room(SQLCipher) + DataStore(偏好) + EncryptedSharedPreferences(密钥/口令)。

## 2. 模块内包布局
```
ui/         Compose 界面与 ViewModel
  theme/    设计令牌(LIGHT/DARK/DYNAMIC)、Typography、Shape、Spacing
  navigation/  Routes / BottomNav / TickClearNavGraph
  components/  共享组件(TaskCard/ConflictBanner/...)
  today/ tasks/ habits/ stats/ assistant/ settings/ ai/ adaptive/
data/       Room 实体、DAO、AppDatabase、Repository、SecureStore
domain/     纯 Kotlin 模型、UseCase、ConflictChecker、ai/、assistant/、scheduler/
di/         Hilt 模块
```

## 3. 编码规范
- **依赖注入**：所有 Repository / UseCase / 客户端经 Hilt 提供，`@Inject constructor` + `@Singleton`。
- **状态管理**：ViewModel 暴露 `StateFlow`/`SharedFlow`；UI 用 `collectAsStateWithLifecycle` 订阅；**禁止**在 Compose 中直接持有可变业务状态。
- **字符串**：**所有用户可见中文进 `res/values/strings.xml`**（落实 PRD A-6），禁止 Compose 内联硬编码。
- **无魔数**：颜色/间距/圆角用 `ui/theme` 令牌；时间窗容差等常量集中定义。
- **错误处理**：统一 `AppError` sealed；Repository 抛域异常，ViewModel 映射为 UI 状态；**禁止裸 catch**。
- **并发**：IO 用 `Dispatchers.IO`（经 `DispatcherProvider`）；流入 UI 在主线程收集。
- **数据库迁移**：`AppDatabase` 版本递增；破坏性变更走 `Migration`，不启用 `fallbackToDestructiveMigration`。

## 4. 密钥与敏感数据处理（红线）
- `local.properties` 含明文调试密钥，**已被 .gitignore 忽略，禁止提交、禁止写进源码**。
- 仅 **debug** 构建经 `buildConfigField` 注入腾讯 ASR 密钥；release 不读取。
- **Release 签名**：`local.properties` 提供 `release.storeFile/release.storePassword/release.keyAlias/release.keyPassword`（明文，已被 .gitignore 忽略）；`app/build.gradle.kts` 据此创建 release `signingConfigs`，缺失则回退 debug 签名以保证可构建。禁止把签名密钥写进源码或提交仓库。
- SQLCipher 口令与 ASR/LLM 密钥存 **EncryptedSharedPreferences**（Keystore, AES256-GCM）。
- 口令丢失 = 加密库不可解密：关于页必须提示"清除应用数据将丢失加密库"。

## 5. 六大 Tab 行为契约
- **今日**：分组展示今日任务；完成/编辑/删除（左滑软删带撤销、右滑完成）；时间窗冲突角标 + `ConflictBanner`；今日完成率；AI 助手入口。
- **任务**：全部任务 + 任务组 CRUD（级联软删）；回收站（软删 `deletedAt`，默认 30 天自动彻底清理，可恢复）。
- **习惯**：习惯 CRUD（emoji / 重复星期 CSV / 提醒时刻 / 配色 / 排序 / 归档），按日打卡（`HabitCheckInEntity`，同日幂等、不允许补卡），连续天数统一走 `HabitDates.computeStreak`（`java.time.LocalDate` DST 安全实现，唯一事实来源）；提醒经 `HabitReminderScheduler` 排程、`HabitReminderReceiver` 触发后自动续排，**习惯提醒无条件响铃 + 震动，不受全局「声音」开关约束**。
- **统计**：按组/日/周/月完成情况、完成率、连续打卡天数（基于 `CheckInEntity`，不允许补卡）、勋章墙（8 枚）。
- **助手**：模拟硬件设备对接小智(Xiaozhi) WebSocket 协议，语音 + 文字聊天；对话出现任务时经 **MCP 函数调用(`create_task`)** 在本机建任务（复用 `AddTaskUseCase` + 冲突检测）。默认 Mock 模式离线可跑。
- **设置**：主题(浅色/深色/动态)、语音/ASR 配置 + 测试、回收站管理、调试(日志/测试按钮)、关于。

## 6. 构建与运行
```bash
# 调试构建（需 Android SDK，已设 ANDROID_HOME=C:\Android）
./gradlew assembleDebug
# Release 构建（需 local.properties 提供 release.* 签名；缺失回退 debug）
./gradlew assembleRelease
# 安装到设备/模拟器
./gradlew installDebug
# 运行测试（含 CI 门禁 assembleDebug + testDebugUnitTest）
./gradlew testDebugUnitTest
```
- 编译需联网拉取依赖（AGP / Compose BOM / Hilt / Room / SQLCipher 等）。
- Release 已启用 `shrinkResources` + R8 压缩；release 签名见 §4。
- SQLCipher 与 Room 共用 SQLite：已在 `app/build.gradle.kts` 排除 `androidx.sqlite:sqlite-framework`。

## 7. 分支与任务追踪
- 当前状态与任务追踪见 `docs/成熟度评估.md`（v2.8.0 封板四维成熟度评估，综合 99.0/100，含 V2.63–V2.83 全量记录 + 已知限制 §6 + §13 v2.8.0 封板收口）；历史阶段任务清单（v1.0 / v2.0–v2.5 各版本「开发计划_*_任务清单」及「未完成任务清单」）已于 2026-07-27 整理删除，其内容（R1–R10、V2.44–V2.51、Q4/Q5 等）已并入该评估，单一可信源即此文档。
- 每完成一个子任务，按需追加 `D:/ai_work/TickClear/.workbuddy/memory/` 工作日志（状态总览见 `docs/成熟度评估.md`）。
- **提交纪律（红线）**：每一次编码 / 修改完成、进入下一项事项前必须 `git commit`（功能自洽、可独立回滚）；禁止把多个不相关改动攒成一次大提交。提交信息用中文，类型前缀 + 简述，例如：`[fix] 子日级实例完成状态脱节`、`[feature] 新增日志查看页`、`[config] release 签名注入`、`[test] 新增备份往返测试`、`[docs] 更新发布说明`、`[refactor] 拆分 Repository 边界`、`[chore] 升级依赖`。
- 工程纪律（全程保持）：中文全抽离 `strings.xml`、数据库迁移显式 `Migration` 禁 `fallbackToDestructiveMigration`、不引入新依赖（复用 OkHttp/DataStore/系统框架）。
  - **唯一例外（v2.8.0 起）**：Opus 编解码本地 AAR `app/libs/opus.aar`（theeasiestway/android-opus-codec，含官方 libopus 1.3.1，全 ABI 含 arm64-v8a）。因 Android `MediaCodec` 的 Opus **编码器**在多数机型 `dequeueInputBuffer` 恒返回 -1 属平台碎片化硬伤，语音链路无法自研绕过。该库以本地文件形式随仓库分发，不新增远程仓库/坐标解析。**禁止回退到 `com.github.martoreto:opuscodec`**（其 `libsenz.so` 仅 32 位，arm64 设备 dlopen 失败）。

## 8. 已知风险与限制
- Opus 编解码：语音链路使用本地 AAR `app/libs/opus.aar`（theeasiestway，libopus 1.3.1 软件实现，各机型可用）；不再依赖设备 `MediaCodec`(audio/opus) 编码器（多数机型 `dequeueInputBuffer` 恒 -1 导致零帧上行）。Mock 模式不需真实 Opus。集中封装在 `domain/assistant/OpusCodec`。
- 真实小智服务端联调（Real 模式）依赖外部 token；Mock 模式为默认演示路径（v1.0 已含）。
- 动态取色(DYNAMIC)仅 API31+，低版本回退浅色。
- **平台限制（v1.0 已知）**：Android 14+ 对 `setFullScreenIntent` 全屏提醒与 `LocationManager.addProximityAlert` 邻近警报均有限制/降级，详见 `release-notes.md` 的「已知限制」。后续增强见 `docs/成熟度评估.md` §6 已知限制（V2.72–V2.75 显式保留项）。
