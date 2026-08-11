# AGENT.md — 点清 TickClear 项目规范

> 本文件为 AI 协作与人工开发共用的项目规范。改动架构/约定时同步更新本文件。

## 1. 项目概览
- **点清 TickClear**：Android 个人任务清理工具（手机 + 平板自适应）。
- **定位**：纯本地、无云端账号；任务数据经 **SQLCipher** 加密存储。
- **包名**：`com.tickclear.app`，单模块 `:app`（MVP 优先，未拆 :core/:data）。
- **基线**：Kotlin 2.0.21 / AGP 8.5.2 / Gradle 8.9 / minSdk 26 / targetSdk 34 / compileSdk 34。
- **UI**：Jetpack Compose + Material3；底部 **5 Tab**：今天 / 计划 / 助手 / 工具 / 设置（见 `Routes.topLevelDestinations`，此为唯一事实来源）。
  - 「任务」与「习惯」自 v2.9.0 起合并为**「计划」Tab 的两个子页**（`ui/plan/PlanScreen.kt` 承载，内部复用 `ui/tasks/TasksContent` 与 `ui/habits/HabitsContent`），一级导航由 6 收敛为 5。
  - 统计详情不占一级 Tab，经今日进度环进入 `Routes.STATS`，仍可达。
- **DI**：Hilt。注解处理器 **Room 与 Hilt 均已统一走 KSP**（`ksp(libs.androidx.room.compiler)` / `ksp(libs.hilt.compiler)`），工程内无 KAPT。
- **本地存储**：Room(SQLCipher) + DataStore(偏好) + EncryptedSharedPreferences(密钥/口令)。

## 2. 模块内包布局
```
ui/         Compose 界面与 ViewModel
  theme/    设计令牌(LIGHT/DARK/DYNAMIC)、Typography、Shape、Spacing、系统栏联动(TickClearTheme)
  navigation/  Routes / BottomNav / TickClearNavGraph
  components/  共享组件(TaskCard/ConflictBanner/...)
  today/    今天 Tab
  plan/     计划 Tab 容器（任务 + 习惯双子页，仅 PlanScreen.kt）
  tasks/    任务子页 / 回收站 / TasksViewModel（被 plan 复用）
  habits/   习惯子页 / HabitsViewModel（被 plan 复用）
  tools/    工具箱：TOOL_CATEGORIES 注册表 + 53 个工具界面 + 共享组件
  stats/ assistant/ settings/ ai/ adaptive/
data/       Room 实体、DAO、AppDatabase、Repository、SecureStore、VaultCrypto
domain/     纯 Kotlin 模型、UseCase、ConflictChecker、util/、tools/、log/、ai/、assistant/、scheduler/
di/         Hilt 模块
```
> `ui/tasks` 与 `ui/habits` 保留独立包（承载各自 ViewModel 与内容组件），`ui/plan` 只做壳与子页切换；不要把二者内容并进 `plan/`。

