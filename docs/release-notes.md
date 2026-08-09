# 点清（TickClear）Release Notes

> 管好每一个时间点，清空每一件烦心事。

---

## v2.12.0（2026-08-09 · 封板）· 模拟解压/拟物重画/真实素材三线齐推 + 新增电子琴 + lint 零警告

**平台**：Android 8.0+（minSdk 26 / targetSdk 34）· 手机 + 平板
**版本**：versionCode 20 / versionName 2.12.0 · DB schema v10（本次无 schema 变更）
**相对 v2.11.0**（本地顺延 tag，未发布）：v2.11.0 是上次顺延的本地 tag（指向 commit ea35a6fe，从未推送），本轮顺着 minor bump 用 v2.12.0；累计 20 个新 commit，含以下三类主线 + 新工具 + UI 打磨 + 维护修复。零新增远程依赖，DB 版本不变。

### 🧰 模拟解压重做（4 个工具大幅升级）
- **弹珠台（Pinball）**：12 颗弹珠雨（顶部随机分布、落底重生）+ 分数水平卡（不换行）+ 真实弹珠碰撞音（Freesound #401741「marble spilling onto wooden table」by PMBROWNE, CC0，silencedetect 定位首击裁 ~0.4s + volume=1.7 + mono/22k/16bit）；视觉手感拖尾辉光（暖琥珀 GLOW 0xFFFFAB40，每球记录最近 8 帧轨迹）+ 连击系统（800ms 窗口、最高 x10 倍率）+ 钉子命中 160ms 衰减白色光圈 + 命中飘分（颜色随连击升级：绿→黄→橙→红）+ 重震动阈值（连击 ≥5 时 22ms）。
- **烟花（Fireworks）**：从底部抛物线发射、13 色全彩虹、80 粒子白光闪 + 爆点 BurstFlash（r 28→98px）；随机调色板（1/3 全彩虹 + 1/3 单色 + 1/3 双色）+ 双击 5 发齐射（间隔 60ms、目标散开 0.16、飞行 0.78~0.96s 错开）；类型切换 FilterChip（牡丹=球形 80 粒/柳叶=58 粒低重力慢垂长拖尾/随机）；发射「咻」声（FoleySynth.launchWhistle 380→1880Hz 急升 0.3s）+ 抵达爆炸 boom（firework_boom.wav Freesound #624413 by MilanKovanda CC0，裁前 3.18s 静音）。
- **剪刀石头布（RPS）**：出拳后双方手势 80ms 切换 ✊/✌️/✋，3.0–5.0s 随机（3000L + Random.nextInt(2001)）定格揭晓；揭晓期间 "VS"→"?"、底部「揭晓中…」、按钮 enabled=!isSpinning 禁用；ChoiceBadge 弹跳动画键控在 revealed（false→true）触发而非 choice 立即触发；触感分级（出招 25ms 轻 + 揭晓定格 60ms 重）。
- **养宠物（Pet）**：拟物重画（侧视狗/坐姿猫/圆猪）+ 每操作各异的粒子反应；提示文字全部压到 12 字内一行不换行。

### 🎨 拟物重画 + 操作差异化
- **小狗**（侧视）：长椭圆身 + 头圆在前侧偏上 + 两片下垂扇形耳（base.darken(0.18)）+ 突出浅色口鼻 + 黑鼻头带高光 + 微笑嘴曲线 + 4 条腿（前后各二，深色）+ 摆尾贝塞尔 sin(phase*7)*0.25。
- **小猫**（坐姿，尖耳）：身体椭圆 + 头圆 + 两条前腿；外三角尖耳（base.darken(0.10)）+ 内层粉色三角；杏仁形绿眼（高度 r*0.20，眨眼压扁到 r*0.04）+ 黑色竖瞳；粉红小三角鼻 + "w" 形嘴曲线 + 6 根胡须分列两侧；长卷尾两段 quadraticTo 从身体右侧勾到上方。
- **小猪**（圆鼓鼓坐姿）：圆身 + 圆头（sway 微动）+ 突出大圆盘鼻 + 两鼻孔 + 小三角耳 + 卷尾 + 黑眼带白色高光。
- **操作差异化粒子**：
  - 鱼·喂食 → 落饵料（追食）；鱼·换水 → 蓝气泡
  - 狗·喂食 → 5 颗棕色 hue=35 骨头；狗·摸摸 → 6 颗粉色心
  - 猪·喂食 → 5 颗红色 hue=0 苹果 + 3 颗鼻息 ring 圈；猪·摸摸 → 6 心 + 2 哼气 ring 圈
  - 猫·喂食 → 5 颗绿色 hue=130 鱼肉；猫·撸猫 → 6 心 + 2 颗蓝色 hue=200 大半径「Z」呼噜；猫·逗猫棒 → 黄星 + 扑逗动画
- **打火机**：开盖角度 -46°→-120°（pivot 铰链，旋转让开火焰不可挡火）；铰链销 fillSphere(hingeR=bodyW*0.05) + drawRimLight 镶边钉在机身，自由端 lidOverlap=底=bodyH*0.025 压入闭合缝；关盖金属「咔嗒」FoleySynth.lidClose（高频噪声 0.03s + 1150Hz 泛音 0.045s）+ 30ms 震动；弹簧 stiffness 520→600 回弹更利落。
- **吹蜡烛**：阈值 0.20→0.13（普通吹气即触发、仍挡说话误触）；blowPower 累积/衰减改成「阈值处净增益为正」（loud: 1.0+over*1.5 每 60ms 帧，over=(rms-0.13)/0.20；quiet: -0.8*dt），轻吹可缓慢累积；满功率 ~1s 灭；无麦降级同步放宽。

### 🎵 真实素材全员接入（CC0 录音替换合成兜底）
- 木鱼 `wood_knock`：duoduosysa/defoldmuyu 仓 `mokugyo.wav`，CC0，16-bit/44.1k/1.83s
- 玻璃杯 7 音 `glass_note_1..7`：mcapodici/pianosounds 仓 Piano.ff.*.ogg，CC0，ffmpeg 转 mono/44.1k/16-bit/3s
- 烟花 `firework_boom`：Freesound #624413「Firework single shot」by MilanKovanda, CC0，裁前 3.18s 静音让爆炸即时触发，185KB
- 弹珠 `marble_click`：Freesound #401741「marble spilling onto wooden table」by PMBROWNE, CC0，silencedetect 定位首击 t≈1.013s 后裁 ~0.4s，volume=1.7，35KB
- 动物 8 个（dog/cat/cow/sheep/chicken/lion/bird/frog）：huydinutran/animal-sound 仓公开数据集
- 动物 4 个补齐：
  - `duck` → Freesound #242664「quack」by reitanna, CC0, 0.6s
  - `pig`  → Freesound #442906「Pig Oink」by qubodup, CC0, 0.74s
  - `tiger` → Freesound #496131「Tiger Growl」by peenois, CC0, 裁首吼 1.85s (volume=1.4)
  - `horse` → Freesound #149024「Horse_Whinny」by foxen10, CC0, 裁首嘶 3.0s (volume=1.3)
  - ⚠️ 跳过 #327842/heisz50：实为 CC BY-NC 3.0（非商用），不符合上架要求；CC0 替换
- 全员经 ffmpeg 转 mono/22050Hz/16-bit PCM；`AnimalSynth.RAW_SOUNDS` 静态 map 引用 `R.raw.animal_*`

### 📳 振动按摩放大 + 扩模式
- 模式 10→15（新增颤动 flutter/呼吸 breath/层叠 cascade/短促 burst/深沉 deep）
- 放大三招：AMP_LOW 215→230（"轻柔"档也明显可感）；`scaledOn()` 把现有 10 模式 ON ×1.3、OFF ×0.8（占空比↑）；ON_BOOST 1.7→2.4（对 `hasAmplitudeControl()==false` 设备直接拉长 ON 段补偿）
- FlowRow 自适应换行 + `selectedModes: Set<String>` 多选组合拼接
- VIBRATE 普通权限已在 manifest 声明；硬件诊断「API=$api，振动器=$ok」紧跟 SimHintCard

