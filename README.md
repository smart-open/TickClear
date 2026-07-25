# TickClear · 点清

> **管好每一个时间点，清空每一件烦心事。**

点清（TickClear）是一款**纯本地、无云端账号**的 Android 个人任务清理工具，以「**任务组 + 定时提醒 + 一键清空**」为核心，兼顾实用与趣味性。所有任务数据经 **SQLCipher** 加密存储，不依赖任何后端服务即可完整运行。

- 包名：`com.tickclear.app`
- 形态：手机 + 平板（含折叠屏）自适应，单模块 `:app`
- 版本基线：v2.5.0（代码健壮性收口）

---

## 功能特性

### 五大 Tab（底部 / 左导航轨自适应）
- **今日**：分组展示今日任务；完成 / 编辑 / 左滑软删（带撤销）/ 右滑完成；时间窗冲突角标 + 冲突横幅；完成率环；一键清空「今日全部」。
- **任务**：全部任务 + 任务组 CRUD（级联软删）；回收站（软删 `deletedAt`，默认 30 天自动彻底清理，可恢复）。
- **统计**：按组 / 日 / 周 / 月完成情况、完成率、连续打卡天数（基于 `CheckInEntity`，不可补卡）；8 枚勋章墙 + 热力图日历。
- **助手**：模拟硬件设备对接**小智（Xiaozhi）WebSocket** 协议，语音 + 文字聊天；对话触发任务时经 **MCP 函数调用（`create_task`）** 在本机建任务（复用 `AddTaskUseCase` + 冲突检测）。默认 **Mock 模式离线可跑**。
- **设置**：主题（浅色 / 深色 / 动态）、语音 / ASR / LLM 配置 + 测试、回收站管理、调试（日志 / 测试按钮）、关于。

### 提醒与通知
- `AlarmManager` 精确闹钟 + 通知（完成 / 稍后 / 跳过）；12+ 精确闹钟权限引导、14+ 全屏提醒权限前置引导。
- 静音时段（默认 22:00–07:00 低优先级静默）、组级定时提醒继承、全屏提醒（`fullScreenIntent` + Activity）。

### 位置提醒
- 地理围栏调度（`addProximityAlert`）+ 权限挂接；无 GMS 降级系统定位。

### 语音能力
- 可配置云端 ASR（阿里云 / 腾讯云 / OpenAI 兼容 / 豆包 / 千问）+ 本地 best-effort 识别；**离线热词指令**（暂停 / 启用 / 删除 + 任务名）；自定义唤醒词与欢迎词。
- 语音链路封装在 `domain/assistant/OpusCodec`：优先用 Android `MediaCodec`（audio/opus），无编码器自动降级文本模式。

### 数据与互通
- 定时**加密自动备份**（AlarmManager 调度）+ 手动立即备份；JSON 备份恢复（含加密与版本化、备份自愈校验）。
- **ICS（iCalendar）导入 / 导出** 走 SAF。
- 桌面小组件（AppWidgetProvider）+ 动态快捷方式 + 键盘快捷键（Ctrl/Cmd+N 新建、Space 完成、Ctrl/Cmd+Enter 清空等）。

### 自适应与无障碍
- `WindowSizeClass` 断点：手机 Compact 底部 Tab；平板 Medium/Expanded 左 `NavigationRail` + 双栏（今日 / 设置 / 任务编辑 / 组编辑带实时预览）。
- 横屏 / 折叠 / 分屏保持状态（不重建 Activity）；TalkBack 语义标签、动态字体、WCAG AA 对比度。

---

## 技术栈

| 维度 | 技术 |
|------|------|
| 语言 / 构建 | Kotlin 2.0.21 / AGP 8.5.2 / Gradle 8.9 |
| 平台 | minSdk 24 / targetSdk 34 / compileSdk 34 |
| UI | Jetpack Compose + Material3 |
| DI | Hilt（KAPT） |
| 本地存储 | Room（SQLCipher）+ DataStore（偏好）+ EncryptedSharedPreferences（密钥 / 口令） |
| 网络 | OkHttp（小智 WebSocket 二进制帧） |
| 序列化 | kotlinx-serialization |
| 权限 | accompanist.permissions |
| 音频 | AudioRecord / AudioTrack + MediaCodec（Opus） |

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

# Lint 门禁（abortOnError=true，0 error 才能过）
./gradlew lintRelease
```

> Release 签名由 `local.properties` 的 `release.*` 提供（已被 `.gitignore` 忽略）；缺失则回退 debug 签名以保证可构建。

---

## 项目结构（模块内）

```
ui/           Compose 界面与 ViewModel
  theme/        设计令牌（LIGHT / DARK / DYNAMIC）、Typography、Shape、Spacing
  navigation/   Routes / BottomNav / TickClearNavGraph
  components/   共享组件（TaskCard / ConflictBanner / ...）
  today/ tasks/ stats/ assistant/ settings/ ai/ adaptive/
data/         Room 实体、DAO、AppDatabase、Repository、SecureStore
domain/       纯 Kotlin 模型、UseCase、ConflictChecker、ai/、assistant/、scheduler/
di/           Hilt 模块
```

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [`docs/开发计划_v2.5_任务清单.md`](docs/开发计划_v2.5_任务清单.md) | 当前版本任务清单（含 ✅ 标记与 v2.5.1 复查修复记录） |
| [`docs/成熟度评估.md`](docs/成熟度评估.md) | 产品设计 / 软件开发 / 质量测试 / 应用配置四维成熟度评估 |
| [`docs/构建手册.md`](docs/构建手册.md) | Windows 命令行最小路径编译环境手册 |
| [`docs/测试与验收清单.md`](docs/测试与验收清单.md) | 静态 / 单测验证 + 真机验收矩阵（V2.44–V2.51）+ 验收指标 |
| [`docs/点清APP_产品设计文档.md`](docs/点清APP_产品设计文档.md) | 产品需求文档（PRD） |
| [`docs/语音助手实现说明.md`](docs/语音助手实现说明.md) | 小智语音助手落地说明（唤醒词 / 息屏唤醒 / 欢迎词） |
| [`docs/archive/`](docs/archive/) | 历史版本任务清单、原型与提示词归档 |

---

## 工程红线（强制规范）

代码与文档改动均须遵守以下红线（详见 [`AGENT.md`](AGENT.md)）：

1. **零新依赖** —— 复用 OkHttp / DataStore / 系统框架，不引入新第三方库。
2. **中文全抽离 `strings.xml`** —— 用户可见中文不得硬编码在源码（识别词典 / 日志 `detail` / 注释允许）。
3. **Room 显式 Migration** —— 版本 1→5 递增，禁用 `fallbackToDestructiveMigration`。
4. **`.workbuddy/` 不提交 git** —— 仅本地工作区数据。
5. **提交纪律** —— 每次自洽改动独立 `git commit`，中文类型前缀（`[fix]` / `[feature]` / `[docs]` / `[config]` / `[test]`）；默认不 `git push`。

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
