# 小智连接排查 —— 本地诊断与 mock 测试

> **背景**：用户在 App 里点「测试连接」一直「N 秒内未收到服务端 hello 响应」，需要定位是网络/DNS/TLS/服务端认证中的哪一环。本目录两个脚本脱离 Android，纯 Python 跑，方便在你 PC 上直接复现。

## 0. 前置
- Python ≥ 3.9（项目沙箱 Python 3.13.12 已就绪）
- 第三方库：`websockets`（仅 mock 服务端需要）

```bash
pip install websockets
```

> **零依赖即可单跑 `xiaozhi_diagnostic.py`** —— 它是无第三方库的纯标准库实现（DNS/TCP/TLS/HTTP/WS frame 全自写）。`mock_xiaozhi_server.py` 才需要 `websockets`。

---

## 1. 先在本地跑通闭环：mock + 诊断
**目的**：先排除「我们 App 的客户端协议本身有没有错」。

```bash
# 终端 A：起 mock 服务端（严格校验官方 5 字段 hello）
python mock_xiaozhi_server.py --port 8765
```

```bash
# 终端 B：用同一个用户的 device-id/client-id/token 对本地 mock 做端到端测试
python xiaozhi_diagnostic.py --endpoint ws://127.0.0.1:8765/ \
    --device-id "B5:B3:66:15:CB:2E" \
    --client-id "90788698-b382-42b8-be36-724e923fcae7" \
    --token "test-token"
```

**期望结果**：12 行带时间戳的日志，逐阶段打印，每阶段 ✅ 即通过；最后应看到
`✅ Upgrade 成功 ... → 发 hello ... 🎉 握手完整链路通过`。
**如果这一步失败**：说明我们 App 的协议字段对不对 —— 是 bug，修客户端代码。

## 2. 验证官方服务端
**目的**：定位是真服务端问题（绑定/token/网络）还是客户端问题。

```bash
# 把端点切回官方
python xiaozhi_diagnostic.py \
    --endpoint wss://api.tenclass.net/xiaozhi/v1/ \
    --device-id "B5:B3:66:15:CB:2E" \
    --client-id "90788698-b382-42b8-be36-724e923fcae7" \
    --token "test-token"
```

**预期之一**：
- **DNS 解析失败 / TCP 不可达**：`❌ 解析失败` 或 `❌ TCP/SSL 失败`。**→ 你所在网络（手机/公司 WiFi/防火墙）拦了 api.tenclass.net**。挂 VPN 或换网。
- **HTTP 状态 401/403 / 4xx 响应体有 `device not activated` 或类似**：服务端识别了你的客户端，但**该 Device-Id 没在小智官方控制台绑定**。到 https://xiaozhi.me → 添加设备 → 输入 6 位验证码。
- **HTTP 状态 200/404/HTML（任意） + WS Upgrade 拿 101 + 12s 无 hello**：服务端接受连接但不回 hello。**→ 多半是 Device-Id 没绑定**（服务端绑定后才回 hello，否则连接挂着）。
- **WS Upgrade 返回 401/403**：服务端拒认证。**→ token 错或服务端版本要求额外 header**。

## 3. 配合 Android logcat 看 App 行为
```bash
# 干净重启 logcat 后再点 App 的「测试连接」
adb logcat -c
adb logcat | grep -E "XzTester|XzTransport|SettingsVM"
```
重点看：
- `SettingsVM` 打印的 `token=∅` ← **是否真的没填 token**！官方测试服要求 `Authorization: Bearer test-token`，空 token 会让服务端挂着不回 hello。
- `XzTester` 预探 `code=...` ← 网络是否通。
- `XzTester` `onOpen` 是否打出来 ← WS Upgrade 是否成功。
- `XzTester` `测试超时 Ns` 之前的最后一条 ← 失败位置。

## 4. 常见雷点
| 现象 | 真实原因 | 修法 |
|---|---|---|
| `设备已绑定` 仍连不上 | 「设备已绑定」只意味 OTA 端没有返回 activation.code，**不代表该 MAC 真的在控制台添加并验证** | 重「激活设备/获取验证码」→ 把新码去 xiaozhi.me 重新添加 |
| 8/12 秒超时 + `token=∅` | token 必填，官方测试服默认 `test-token` | 在「令牌」字段填 `test-token`，点保存后再点测试 |
| 网络 OK 但服务端 25 秒仍不回 welcome | 服务端收到 hello 后异步调 `get_private_config_from_api` 校验设备 + `_initialize_components` 加载 TTS/ASR（FunASR 加载数秒），完成才发 welcome | 确认 xiaozhi.me 控制台真添加了这个 MAC；不是协议问题 |
| HTTP 探测 status=200 但路径是 `/` HTML | 服务端 HTTP 与 WSS 是同一进程，但只接受 WS Upgrade；GET 任何路径都会回首页 | 正常，可忽略 |
| **测试连接握手成功但发消息无回复（V2.8X+）** | 文本输入 listen 帧必须用 `state="detect"` 携带 `text`；旧 `state="stop"+text` 被服务端静默忽略 | App V2.8X+ 已用 `state=detect`；若旧版，重新 build |
| **测试连接一直 1005 关（V2.8X+）** | xiaozhi.me 对 Device-Id(MAC) 大小写敏感，控制台绑定的是小写（如 `e8:06:90:98:6c:d4`），发送大写会被拒 | App V2.8X+ 出口统一转小写；UI 输入框也建议手填小写 |

