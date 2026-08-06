# -*- coding: utf-8 -*-
"""xiaozhi 完整启动流程模拟（POST OTA + WS 握手 + 设备激活提示）。

完全对齐官方 78/xiaozhi-esp32 v1.9+ 固件启动流程：
    1. POST https://api.tenclass.net/xiaozhi/ota/  提交固件信息 JSON
       → 服务端返回：websocket 端点 / token / mqtt 配置 / activation 激活码（首次）
    2. 拿到响应里的 websocket 端点 + token，建立 WS 连接
    3. 发送 v1.6+ 协议 6 字段 hello（含 features.mcp=true）
    4. 等服务端 hello 完成握手

用法：
    python xiaozhi_full_flow.py \\
        --device-id "B5:B3:66:15:CB:2E" \\
        --client-id "90788698-b382-42b8-be36-724e923fcae7" \\
        --token "test-token"                       # 可选；留空则从 OTA 响应里取
        --firmware-version "1.9.4"                 # 改成你设备实际烧录的版本
"""

from __future__ import annotations
import argparse
import asyncio
import base64
import json
import socket
import ssl
import sys
import time
import urllib.parse
import uuid

# ── 日志 ─────────────────────────────────────────────
TAG_W = 10
def ts(): return f"{time.strftime('%H:%M:%S')}.{int(time.time()*1000)%1000:03d}"
def log(tag, msg):
    print(f"[{ts()}] [{tag:<{TAG_W}}] {msg}", flush=True)

# ── ① OTA 注册 ──────────────────────────────────────────
async def step_ota(ota_url: str, device_id: str, client_id: str, firmware_version: str):
    """
    POST 固件信息 JSON 到 OTA 端点。
    官方固件 v1.6+ 的请求体大致：
      {
        "flash_size": 16777216,
        "minimum_free_heap_size": 8318916,
        "mac_address": "B5:B3:66:15:CB:2E",
        "chip_model_name": "esp32s3",
        "chip_info": { "model": 9, "cores": 2, "revision": 2, "features": 18 },
        "application": {
          "name": "xiaozhi",
          "version": "1.9.4",
          "compile_time": "Jan 22 2025T20:40:23Z",
          "idf_version": "v5.x.x"
        },
        "partition_table": [...],   # 可选
        "ota": { "label": "factory" }  # 可选
      }
    """
    log("OTA", f"POST {ota_url}")
    log("OTA", f"设备 device_id={device_id} client_id={client_id} firmware={firmware_version}")
    body = {
        "flash_size": 16 * 1024 * 1024,
        "minimum_free_heap_size": 8 * 1024 * 1024,
        "mac_address": device_id,
        "chip_model_name": "esp32s3",
        "chip_info": {"model": 9, "cores": 2, "revision": 2, "features": 18},
        "application": {
            "name": "xiaozhi",
            "version": firmware_version,
            "compile_time": "Jan 22 2025T20:40:23Z",
            "idf_version": "v5.1.2",
        },
    }
    raw = json.dumps(body).encode()
    u = urllib.parse.urlparse(ota_url)
    port = u.port or (443 if u.scheme == "https" else 80)
    ctx = ssl.create_default_context() if u.scheme == "https" else None
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(u.hostname, port, ssl=ctx, server_hostname=u.hostname if u.scheme == "https" else None),
            timeout=8.0,
        )
    except Exception as e:
        log("OTA", f"❌ TCP/TLS 失败: {e!r}")
        return None
    try:
        req = (
            f"POST {u.path or '/'} HTTP/1.1\r\n"
            f"Host: {u.hostname}\r\n"
            f"User-Agent: TickClear/xiaozhi-full-flow\r\n"
            f"Content-Type: application/json\r\n"
            f"Content-Length: {len(raw)}\r\n"
            f"Device-Id: {device_id}\r\n"
            f"Client-Id: {client_id}\r\n"
            f"Connection: close\r\n"
            f"\r\n"
        ).encode() + raw
        writer.write(req)
        await writer.drain()
        status = await asyncio.wait_for(reader.readline(), timeout=8.0)
        log("OTA", f"状态行: {status.decode(errors='replace').rstrip()!r}")
        # 读头
        while True:
            line = await asyncio.wait_for(reader.readline(), timeout=5.0)
            d = line.decode(errors="replace").rstrip()
            if not d:
                break
            log("OTA", f"Header: {d}")
        # 读 body
        body_bytes = b""
        try:
            while True:
                chunk = await asyncio.wait_for(reader.read(4096), timeout=3.0)
                if not chunk:
                    break
                body_bytes += chunk
        except asyncio.TimeoutError:
            pass
        text = body_bytes.decode(errors="replace")
        log("OTA", f"Body 前 800 字节: {text[:800]!r}")
        try:
            obj = json.loads(text)
            log("OTA", "✅ JSON 解析成功")
            return obj
        except Exception as e:
            log("OTA", f"❌ JSON 解析失败: {e!r}")
            return None
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass

# ── WS Hello 握手（v1.6+ 协议 6 字段）──────────────────
async def step_ws_hello(ws_endpoint: str, device_id: str, client_id: str, token: str | None,
                        ws_timeout: float = 10.0):
    log("WS", f"endpoint={ws_endpoint}")
    u = urllib.parse.urlparse(ws_endpoint)
    scheme = u.scheme
    if scheme not in ("ws", "wss"):
        log("WS", f"❌ 非法 scheme: {scheme}")
        return False
    host, port = u.hostname, u.port or (443 if scheme == "wss" else 80)
    path = (u.path or "/") + (("?" + u.query) if u.query else "")
    ctx = ssl.create_default_context() if scheme == "wss" else None
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port, ssl=ctx, server_hostname=host if scheme == "wss" else None),
            timeout=5.0,
        )
    except Exception as e:
        log("WS", f"❌ TCP 失败: {e!r}")
        return False
    key = base64.b64encode(uuid.uuid4().bytes).decode()
    lines = [
        f"GET {path} HTTP/1.1",
        f"Host: {host}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}",
        "Sec-WebSocket-Version: 13",
        "User-Agent: TickClear/xiaozhi-full-flow",
        "Protocol-Version: 1",
    ]
    if token and token.strip():
        lines.append(f"Authorization: Bearer {token.strip()}")
    if device_id:
        lines.append(f"Device-Id: {device_id}")
    if client_id:
        lines.append(f"Client-Id: {client_id}")
    req = ("\r\n".join(lines) + "\r\n\r\n").encode()
    writer.write(req)
    await writer.drain()
    try:
        status_line = await asyncio.wait_for(reader.readline(), timeout=5.0)
    except Exception as e:
        log("WS", f"❌ 状态行失败: {e!r}")
        writer.close()
        return False
    log("WS", f"状态行: {status_line.decode(errors='replace').rstrip()!r}")
    if b"101" not in status_line:
        # 抓错误体
        while True:
            try:
                line = await asyncio.wait_for(reader.readline(), timeout=2.0)
            except Exception:
                break
            d = line.decode(errors="replace").rstrip()
            if not d:
                break
            log("WS", f"Header: {d}")
        try:
            body = await asyncio.wait_for(reader.read(4096), timeout=2.0)
            log("WS", f"Body: {body!r}")
        except Exception:
            pass
        writer.close()
        return False
    while True:
        try:
            line = await asyncio.wait_for(reader.readline(), timeout=2.0)
        except Exception:
            break
        d = line.decode(errors="replace").rstrip()
        if not d:
            break
        log("WS", f"Header: {d}")
    log("WS", "✅ Upgrade 成功，进入 WS 收发")

    # v1.6+ 协议 hello（6 字段，含 features.mcp=true）
    hello = json.dumps({
        "type": "hello",
        "version": 1,
        "features": {"mcp": True},  # 必填！
        "transport": "websocket",
        "audio_params": {
            "format": "opus",
            "sample_rate": 16000,
            "channels": 1,
            "frame_duration": 60,
        },
    }, ensure_ascii=False)
    log("WS", f"→ 发 hello: {hello}")
    writer.write(encode_text_frame(hello))
    await writer.drain()

    log("WS", f"⏳ 等服务端 hello，超时 {ws_timeout}s")
    t1 = time.time()
    try:
        frame = await read_frame(reader)
        elapsed = time.time() - t1
        log("WS", f"✅ {elapsed:.2f}s 内收到首帧 ({len(frame)}B): {frame[:400]!r}")
        try:
            obj = json.loads(frame.decode(errors="replace"))
            if obj.get("type") == "hello":
                log("WS", f"🎉 完整握手通过 session_id={obj.get('session_id')}")
                return True
            log("WS", f"⚠️ 首帧 type={obj.get('type')!r}，等业务消息走通")
            return True
        except Exception:
            return True
    except asyncio.TimeoutError:
        log("WS", f"❌ {ws_timeout}s 内未收到服务端 hello")
        return False
    except Exception as e:
        log("WS", f"❌ 读帧异常: {e!r}")
        return False
    finally:
        try:
            writer.close()
        except Exception:
            pass

# ── 极简 WS frame 编解码 ──────────────────────────────
def encode_text_frame(payload: str) -> bytes:
    data = payload.encode()
    mask = uuid.uuid4().bytes
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    n = len(data)
    if n < 126:
        hdr = bytes([0x81, 0x80 | n])
    elif n <= 0xFFFF:
        hdr = bytes([0x81, 0x80 | 126]) + n.to_bytes(2, "big")
    else:
        hdr = bytes([0x81, 0x80 | 127]) + n.to_bytes(8, "big")
    return hdr + mask + masked

