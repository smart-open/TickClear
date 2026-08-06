# -*- coding: utf-8 -*-
"""小智官方接口「固件版本门禁」对照探针。

目的：用同一套流程、不同的 `application.version`（固件版本标签），
打官网 OTA + WebSocket 接口，隔离出「版本」这一个变量，看服务端反应。

- v1.0.0（旧值，对照组）：应被官方 ≥v1.6.1 门禁拒收
- v2.4.0（最新稳定版，实验组）：应能通过版本门禁

用法：
    python xiaozhi_version_probe.py

说明：
- 每个版本用【全新】device-id / client-id，避免服务端缓存首次 OTA 的版本，
  从而干净地隔离「版本」变量（两者都是未绑定状态）。
- 即便版本达标，WS 完整握手通过还要求设备已在 xiaozhi.me 控制台
  用 6 位激活码「添加设备」完成绑定；本脚本会打印激活码供你手动绑定后复测。
- 关注两点：
  ① OTA 是否 200 且返回 token（版本门禁不在 OTA 层，旧版也通常 200）
  ② WS Upgrade 成功后，发 hello 是否被立即关闭（close_code=0, reason='' 即版本/绑定拒收）
"""

from __future__ import annotations
import argparse
import asyncio
import base64
import json
import time
import urllib.parse
import uuid

OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
WS_DEFAULT = "wss://api.tenclass.net/xiaozhi/v1/"

TAG_W = 12
def ts():
    return f"{time.strftime('%H:%M:%S')}.{int(time.time()*1000)%1000:03d}"
def log(tag, msg):
    print(f"[{ts()}] [{tag:<{TAG_W}}] {msg}", flush=True)


def gen_mac() -> str:
    b = uuid.uuid4().bytes[0:6]
    return ":".join(f"{x:02X}" for x in b)


async def ota_register(device_id: str, client_id: str, firmware: str):
    body = {
        "version": 1,
        "mac_address": device_id,
        "application": {
            "version": firmware,
            "name": "xiaozhi-esp32",
        },
        "board": {"type": "xiaozhi_v1"},
    }
    raw = json.dumps(body).encode()
    u = urllib.parse.urlparse(OTA_URL)
    port = u.port or 443
    import ssl
    ctx = ssl.create_default_context()
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(u.hostname, port, ssl=ctx, server_hostname=u.hostname),
            timeout=8.0,
        )
    except Exception as e:
        return {"error": f"TCP/TLS 失败: {e!r}"}
    try:
        req = (
            f"POST {u.path or '/'} HTTP/1.1\r\n"
            f"Host: {u.hostname}\r\n"
            f"Content-Type: application/json\r\n"
            f"Content-Length: {len(raw)}\r\n"
            f"Device-Id: {device_id}\r\n"
            f"Client-Id: {client_id}\r\n"
            f"Connection: close\r\n\r\n"
        ).encode() + raw
        writer.write(req)
        await writer.drain()
        status = await asyncio.wait_for(reader.readline(), timeout=8.0)
        status = status.decode(errors="replace").rstrip()
        body_b = b""
        try:
            while True:
                c = await asyncio.wait_for(reader.read(4096), timeout=3.0)
                if not c:
                    break
                body_b += c
        except asyncio.TimeoutError:
            pass
        text = body_b.decode(errors="replace")
        try:
            obj = json.loads(text)
        except Exception:
            obj = {"_raw": text[:300]}
        return {"status": status, "json": obj}
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


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


async def read_frame(reader):
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
        return data, "text"
    if op == 0x8:
        code = int.from_bytes(data[:2], "big") if len(data) >= 2 else 0
        reason = data[2:].decode(errors="replace") if len(data) > 2 else ""
        raise ConnectionError(f"close_code={code} reason={reason!r}")
    return data, ("binary" if op == 0x2 else "other")


