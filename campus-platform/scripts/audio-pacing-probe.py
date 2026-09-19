"""给 ESP32 网关的音频下行做「节奏体检」：逐帧记录到达时间，统计卡顿。

用法：
  python audio-pacing-probe.py --url wss://... --token <设备token> --pcm <16k单声道PCM> [--jpeg x.jpg]

输出：帧数、音频总时长、到达间隔分布、超过阈值的间隔（耳朵听到的「一卡一卡」）。
"""
import argparse
import importlib.util
import json
import os
import statistics
import struct
import time

SMOKE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "esp32-assistant-smoke.py")
spec = importlib.util.spec_from_file_location("esp32_smoke", SMOKE)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--token", required=True)
    parser.add_argument("--device-id", default="esp32-pacing-probe")
    parser.add_argument("--pcm", required=True)
    parser.add_argument("--jpeg", action="append", default=[])
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--gap-ms", type=float, default=80.0, help="超过该间隔算一次卡顿")
    args = parser.parse_args()

    sock = smoke.open_websocket(args.url, args.token, args.device_id, args.timeout)
    request_id = "probe-%d" % int(time.time())
    marks = []
    sizes = []
    started = 0.0
    first_frame_at = 0.0
    audio_bytes = 0
    text_parts = []
    try:
        # 先等网关进入 listening
        while True:
            opcode, payload = smoke.receive_frame(sock)
            if opcode == 0x9:
                smoke.send_frame(sock, 0xA, payload)
                continue
            if opcode != 0x1:
                continue
            event = json.loads(payload.decode("utf-8"))
            if event.get("type") == "state" and event.get("state") == "listening":
                break

        smoke.send_json(sock, {"type": "turn_start", "request_id": request_id})
        with open(args.pcm, "rb") as handle:
            pcm = handle.read()
        for offset in range(0, len(pcm), 640):
            smoke.send_frame(sock, 0x2, b"\x01" + pcm[offset:offset + 640])
        for path in args.jpeg:
            with open(path, "rb") as handle:
                smoke.send_frame(sock, 0x2, b"\x02" + handle.read())
        smoke.send_json(sock, {"type": "turn_commit", "request_id": request_id})

        started = time.perf_counter()
        stages = {}
        while True:
            opcode, payload = smoke.receive_frame(sock)
            if opcode is None:
                break
            if opcode == 0x9:
                smoke.send_frame(sock, 0xA, payload)
                continue
            if opcode == 0x2:
                now = time.perf_counter()
                if not marks:
                    first_frame_at = now
                    stages.setdefault("first_audio", (now - started) * 1000)
                marks.append(now)
                sizes.append(len(payload))
                audio_bytes += len(payload)
                continue
            if opcode == 0x8:
                break
            if opcode == 0x1:
                event = json.loads(payload.decode("utf-8"))
                event_type = event.get("type")
                if event_type == "state":
                    stages.setdefault("state_" + str(event.get("state")),
                                      (time.perf_counter() - started) * 1000)
                elif event_type == "text_delta":
                    stages.setdefault("first_text", (time.perf_counter() - started) * 1000)
                    text_parts.append(event.get("text", ""))
                elif event_type == "audio_start":
                    stages.setdefault("tts_start", (time.perf_counter() - started) * 1000)
                elif event_type == "audio_done":
                    stages.setdefault("audio_done", (time.perf_counter() - started) * 1000)
                elif event_type == "error":
                    print("网关返回错误:", event.get("code"), event.get("msg"))
                elif event_type == "turn_done":
                    break
    except Exception as error:  # noqa: BLE001
        print("异常:", error)
    finally:
        try:
            smoke.send_frame(sock, 0x8, struct.pack("!H", 1000))
        except OSError:
            pass
        sock.close()

    if len(marks) < 2:
        print("没有收到足够的音频帧:", len(marks))
        return

    gaps = [(marks[i] - marks[i - 1]) * 1000 for i in range(1, len(marks))]
    audio_ms = audio_bytes / 2 / 24000 * 1000
    wall_ms = (marks[-1] - marks[0]) * 1000
    stutters = [g for g in gaps if g > args.gap_ms]

    # 累积音频时长到达 1s / 3s 时实际用了多久：直接反映「起步是否先把缓冲填起来」
    reached = {}
    total = 0
    for index, size in enumerate(sizes):
        total += size / 2 / 24000 * 1000
        for target in (1000, 3000):
            if target not in reached and total >= target:
                reached[target] = (marks[index] - marks[0]) * 1000

    print("回答: %s" % "".join(text_parts).strip()[:80])
    chain = []
    for key, label in (("state_thinking", "提交模型"), ("first_text", "模型首字"),
                       ("tts_start", "TTS 开始"), ("first_audio", "首帧音频"),
                       ("audio_done", "音频完成")):
        if key in stages:
            chain.append("%s=%.0fms" % (label, stages[key]))
    print("延迟链路: %s" % " → ".join(chain))
    print("帧数=%d 音频=%.0fms(%d 字节) 首帧延迟=%.0fms" %
          (len(marks), audio_ms, audio_bytes, (first_frame_at - started) * 1000))
    print("帧间隔: 最小=%.1f 中位=%.1f 平均=%.1f 最大=%.1f ms" %
          (min(gaps), statistics.median(gaps), statistics.mean(gaps), max(gaps)))
    print("每帧平均音频=%.1fms 传输效率=%.0f%%（整体；领先量到位后会回落到约 100%%）" %
          (audio_ms / len(marks), audio_ms / wall_ms * 100))
    print("起步填充: 首 1s 音频用时 %s ms，首 3s 音频用时 %s ms（越小说明先把缓冲填起来了）" %
          (round(reached.get(1000, -1)), round(reached.get(3000, -1))))
    print("前 20 帧间隔: %s" % [round(g) for g in gaps[:20]])
    print("大于 %.0fms 的间隔: %d 次，合计 %.0fms" %
          (args.gap_ms, len(stutters), sum(stutters)))
    print("最大的 10 个间隔: %s" % [round(g) for g in sorted(gaps, reverse=True)[:10]])


if __name__ == "__main__":
    main()
