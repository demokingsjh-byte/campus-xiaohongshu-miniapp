#!/usr/bin/env python3
"""OpenAI 兼容多模态接口冒烟测试（纯标准库）。

用于验证要接入 ESP32 网关的第三方模型地址是否可用，覆盖三种请求：
1. 非流式纯文本
2. 流式纯文本
3. 流式图文（JPEG 以 data URL 内嵌）

用法：
    python3 scripts/llm-endpoint-smoke.py \
        --url "http://<host>/api/predict/<service>/v1/chat/completions" \
        --model "GLM-5.3-flash" \
        --api-key "<token>" \
        --image ./camera.jpg          # 可选；缺省下载官方示例图，保证尺寸满足 VL 模型要求
"""

import argparse
import base64
import json
import sys
import time
import urllib.error
import urllib.request

PLAIN_QUESTION = "用一句话介绍你自己"

# 未指定 --image 时优先下载这张官方示例图：多数 VL 模型要求宽高均大于 10px，
# 且提示词里能出现可验证的具体内容。
SAMPLE_IMAGE_URL = "https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg"

# 下载失败时的兜底：1x1 最小合法 JPEG。注意部分 VL 模型会以尺寸过小为由拒绝。
TINY_JPEG_B64 = (
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
    "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA"
    "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="
)


def load_image(image_path: str):
    """返回 (base64 字符串, 来源描述)。"""
    if image_path:
        with open(image_path, "rb") as handle:
            return base64.b64encode(handle.read()).decode("ascii"), image_path
    try:
        request = urllib.request.Request(SAMPLE_IMAGE_URL)
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
        print("[INFO] 未指定 --image，已下载官方示例图（%d 字节）" % len(data))
        return base64.b64encode(data).decode("ascii"), SAMPLE_IMAGE_URL
    except Exception as error:  # noqa: BLE001 - 兜底路径需要吞掉任意网络异常
        print("[WARN] 示例图下载失败（%s），回退到 1x1 占位 JPEG；"
              "部分 VL 模型会因尺寸过小拒绝该图片" % error)
        return TINY_JPEG_B64, "内置 1x1 占位 JPEG"



def post(url: str, api_key: str, payload: dict, stream: bool, timeout: float):
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "text/event-stream" if stream else "application/json")
    if api_key:
        request.add_header("Authorization", "Bearer " + api_key)
    return urllib.request.urlopen(request, timeout=timeout)


def run_case(name: str, url: str, api_key: str, payload: dict, stream: bool, timeout: float) -> bool:
    started = time.time()
    try:
        with post(url, api_key, payload, stream, timeout) as response:
            raw = response.read().decode("utf-8", "replace")
            code = response.status
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:300]
        print("[FAIL] %s HTTP %s：%s" % (name, error.code, detail))
        return False
    except Exception as error:  # noqa: BLE001 - 冒烟脚本需要打印所有失败原因
        print("[FAIL] %s 请求异常：%s" % (name, error))
        return False

    elapsed = int((time.time() - started) * 1000)
    if not stream:
        try:
            data = json.loads(raw)
        except ValueError:
            print("[FAIL] %s 返回非 JSON：%s" % (name, raw[:200]))
            return False
        if data.get("error"):
            print("[FAIL] %s 模型报错：%s" % (name, json.dumps(data["error"], ensure_ascii=False)[:300]))
            return False
        choices = data.get("choices") or []
        if not choices:
            print("[FAIL] %s 缺少 choices：%s" % (name, raw[:200]))
            return False
        text = (choices[0].get("message") or {}).get("content") or ""
        print("[ OK ] %s HTTP %s %sms 回答=%s" % (name, code, elapsed, text.strip()[:80]))
        return bool(text.strip())

    deltas = 0
    text_parts = []
    saw_done = False
    for line in raw.splitlines():
        line = line.strip()
        if not line.startswith("data:"):
            continue
        chunk = line[5:].strip()
        if chunk == "[DONE]":
            saw_done = True
            continue
        try:
            event = json.loads(chunk)
        except ValueError:
            continue
        choices = event.get("choices") or []
        if not choices:
            continue
        delta = choices[0].get("delta") or {}
        content = delta.get("content") or ""
        if content:
            deltas += 1
            text_parts.append(content)
    if deltas == 0:
        print("[FAIL] %s 未收到任何流式文本，原始响应前 300 字：%s" % (name, raw[:300]))
        return False
    print("[ OK ] %s HTTP %s %sms 分片=%d DONE=%s 回答=%s"
          % (name, code, elapsed, deltas, saw_done, "".join(text_parts).strip()[:80]))
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenAI 兼容多模态接口冒烟测试")
    parser.add_argument("--url", required=True, help="chat/completions 完整地址")
    parser.add_argument("--model", required=True, help="模型名")
    parser.add_argument("--api-key", default="", help="服务调用 Token")
    parser.add_argument("--image", default="", help="用于图文测试的 JPEG 路径，缺省自动生成")
    parser.add_argument("--timeout", type=float, default=90.0, help="单次请求超时秒数")
    parser.add_argument("--skip-image", action="store_true", help="跳过图文测试")
    args = parser.parse_args()

    text_payload = {
        "model": args.model,
        "messages": [{"role": "user", "content": PLAIN_QUESTION}],
        "max_tokens": 128,
    }
    results = {
        "非流式文本": run_case("非流式文本", args.url, args.api_key, text_payload, False, args.timeout),
    }
    stream_payload = dict(text_payload, stream=True)
    results["流式文本"] = run_case("流式文本", args.url, args.api_key, stream_payload, True, args.timeout)

    if not args.skip_image:
        jpeg_b64, source = load_image(args.image)
        data_url = "data:image/jpeg;base64," + jpeg_b64
        image_payload = {
            "model": args.model,
            "stream": True,
            "max_tokens": 128,
            "messages": [
                {"role": "system", "content": "你是校园导览助手，请说明图片中能看到的内容。"},
                {"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": data_url}},
                    {"type": "text", "text": "这张图片里有什么？"},
                ]},
            ],
        }
        results["流式图文（%s）" % source] = run_case(
            "流式图文", args.url, args.api_key, image_payload, True, args.timeout)

    print("---- 结果 ----")
    for name, ok in results.items():
        print("%s %s" % ("[PASS]" if ok else "[FAIL]", name))
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