async def ws_probe(device_id: str, client_id: str, token: str, ws_url: str, fw_label: str):
    u = urllib.parse.urlparse(ws_url)
    host, port = u.hostname, u.port or 443
    path = (u.path or "/") + (("?" + u.query) if u.query else "")
    import ssl
    ctx = ssl.create_default_context()
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port, ssl=ctx, server_hostname=host), timeout=5.0
        )
    except Exception as e:
        return f"TCP 失败: {e!r}"
    key = base64.b64encode(uuid.uuid4().bytes).decode()
    lines = [
        f"GET {path} HTTP/1.1", f"Host: {host}",
        "Upgrade: websocket", "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}", "Sec-WebSocket-Version: 13",
        "Protocol-Version: 1",
    ]
    if token:
        lines.append(f"Authorization: Bearer {token}")
    lines.append(f"Device-Id: {device_id}")
    lines.append(f"Client-Id: {client_id}")
    writer.write(("\r\n".join(lines) + "\r\n\r\n").encode())
    await writer.drain()
    try:
        status_line = await asyncio.wait_for(reader.readline(), timeout=5.0)
        status_line = status_line.decode(errors="replace").rstrip()
        if "101" not in status_line:
            return f"WS Upgrade 失败: {status_line!r}"
        while True:
            try:
                line = await asyncio.wait_for(reader.readline(), timeout=2.0)
            except Exception:
                break
            if not line.strip():
                break
        # 发 hello（protocol v1，6 字段，含 features.mcp）
        hello = json.dumps({
            "type": "hello", "version": 1,
            "features": {"mcp": True}, "transport": "websocket",
            "audio_params": {"format": "opus", "sample_rate": 16000,
                             "channels": 1, "frame_duration": 60},
        }, ensure_ascii=False)
        writer.write(encode_text_frame(hello))
        await writer.drain()
        t1 = time.time()
        try:
            frame, kind = await asyncio.wait_for(read_frame(reader), timeout=8.0)
            el = time.time() - t1
            if kind == "text":
                try:
                    obj = json.loads(frame.decode(errors="replace"))
                    if obj.get("type") == "hello":
                        return f"✅ 收到服务端 hello（{el:.3f}s），握手通过 session_id={obj.get('session_id')}"
                    return f"⚠️ 收到业务帧 type={obj.get('type')!r}（{el:.3f}s）"
                except Exception:
                    return f"⚠️ 收到文本帧（{el:.3f}s）：{frame[:200]!r}"
            return f"⚠️ 收到 {kind} 帧（{el:.3f}s，{len(frame)}B）"
        except ConnectionError as e:
            return f"❌ 发 hello 后被服务端立即关闭：{e}  ← 版本/绑定门禁拒收"
        except asyncio.TimeoutError:
            return f"❌ 发 hello 后 {time.time()-t1:.1f}s 内未收到任何帧（疑似静默挂起）"
        except Exception as e:
            return f"❌ 读帧异常: {e!r}"
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def run_one(firmware: str):
    log("PROBE", f"═══════ 测试固件版本 {firmware} ═══════")
    device_id = gen_mac()
    client_id = str(uuid.uuid4())
    log("PROBE", f"device-id={device_id}  client-id={client_id}")
    ota = await ota_register(device_id, client_id, firmware)
    if "error" in ota:
        log("OTA", f"❌ {ota['error']}")
        return {"fw": firmware, "ota": "FAIL", "ws": "未测"}
    log("OTA", f"状态: {ota.get('status')}")
    j = ota.get("json", {})
    act = j.get("activation") or {}
    code = act.get("code")
    ws_url = (j.get("websocket") or {}).get("url") or WS_DEFAULT
    token = j.get("token") or (j.get("websocket") or {}).get("token") or ""
    if code:
        log("OTA", f"⚠️ 设备未绑定，激活码={code}（需在 xiaozhi.me 添加设备输入此码）")
    else:
        log("OTA", "✅ 设备已绑定/无需激活码")
    log("WS", f"→ 用 ws={ws_url} 做握手探针")
    ws_result = await ws_probe(device_id, client_id, token, ws_url, firmware)
    log("WS", ws_result)
    return {"fw": firmware, "ota": ota.get("status", "?"), "ws": ws_result}


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--firmwares", default="v1.0.0,v2.4.0",
                    help="逗号分隔的固件版本，对照组+实验组")
    args = ap.parse_args()
    firmwares = [f.strip() for f in args.firmwares.split(",") if f.strip()]
    log("START", f"探针启动，测试版本：{firmwares}")
    results = []
    for fw in firmwares:
        r = await run_one(fw)
        results.append(r)
        log("PROBE", "─" * 40)
    log("SUMMARY", "对照结果：")
    for r in results:
        log("SUMMARY", f"  {r['fw']:<10} OTA={r['ota']:<22} WS={r['ws']}")
    log("SUMMARY", "解读：若 v1.0.0 在 WS 层被立即关闭、而 v2.4.0 收到服务端 hello，")
    log("SUMMARY", "      即证明「固件版本门禁」是此前不通的根因，升到 v2.4.0 可解。")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
