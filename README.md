# TickClear · 点清

> **管好每一个时间点，清空每一件烦心事。**

点清（TickClear）是一款**纯本地、无云端账号**的 Android 个人任务清理工具，以「**任务组 + 定时提醒 + 一键清空**」为核心，兼顾实用与趣味性。所有数据加密落本地（Room 数据库经 **SQLCipher** 加密，加密口令由 Android Keystore 经 Jetpack security-crypto 保护的 `SecureStore` 派生与保存），不依赖任何后端服务即可完整运行。

- 包名：`com.tickclear.app`
- 形态：手机 + 平板（含折叠屏）自适应，单模块 `:app`
- 版本基线：**v2.13.0（versionCode 22，2026-08-10 封板）** — **工具箱按「用户想做什么」重新归类为 8 大类 53 个工具**（前 6 类刚需靠前、后 2 类娱乐沉底，每类 5–8 个）+ 音效层改池化播放（修复连发只响一声）+ 弹珠台发射台玩法重设计 + 骰子朝上面读数与三段弹跳 + 烟花三连发依次开花 + **全量质量加固**（图片 OOM / 音频泄漏 / 并发竞态 / Compose 逐帧分配 / 边界异常 / 权限最小化共 14 项修复）。导航仍为五大 Tab（原「任务 / 习惯」合并为「计划」双子页，原「统计」Tab 改造为「工具」箱，统计详情由今日进度环进入 `Routes.STATS`）。历史版本详见 [docs/release-notes](docs/release-notes.md)。
- 成熟度：四维（产品设计 / 软件开发 / 质量测试 / 应用使用配置）均 **≥99**，综合 **99.6 / 100**，详见 [成熟度评估](docs/成熟度评估.md)。

## 功能特性

### 五大 Tab（底部 / 左导航轨自适应）

| Tab | 路由 | 职责 |
|-----|------|------|
| 今天 | `today` | 当日执行视图 |
| 计划 | `tasks` | 任务 + 习惯（双子页） |
| 助手 | `assistant` | 小智语音 / 文字对话 |
| 工具 | `tools` | 53 个离线工具（8 大类） |
| 设置 | `settings` | 偏好 / 配置 / 关于 |

- **今天**：分组展示今日任务；完成 / 编辑 / 左滑软删（带撤销）/ 右滑完成；时间窗冲突角标 + 冲突横幅；完成率环（点击进入统计详情 `Routes.STATS`）；一键清空「今日全部」；已完成区可折叠。
- **计划**（`ui/plan/PlanScreen.kt`）：顶部双子页切换 —— **任务**（全部任务 + 任务组 CRUD、级联软删、标签筛选、回收站 30 天自动清理可恢复）与 **习惯**（星期重复打卡、连续 streak、休息日标识）。习惯自 v2.9.0 起由一级 Tab 降为本 Tab 子页，一级导航收敛至 5 个。
- **工具**：原「统计」Tab 改造为工具箱（v2.8X 起持续扩充），当前 **8 大类 53 个工具**，全部离线可用、零新增远程依赖。分类按「用户想做什么」划分，前 6 类刚需靠前、后 2 类娱乐沉底，每类控制在 5–8 个：
  - **健康作息**（8）：喝水提醒 / 久坐休息 / 眼保健 / 午休小憩 / 睡眠白噪音 / 听力保护 / 视力自测 / 情绪打卡 —— 提醒类间隔可配、到点通知、触发后自动续排。
  - **效率与计算**（7）：番茄专注 / 倒计时 / 悬浮时钟 / 打卡补录 / 表格计算 / 贷款测算 / 个税测算。
  - **生活助手**（7）：到期提醒 / 烹饪定时 / 家庭积分 / 语音备忘录（录制/播放/删除，音频存本地，可开降噪）/ 到站提醒 / 手电筒 / 补光反光板。
  - **图像与识别**（6）：马赛克 / 去水印 / 图片压缩 / 图片黑白 / 二维码 / 条码识别（含拍照识别 + Open Food Facts 查询）—— 纯 Bitmap 本地处理，长边上限 + 及时 `recycle()` 防 OOM。
  - **测量与传感**（6）：测距仪（含拍照参照物比例换算）/ 水平仪 / 指南针 / 噪音检测（含国标评价）/ 地磁场观测 / 坏点检测。
  - **隐私与安全**（5）：密码保险箱（PBKDF2 + AES-GCM，主口令 + 安全问题找回）/ 隐私探测仪（摄像头与麦克风占用监测）/ 剪贴板保护 / 隐私检查（按权限组反查已授权应用）/ 备份导出。
  - **解压模拟**（7）：吹蜡烛 / 敲木鱼 / 打火机 / 烟花特效 / 玻璃杯敲击 / 动物拟声（12 动物 CC0 录音）/ 振动按摩 —— 拟物重画 + 真实素材 + 振动反馈，全部本地合成/录音。
  - **趣味玩法**（7）：弹珠台 / 石头剪刀布（揭晓动画）/ 养宠物（侧视狗 + 坐姿猫 + 圆猪）/ 涂鸦画板 / 电子琴 / 今日运势 / 抽签器。
  <br/>**支持常用工具置顶**（点星标）—— 顶部一行紧凑快捷入口，常用工具一键打开。工具注册表集中在 `ToolsScreen.kt` 的 `TOOL_CATEGORIES`，路由常量在 `Routes.kt`，新增工具只需三处登记（注册表 + 路由 + `TickClearNavGraph` 的 `composable`），三者数量必须一致。
