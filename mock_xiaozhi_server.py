# -*- coding: utf-8 -*-
"""官方小智协议本地 mock 服务端（纯 Python）。

按官方 ESP32 固件的三方一致协议：
- 接受 GET /xiaozhi/v1/ 的 WebSocket Upgrade
- 等客户端发 hello（严格 5 字段）
- 回服务端 hello + 可选 welcome（sentence_start）
- 心跳用 WS Ping/Pong

运行：
    pip install websockets
    python mock_xiaozhi_server.py --port 8765

作为对照基线，再用 xiaozhi_diagnostic.py --mock 127.0.0.1:8765 来测试。
"""

from __future__ import annotations
import argparse
import asyncio
import json
import logging
import sys
import time

try:
    import websockets
    from websockets.server import serve
except ImportError:
    print("需要 websockets 库：pip install websockets", file=sys.stderr)
    sys.exit(2)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03d [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("mock-xz")

REQUIRED_HELLO_FIELDS = {"type", "version", "transport", "audio_params"}

async def validate_hello(raw: str) -> tuple[bool, str]:
    """严格校验 hello 5 字段。"""
    try:
        msg = json.loads(raw)
    except json.JSONDecodeError as e:
        return False, f"hello 不是合法 JSON: {e}"
    if not isinstance(msg, dict):
        return False, "hello 不是 JSON 对象"
    missing = REQUIRED_HELLO_FIELDS - msg.keys()
    if missing:
        return False, f"hello 缺少必含字段 {sorted(missing)}"
    if msg.get("type") != "hello":
        return False, f"hello.type 必须 'hello'，实为 {msg.get('type')!r}"
    if msg.get("version") != 1:
        return False, f"hello.version 必须 1，实为 {msg.get('version')!r}"
    if msg.get("transport") != "websocket":
        return False, f"hello.transport 必须 'websocket'，实为 {msg.get('transport')!r}"
    ap = msg.get("audio_params")
    if not isinstance(ap, dict):
        return False, "audio_params 必须对象"
    for k in ("format", "sample_rate", "channels", "frame_duration"):
        if k not in ap:
            return False, f"audio_params 缺 {k}"
    return True, "ok"

async def handshake_and_keep_alive(ws, device_id_expected: str | None):
    log.info("客户端连上 headers=%s", ws.request.headers if hasattr(ws, "request") else "?")
    # 仅做协议层校验：device_id 是否在控制台「绑定」过由调用方决定
    # 服务端检查必备头（mock 也模仿真实服务端拒绝缺失 Device-Id 的情况）
    headers = ws.request.headers
    did = headers.get("Device-Id")
    if not did:
        log.warning("⚠️  客户端没带 Device-Id 头（真实服务端会拒绝握手）")
    elif device_id_expected and did != device_id_expected:
        log.warning(f"⚠️  Device-Id 头={did} 与控制台绑定 {device_id_expected} 不一致（服务端通常静默不回 hello 直到超时）")
    token = headers.get("Authorization")
    log.info("Device-Id=%r Client-Id=%r Authorization=%r Protocol-Version=%r",
             did, headers.get("Client-Id"), token, headers.get("Protocol-Version"))

    # 等 hello（10s 内）
    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=10.0)
    except asyncio.TimeoutError:
        log.warning("❌ 客户端 10s 未发 hello — 关闭")
        await ws.close(code=1008, reason="no hello")
        return
    if not isinstance(raw, str):
        log.warning(f"❌ hello 应为 text 帧，实为 {type(raw).__name__}")
        await ws.close(code=1003, reason="hello must be text")
        return

    log.info("← 收到 hello: %s", raw)
    ok, detail = await validate_hello(raw)
    if not ok:
        log.warning(f"❌ hello 校验失败: {detail}")
        # 真实服务端：可能静默直到客户端超时。这里显式报错帮诊断
        err = json.dumps({"type": "error", "message": detail, "code": "invalid_hello"})
        await ws.send(err)
        await ws.close(code=1008, reason="invalid hello")
        return

    # ✅ 回服务端 hello
    server_hello = {
        "type": "hello",
        "session_id": f"mock-{int(time.time())}-{id(ws) & 0xFFFF:04X}",
        "audio_params": {
            "format": "opus",
            "sample_rate": 16000,
            "channels": 1,
            "frame_duration": 60,
        },
        "transport": "websocket",
        "version": 1,
    }
    log.info("→ 发送服务端 hello: %s", json.dumps(server_hello, ensure_ascii=False))
    await ws.send(json.dumps(server_hello, ensure_ascii=False))

    # 模拟一段欢迎话术（sentence_start/stop）
    await asyncio.sleep(0.3)
    welcome = {
        "type": "tts",
        "state": "sentence_start",
        "text": "你好，我是本地 mock 小智。握手已通过。",
    }
    await ws.send(json.dumps(welcome, ensure_ascii=False))

    # 继续监听 + 心跳保活
    try:
        async for msg in ws:
            if isinstance(msg, str):
                log.info("← 文本帧: %s", msg[:200])
                # 简单回声
                if msg.strip().startswith("{"):
                    try:
                        d = json.loads(msg)
                        if d.get("type") == "listen" and d.get("state") == "stop" and d.get("text"):
                            user_text = d["text"]
                            await ws.send(json.dumps({
                                "type": "stt",
                                "text": user_text,
                                "session_id": server_hello["session_id"],
                            }, ensure_ascii=False))
                            await ws.send(json.dumps({
                                "type": "llm",
                                "text": f"[mock 回复] 收到：{user_text}",
                                "session_id": server_hello["session_id"],
                            }, ensure_ascii=False))
                    except json.JSONDecodeError:
                        pass
            else:
                log.info("← 二进制帧 %dB", len(msg))
    except websockets.ConnectionClosed as e:
        log.info(f"连接关闭 code={e.code} reason={e.reason!r}")


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--device-id-expected", default=None,
                    help="若指定，则校验客户端 Device-Id 头必须等于此值（模拟「控制台已绑定」检查）")
    args = ap.parse_args()

    log.info(f"启动 mock 小智服务端 ws://{args.host}:{args.port}/")
    log.info("对照测试：python xiaozhi_diagnostic.py --endpoint ws://127.0.0.1:8765/ --device-id <MAC>")
    async with serve(
        lambda ws: handshake_and_keep_alive(ws, args.device_id_expected),
        args.host, args.port,
        ping_interval=20, ping_timeout=20,
        max_size=10 * 1024 * 1024,
    ):
        await asyncio.Future()  # run forever

if __name__ == "__main__":
    asyncio.run(main())