### 🎨 UI 打磨
- **涂鸦画板**：笔刷调节器加 56dp 实时预览圆（surface 底 + outlineVariant 1dp 边框 + 1dp 阴影），`drawCircle(drawColor, brushSize/2.coerceAtLeast(1.5f))` 实时反映；3 按钮独立一行（撤销 2 / 撤销 / 清空），按笔画数 `enabled`（strokes.size>=2 等）
- **敲木鱼**：计数「4 发」水平单行 + 木锤动画 + +1 淡出 + 真实录音
- **玻璃杯**：7 杯水平排列不同水位（fill=0.85-(i/6)*0.70，杯 0 最满→最低音，杯 6 最浅→最高音）+ 标准音符 C5..B5
- **图片编辑**：参数滑块统一改竖向 + 长度减半（与其他工具对齐）

### 🆕 新工具
- **电子琴（PianoScreen）**：拟真钢琴单音 + 横竖屏切换（屏幕布局调整）

### 🔧 维护 / 修复
- 修复 release lint 全部告警：AutoboxingStateCreation（mutableStateOf<int>） + TypographyDashes
- 图标前置 / Tab 布局打磨：图片编辑 4 类工具参数滑块统一竖向
- 涂鸦/弹珠台/烟花/剪刀石头布等工具「点后无反应」「节流太宽」类问题统一排查修复
- 工具箱 7 大类 55 → **53 个**（v2.12.0 期间清理了 2 个不再合适的工具位）

### 📚 文档同步
- `README.md`：版本基线 v2.9.0 → v2.12.0，工具数 55 → 53，综合成熟度 99.2 → 99.6
- `docs/release-notes.md`：新增本节（v2.12.0 封板记录）
- `docs/成熟度评估.md`：v2.12.0 封板综合成熟度评估（详见该文档）

### 成熟度
- 综合 **99.6 / 100**（产品设计 99 / 软件开发 99 / 质量测试 99 / 应用配置 99；本轮拟物重画 + 真实素材接入 + lint 零警告 + 三道门禁稳定全绿）
- 三道门禁（`compileDebugKotlin` / `lintRelease` / `scan_strings.mjs`）+ 单元测试（`testDebugUnitTest`）全绿
- 默认不 push：领先 origin/master 37 个 commit（v2.12.0 + [config] bump），等待用户授权推送

---

## v2.9.0（2026-08-06 · 封板）· 五大 Tab 导航改版 + 工具箱 55 工具 + 全维度质量加固

**平台**：Android 8.0+（minSdk 26 / targetSdk 34）· 手机 + 平板
**版本**：versionCode 17 / versionName 2.9.0 · DB schema v10（本次无 schema 变更）
**相对 v2.8.0**：导航形态改版 + 工具箱扩充 + 一轮覆盖「产品设计 / 软件开发 / 质量测试 / 应用配置」四维的全量审查与修复。零新增远程依赖，DB 版本不变。

### 🧭 导航改版：六大 Tab → 五大 Tab
- 「任务」与「习惯」合并为**「计划」Tab 的两个子页**（新增 `ui/plan/PlanScreen.kt` 作容器，内部复用既有 `TasksContent` / `HabitsContent`，两者 ViewModel 与业务逻辑零改动）。
- 一级导航由 6 收敛为 5：**今天 / 计划 / 助手 / 工具 / 设置**。统计详情不占一级位，仍由今日进度环进入 `Routes.STATS`。
- 收益：底栏单项宽度增加，5 项在小屏不再挤压文字；「任务」与「习惯」同属"要坚持的事"，合并后信息架构更自洽。

### 🧰 工具箱扩充至 7 大类 55 个工具
- 分类：健康提醒 6 / 效率与安全 5 / 生活助手 5 / **模拟解压 14** / 实用工具 18 / 健康自查 2 / 效率工具 5。
- 全部离线可用；模拟解压系列的音效由 `FoleySynth` 本地合成（`AudioTrack` MODE_STATIC + 振动反馈），不含任何音频资源文件。
- 注册表（`TOOL_CATEGORIES`）/ 路由常量（`Routes.TOOLS_*`）/ 导航图（`TickClearNavGraph`）三方数量已对账一致（55/55/55）。

### 🐛 P0 修复：应用内主题与系统状态栏/导航栏失联
- **问题**：框架窗口主题走资源限定符跟随**系统**深色，而 Compose 主题跟随**应用内** `ThemeMode` 设置。二者无联动，当「应用内深色 + 系统浅色」时，状态栏/导航栏仍是浅色底 + 深色图标，与深色内容之间出现明显色带割裂；反向组合则出现浅底浅图标近乎不可见。
- **修复**（`ui/theme/TickClearTheme.kt`）：新增 `Context.findActivity()` 递归解包，在 `SideEffect` 中按实际生效的 `dark` 值同步 `window.statusBarColor` / `navigationBarColor` 与 `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars/NavigationBars`。不启用 edge-to-edge，对现有布局零影响。

### 🔒 P1 修复：设备身份并发生成漂移
- **问题**（`data/repositories/SettingsRepository.kt`）：`xzClientId` / `xzSerialNumber` 在 `Flow.map` 变换内做「读不到就生成并写回」，副作用写在流变换里 —— 多个 collector 并发订阅时会各自生成不同 UUID，先后写入互相覆盖，导致小智设备身份漂移、服务端侧被判为不同设备。
- **修复**：抽出 `Mutex` 保护的 `suspend ensureIdentity(key)`，锁内先 `first()` 二次检查再生成写入；写失败经 `AppLogger.e` 记录而非静默。

### 🧠 P1 修复：位图缩放未回收原图（Native 内存峰值翻倍）
- `domain/tools/ImageProcessor.downscale` 与 `domain/tools/ImageMasker.downscaleIfNeeded` 在 `createScaledBitmap` 后未释放原图。大图（如 4000×3000）连开多个图片工具时 Native 堆峰值翻倍，低端机易 OOM。
- 修复：返回前 `if (out !== src) src.recycle()`。

### ⚡ P1 修复：首页折叠失效与列表重组开销
- **今日页已完成区折叠失效**：`LaunchedEffect(Unit)` 只在首帧跑一次，而首帧数据为空 → 自动折叠判断恒不成立。改为以 `doneItems.size` 为 key，并引入 `userToggledDone` 标志，用户手动展开/折叠后不再被自动逻辑覆写（`rememberSaveable` 跨旋转保持）。
- **任务列表重算**：原实现对每个任务组各跑一次 `filter`，复杂度 O(组数 × 任务数) 且每次重组重跑。改为 `remember(tasks, groups)` 内单次 `groupBy`。
- **摄像头检测事件列表**：`SimpleDateFormat` 与 `reversed()` 从列表项内提到 `remember`，避免每项每帧新建 formatter 与整表复制。
- **抽签器列表**：`itemsIndexed` 补稳定 `key`，避免增删项时整列重组。

### 📦 应用配置与门禁
- **包体瘦身**：`resourceConfigurations += ["zh-rCN", "en"]` 裁剪三方库多余语言资源；移除 `vectorDrawables.useSupportLibrary`（minSdk 26 原生支持矢量图，该开关只会让 AGP 额外生成 PNG 回退）。
- **门禁脚本接入 CI**（`.github/workflows/ci.yml`）：`test/check_migrations.py`（Room 迁移与 schema 一致性）与 `test/scan_strings.mjs`（strings.xml 引用完整性）在 `assembleDebug` 之前执行；instrumented-test job 增加 `needs: unit-test`，避免单测未过就白跑仪器化。
- **修复门禁哑弹**：`scan_strings.mjs` 判定动态引用时用 `DYNAMIC_PREFIXES.includes(n)` 做精确匹配（`DYNAMIC_PREFIXES` 存的是前缀），导致动态前缀白名单从未生效。改为 `some(p => n.startsWith(p))`。

### 🧪 测试加固
- **消除假绿测试**：`ReminderIdsTest` 用 Kotlin `assert()` 断言 —— JVM 默认 `-da`，整段断言被跳过，测试永远通过。改为 `assertTrue`。`ImageMaskerTest` 用 `runCatching{}.isFailure` 断言异常，任何异常（含 NPE）都算通过。改为 `assertThrows(IllegalArgumentException::class.java)`。
- **补零覆盖的关键路径**：
  - `VaultCryptoTest`（11 例）：密码保险箱 PBKDF2 + AES-256-GCM 的加解密往返、错误口令必须抛 `AEADBadTagException`（不得静默返回乱码）、密文篡改检测、IV 每次随机（GCM IV 复用可恢复明文）、salt 生效性、密钥长度、答案慢哈希确定性。
  - `HabitDatesTest`（17 例）：`computeStreak` 的两个易错语义（今天未打但昨天打了 → streak 保留；今昨皆未打 → 归零）、中间断档只计最近段、乱序/重复/非法日期容错，以及 `isHabitDueOn` 的 ISO 星期口径（周日 = 7 而非 0）。