- **助手**：对接**小智（Xiaozhi）WebSocket** 协议，语音 + 文字聊天。**REAL 模式**走官方云（含 MCP JSON-RPC 2.0 双向握手：`initialize` / `notifications/initialized` / `tools/list` / `tools/call`）；对话触发任务经 MCP `create_task` 在本机建任务（复用 `AddTaskUseCase` + 冲突检测）；服务端塞进 `text` 的多模态资源引用（`@image#<i>:<hash>.<ext>`）由 `MessageTextFilter` 自动净化。**Mock 模式离线可跑**。连接与语音排查详见 [test/小智诊断手册](test/XIAOZHI_DIAGNOSTIC_README.md)。
- **设置**：主题（浅色 / 深色 / 动态）、语音 / ASR / LLM 配置 + 测试、回收站管理、调试（日志 / 测试按钮）、关于。

### 提醒与通知
- `AlarmManager` 精确闹钟 + 通知（完成 / 稍后 / 跳过）；12+ 精确闹钟权限引导、14+ 全屏提醒权限前置引导。
- 静音时段（默认 22:00–07:00 低优先级静默）、组级定时提醒继承、全屏提醒（`fullScreenIntent` + Activity）。

### 位置提醒
- 地理围栏调度（`addProximityAlert`）+ 权限挂接；无 GMS 降级系统定位。

### 语音能力
- 可配置云端 ASR（阿里云 / 腾讯云 / OpenAI 兼容 / 豆包 / 千问）+ 本地 best-effort 识别；**离线热词指令**（暂停 / 启用 / 删除 + 任务名）；自定义唤醒词与欢迎词。
- 语音链路封装在 `domain/assistant/OpusCodec`：自 v2.8.0 起使用本地 AAR `app/libs/opus.aar`（theeasiestway，官方 libopus 1.3.1 软件实现，全 ABI 含 arm64-v8a），不再依赖设备 `MediaCodec` Opus 编码器；原生库加载失败时自动降级文本模式。

### 数据与互通
- 定时**加密自动备份**（AlarmManager 调度）+ 手动立即备份；JSON 备份恢复（含加密与版本化、备份自愈校验）。
- **ICS（iCalendar）导入 / 导出** 走 SAF。
- 桌面小组件（AppWidgetProvider）+ 系统动态快捷方式（长按图标直达「新建任务 / 助手」，`ShortcutManager`，API 25+）。<br/>*注：物理键盘快捷键（Ctrl+N 等）为规划项，当前版本未实现，见 [成熟度评估 §6](docs/成熟度评估.md) 已知限制。*

### 自适应与无障碍
- `WindowSizeClass` 断点：手机 Compact 底部 Tab；平板 Medium/Expanded 左 `NavigationRail` + 双栏（今日 / 设置 / 任务编辑 / 组编辑带实时预览）。
- 横屏 / 折叠 / 分屏保持状态（不重建 Activity）；TalkBack 语义标签、动态字体、WCAG AA 对比度。

---

## 技术栈

| 维度 | 技术 |
|------|------|
| 语言 / 构建 | Kotlin 2.0.21 / AGP 8.5.2 / Gradle 8.9 |
| 平台 | minSdk 26 / targetSdk 34 / compileSdk 34 |
| UI | Jetpack Compose + Material3 |
| DI | Hilt（KSP） |
| 本地存储 | Room（**SQLCipher** 加密）+ DataStore（偏好）+ Jetpack security-crypto / `SecureStore`（Android Keystore 派生并保存 SQLCipher 口令） |
| 网络 | OkHttp（小智 WebSocket 二进制帧） |
| 序列化 | kotlinx-serialization |
| 权限 | accompanist.permissions |
| 音频 | AudioRecord / AudioTrack + libopus（本地 AAR `app/libs/opus.aar`，theeasiestway，全 ABI） |

---

## 快速开始

> 完整的最小命令行编译环境配置（Windows，无需 Android Studio）见 **[`docs/构建手册.md`](docs/构建手册.md)**。

前置：`ANDROID_HOME` 指向 Android SDK（compileSdk 34、build-tools 34.0.0），JDK 17+（AGP 8.5.2 兼容 JDK 21）。

