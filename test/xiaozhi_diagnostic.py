# -*- coding: utf-8 -*-
"""xiaozhi 连接诊断脚本（纯 Python，零额外依赖需 websockets）。

用法：
    pip install websockets
    python xiaozhi_diagnostic.py                           # 默认连 api.tenclass.net
    python xiaozhi_diagnostic.py --endpoint wss://...      # 任意自定义端点
    python xiaozhi_diagnostic.py --mock 127.0.0.1:8765     # 连本地 mock
    python xiaozhi_diagnostic.py --token "test-token"      # 显式 token

诊断步骤：
    ① DNS 解析 api.tenclass.net
    ② TCP/TLS 握手可达性
    ③ HTTP 探测（GET /）— 看是否返回 404/HTML（判断服务端是否在听 WSS 但 HTTP 也活着）
    ④ WebSocket Upgrade：发 101 请求，捕捉状态码/响应头（关键:401/403 表示 token/Device-Id 问题）
    ⑤ WS Upgrade 成功后发严格 5 字段 hello，看 8s 内是否回 hello
所有结果带毫秒时间戳，按阶段分行打印，便于判断卡在哪一步。
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
TAG_W = 70
def ts(): return f"{time.strftime('%H:%M:%S')}.{int(time.time()*1000)%1000:03d}"
def log(tag, msg):
    print(f"[{ts()}] [{tag:<10}] {msg}", flush=True)

# ── 解析端点 ──────────────────────────────────────────
def parse_ws(endpoint: str):
    u = urllib.parse.urlparse(endpoint)
    if u.scheme not in ("ws", "wss"):
        raise ValueError(f"非 ws/wss 端点: {endpoint}")
    host = u.hostname
    port = u.port or (443 if u.scheme == "wss" else 80)
    path = (u.path or "/") + (("?" + u.query) if u.query else "")
    return u.scheme, host, port, path

# ── ① DNS ──────────────────────────────────────────────
async def step_dns(host: str):
    log("DNS", f"开始解析 {host}")
    loop = asyncio.get_event_loop()
    try:
        infos = await loop.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except Exception as e:
        log("DNS", f"❌ 解析失败: {e}")
        return None
    addrs = sorted({i[4][0] for i in infos})
    log("DNS", f"✅ 解析到 {len(addrs)} 个 IP: {addrs}")
    return addrs

# ── ② TCP/③ HTTP ──────────────────────────────────────
async def step_tcp_http(scheme: str, host: str, port: int, path: str, timeout: float = 5.0):
    log("TCP", f"开始 TCP 握手 {host}:{port}")
    ctx = ssl.create_default_context() if scheme == "wss" else None
    t0 = time.time()
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port, ssl=ctx, server_hostname=host if scheme == "wss" else None),
            timeout=timeout,
        )
    except (asyncio.TimeoutError, OSError) as e:
        log("TCP", f"❌ TCP/SSL 失败 ({time.time()-t0:.2f}s): {e!r}")
        return None
    log("TCP", f"✅ TCP/SSL 建立 ({time.time()-t0:.3f}s)")

    log("HTTP", f"开始 HTTP 探测 GET {path}")
    req = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}\r\n"
        f"Accept: */*\r\n"
        f"Connection: close\r\n"
        f"User-Agent: TickClear/xiaozhi-diagnostic\r\n\r\n"
    ).encode()
    writer.write(req)
    await writer.drain()
    try:
        status = await asyncio.wait_for(reader.readline(), timeout=timeout)
        log("HTTP", f"状态行: {status.decode().rstrip()!r}")
        # 读头
        while True:
            line = await asyncio.wait_for(reader.readline(), timeout=timeout)
            decoded = line.decode(errors="replace").rstrip()
            if not decoded:
                break
            log("HTTP", f"Header: {decoded}")
        # 试着读一些 body（不必读完）
        try:
            body = await asyncio.wait_for(reader.read(512), timeout=2.0)
            log("HTTP", f"Body 前 512 字节: {body!r}")
        except asyncio.TimeoutError:
            log("HTTP", "(无 body 或服务器保持连接)")
    except Exception as e:
        log("HTTP", f"❌ HTTP 探测失败: {e!r}")
    try:
        writer.close()
        await writer.wait_closed()
    except Exception:
        pass
    return True  # TCP/HTTP 至少看过

# ── ④ WS Upgrade + ⑤ Hello ─────────────────────────────
async def step_ws_hello(scheme: str, host: str, port: int, path: str,
                        device_id: str, client_id: str, token: str | None,
                        ws_timeout: float = 8.0):
    log("WS", f"开始 WebSocket Upgrade GET {path}")
    ctx = ssl.create_default_context() if scheme == "wss" else None
    t0 = time.time()
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port, ssl=ctx, server_hostname=host if scheme == "wss" else None),
            timeout=5.0,
        )
    except Exception as e:
        log("WS", f"❌ TCP 失败: {e!r}")
        return
    key = base64.b64encode(uuid.uuid4().bytes).decode()
    lines = [
        f"GET {path} HTTP/1.1",
        f"Host: {host}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}",
        "Sec-WebSocket-Version: 13",
        "User-Agent: TickClear/xiaozhi-diagnostic",
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
    log("WS", f"已发 Upgrade 请求（{len(req)}B）")

    # 状态行
    try:
        status_line = await asyncio.wait_for(reader.readline(), timeout=5.0)
    except Exception as e:
        log("WS", f"❌ 读状态行超时/失败: {e!r}")
        writer.close()
        return
    log("WS", f"状态行: {status_line.decode(errors='replace').rstrip()!r}")
    if b"101" not in status_line:
        # 读 body 抓 error message
        headers = []
        try:
            while True:
                line = await asyncio.wait_for(reader.readline(), timeout=2.0)
                d = line.decode(errors="replace").rstrip()
                if not d:
                    break
                log("WS", f"Header: {d}")
                headers.append(d)
            body = await asyncio.wait_for(reader.read(4096), timeout=2.0)
            log("WS", f"Body: {body!r}")
        except Exception as e:
            log("WS", f"(无 body/读失败: {e!r})")
        writer.close()
        log("WS", f"❌ 非 101 升级响应，Upgrade 失败")
        return
    # 读 101 后的响应头（无 body）
    try:
        while True:
            line = await asyncio.wait_for(reader.readline(), timeout=2.0)
            d = line.decode(errors="replace").rstrip()
            if not d:
                break
            log("WS", f"Header: {d}")
    except Exception as e:
        log("WS", f"(读响应头异常: {e!r})")
    log("WS", f"✅ Upgrade 成功 ({time.time()-t0:.3f}s) — 进入 WS 收发")

    # ── 5 字段 hello（严格对齐官方 ESP32 固件）──
    hello = json.dumps({
        "type": "hello",
        "version": 1,
        "transport": "websocket",
        "audio_params": {
            "format": "opus",
            "sample_rate": 16000,
            "channels": 1,
            "frame_duration": 60,
        },
    }, ensure_ascii=False)
    log("WS", f"→ 发 hello: {hello}")
    frame = encode_text_frame(hello)
    writer.write(frame)
    await writer.drain()

    # 等服务端 hello（8s 超时）
    log("WS", f"⏳ 等服务端 hello，超时 {ws_timeout}s")
    t1 = time.time()
    try:
        # 简化：服务器回帧文本/二进制帧；这里只看业务 hello。
        # 真实解析需要 WS frame decoder；这里用 1 帧读取，看 first frame。
        frame = await read_frame(reader)
        elapsed = time.time() - t1
        log("WS", f"✅ {elapsed:.2f}s 内收到首帧 ({len(frame)}B): {frame[:300]!r}")
        log("WS", f"🎉 握手完整链路通过")
    except asyncio.TimeoutError:
        log("WS", f"❌ {ws_timeout}s 内未收到服务端 hello（典型原因：device_id 未在 xiaozhi.me 绑定 / token 无效 / 网络被劫持）")
    except Exception as e:
        log("WS", f"❌ 读帧异常: {e!r}")
    finally:
        writer.close()


# 最小 WS frame 编解码（仅 text/unmasked server→client 都接受）
def encode_text_frame(payload: str) -> bytes:
    data = payload.encode()
    # client→server 必须 masked
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
    # 读 2B 头
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
    if op == 0x1:  # text
        return data
    if op == 0x8:  # close
        raise ConnectionError(f"服务端关闭: {data!r}")
    # 其他帧类型：继续读
    rest = await read_frame(reader)
    return data + rest

# ── main ───────────────────────────────────────────────
async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--endpoint", default="wss://api.tenclass.net/xiaozhi/v1/")
    ap.add_argument("--device-id", default="")
    ap.add_argument("--client-id", default="")
    ap.add_argument("--token", default="")
    ap.add_argument("--ws-timeout", type=float, default=8.0)
    args = ap.parse_args()

    scheme, host, port, path = parse_ws(args.endpoint)
    log("START", f"endpoint={args.endpoint}")
    log("START", f"scheme={scheme} host={host} port={port} path={path}")
    log("START", f"device-id='{args.device_id}' client-id='{args.client_id}' token-set={'yes' if args.token else 'no'}")

    addrs = await step_dns(host)
    if not addrs:
        sys.exit(2)
    await step_tcp_http(scheme, host, port, path)
    await step_ws_hello(scheme, host, port, path, args.device_id, args.client_id, args.token, ws_timeout=args.ws_timeout)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