### 📚 文档同步
- `AGENT.md`：§1/§2/§5 由「六大 Tab」更正为「五大 Tab」并补 `plan/` `tools/` 包职责；§3 新增 6 条编码红线（主题系统栏联动 / Bitmap 回收 / Compose 重组 / LaunchedEffect key / DataStore 惰性生成 / 单测断言）；§6 补静态门禁命令。
- `README.md`：Tab 表格化、工具数 29 → 55、项目结构补齐、Room 版本 1→8 更正为 1→10、快速开始补门禁脚本。
- `docs/成熟度评估.md`：新增 v2.9.0 审查与修复记录章节。

---

## v2.8.0（2026-08-02 · 封板）· 消息净化 + 语音 Opus 根因修复 + 协议实证补漏 + 工程严谨性收敛

**平台**：Android 8.0+（minSdk 26 / targetSdk 34）· 手机 + 平板
**版本**：versionCode 16 / versionName 2.8.0 · DB schema v8（无 schema 变更）
**相对 v2.7.2**：P0/P1 修复（消息净化 / 语音诊断日志 / 协议实证补漏 / 语音 Opus 编码根因修复 / 助手连接生命周期 / 工程严谨性收敛 / 文档同步）。**唯一红线例外**：Opus 编解码引入本地 AAR `app/libs/opus.aar`（theeasiestway/android-opus-codec，含官方 libopus 1.3.1，全 ABI 含 arm64-v8a），以本地文件随仓库分发、不新增远程仓库与坐标解析（见下）；其余仍零新功能、中文全抽离、DB 版本不变（v8）。

### 🧹 消息列表净化（P0 · 用户可见）
- **剥离多模态资源 token**（@image#xxx）：小智官方服务端在 LLM/TTS 文本中插入形如 `@image#1:23e150c406a50b91e200450bf3d94b31.jpg` 的多模态资源引用（用于 TTS 朗读时插入图片/表情包合成播放），TTS 音频会念"图片"，但**给 UI 展示用的 text 字段是带 token 的原文**——直接显示给用户会出现纯技术串。修复：
  - 新增 `MessageTextFilter.strip(s)` 纯 Kotlin 单例，匹配 `@image#\d+:[hex]{8,128}\.(jpg|jpeg|png|gif|webp)`，整句被过滤为空则不 emit（避免空气泡）。
  - **源头过滤**（`WebSocketXiaozhiTransport.handleServerMessage`）：`llm` 帧与 `tts` `sentence_start` 帧在 emit `LlmText` 前先 strip。
  - **UI 层防御**（`AssistantViewModel.onEvent` 的 `LlmText` 分支）：再 strip 一次，兜底 Mock / 未来其他来源。

### 🎙 语音上行诊断日志（P0 · 用户"语音不能用"排查）
- 之前"语音不能用"完全无定位线索（启动看起来都成功，但 7+ 秒内 0 帧上行）。逐盲区补全链路日志，不改行为：
  - `WebSocketXiaozhiTransport.sendAudio`：v 级发送日志 + send 失败 e 级错误日志 + `ws=null` w 级丢弃日志。
  - `AudioCapture` 采集线程：启动 d 级"start OK"；`onFrame` v 级触发日志；read 异常**不再静默 break**（旧实现一遇异常就退出线程，导致"无声无息失败"），改 v 级日志后继续循环；read 返回 0/负值不退出（设备预热正常现象）；线程退出时打印 `readCount / readErr / readZero / frameCount` 汇总。
  - `OpusCodec.encodeFrame`：关键失败路径 w 级（pcm 长度不足 / ensureEncoder 返回 null / dequeueInputBuffer 失败 / dequeueOutputBuffer 失败 / 整体异常）各自打日志；成功路径 v 级。
  - `AssistantViewModel.startXiaozhiOpusVoice` 的 onFrame 回调：v 级 pcm→opus→sendAudio 三段日志，便于对账。
- **本轮目标**：用户本地 `./gradlew assembleDebug` + 真机回归一次，复制 logcat `XzTransport / AudioCapture / OpusCodec / AssistantVM` 四个标签的输出给项目组，按日志区分"录音未起 / read 阻塞 / 编码失败 / send 失败"四类根因后，再二次定位并出方案 B（3s 自愈）/ 方案 C（60ms→3×20ms）。

### 🎙 Opus 编解码改用本地 AAR（theeasiestway）（P0 · 语音彻底不可用根因修复 · 2026-07-29 起，2026-08-01 定案）
- **根因（由诊断日志实证）**：本机及多数 Android 机型 `MediaCodec` 声称提供 Opus **编码器**，但同步模式下 `dequeueInputBuffer` **永久返回 -1**（输入缓冲区永远不可取用）。导致 `OpusCodec.encodeFrame` 每帧返回 null → **零字节上行** → 服务端收不到音频 → 无 STT/LLM/TTS → "麦克风亮着但说话没反应"。日志铁证：录音 155 帧零错误、编码 155 帧 100% 失败、全程 0 条 `sendAudio`、WS/MCP 握手正常（服务端健康）。
- **修复**：编码 + 解码路径整体替换为 **theeasiestway/android-opus-codec**（本地 AAR `app/libs/opus.aar`，JNI 包官方 libopus 1.3.1，`libopus.so / libeasyopus.so / libopusenc.so` **全 ABI 含 arm64-v8a**）。纯软件实现，各机型 100% 可用，与 xiaozhi 参考客户端（py-xiaozhi 等）一致。
  - 依赖落地波折（已全数解决）：① 误用 `org.concentus:concentus:2.0.1`——该坐标仅在已关停的 JCenter 发布，Maven Central / 阿里云镜像均拉不到；② JitPack `com.github.lostromb:concentus` 多模块、子模块未发布，解析失败；③ 一度改用 `com.github.martoreto:opuscodec:v1.2.1.2`，虽可解析，但其 `libsenz.so` **仅打了 32 位 ABI**，arm64-v8a 真机 `dlopen` 失败报 `libsenz.so not found`，语音仍不可用；④ **最终改为本地 AAR `app/libs/opus.aar`（theeasiestway）**，全 ABI 齐备、无需外部仓库解析。**红线注记：禁止回退 martoreto/opuscodec。**
  - `OpusCodec.encodeFrame`：PCM16 字节（1920B/帧）直接交给该库 `Opus.encode(pcm16, Constants.FrameSize._960())` → 返回原始 Opus 包（不含 Ogg 容器，直发 WebSocket 二进制帧）。编码器初始化 `encoderInit(SampleRate._16000(), Channels.mono(), Application.voip())` + `encoderSetBitrate(Bitrate.instance(24_000))`。
  - `OpusCodec.decodeFrame`：同样走该库 `Opus.decode(opus, Constants.FrameSize.fromValue(frameSamples))` → PCM16 字节（TTS 播放不再依赖设备 MediaCodec Opus 解码器）；解码器 `decoderInit(sampleRateFor(rate), Channels.mono())`。
  - `OpusCodec.isEncoderAvailable()/isDecoderAvailable()` 改为**以 libopus 原生库加载结果为准**（不再用 MediaCodec 探测，避免设备声称支持但实际不可用的误判）；加载失败打 e 级日志明确告知语音流不可用。
  - `app/build.gradle.kts`：以 `app/libs/opus.aar` 本地文件依赖引入，并在 `packaging` 保留 `libopus.so / libeasyopus.so / libopusenc.so` 的调试符号、**不再剔除 `arm64-v8a` 目录**；`proguard-rules.pro` 加 `-keep class com.theeasiestway.opus.** { *; }` + `-dontwarn`。**未新增任何远程仓库（无 JitPack）与依赖坐标。**
- **红线影响**：红线①「零新依赖」以**本地 AAR 形式做唯一例外**（远程坐标/仓库零新增；因 MediaCodec Opus 编码在多数机型不可用属 Android 碎片化硬伤，无法自研绕过）；②中文全抽离（仅 AppLogger 日志，不计入）③Room 显式 Migration（无 schema 变更）④`.workbuddy/` 不提交，均守住。