## 3. 编码规范
- **依赖注入**：所有 Repository / UseCase / 客户端经 Hilt 提供，`@Inject constructor` + `@Singleton`。
- **状态管理**：ViewModel 暴露 `StateFlow`/`SharedFlow`；UI 用 `collectAsStateWithLifecycle` 订阅；**禁止**在 Compose 中直接持有可变业务状态。
- **字符串**：**所有用户可见中文进 `res/values/strings.xml`**（落实 PRD A-6），禁止 Compose 内联硬编码。
- **无魔数**：颜色/间距/圆角用 `ui/theme` 令牌；时间窗容差等常量集中定义。
- **错误处理**：统一 `AppError` sealed；Repository 抛域异常，ViewModel 映射为 UI 状态；**禁止裸 catch**。
- **并发**：IO 用 `Dispatchers.IO`（经 `DispatcherProvider`）；流入 UI 在主线程收集。
- **数据库迁移**：`AppDatabase` 版本递增（当前 **10**，schema 导出 `app/schemas/`）；破坏性变更走 `Migration`，不启用 `fallbackToDestructiveMigration`。
- **主题与系统栏**：框架窗口主题跟随**系统**深色（`values/` 与 `values-night/` 资源限定符），Compose 主题跟随**应用内** `ThemeMode`；二者必须由 `TickClearTheme` 的 `SideEffect` 强制联动（写 `window.statusBarColor` / `navigationBarColor` + `WindowCompat.getInsetsController(...).isAppearanceLight*Bars`），否则「应用内深色 + 系统浅色」会出现状态栏色带割裂与图标不可见。
- **Bitmap 生命周期**：`Bitmap.createScaledBitmap` / `copy` 等产生新位图后，若原图不再使用必须 `recycle()`（判空 `if (out !== src)`），否则 Native 内存峰值翻倍，大图工具连开必 OOM。
- **Compose 重组开销**：列表/派生集合的计算（`groupBy`、`filter`、排序、`SimpleDateFormat` 构造）一律用 `remember(key)` 缓存，禁止写在组合体里裸算；`items`/`itemsIndexed` 必须给稳定 `key`。
- **高频状态的重组隔离**：传感器回调 / 帧循环驱动的状态（如方位角、动画进度）若在组合体顶层被直接读取，会以 50–60Hz 重组整页。必须把状态读取下沉：传 `() -> T` lambda 给最小子组件，或只在 `Canvas`/`drawBehind` 的绘制 lambda 内读（只失效绘制阶段，不触发重组）；文本类展示再叠一层 `derivedStateOf` 收敛到人眼可辨的粒度。
- **逐帧对象分配**：`android.graphics.Paint`、`Path` 等不要在 `DrawScope` 内 `new`（每帧 × 每元素稳定产生垃圾，长时间停留必 GC 抖动）。提到组合层 `remember`，绘制时只改会变的字段（注意先设 `color` 再设 `alpha`，`color` 赋值会覆盖 alpha）。
- **ViewModel 事件流封装**：`MutableSharedFlow` 一律 `private` + `asSharedFlow()` 只读暴露，emit 走 ViewModel 的具名方法，禁止 UI 直接 `tryEmit`。`extraBufferCapacity` 要覆盖「一次操作连发多条」的最坏情况（如导入备份连发 4 条结果），给 1 会静默丢提示。
- **读-改-写复合操作**：`_state.value = _state.value + delta` 这类跨挂起点的读改写（尤其后面还要写 DataStore/DB），在快速连点下会互相覆盖。必须用 `Mutex.withLock` 把整段（含持久化）串起来，或改用 `MutableStateFlow.update{}` 原子更新。
- **权限最小化**：`AndroidManifest.xml` 里的每条 `uses-permission` 都必须对应真实调用点。只读查询类 API（如 `isNotificationPolicyAccessGranted`）通常并不需要对应权限，声明了只会在应用信息里多出用户看不懂的条目，与「纯本地、最小权限」定位相悖。
- **依赖状态的副作用**：`LaunchedEffect(Unit)` 只在首帧执行一次，若逻辑依赖异步到达的数据（首帧通常为空）必须把数据作为 key；用户手动操作过的状态要用标志位隔离，避免被自动逻辑覆写。
- **DataStore 惰性生成**：在 `Flow` 变换里写副作用会因多 collector 并发生成不同值（如设备 ID 漂移）；必须收敛到 `Mutex` 保护的 `suspend` 函数内做「二次检查 + 写入」。
- **单元测试**：禁止用 Kotlin 的 `assert()`（JVM 默认 `-da`，断言被整段跳过、测试永远绿），一律 `org.junit.Assert.*`；断言异常必须用 `assertThrows(具体异常类)`，禁止 `runCatching{}.isFailure`（NPE 也会被算作通过）。

## 4. 密钥与敏感数据处理（红线）
- `local.properties` 含明文调试密钥，**已被 .gitignore 忽略，禁止提交、禁止写进源码**。
- 仅 **debug** 构建经 `buildConfigField` 注入腾讯 ASR 密钥；release 不读取。
- **Release 签名**：`local.properties` 提供 `release.storeFile/release.storePassword/release.keyAlias/release.keyPassword`（明文，已被 .gitignore 忽略）；`app/build.gradle.kts` 据此创建 release `signingConfigs`，缺失则回退 debug 签名以保证可构建。禁止把签名密钥写进源码或提交仓库。
- SQLCipher 口令与 ASR/LLM 密钥存 **EncryptedSharedPreferences**（Keystore, AES256-GCM）。
- 口令丢失 = 加密库不可解密：关于页必须提示"清除应用数据将丢失加密库"。

