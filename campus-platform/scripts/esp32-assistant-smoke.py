#!/usr/bin/env python3
"""ESP32 assistant WebSocket end-to-end smoke test (stdlib only)."""

import argparse
import base64
import hashlib
import json
import os
import socket
import ssl
import struct
import sys
import time
import urllib.parse
import wave


class BufferedSocket:
    def __init__(self, sock, buffered=b""):
        self.sock = sock
        self.buffered = bytearray(buffered)

    def recv(self, size):
        if self.buffered:
            chunk = bytes(self.buffered[:size])
            del self.buffered[:size]
            return chunk
        return self.sock.recv(size)

    def sendall(self, data):
        self.sock.sendall(data)

    def close(self):
        self.sock.close()


def read_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("WebSocket connection closed unexpectedly")
        data.extend(chunk)
    return bytes(data)


def send_frame(sock, opcode, payload=b""):
    mask = os.urandom(4)
    length = len(payload)
    header = bytearray([0x80 | opcode])
    if length < 126:
        header.append(0x80 | length)
    elif length <= 0xFFFF:
        header.append(0x80 | 126)
        header.extend(struct.pack("!H", length))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack("!Q", length))
    header.extend(mask)
    masked = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    sock.sendall(header + masked)


def receive_frame(sock):
    first, second = read_exact(sock, 2)
    opcode = first & 0x0F
    masked = bool(second & 0x80)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack("!H", read_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", read_exact(sock, 8))[0]
    mask = read_exact(sock, 4) if masked else None
    payload = read_exact(sock, length)
    if mask:
        payload = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    return opcode, payload


def open_websocket(url, token, device_id, timeout):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ("ws", "wss"):
        raise ValueError("--url must start with ws:// or wss://")
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    query.extend((("device_token", token), ("device_id", device_id)))
    path = parsed.path or "/"
    path += "?" + urllib.parse.urlencode(query)
    port = parsed.port or (443 if parsed.scheme == "wss" else 80)
    raw = socket.create_connection((parsed.hostname, port), timeout=timeout)
    if parsed.scheme == "wss":
        raw = ssl.create_default_context().wrap_socket(raw, server_hostname=parsed.hostname)
    raw.settimeout(timeout)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    host = parsed.hostname if parsed.port is None else f"{parsed.hostname}:{parsed.port}"
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    )
    raw.sendall(request.encode("ascii"))
    response = bytearray()
    while b"\r\n\r\n" not in response:
        response.extend(raw.recv(4096))
        if len(response) > 64 * 1024:
            raise ConnectionError("WebSocket handshake response is too large")
    header_bytes, buffered = bytes(response).split(b"\r\n\r\n", 1)
    status_line = header_bytes.split(b"\r\n", 1)[0].decode("ascii", "replace")
    if " 101 " not in status_line:
        raise ConnectionError(f"WebSocket handshake failed: {status_line}")
    expected = base64.b64encode(hashlib.sha1(
        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")
    ).digest()).decode("ascii")
    headers = header_bytes.decode("iso-8859-1").lower()
    if f"sec-websocket-accept: {expected.lower()}" not in headers:
        raise ConnectionError("WebSocket handshake validation failed")
    return BufferedSocket(raw, buffered)


def send_json(sock, payload):
    send_frame(sock, 0x1, json.dumps(payload, ensure_ascii=False).encode("utf-8"))


def read_pcm(args):
    if not args.pcm:
        return b"\x00\x00" * int(16000 * args.seconds)
    with open(args.pcm, "rb") as source:
        data = source.read()
    if len(data) % 2:
        raise ValueError("PCM16LE file size must be even")
    return data


def save_wav(path, pcm):
    with wave.open(path, "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(24000)
        target.writeframes(pcm)


def main():
    parser = argparse.ArgumentParser(description="Test the ESP32 assistant WebSocket end to end")
    parser.add_argument("--url", required=True, help="WebSocket URL without credentials")
    parser.add_argument("--token", required=True, help="ESP32 device token")
    parser.add_argument("--device-id", default="esp32-smoke-test")
    parser.add_argument("--pcm", help="Raw PCM16LE/16kHz/mono input file")
    parser.add_argument("--jpeg", action="append", default=[], help="JPEG path; repeat up to 3 times")
    parser.add_argument("--seconds", type=float, default=1.0, help="Silence length when --pcm is omitted")
    parser.add_argument("--output", default="esp32-smoke-output.wav")
    parser.add_argument("--timeout", type=float, default=45.0)
    args = parser.parse_args()
    if not 0.25 <= args.seconds <= 30:
        parser.error("--seconds must be between 0.25 and 30")
    if len(args.jpeg) > 3:
        parser.error("at most 3 --jpeg arguments are allowed")

    sock = open_websocket(args.url, args.token, args.device_id, args.timeout)
    request_id = f"smoke-{int(time.time())}"
    text_parts = []
    audio = bytearray()
    try:
        listening = False
        while not listening:
            opcode, payload = receive_frame(sock)
            if opcode == 0x9:
                send_frame(sock, 0xA, payload)
                continue
            if opcode != 0x1:
                continue
            event = json.loads(payload.decode("utf-8"))
            print("EVENT", event.get("type"), event.get("state", ""))
            listening = event.get("type") == "state" and event.get("state") == "listening"

        send_json(sock, {"type": "turn_start", "request_id": request_id})
        pcm = read_pcm(args)
        for offset in range(0, len(pcm), 640):
            send_frame(sock, 0x2, b"\x01" + pcm[offset:offset + 640])
        for path in args.jpeg:
            with open(path, "rb") as source:
                image = source.read()
            send_frame(sock, 0x2, b"\x02" + image)
        send_json(sock, {"type": "turn_commit", "request_id": request_id})

        done = False
        while not done:
            opcode, payload = receive_frame(sock)
            if opcode == 0x9:
                send_frame(sock, 0xA, payload)
            elif opcode == 0x2:
                audio.extend(payload)
            elif opcode == 0x8:
                raise ConnectionError("Server closed the WebSocket")
            elif opcode == 0x1:
                event = json.loads(payload.decode("utf-8"))
                event_type = event.get("type")
                print("EVENT", event_type, event.get("state", event.get("code", "")))
                if event_type == "text_delta":
                    text_parts.append(event.get("text", event.get("delta", "")))
                elif event_type == "text_done" and not text_parts:
                    text_parts.append(event.get("text", ""))
                elif event_type == "error":
                    raise RuntimeError(f"{event.get('code')}: {event.get('msg')}")
                elif event_type == "turn_done":
                    done = True
        if not audio:
            raise RuntimeError("No TTS audio was returned")
        save_wav(args.output, audio)
        print(f"RESULT text_chars={len(''.join(text_parts))} audio_bytes={len(audio)} output={args.output}")
    finally:
        try:
            send_frame(sock, 0x8, struct.pack("!H", 1000))
        except OSError:
            pass
        sock.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print(f"FAILED {exception}", file=sys.stderr)
        sys.exit(1)