### 🔌 小智协议实证补漏（文档同步，非代码改动）
- **Device-Id 大小写敏感**：xiaozhi.me 控制台对 Device-Id(MAC) 做大小写敏感匹配。已绑定小写 MAC（如 `e8:06:90:98:6c:d4`）必须以小写发送，大写会被静默拒 → close code 1005。已通过独立探针实证。
- **listen 帧 state 必须 detect**：服务端仅当 listen `state="detect"` 且带 `text` 时才把文本当作用户输入处理；`state="stop"+text` 服务端静默忽略 → "测试连接正常但发消息无回复"。已用真机 MAC 实证完整 STT→LLM→TTS→MCP 工具调用链路。
- **握手 25s 超时**：服务端 connection.py 源码显示收到 hello 后会异步调 `get_private_config_from_api` 校验设备 + `_initialize_components` 加载 TTS/ASR（默认 FunASR 加载数秒），给服务端留足初始化时间。原 10/12s 容易误判"被拒"。
- **MCP 协议 25s 死锁**：服务端 `method=initialize` 必须回（已修），`tools/list`/`tools/call` 的 `result.tools`/`result.content` 必须是 JSON 数组（已修），`required`/`enum` 必须是字符串数组（已修）。

### 🛡 工程代码审计修复（2026-07-29 · 红线 + 可靠性）
> 全量静态扫描（独立只读审查子代理 + 人工逐文件核实）发现的真实隐患，全部零新依赖、中文全抽离、DB 版本不变（v8）。

- **[fix] `ReminderReceiver` 共享 scope 被取消 → 后续提醒静默丢失（P1 · 真实功能缺陷）**：原实现把 `CoroutineScope` 存为实例字段，并在 `finally` 中 `scope.cancel()`。系统会复用同一 receiver 实例投递多条提醒，首条完成即取消共享 scope，后续 `onReceive` 在已取消的 scope 上 `launch` 永不执行，`goAsync()` 的 `PendingResult` 也永不 `finish()` → **该提醒被静默丢弃且 PendingResult 泄露（系统最长等待 10s 后杀进程）**。修复：每次 `onReceive` 使用独立局部 `CoroutineScope`，`finally` 仅取消本次局部作用域。
- **[fix] `AudioCapture.startAccumulate` AudioRecord 泄露（P2 · 资源泄漏）**：累积采集线程 `finally` 仅回调 `onComplete(pcmFile)`，未 `release()` 字段 `record`；若调用方未在 `onComplete` 后显式 `stop()`，AudioRecord 永久泄露至进程死亡。修复：`finally` 内补 `running=false → record?.release() → record=null`（与 `stop()` 清理一致）。
- **[fix] `WebSocketXiaozhiTransport` 文本帧未校验 connected（P3 · 资源触碰窗口）**：`onMessage(text)` 不像 `onMessage(bytes)` 那样先 `if(!connected) return`，`disconnect()` 释放资源与置 `connected=false` 之间存在极短窗口，文本帧可能触达已释放的 codec/player。修复：文本分支补 `connected` 守卫（`runCatching` 兜底保留）。
- **[fix] `XiaozhiConnectionTester` 诊断文案硬编码中文（红线 · 封板前必修，已结清）**：`test()` 的用户可见失败原因（网络不可达 / 1005 license 不同步 / 1006 异常 / 超时根因等十余处）原硬编码中文。修复：`test()` 新增 `context: Context` 参数（调用方 `SettingsViewModel.testXiaozhiConnection` 传 `appContext`），全部诊断文案抽离至 `strings.xml`（新增 `xz_test_*` 12 条，沿用 `%1$s/%1$d` 占位符约定）；`AppLogger` 开发日志不涉及此红线。

### 🩹 助手体验与稳定性修复（2026-07-30 ~ 08-01）
- **[fix] 助手发消息必闪退真根因：`LazyColumn` item key 撞号（P0 · 必现崩溃）**：`voice_history` 落库消息 id 从 1 起自增，而内存临时消息 id 也用 `++seq` 从 1 起，重启后发第一条消息即出现重复 key → `IllegalArgumentException: Key "1" was already used`。该异常抛在 **Compose 组合/测量阶段**，ViewModel 侧任何 `runCatching` 都拦不住。修复：**内存临时 ID 递减取负**（`nextId() = --seq`，DB 主键恒正，天然分区），落库后回填真实主键（回填前查重），渲染前再 `distinctBy { it.id }` 兜底。
- **[fix] 助手协程未预期异常闪退（P0）**：`viewModelScope.launch` 内调用会落库/排程的 UseCase（`mcpTools.commit` / `addTaskUseCase` / `applyOfflineCommand`）此前无兜底，`taskRepository.upsert` 抛异常会冒泡出协程直接闪退；`transport.events.collect` 也未逐条兜底，单条事件异常拖垮整个 collect。修复：全部包 `runCatching`。
- **[fix] `setAlarmClock` 缺精确闹钟权限导致 SecurityException 闪崩（P0）**：勘误此前"`setAlarmClock` 免权限"的错误结论——自 Android 12(S) 起它与 `setExact*` **同样需要**精确闹钟权限，Android 14 新装默认拒绝 `SCHEDULE_EXACT_ALARM`。修复：Manifest 组合声明 `SCHEDULE_EXACT_ALARM`(`maxSdkVersion=32`) + `USE_EXACT_ALARM`(33+ 安装即授予)；`setExact` 三级降级 `setAlarmClock → setExactAndAllowWhileIdle → setAndAllowWhileIdle`，每级 `runCatching` 兜底绝不抛给调用方。
- **[fix] 切 Tab 卡顿：助手连接生命周期下沉到应用前后台**：原 `DisposableEffect { onDispose { disconnect() } }` 导致每次切 Tab 都断连重握手。改为连接/传输层生命周期跟随应用前后台（`ActivityLifecycleCallbacks`，因零新依赖红线不可用 `ProcessLifecycleOwner`），切离助手页仅停麦/停唤醒词、**保留 WebSocket**。
- **[feature] 助手消息左滑删除 + 长按动作卡**：微信风底部动作卡（主题色、尺寸减半、锚定选中行下方），多选模式补齐复制/删除底栏；调试页运行日志支持 `SelectionContainer` 长按选区 + 一键复制全部。
- **[feature] 轻提示统一 3 秒自动淡出**，替换 Material3 固定 4 秒 Short 时长。
- **[fix] 开屏清扫机器人动画**做减法并放慢，修正 `withTransform` 内 `drawCircle` 未指定 `center = Offset.Zero` 导致的圆盘绘制错位。
- **[fix] 关于页版本号写死** → 改读 `BuildConfig`；**AI 引擎 chip 状态错位**；**种子数据中文红线**（抽离 `strings.xml`）。

### 🧱 工程严谨性收敛与封板配置（2026-08-02）
- **[config] 清理死依赖与失效配置，补齐设备兼容声明与 CI 构建门禁**，封板 versionCode 16 / versionName 2.8.0。
- **[fix] 迁移建表语句去除多余 `DEFAULT`，对齐导出 schema**：`MIGRATION_5_6`(voice_history.kind)、`MIGRATION_7_8`(habit 全部字段 / habit_checkin.checkedAt) 的 `DEFAULT` 与 Room 导出 schema 不一致。虽然 Room 2.6.1 的 `TableInfo` 比较**不含 `defaultValue`**、不会导致升级崩溃，仍作为严谨性收敛统一。`ALTER TABLE task ADD COLUMN tags TEXT NOT NULL DEFAULT ''` 保留（NOT NULL 加列语法必需）。
- **[refactor] 连续天数算法单一事实来源**：`StreakUtils.computeStreak` 与 `HabitDates.computeStreak` 曾是两份等价实现。改为 `StreakUtils` 委托 `HabitDates`（DST 安全的 `java.time.LocalDate` 实现），调用方入口不变、行为不变，消除算法漂移风险。