## 5. V2.8X+ 协议补漏（实证结论）

### 5.1 Device-Id 大小写敏感
- **现象**：绑定小写 MAC 后握手一直 1005。
- **根因**：xiaozhi.me 对 Device-Id 做大小写敏感匹配，控制台展示的 MAC 是小写原样。
- **修复**：`WebSocketXiaozhiTransport.openSocket` + OTA 出口统一 `.lowercase()`（覆盖历史已存大写 MAC）。
- **验证**：同 MAC 小写握手成功拿 session_id，大写必 1005。

### 5.2 listen 帧 state 必须 detect
- **现象**：「测试连接正常（握手通过）但发消息无回复」。
- **根因**：服务端仅当 listen `state="detect"` 且带 `text` 时才把文本当作用户输入；`state="stop"+text` 服务端静默忽略。
- **修复**：`WebSocketXiaozhiTransport.sendText` 第二帧 `state="stop"` → `state="detect"`（保留 `start` 首帧）。
- **验证**：用 e8:06 真机，`detect+text` 完整跑通 STT→MCP→LLM→TTS。

### 5.3 握手 25s 超时
- **现象**：12s 超时误判服务端拒收。
- **根因**：服务端 connection.py 源码显示 hello 协议层无字段校验，但收到 hello 后会异步加载 TTS/ASR 组件，需数秒。
- **修复**：超时从 12s 提到 25s；`WebSocketXiaozhiTransport` 与 `XiaozhiConnectionTester` 同步。

## 6. V2.8X+ 消息净化（@image#xxx）

### 6.1 现象
消息列表偶现 `@image#1:23e150c406a50b91e200450bf3d94b31.jpg` 这种纯技术串。

### 6.2 根因
小智服务端在 LLM/TTS 文本中插入多模态资源引用 token（用于 TTS 朗读时插入图片/表情包合成播放）。TTS 音频会把它念成"图片"，但给 UI 展示用的 text 字段是带 token 的原文。

### 6.3 修复
- 新增 `MessageTextFilter.strip(s)`（`app/.../domain/assistant/MessageTextFilter.kt`）：纯 Kotlin 单例，正则 `@image#\d+:[0-9a-fA-F]{8,128}\.(jpg|jpeg|png|gif|webp)`。
- 双层防御：源头 `WebSocketXiaozhiTransport.handleServerMessage`（llm/tts sentence_start）+ UI 层 `AssistantViewModel.onEvent`（LlmText 分支）。
- 整句被过滤为空则不 emit，避免空气泡。

## 7. V2.8X+ 语音上行诊断（"语音不能用"排查）

### 7.1 现象
按下麦克风后 7+ 秒没有任何对话；logcat 看到 `sendListenStart` 但**无任何 Opus 帧上送**；切到文本输入又能正常对话。

### 7.2 三处诊断标签
| 标签 | 关键日志 | 含义 |
|---|---|---|
| `XzTransport` | `→ sendAudio Opus 帧 len=N` | 编码后已调用 sendAudio 成功上行 |
| `AudioCapture` | `start OK ...` + `→ onFrame NB` + 线程退出时 `frameCount=N` | 录音线程已起，N 帧触发回调 |
| `OpusCodec` | `→ encodeFrame OK 输出 NB` | 编码器成功输出 Opus 包 |

### 7.3 四类根因区分
按日志组合判断：
- **A · 录音未起**：`AudioCapture` 标签只有"start OK"，无 `onFrame`；线程退出时 `frameCount=0` + `readZero>10`。
  → 多半 `AudioSource.VOICE_RECOGNITION` 在该设备不可用；方案：fallback `AudioSource.MIC`。
- **B · read 阻塞**：`AudioCapture` 标签"start OK"后无任何 `onFrame`，线程退出时 `frameCount=0` + `readCount=0`。
  → AudioRecord 启动后 read 一直未返回；方案：缩短 minBuf、检查录音权限、检查是否被其他应用占用麦克风。
- **C · 编码失败**：`AudioCapture` 有 `onFrame`（录音正常），但 `OpusCodec` 持续 w 级 `encodeFrame 失败`。
  → 设备 Opus 编码器存在但 60ms 帧不被接受；方案：60ms 拆 3×20ms 输入。
- **D · send 失败**：`OpusCodec` 编码 OK，但 `XzTransport.sendAudio` e 级 `sendAudio 发送失败`。
  → WebSocket 已断开但 sendAudio 还被调用；方案：检查 `disconnect` 时序。

### 7.4 完整 logcat 命令
```bash
adb logcat -c
adb logcat | grep -E "XzTransport|AudioCapture|OpusCodec|AssistantVM"
```

按上面四类根因分类复制相关日志到项目组。