```bash
# 调试构建
./gradlew assembleDebug

# 安装到已连接设备 / 模拟器
./gradlew installDebug

# 单元测试（CI 门禁之一）
./gradlew testDebugUnitTest

# Lint 门禁（abortOnError=true，0 error 才能过；当前基线为 0 warning）
./gradlew lintRelease
```

提交前还需通过两道静态门禁（已接入 CI，见 `.github/workflows/ci.yml`）：

```bash
python3 test/check_migrations.py   # Room 迁移与 app/schemas/*.json 一致性
node    test/scan_strings.mjs      # strings.xml 引用完整性（Missing 必须为 0）
```

> Release 签名由 `local.properties` 的 `release.*` 提供（已被 `.gitignore` 忽略）；缺失则回退 debug 签名以保证可构建。

---

## 项目结构（模块内）

```
ui/           Compose 界面与 ViewModel
  theme/        设计令牌（LIGHT / DARK / DYNAMIC）、Typography、Shape、Spacing、系统栏联动
  navigation/   Routes / BottomNav / TickClearNavGraph
  components/   共享组件（TaskCard / ConflictBanner / ...）
  today/        今天 Tab
  plan/         计划 Tab 容器（任务 + 习惯双子页）
  tasks/        任务子页与回收站、TasksViewModel
  habits/       习惯子页与 HabitsViewModel
  tools/        工具箱（TOOL_CATEGORIES 注册表 + 53 个工具界面）
  stats/        统计详情（由今日进度环进入）
  assistant/ settings/ ai/ adaptive/
data/         Room 实体、DAO、AppDatabase、Repository、SecureStore、VaultCrypto
domain/       纯 Kotlin 模型、UseCase、ConflictChecker、util/、tools/、ai/、assistant/、scheduler/
di/           Hilt 模块
```

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [`docs/成熟度评估.md`](docs/成熟度评估.md) | 产品设计 / 软件开发 / 质量测试 / 应用配置四维成熟度评估（含已知限制 §6，历史任务清单已并入此文档） |
| [`docs/构建手册.md`](docs/构建手册.md) | Windows 命令行最小路径编译环境手册 |
| [`docs/测试与验收清单.md`](docs/测试与验收清单.md) | 静态 / 单测验证 + 真机验收矩阵（V2.44–V2.51）+ 验收指标 |
| [`docs/用户配置手册.md`](docs/用户配置手册.md) | 安装后配置手册（设置 / ASR / 小智助手） |
| [`docs/点清APP_产品设计文档.md`](docs/点清APP_产品设计文档.md) | 产品需求文档（PRD） |
| [`docs/语音助手实现说明.md`](docs/语音助手实现说明.md) | 小智语音助手落地说明（唤醒词 / 息屏唤醒 / 欢迎词） |
| [`docs/archive/`](docs/archive/) | 历史版本原型与提示词归档（任务清单已并入 `成熟度评估.md`） |

---

## 工程红线（强制规范）

代码与文档改动均须遵守以下红线（详见 [`AGENT.md`](AGENT.md)）：

1. **零新依赖** —— 复用 OkHttp / DataStore / 系统框架，不引入新第三方库。
2. **中文全抽离 `strings.xml`** —— 用户可见中文不得硬编码在源码（识别词典 / 日志 `detail` / 注释允许）。
3. **Room 显式 Migration** —— 版本 1→10 递增（schema 导出至 `app/schemas/`），禁用 `fallbackToDestructiveMigration`；迁移与 schema 的一致性由 `test/check_migrations.py` 在 CI 门禁校验。
4. **`.workbuddy/` 不提交 git** —— 仅本地工作区数据。
5. **提交纪律** —— 每次自洽改动独立 `git commit`，中文类型前缀（`[fix]` / `[feature]` / `[docs]` / `[config]` / `[test]`）；允许 `git push`（按功能拆分提交后由开发者于本地执行 `git push origin master` 完成推送）。

---

## 已知限制与待真机验证

部分能力依赖系统版本 / 厂商策略，且仪器化 / 真机指标评测（**V2.44–V2.51**）需在物理设备或 CI 上逐项勾选，当前以已知限制封板：

- Opus 编码依赖设备能力；无编码器自动降级文本。
- Android 14+ `setFullScreenIntent` 未授权时降级普通通知。
- 位置提醒受厂商省电策略影响，精度有限。
- 本地离线 ASR 为系统框架 best-effort（引入推理框架会破「零新依赖」红线）。
- 仪器化 / Macrobenchmark 测试仅编译验证，待真机 / CI 实跑。

完整验收矩阵、验收指标与执行记录模板见 **[`docs/测试与验收清单.md`](docs/测试与验收清单.md)**。

---

## 许可证

本项目为个人任务管理工具，源码与文档版权归项目作者所有。商业化 / 再分发请先取得授权。