### 📖 文档同步
- `release-notes.md`：本章节（含 v2.8.0 封板信息、Opus 依赖描述更正为本地 AAR）。
- `AGENT.md`：底部 Tab 由 5 改为 6（补「习惯」）、`ui/habits/` 包补录、新增习惯 Tab 行为契约、封板引用 v2.7.1→v2.8.0、Opus 已知风险条更正为本地 AAR、零新依赖红线补注唯一本地 AAR 例外。
- `docs/成熟度评估.md`：标题 v2.8.0、迁移链 1→8、综合评分 98.5→**99.0/100**、构建行 versionCode 16 / versionName 2.8.0、新增 §13「2026-08-02 v2.8.0 封板收口」。
- `test/XIAOZHI_DIAGNOSTIC_README.md`：新增「image token 过滤」段、「语音上行诊断」段；「已知雷点」表加 listen state=detect / Device-Id 必须小写 / 25s 超时说明。
- `docs/用户配置手册.md`：新增「语音不能用排查步骤」——看 logcat `XzTransport/AudioCapture/OpusCodec/AssistantVM` 四个标签。
- `docs/语音助手实现说明.md`：在差异点处标注"REAL 模式语音上行日志路径"。
- `.workbuddy/memory/MEMORY.md`：本次根因 + 方案 + 关键代码位置（`MessageTextFilter` / `WebSocketXiaozhiTransport.sendAudio` / `AudioCapture.start` / `OpusCodec.encodeFrame`）。

### 🧪 质量门禁
- 待本地执行：`./gradlew :app:assembleDebug :app:lintRelease :app:testDebugUnitTest`（沙箱无 JDK/Gradle）。
- 真机回归：① 消息列表不再出现 `@image#xxx`（带真实带 token 的 LLM 回复验证）② 语音启动后 logcat `AudioCapture` 标签出现"start OK ... frameCount=N" ③ **`OpusCodec` 标签不再有 `dequeueInputBuffer 返回 -1`，改为 `→ encodeFrame OK 输出 N B`**（arm64 真机需确认无 `libsenz.so not found`；若出现说明装的是旧 martoreto 版 APK，先 `adb uninstall` 再重装）④ `XzTransport` 标签出现 `→ sendAudio Opus 帧 len=...` ⑤ 端到端语音对话可正常 stt→llm→tts（带真实 e8:06 真机或控制台已绑定设备 MAC）⑥ **冷启动后助手页立即发第一条消息不闪退**（LazyColumn key 撞号回归）⑦ Android 14 新装未授精确闹钟权限时新建带提醒任务不闪退、提醒仍能到达（降级路径）⑧ 助手页与其他 Tab 反复切换不再出现重握手卡顿。
- **红线校验**：`XiaozhiConnectionTester` 诊断文案硬编码中文已在本轮抽离 `strings.xml`（见上「工程代码审计修复」），源码零可见中文；其余 `AppLogger` 日志中文为开发者可见、不计入红线。

---

## v2.7.2（2026-07-27）· 小智连接/语音修复 + 十一处 UI 打磨（零新功能）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 15 / versionName 2.7.2
**相对 v2.7.1**：十一处 UI 修复 + 小智 REAL 连接/语音深度修复，零新功能、零新依赖、中文全抽离、DB 版本不变（v8）。

### 🔌 小智 REAL 连接 / 语音（深度修复，根因级）
- **WS 端点纠正（P0 · 一直「未连接」主因）**：三处默认 WS 地址由错误主机 `wss://api.xiaozhi.me/ws` 改为官方真实域名 `wss://api.tenclass.net/xiaozhi/v1/`（`api.xiaozhi.me` 仅为官网管理后台，非 WS 主机）。`WebSocketXiaozhiTransport` / `SettingsRepository` / `SettingsViewModel` 默认值同步修正，并在 `openSocket()` 内对已存 DataStore 的遗留错误主机做归一化纠正。
- **握手顺序纠正（P0 · 连不上的次因）**：原实现错误地「等待服务端先发 hello」，但权威协议（xiaozhi.me 文档 / ESP32 固件 / py-xiaozhi 三方一致）要求**客户端连接建立后先发 hello**。修正为 `onOpen` 立即发送客户端 hello（携带 `version`/`transport`/`audio_params`/`prompt`/`device_id`/`client_id`/`token`），服务端回 hello 后 `handshakeDone=true` 并广播 `Connected`。
- **TTS 回复播放/展示（P0 · 连上却看不到回复）**：原传输层只处理 `hello/stt/llm/mcp`，漏掉服务端主回复通道 `tts`（`sentence_start` 携带文本 + 二进制 Opus 帧）。新增 `tts` 处理：`sentence_start` 文本 → `LlmText`（界面展示），`start` 承载采样率初始化播放器，`stop` 释放；二进制帧按服务端声明采样率解码播放。
- **握手超时守卫（P1 · 之前「无日志无报错」）**：设备未在 xiaozhi.me 控制台绑定时，服务端不回 hello，原逻辑无限「连接中」。新增 10s 超时，超时经 `XiaozhiEvent.Error` 明确提示「请确认设备已在控制台绑定 / 检查网络」，便于排查。
- **采样率自适应（P2）**：服务端 `hello`/`tts-start` 的 `audio_params.sample_rate` 现驱动 `OpusCodec.preferredDecodeRate` 与 `AudioPlayer` 重建，避免 16k/24k 错配导致的无声或变调。
- **语音链路打通（P1 · 麦克风无反应）**：`voiceSupported` 门控由「仅系统识别器可用」扩展为「小智 REAL 且（系统 ASR ‖ Opus 编码器 ‖ 云 ASR 就绪）」；无系统识别器但有 Opus 编码器的设备走真·语音流（mic PCM→Opus 二进制帧→服务端 ASR）。`stopVoice()` 清理嵌套逻辑。
- **回复去重（P3）**：部分服务端在 `llm` 与 `tts.sentence_start` 各下发一遍相同文本，新增末条同角色同文本连续去重，避免两条一模一样的助手消息。
- 激活设备 OTA 返回 `websocket.url` 时自动填入并持久化 endpoint（免手填出错）。

### 🎨 UI / 视觉（用户十一处反馈）
1. **今日列表**：去掉首个任务默认 focus 边框（`focusedIndex` 初始 `-1` + `drawFocus` 门控）；任务行隔行浅底色交替（`surfaceContainer`/`surface`）。
2. **顶栏标题垂直居中**：今日/统计/习惯/设置/任务/助手/关于/调试 八处 `TopAppBar` 统一 `Box(fillMaxHeight)+CenterStart` 包裹问候/时间/标题文字。
3. **助手输入框**：移除与 M3 内部 padding 冲突的固定 `height(36.dp)`（导致文字不可见），改 `weight(1f)` + 显式 `colors`（文字 `onSurface`、占位 `onSurfaceVariant`）。
4. **习惯新建 emoji**：内置 `HABIT_EMOJIS`（36 个）+ `FlowRow` 可点选网格。
5. **习惯重复星期**：由 `Row(FilterChip)` 改 `LazyRow` 横向滚动，解决显示不全/无法横滑。
6. **习惯卡片**：`Card heightIn(min=48dp)`，单行 `Row` 垂直居中（高度约减 1/3）。
7. **统计日趋势坐标**：DAILY 标签由 `"M/d"`（7 月每日开头都是 7，显示成「77777」）改为纯日号 `dayOfMonth`。
8. **勋章墙高度一致**：同行 cell 等高（移除 `IntrinsicSize` 依赖，未解锁 cell 进度区统一包入固定高度 `Box`，避免被进度条/文字顶高）。
9. **设置主题块间距**：主题/皮肤行加 `HorizontalDivider()` + 统一 `padding(vertical=3.dp)`，与其余 `SettingRow` 间距一致。
10. **子页面底栏高亮**：关于/调试/语音历史进入时「设置」tab 保持高亮，回收站→「任务」，助手配置→「助手」（选中逻辑由精确相等改为「精确相等或路由以该 tab 路由 + '/' 开头」）。

### 🧪 质量门禁
- 待本地执行：`./gradlew :app:assembleDebug :app:lintRelease :app:testDebugUnitTest`（沙箱无 JDK/Gradle）。本轮修复历经多轮 `compileDebugKotlin` 静态核对，重点补齐 `fillMaxHeight`/`IntrinsicSize`/`Row`/`Spacer` 等遗漏 import。
- 真机回归：① 隔行底色深浅 ② 关于/调试标题居中 + 底栏「设置」高亮 ③ 星期 chip 横滑 ④ 小智 REAL 保存后即「已连接」且走真实对话、点话筒可说→转文字发送 ⑤ 设备未绑定时 10s 内给出明确错误提示。

---

## v2.7.1（2026-07-26）· UI 紧凑化与统计崩溃防御（零新功能）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 14 / versionName 2.7.1
**相对 v2.7.0**：九项 UI/视觉与崩溃修复 + 复审增量三项收敛，零新功能、零新依赖、中文全抽离、DB 版本不变（v8）。