## 5. 五大 Tab 行为契约
> 一级导航共 **5** 个，唯一事实来源是 `ui/navigation/Routes.kt` 的 `topLevelDestinations`；增删 Tab 必须同步本节与 README。

- **今天**（`today`）：分组展示今日任务；完成/编辑/删除（左滑软删带撤销、右滑完成）；时间窗冲突角标 + `ConflictBanner`；今日完成率环（点击进 `Routes.STATS`）；已完成区可折叠（折叠态在用户手动切换后不再被自动逻辑覆盖）；AI 助手入口。
- **计划**（`tasks` → `ui/plan/PlanScreen.kt`）：容器 Tab，顶部切换「任务 / 习惯」两个子页，各自 ViewModel 独立。
  - **任务子页**：全部任务 + 任务组 CRUD（级联软删）；回收站（软删 `deletedAt`，默认 30 天自动彻底清理，可恢复）。
  - **习惯子页**：习惯 CRUD（emoji / 重复星期 CSV / 提醒时刻 / 配色 / 排序 / 归档），按日打卡（`HabitCheckInEntity`，同日幂等；习惯列表内的快捷打卡仍仅限当天）。连续天数统一走 `HabitDates.computeStreak`（`java.time.LocalDate` DST 安全实现，唯一事实来源）；提醒经 `HabitReminderScheduler` 排程、`HabitReminderReceiver` 触发后自动续排，**习惯提醒无条件响铃 + 震动，不受全局「声音」开关约束**。
  - **打卡补录（v2.9++ 工具）**：底层 `HabitRepository.checkIn(habitId, date)` / `CheckInRepository` 本就支持任意历史日期，该工具开放显式入口，为习惯或每日记录补打 / 取消过往日期的打卡，补全缺失记录；与习惯列表内「仅当天」的快捷打卡互不冲突。