async def read_frame(reader) -> bytes:
    hdr = await reader.readexactly(2)
    b1, b2 = hdr[0], hdr[1]
    op = b1 & 0x0F
    masked = b2 & 0x80
    n = b2 & 0x7F
    if n == 126:
        n = int.from_bytes(await reader.readexactly(2), "big")
    elif n == 127:
        n = int.from_bytes(await reader.readexactly(8), "big")
    mask = await reader.readexactly(4) if masked else None
    data = await reader.readexactly(n)
    if mask:
        data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    if op == 0x1:
        return data
    if op == 0x8:
        # close frame 状态码在前 2 字节
        code = int.from_bytes(data[:2], "big") if len(data) >= 2 else 0
        reason = data[2:].decode(errors="replace") if len(data) > 2 else ""
        raise ConnectionError(f"close_code={code} reason={reason!r}")
    rest = await read_frame(reader)
    return data + rest

# ── main ───────────────────────────────────────────────
async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ota-url", default="https://api.tenclass.net/xiaozhi/ota/",
                    help="OTA 注册端点。官方: https://api.tenclass.net/xiaozhi/ota/")
    ap.add_argument("--ws-url", default="",
                    help="WS 端点（留空 = 用 OTA 响应里的 websocket 字段）")
    ap.add_argument("--device-id", required=True)
    ap.add_argument("--client-id", required=True)
    ap.add_argument("--token", default="",
                    help="可选；留空 = 用 OTA 响应里的 token 字段")
    ap.add_argument("--firmware-version", default="1.9.4",
                    help="设备固件版本（影响服务端是否接受）")
    ap.add_argument("--skip-ota", action="store_true",
                    help="跳过 OTA POST，直接 WS 握手（用 --ws-url + --token）")
    ap.add_argument("--ws-timeout", type=float, default=10.0)
    args = ap.parse_args()

    log("START", f"device-id={args.device_id} client-id={args.client_id} fw={args.firmware_version}")

    ws_url = args.ws_url
    token = args.token

    if not args.skip_ota:
        log("START", "─── 第 1 步：POST OTA 注册设备 ───")
        ota_resp = await step_ota(args.ota_url, args.device_id, args.client_id, args.firmware_version)
        if ota_resp is None:
            log("START", "❌ OTA 注册失败，停止后续 WS 流程")
            sys.exit(2)

        # 解析响应
        log("OTA", "─── 解析 OTA 响应 ───")
        if not ws_url:
            ws_obj = ota_resp.get("websocket") or {}
            ws_url = ws_obj.get("url") or ""
            log("OTA", f"websocket.url = {ws_url!r}")
        if not token:
            token = ota_resp.get("token") or (ota_resp.get("websocket") or {}).get("token") or ""
            log("OTA", f"token 长度 = {len(token) if token else 0}")

        # 激活码（设备未在控制台添加时才会有）
        act = ota_resp.get("activation") or {}
        if act:
            code = act.get("code")
            msg = act.get("message")
            challenge = act.get("challenge")
            log("OTA", f"⚠️ 设备【未激活】，需在 xiaozhi.me 控制台添加：")
            log("OTA", f"  activation.code     = {code}")
            log("OTA", f"  activation.message  = {msg}")
            log("OTA", f"  activation.challenge= {challenge}")
            log("OTA", f"  步骤：登录 https://xiaozhi.me → 设备管理 → 添加设备 → 输入 6 位码 {code}")
        else:
            log("OTA", "✅ 设备【已激活】/无需验证码")

        mqtt = ota_resp.get("mqtt")
        if mqtt:
            log("OTA", f"另注：服务端也提供了 MQTT 配置（{mqtt.get('broker')}:{mqtt.get('port')}），"
                f"本脚本只演示 WS 路径")

        firm = ota_resp.get("firmware") or {}
        if firm:
            log("OTA", f"固件升级信息：version={firm.get('version')!r} url={firm.get('url')!r}")

        if not ws_url:
            log("OTA", "❌ OTA 响应里没找到 websocket.url，停止")
            sys.exit(3)

    if not ws_url:
        log("START", "❌ 没 WS 端点可用，请 --ws-url 显式指定")
        sys.exit(4)

    log("START", "─── 第 2 步：WS 握手 ───")
    ok = await step_ws_hello(ws_url, args.device_id, args.client_id, token, ws_timeout=args.ws_timeout)
    if ok:
        log("START", "🎉 完整启动流程通过：OTA + WS hello 都成功")
        sys.exit(0)
    log("START", "❌ WS 握手失败")
    sys.exit(5)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