### 🎨 UI / 视觉（用户九项反馈）
- **应用图标重绘**：居中白色描边对勾（`M38,54 L49,65 L70,42`，strokeWidth 9），蓝底 `#2F6BFF` + 22dp 圆角（自适应图标 + legacy 矢量回退双路径）。
- **图标名称**：桌面应用名改为「点清」（`app_name`）。
- **底部导航紧凑化**：`NavigationBar` 高度 80dp → 60dp（约降 1/4），点击热区覆盖「图标 + 文字」整块。
- **顶栏统一降高**：全部 10 处 `TopAppBar` 统一 48dp（原 M3 默认 64dp）；今日页完成率环 44dp → 36dp、问候语 `titleLarge` → `titleMedium` 防低顶栏裁切。
- **FAB 缩小**：今日/习惯/任务三页新建按钮 56dp → 40dp 圆形（约减 1/3）。
- **今日列表柔化**：冲突横幅由 `errorContainer` 红改 `surfaceVariant`（图标保留 error tint 提示语义）；任务行纵向内边距收紧（行高约减 1/3）。
- **设置页外观区间距**：`SectionTitle`/`SettingRow` 上下间距与其余分区统一。

### 🛡 崩溃防御（统计链路，双层兜底）
- **加任务后点统计崩 / 今日点完成率环崩**：`GetStatsUseCase.invoke()` 整段 `runCatching`（失败发射空 `TaskStats` + `Log.e`）；`StatsViewModel` 三处 Flow 补 `.catch` 兜底（completions / uiState / trend）。统计页在底层异常时降级为空态而非崩溃；真实根因待 logcat 堆栈进一步定位。

### 🔍 复审增量（本轮整体代码审查收敛，1×P2 逻辑 + 1×P2 视觉 + 1×P3）
- **P2 · 习惯 streak DST 偏差（HabitDates）**：`toEpochDay` 原用 `Calendar` 毫秒 `/86_400_000` 计算，DST 切换日（23h/25h）会偏差 1 天误断连续打卡 → 改 `java.time.LocalDate.toEpochDay()`（工程已启用 desugaring，minSdk 24 可用；原注释「避免 java.time minSdk 限制」为错误前提）。
- **P2 · 今日页 48dp 顶栏双行标题裁切**：见上「顶栏统一降高」。
- **P3 · 统计实例生成静默失败**：内层 `ensureInstancesForDate` 的 `runCatching` 补 `onFailure Log.e`，失败可追溯。

### 🧪 质量门禁
- 待本地执行：`./gradlew :app:assembleDebug :app:lintRelease :app:testDebugUnitTest`（沙箱无 JDK）；红线守住（零新依赖 / 中文全抽离 / 显式 Migration 1→8 / `.workbuddy/` 不提交）。
- 待真机走查：60dp 底栏 label 是否裁切（M3 默认 80dp，如有裁切可调 item 内边距）、48dp 顶栏各页视觉。

---

## v2.7.0（2026-07-26）· 整体代码复审与健壮性修复（零新功能）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 13 / versionName 2.7.0
**相对 v2.6.1**：封板后整体代码复审（v2.6.2 开发态已并入本版本），结清 1×P0 + 2×P1 + 5×P2 隐患，无新功能、零新依赖、中文全抽离、未升 DB 版本（仍为 v8）。

### 🛠 代码复审修复（P0 / P1 / P2）
- **P0 · 真实小智连接崩溃 + 卡死（WebSocketXiaozhiTransport）**：`openSocket()` 原在 `connected=true` 之后才建连，端点非法（`Request.Builder().url()` 抛 `IllegalArgumentException`）或建连异常会冒泡出 `connect()` 协程导致崩溃，且异常后 `connected` 卡死使后续 `connect()` 永远早退。修复：URL 构建与建连整体 `try/catch`，成功才置 `connected=true`，失败复位状态并经新增 `XiaozhiEvent.Error(detail)` 回显「连接失败：…」，不再崩溃、可重试。
- **P1 · 麦克风采集泄漏（AudioCapture）**：`start()`/`startAccumulate()` 的 `rec.startRecording()` 原在 try 之外，部分设备抛异常会使 `record` 已赋值且 `running=true` 残留（AudioRecord 泄漏 + 调用方崩溃）。修复：包裹 `try/catch`，失败即释放并复位返回 `false`。
- **P1 · 位置提醒服务主线程阻塞（LocationReminderService）**：`onStartCommand`（主线程）原 `runBlocking(Dispatchers.IO)` 查询全量任务，数据量大时 ANR 风险。修复：改服务作用域 `CoroutineScope(SupervisorJob()+Dispatchers.IO)`，查询在 IO 线程执行、完成后再决定是否轮询/停止，并在 `onDestroy` 取消作用域。
- **P2 · 主题皮肤非空断言（Color.kt）**：`skinScheme`/`skinPreviewColor` 的 `SKIN_SEEDS[ThemeSkin.BLUE]!!` 改为 `SKIN_SEEDS.getValue(ThemeSkin.BLUE)`，消除 `!!`。
- **P2 · 习惯删除对话框空断言（HabitsScreen）**：`pendingDelete!!.habit.id` 改为 `pendingDelete ?: return@TextButton`，消除潜在 NPE。
- **P2 · 语音历史逐条读 DataStore（AssistantViewModel）**：`recordVoiceHistory` 原每条消息都读 `voiceHistoryEnabled.first()`（每会话最多 100 次）。修复：新增 `voiceHistoryOn` 缓存 StateFlow，`init` 中收集一次，避免重复读取。
- **P2 · 本地识别器回调滞留（LocalSpeechRecognizer）**：`stop()` 原仅 `destroy()` 识别器，未清 `onPartial/onFinal`，到 `destroy` 执行前短暂持有回调。修复：`stop()` 同步置空两个回调。
- **P2 · 唤醒服务空重启（WakeWordService）**：识别器不可用时 `startListening()` 内部 `stopSelf()`，但 `onStartCommand` 仍返回 `START_STICKY` 导致系统可能重建空服务；并补 `NotificationHelper.createChannels(this)` 兜底防 `startForeground` 渠道缺失崩溃。修复：`startListening()` 返回 `Boolean`，不可用路径返回 `START_NOT_STICKY`。

### 🔧 工程改进（构建工具链，零功能变化）
- **Hilt 注解处理 Kapt → KSP**：原 `kapt(libs.hilt.compiler)` 在 Kotlin 2.0 下触发 `Kapt currently doesn't support language version 2.0+. Falling back to 1.9.2` 告警并以 1.9.2 回退处理。Hilt `2.51.1` 已支持 KSP，且 Room 早已使用 KSP（`2.0.21-1.0.28`），故将 Hilt 改走 `ksp(libs.hilt.compiler)` / `kspAndroidTest(...)`，并移除 `kotlinKapt` 插件。告警消除、构建提速，零新依赖（KSP 插件本就存在）。

### 🔍 Lint 全量告警清理与 Opt-in 治理（release 版报告）
- **背景**：分析 `app/build/reports/lint-results-release.html`，真正启用的告警 15 条（非折叠区 69 条附加检查 / 41 条未启用检查）。按「可逆修复」与「带注释显式抑制」分级处理，未采用全盘 `lintOptions { disable }`，保留门禁价值。
- **Opt-in 治理**：`HabitsViewModel.kt` 第 46 行 `flatMapLatest` 触发 `ExperimentalCoroutinesApi`，在 `uiState` 声明上方加 `@OptIn(ExperimentalCoroutinesApi::class)`，消除告警。
- **可逆修复（改代码）**：
  - `ModifierParameter`（5 处）：`EmptyStateGuide`/`MedalWall`/`StatsContent`/`TodayMainContent` 将 `modifier` 重排为首个可选参数（调用点均具名传参，无破坏）；`widget_today.xml` 根 `LinearLayout` 加 `tools:ignore="Overdraw"`（App Widget 根背景与主题背景固有重叠）。
  - `AutoboxingStateCreation`（1 处）：`FullScreenAlertActivity.kt` 的 `mutableStateOf(Int)` 改 `mutableIntStateOf`，消除 `Int` 自动装箱。
  - `ContentDescription`（1 处）：`widget_today_item.xml` 装饰性勾选 `ImageView` 加 `android:contentDescription="@null"`，修复无障碍声明。
  - `GradleDependency`（2 处）：`securityCrypto` alpha `1.1.0-alpha06` → 稳定版 `1.1.0`；`testImplementation("org.json:json:20231013")` 移入 `libs.versions.toml`（`orgJson`），消除 `UseTomlInstead`。
