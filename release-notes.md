# 点清（TickClear）Release Notes

> 管好每一个时间点，清空每一件烦心事。

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