- **工具**（`tools`）：原「统计」Tab 改造为工具箱（v2.8X 起持续扩充），当前 **8 大类 53 个工具**（健康作息 8 / 效率与计算 7 / 生活助手 7 / 图像与识别 6 / 测量与传感 6 / 隐私与安全 5 / 解压模拟 7 / 趣味玩法 7），全部离线可用。分类按「用户想做什么」而非「技术上是什么」划分，前 6 类为刚需、后 2 类娱乐沉底，每类控制在 5–8 个，避免再出现「实用工具」式垃圾桶分类。唯一事实来源是 `ToolsScreen.kt` 的 `TOOL_CATEGORIES`，增删工具必须同步本节。分类展示小工具——健康类：喝水提醒、久坐/眨眼休息提醒（间隔可配、到点通知、自调度）；效率安全类：语音备忘录（录制/播放/删除，音频存本地；录制可开启「降噪」，改用 `VOICE_RECOGNITION` 音源触发平台级降噪/回声消除，开关持久化于 DataStore）、密码保险箱（PBKDF2+AES-GCM 加密、主口令 + 安全问题找回、条目含名称/地址/用户名/密码/备注）。统计详情仍经今日页进度环点击进入 `Routes.STATS`。
  - **v2.9++ 新增工具（分类入工具箱）**：`条码识别`（生活助手：从相册选图经 ZXing core 解码商品条码，并可联网查 Open Food Facts 商品基础信息，无相机实时扫码以规避新增 CameraX 依赖）、`马赛克`（实用工具：选图后拖动框选，支持马赛克/涂黑两种遮挡方式，保存到相册）、`指南针`（实用工具：加速度计+磁力计融合解算方位角，表盘随朝向旋转）、`打卡补录`（实用工具：为习惯/每日记录补打或取消过往日期打卡）、`去水印`（实用工具：选图后拖动框选水印区域，支持「色彩修复」（四周环带取色覆盖，适合纯色背景/AI 文字水印）与「模糊柔化」，保存到相册，纯 Bitmap 处理）、`摄像头检测`（安全工具：相机实时占用经 `CameraManager.AvailabilityCallback` 监测；麦克风占用经 `AppOpsManager` 监听尽力检测；并静态审计已授权相机/麦克风权限的应用供用户收敛）、`剪贴板保护`（安全工具：监听剪贴板，开启后复制内容延迟 N 秒自动清空以规避后台读取，并提供「安全复制并自动清除」与「立即清除」，开关与延迟持久化于 DataStore；受 Android 10+ 限制无法真正拦截其他 App 读取，采用延时清空策略）。新增工具统一在 `ToolsScreen.kt` 的 `TOOL_CATEGORIES` 登记、在 `TickClearNavGraph.kt` 登记路由（命名 `TOOLS_*`、值 `"tools/*"`），文案全部抽离 `strings.xml`，图标用 Material Icons Extended。
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
# Lint 门禁（abortOnError=true；当前基线 0 error 0 warning，新增告警视为回归）
./gradlew lintRelease
```
**静态门禁（已接入 `.github/workflows/ci.yml`，提交前本地必跑）**：
```bash
python3 test/check_migrations.py   # Room Migration 与 app/schemas/*.json 逐字段一致性
node    test/scan_strings.mjs      # strings.xml 引用完整性，Missing 必须为 0
```
- 编译需联网拉取依赖（AGP / Compose BOM / Hilt / Room / SQLCipher 等）。
- Release 已启用 `shrinkResources` + R8 压缩；release 签名见 §4。
- SQLCipher 与 Room 共用 SQLite：已在 `app/build.gradle.kts` 排除 `androidx.sqlite:sqlite-framework`。

## 7. 分支与任务追踪
- 当前状态与任务追踪见 `docs/成熟度评估.md`（v2.12.0 封板四维成熟度评估，综合 99.6/100，2026-08-09；含 V2.63–V2.83 全量记录 + 已知限制 §6 + §13 v2.8.0 封板收口 + v2.12.0 拟物重画/真实素材增量）；历史阶段任务清单（v1.0 / v2.0–v2.5 各版本「开发计划_*_任务清单」及「未完成任务清单」）已于 2026-07-27 整理删除，其内容（R1–R10、V2.44–V2.51、Q4/Q5 等）已并入该评估，单一可信源即此文档。
- 每完成一个子任务，按需追加 `D:/ai_work/TickClear/.workbuddy/memory/` 工作日志（状态总览见 `docs/成熟度评估.md`）。
- **提交纪律（红线）**：每一次编码 / 修改完成、进入下一项事项前必须 `git commit`（功能自洽、可独立回滚）；禁止把多个不相关改动攒成一次大提交。提交信息用中文，类型前缀 + 简述，例如：`[fix] 子日级实例完成状态脱节`、`[feature] 新增日志查看页`、`[config] release 签名注入`、`[test] 新增备份往返测试`、`[docs] 更新发布说明`、`[refactor] 拆分 Repository 边界`、`[chore] 升级依赖`。
- 工程纪律（全程保持）：中文全抽离 `strings.xml`、数据库迁移显式 `Migration` 禁 `fallbackToDestructiveMigration`、不引入新依赖（复用 OkHttp/DataStore/系统框架）。
  - **唯一例外（v2.8.0 起）**：Opus 编解码本地 AAR `app/libs/opus.aar`（theeasiestway/android-opus-codec，含官方 libopus 1.3.1，全 ABI 含 arm64-v8a）。因 Android `MediaCodec` 的 Opus **编码器**在多数机型 `dequeueInputBuffer` 恒返回 -1 属平台碎片化硬伤，语音链路无法自研绕过。该库以本地文件形式随仓库分发，不新增远程仓库/坐标解析。**禁止回退到 `com.github.martoreto:opuscodec`**（其 `libsenz.so` 仅 32 位，arm64 设备 dlopen 失败）。

## 8. 已知风险与限制
- Opus 编解码：语音链路使用本地 AAR `app/libs/opus.aar`（theeasiestway，libopus 1.3.1 软件实现，各机型可用）；不再依赖设备 `MediaCodec`(audio/opus) 编码器（多数机型 `dequeueInputBuffer` 恒 -1 导致零帧上行）。Mock 模式不需真实 Opus。集中封装在 `domain/assistant/OpusCodec`。
- 真实小智服务端联调（Real 模式）依赖外部 token；Mock 模式为默认演示路径（v1.0 已含）。
- 动态取色(DYNAMIC)仅 API31+，低版本回退浅色。
- **平台限制（v1.0 已知）**：Android 14+ 对 `setFullScreenIntent` 全屏提醒与 `LocationManager.addProximityAlert` 邻近警报均有限制/降级，详见 `docs/release-notes.md` 的「已知限制」。后续增强见 `docs/成熟度评估.md` §6 已知限制（V2.72–V2.75 显式保留项）。