- **带注释显式抑制（保正确性）**：
  - `ApplySharedPref`（`SecureStore.getDbPassphrase`）：保留 `commit()` 同步落盘避免首启「建库读口令 / 写口令」竞态丢库，加 `@SuppressLint("ApplySharedPref")`。
  - `InlinedApi`（3 处）：`SettingsScreen`（API 26 通知设置常量，已 `SDK_INT >= O` 守卫）、`TaskEditSheet`（API 29 后台定位权限，accompanist 内部按 SDK 守卫）加 `@SuppressLint("InlinedApi")`。
  - `ReportShortcutUsage`（`ShortcutHelper.register`）：动态快捷方式无需 usage 上报，加 `@SuppressLint("ReportShortcutUsage")`。
  - `OldTargetApi`（lint 配置）：锁定 `targetSdk 34` 不升 35 以免行为变更，在 `lint {}` 块 `disable += "OldTargetApi"`（AGP 8.x Kotlin DSL 中 `disable` 是 `MutableSet<String>` 属性，须用 `+=` 而非 Groovy 方法式 `disable("id")`，否则脚本编译期 Unresolved reference），仅屏蔽该检查不卡构建。
  - `security-crypto` 弃用告警（带注释抑制，受零依赖红线约束）：`androidx.security:security-crypto` 自 `1.1.0-alpha07` 起将 `EncryptedSharedPreferences`/`MasterKey` 整体标记 `@Deprecated`（官方推荐迁移到 Jetpack DataStore + Google Tink）。为清 `GradleDependency` 告警已将依赖升到稳定版 `1.1.0`，但稳定版同样带弃用标记，于 `SecureStore.kt` 文件级 `@file:Suppress("DEPRECATION")`。不迁 DataStore+Tink 的原因：会引入 Tink/protobuf 等新依赖，破「零新依赖」红线，且无功能等价、零依赖的替代实现；该 API 在 minSdk 24 上仍完全可用、行为正确。后续若放宽红线再评估迁移。

### 🧪 质量门禁
- `./gradlew :app:assembleDebug :app:lintRelease :app:testDebugUnitTest`（+ `assembleDebugAndroidTest` 编译）全绿；红线守住（零新依赖 / 中文全抽离 `strings.xml` / Room 显式 Migration 1→8 无 `fallbackToDestructiveMigration` / `.workbuddy/` 不提交）。

---

## v2.6.1（2026-07-26）· 产品规划功能缺口五项落地（标签 + 皮肤 + 习惯 + 迁移 + 模板）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 11 / versionName 2.6.1
**相对 v2.6.0**：落地 `docs/成熟度评估.md` 二节产品规划功能缺口 V2.67–V2.71 五项用户可见功能，V2.72–V2.75 评估落档显式保留，零新依赖。

### ✨ 新功能（V2.67–V2.71）
- **任务标签 / 分类（V2.67）**：任务可打多个标签（编辑表单输入 + 已有标签快捷 chips），任务列表顶部标签筛选条多选并集过滤，任务行显示 `#标签`。`task` 表新增 `tags` CSV 列（Room v6→7 显式 `MIGRATION_6_7`，schema `7.json`）；备份序列化携带 `tags`（向后兼容旧备份）。
- **更多主题皮肤（V2.68）**：新增六套配色皮肤（清蓝/薄荷/紫罗兰/落日橙/玫瑰/青碧），与浅色/深色/动态取色模式**正交**——最终配色 = 模式 × 皮肤。种子色经 HSL 派生明/暗 `ColorScheme`（零依赖实现），默认清蓝完全复用原配色零回归；设置页皮肤选择行带主色圆点预览，关于页回显「模式 · 皮肤」。
- **习惯养成模式（V2.69）**：新增「习惯」顶级页——创建周期性习惯（名称/emoji/星期重复），每日打卡/取消，卡片展示**连续打卡 streak**（今天未打卡时从昨天起算不断链）、休息日标识。`habit` + `habit_checkin` 表（Room v7→8 显式 `MIGRATION_7_8`，schema `8.json`），streak 纯函数 Calendar 实现兼容 minSdk 24。
- **设备间本地迁移（V2.70）**：设置页「导出/导入备份」（SAF JSON，无云）补全 V2.69 习惯数据——`BackupManager` 导出/导入 `habits`/`habitCheckIns`，事务内按主键合并；**兼容旧备份**（缺失数组按空处理，不升备份 SCHEMA_VERSION）；导入回执含习惯计数，副标题明确跨设备迁移用途。
- **分享任务组模板（V2.71）**：设置页新增「分享任务组模板」——选择任务组后经 SAF 导出 `type=groupTemplate` 的 JSON（单组+其任务，`group_<名称>_template.json`）；对方设备用现有「导入备份」即可按主键合并成组。

### 🧭 评估落档（V2.72–V2.75，显式保留）
- **V2.72 端侧识别 / V2.75 端侧 NLU**：需 ML 推理运行时 + 模型文件，破「零新依赖」红线（同 V2.27/V2.28 结论），红线放宽后重评；现有离线热词指令（V2.42）+ 常驻唤醒 spotting（V2.66）+ 本地规则 NLU 为轻量替代。
- **V2.73 真实 ASR SDK 联调 / V2.74 Real 模式连真实小智服务端**：代码侧已就绪（多服务商 ASR 协议 + WebSocket/Opus/Function Calling 框架），剩余为真机 + 用户密钥/服务端联调，随真机 QA 轮次（V2.44–V2.51）结转。

### 🧪 质量门禁
- `./gradlew :app:assembleDebug :app:lintRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest` 全绿；新增习惯备份导出→导入往返单测；Room schema `7.json`/`8.json` 导出提交；红线守住（零新依赖 / 中文全抽离 `strings.xml` / Room 显式 Migration 1→8 无 `fallbackToDestructiveMigration` / `.workbuddy/` 不提交）。

---

## v2.6.0（2026-07-25）· §13 开放问题决策落地（开源音效 + 语音历史 + 常驻唤醒）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 10 / versionName 2.6.0
**相对 v2.5.2**：落地产品设计文档 §13 四项开放问题决策，新增 3 项用户可见功能，零新依赖。

### ✨ 新功能（§13 决策）
- **开源提示音（Q2 / V2.63）**：内置自创作 CC0 双音提示音 `res/raw/notify_chime.wav`（880Hz→1320Hz），高优先级提醒改用内置开源音效（Android 8.0+ 由高优先级渠道 `USAGE_ALARM` 播放，8.0- 由通知 builder 指定），规避系统默认音的版权/一致性问题。音效开关关闭时仍仅震动+灯光。
- **语音对话历史（Q8 / V2.65）**：新增 `voice_history` 表（Room v5→v6 显式 `MIGRATION_5_6`）+ 仓储/DAO/设置开关/历史页。**默认关闭**；开启后助手的用户/助手对话文本落库，设置页可进入「查看语音历史」并一键清空。系统提示消息不记录。
- **常驻语音唤醒（Q9 / V2.66）**：新增前台服务 `WakeWordService`（`foregroundServiceType=microphone`），复用 `WakeWordManager`（系统 `SpeechRecognizer` 持续识别，零新依赖）做关键词 spotting，命中设置中的唤醒词（默认「小清」）即经 `WakeWordBus` 跳转助手页自动收音。设置开关默认关，开/关联动启停服务。

### 🧭 决策落档（不改代码）
- **位置提醒选型（Q3 / V2.64）**：维持系统定位（`FusedLocationProvider`/`GeofenceScheduler`，零 GMS、零新依赖）；高德地图 SDK 记为未来「选点/逆地理编码」选型，**v2.6 不引入 SDK**（守零新依赖红线）。

### ⚠️ 局限
- 常驻唤醒依赖系统识别服务、并非端侧神经网络模型，受厂商识别服务与联网状态影响、可能耗电；真正端侧离线唤醒词需 ML 运行时（破「零新依赖」红线，记为后续 V2.72/V2.75）。

### 🗺 v2.6 后续 backlog（未实现，见 `docs/成熟度评估.md`）
- V2.67 任务标签/分类 · V2.68 更多主题皮肤 · V2.69 习惯养成模式 · V2.70 设备间本地迁移 · V2.71 分享任务组模板 · V2.72/73/74/75 语音/ASR/NLU 真机与端侧模型（部分需破红线评估）

### 🧪 质量门禁
- `./gradlew :app:assembleDebug :app:lintRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest` 全绿；Room schema 6.json 已导出提交；红线守住（零新依赖 / 中文全抽离 `strings.xml` / Room 显式 Migration 1→6 无 `fallbackToDestructiveMigration` / `.workbuddy/` 不提交）。

---

## v2.5.2（2026-07-25）· 复查修复（全天/随时任务实例 + 迁移自包含 + 导入事务嵌套读 + WebSocket 资源）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 9 / versionName 2.5.2
**相对 v2.5.1**：封板后深度复查再修复 10 项（R1–R10，其中 3 项本轮「整体代码复查」新发现），无新功能、无功能回归、零新依赖。

### 🛠 复查修复（R1–R10）
- **全天/随时任务永不生成实例（R1，P1 真实功能缺陷）**：`TaskInstanceRepository.ensureInstancesForDate` 对全天/「随时任务」原不生成实例 → 今日消失、无提醒、无法完成。修复：列表为空时插入 `dueMinute=null` 实例（今日可见/可完成/入统计）。
- **统计页「未分组」硬编码（R2，P2 红线）**：改用哨兵 `UNGROUPED_GROUP_ID` + `strings.xml`，源码零可见中文。
- **冲突窗口口径不一致（R3，P3）**：`ScheduleUtils.effectiveEndMin()` NONE 分支与今日视图统一为 `scheduledEndMin ?: (start + 30)`。
- **OkHttp 响应未关闭（R4，P3）**：`OpenAiCompatibleLlmProvider` 改 `execute().use{}`。
- **备份导入事务原子性（R5，P3）**：`TransactionRunner` 抽象重做——生产 `RoomTransactionRunner` 走 `AppDatabase.withTransaction`，测试 `NoOpTransactionRunner`，中途异常整体回滚；零新依赖、保留 mock 仓储单测范式。
- **备份校验 KDoc 误导（R6，P3）**：`validateBackupJson` KDoc 与代码对齐。
- **SeedUseCase 死代码（R7，P3）**：删除计算后丢弃的 `LocalDate.now().format(...).let { null }`。
- **MIGRATION_4_5 缺建 taskId 单列索引（R8，P2）**：补 `CREATE INDEX IF NOT EXISTS index_task_instance_taskId`，迁移幂等且自包含（`AppDatabase` 暴露 `internal MIGRATIONS` 供仪器化测试引用）。
- **备份导入事务内嵌套读 + 组引用误置空（R9，P1）**：`validGroupIds`（备份内组 ∪ 现有活跃组）移到事务外计算，消除 SQLCipher 单连接池死锁/ANR，且保备份内组引用不被误清空。
- **小智 WebSocket 重连竞态与资源泄漏（R10，P2）**：`connect()` 重建作用域前取消旧作用域 + `openSocket` 守卫避免重复 socket；`disconnect()` 始终释放 `AudioTrack`/`OpusCodec`/作用域/WS（重连中离开也不再残留声音）。

### 🧪 仪器化契约测试（CI 模拟器通道）
- 新增 `app/src/androidTest/.../AppDatabaseMigrationAndTransactionTest.kt`：真实 SQLCipher+Room 库打开/DAO 往返、`RoomTransactionRunner` 提交/回滚、`task_instance` 三索引齐备；由 `ci.yml` `connectedDebugAndroidTest` 在模拟器通道执行（零新依赖，复用已声明 `androidx.room.testing`/`androidx.junit`/`sqlcipher`）。

### 🛠 质量与工程
- 全程守住红线：零新依赖、中文全抽离 `strings.xml`、Room 显式 Migration（1→5，无 `fallbackToDestructiveMigration`）、`.workbuddy/` 不提交、按功能拆 commit。
- `./gradlew :app:testDebugUnitTest :app:lintRelease` 全绿（0 error）；`assembleDebugAndroidTest` 编译通过（仪器化契约测试随 CI 实跑）。

### ⚠️ 已知限制 / 显式保留（同 v2.5.1）
- 真机指标评测（V2.44–V2.46）与全量冒烟 QA（V2.47–V2.51）仍待物理设备轮次，已整合进 `docs/测试与验收清单.md`。
- 其余平台约束（Android 14+ 全屏提醒降级、位置提醒 OEM 省电影响、系统 ASR best-effort、Opus 编码依赖设备能力）同前。

### 📦 构建
```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 正式包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintRelease            # 质量门禁
./gradlew :app:connectedDebugAndroidTest  # 仪器化测试（CI 模拟器通道）
```

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

## v2.5.1（2026-07-25）· 复查修复（子日级冲突 / 红线中文 / OpusCodec 释放）

**平台**：Android 7.0+（minSdk 24 / targetSdk 34）· 手机 + 平板
**版本标识**：versionCode 8 / versionName 2.5.1
**相对 v2.5.0**：封板后深度复查修复 3 项（V2.59–V2.61），无新功能、无功能回归、零新依赖。

### 🛠 复查修复（V2.59–V2.61）
- **子日级冲突逻辑修复（V2.59，P1 真实 bug）**：`GetTodayTasksUseCase` 原对「每 N 小时」等子日级重复任务的所有实例统一取单一锚点 `task.instanceDueMinute()`，导致多个实例（如 8:00 / 16:00）互相误判冲突，且与其它任务真实冲突漏判。`TodayItem` 新增 `dueMinute` 取实例自身 `inst.dueMinute ?: task.instanceDueMinute()`，排序与冲突窗口均改用实例分钟，冲突检测现在精确到每个子日级实例。
- **红线中文抽离（V2.60，P1/P2）**：抽离残留硬编码用户可见中文至 `strings.xml` —— `MockXiaozhiTransport`「每天 / 每周」、`XiaozhiMcpTools`「已创建任务 / 时间冲突备注 / 未知工具 / 新任务默认」、`DebugScreen` yesNo「是 / 否」；`MedalCatalog` 8 枚勋章名/描述改为 `@StringRes`（`nameRes` / `descRes`），`MedalWall` / `StatsScreen` 经 `stringResource()` 取文案，勋章名/描述实现国际化。
- **OpusCodec 资源释放（V2.61，P2）**：`@Singleton` 的 `OpusCodec.release()` 此前从未被调用，`WebSocketXiaozhiTransport.disconnect()` 新增 `runCatching { codec.release() }`，断开助手页即释放 `MediaCodec`（释放后惰性重建安全）。

### 🛠 质量与工程
- 全程守住红线：零新依赖、中文全抽离 `strings.xml`、Room 显式 Migration（1→5，无 `fallbackToDestructiveMigration`）、`.workbuddy/` 不提交、按功能拆 commit。
- 本轮回查修复前已通过构建门禁（`lintRelease` 0 error / `testDebugUnitTest` 全绿）。

### ⚠️ 已知限制 / 显式保留（结转）
- 真机指标评测（V2.44–V2.46）与全量冒烟 QA（V2.47–V2.51）仍待物理设备轮次，已整合进 `docs/测试与验收清单.md`（原 `docs/开发计划_v2.5_任务清单.md` 二、三节），相关逻辑已通过单元 + lint 门禁。
- 其余平台约束（Android 14+ 全屏提醒降级、位置提醒 OEM 省电影响、系统 ASR best-effort、Opus 编码依赖设备能力）同 v2.5.0。

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
- 四维成熟度评估 **98+/100**（产品 / 开发 / 测试 / 配置），详见 `docs/成熟度评估.md`

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

环境最小路径见 `docs/构建手册.md`（JDK 21 + 命令行 SDK，无需 Android Studio）。

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
- 封板前深度审计修复 9 类问题（子日级实例完成/跳过脱节、备份字段丢失、语音识别器泄漏、旋转丢对话框、除零守卫等），四维成熟度评估 98+/100（详见 `docs/成熟度评估.md` Phase 9）

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

环境最小路径见 `docs/构建手册.md`（JDK 21 + 命令行 SDK，无需 Android Studio）。

---

*未实现需求与遗留事项已全部归纳至 v2.0 计划：见 `docs/开发计划_v2.0_任务清单.md`。*
