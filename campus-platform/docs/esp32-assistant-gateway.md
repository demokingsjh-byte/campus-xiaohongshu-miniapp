# ESP32-S3 视听说机器人中间服务

## 1. 目标与边界

本服务位于 `yudao-module-campus`，先通过火山 ASR 将设备音频转成文字，再通过火山方舟 Responses API 调用 `doubao-seed-2-1-pro-260915` 理解文字与图片，最后复用火山 TTS 给 XIAO ESP32-S3 Sense 提供“听、看、理解、说”的闭环。

它是校园平台内的一条独立设备链路，不修改原 AI_GUIDE H5 视频通话接口。设备只有提交一轮语音后才会请求模型，摄像头画面变化不会单独触发回答。

## 2. 完整流程

```text
ESP32-S3                      campus-platform                     外部服务
   │                                │                                │
   ├── WebSocket + 设备 token ─────>│                                │
   │<── connected / listening ──────┤                                │
   │                                │                                │
   ├── turn_start ─────────────────>│                                │
   ├── 0x01 + PCM 音频帧 ──────────>│                                │
   ├── 0x02 + JPEG（0~3 张）────────>│                                │
   ├── turn_commit ────────────────>│                                │
   │                                ├── WAV ───────────────────────> 火山 ASR
   │                                │<── 用户问题文字 ────────────── 火山 ASR
   │                                ├── 问题文字 + JPEG ───────────> 火山方舟 Pro 模型
   │                                │<── text_delta / text_done ─── 多模态模型
   │                                ├── 分句文字流 ────────────────> 火山 TTS
   │<── audio_start ────────────────┤                                │
   │<── PCM16LE / 24k 二进制流 ─────┤<── PCM 音频流 ─────────────── 火山 TTS
   │<── audio_done / turn_done ─────┤                                │
   │        （等待 650ms）           │                                │
   │<── listening ──────────────────┤                                │
```

关键设计：

- `turn_commit` 后先将 16 kHz、单声道 PCM16LE 无损封装为 WAV 并提交火山 ASR，再把转写文字和 JPEG 图片组成 Responses 请求。
- 火山 Chat API 的直接音频格式是 `input_audio: {data: <纯 Base64>, format: "wav"}`，还支持 MP3、AAC、M4A；但官方音频理解模型列表只包含 Seed 2.0 Lite/Mini，不包含当前的 Seed 2.1 Pro。
- 方舟接口使用流式响应，网关将每个文本增量转换为设备协议的 `text_delta`，因此仍可按句启动 TTS。
- 网关默认使用 `doubao-seed-2-1-pro-260915` 的 Responses API；该模型接收 ASR 文字与图片，不接收 `input_audio`。
- ASR 是模型调用的前置步骤；转写失败时本轮返回 `ASR_FAILED`，提示设备重新提问。
- TTS 按完整短句流式合成，避免逐字合成造成“一段一段”的播音。
- 返回音频按 PCM 播放速度节流，避免数据灌入过快撑满 ESP32 播放缓冲。
- AI 播放期间采用半双工，不自动采集下一轮；设备发送 `interrupt` 可手动打断。
- 一轮最多携带 3 张 JPEG，单张最大 2MB，总计最大 6MB。

## 3. 服务接口

### 3.1 设备 WebSocket

```text
/app-api/campus/esp32/assistant/ws
```

同时保留旧 AI_GUIDE 路径 `/ai_guide_service/api/v1/esp32/assistant/ws`，因此已有固件不改路径也能迁移；新固件建议使用上面的校园平台路径。

推荐请求头：

```text
Authorization: Bearer <device-token>
X-Device-Id: xiao-esp32s3-01
```

固件不方便设置请求头时，可使用查询参数：

```text
wss://<域名>/app-api/campus/esp32/assistant/ws?device_token=<token>&device_id=xiao-esp32s3-01
```

### 3.2 健康检查

```text
GET /app-api/campus/esp32/assistant/health
```

`configured=true` 表示设备 token、模型、ASR（启用时）和 TTS 参数已经填全。

## 4. 设备协议 `esp32-av/1.0`

### 4.1 固件状态机

设备端只需维护以下状态，不要在 `speaking` 时继续录音：

```text
DISCONNECTED --连接成功--> LISTENING --按键/唤醒--> CAPTURING
      ^                         ^                         |
      |                         |                         | turn_commit
      |                         |                         v
      +----重连-----------------+---- turn_done ---- THINKING/SPEAKING
                                      （中间会短暂进入 COOLDOWN）
```

建议固件行为：

1. Wi-Fi 就绪后建立 WSS，只有收到 `state=listening` 才允许开始一轮。
2. 按键或唤醒后发送 `turn_start`，收到 `turn_ready` 后上传音频和可选图片。
3. 停止说话后发送 `turn_commit`；进入 `thinking` 后停止上行媒体。
4. 收到 `audio_start` 后打开 24kHz I2S 播放；二进制消息全部放进播放队列。
5. 收到 `audio_done` 后排空播放队列；收到 `turn_done` 后等待服务再次下发 `listening`。
6. 播放中需要打断时发送 `interrupt`，收到 `interrupt_ack` 后清空播放队列。
7. 网络断开后采用 1、2、4、8、15 秒退避重连；每 30 秒可发送一次 `ping` 保活。

### 4.2 建连与鉴权

成功握手的 HTTP 状态是 `101 Switching Protocols`。缺失或错误 token 返回 `401`，服务未启用或配置不完整返回 `503`；同一 IP 超过连接上限后，WebSocket 会以 `4429` 关闭。

建连成功后，服务会先返回能力描述：

```json
{
  "type": "connected",
  "session_id": "server-session-id",
  "device_id": "xiao-esp32s3-01",
  "protocol_version": "esp32-av/1.0",
  "mode": "half_duplex",
  "input_audio": {"binary_prefix": 1, "format": "pcm_s16le", "sample_rate": 16000, "channels": 1},
  "input_image": {"binary_prefix": 2, "format": "jpeg", "max_count": 3},
  "output_audio": {"format": "pcm_s16le", "sample_rate": 24000, "channels": 1}
}
```

随后返回：

```json
{"type":"state","state":"listening","message":"可以提问了"}
```

### 4.3 设备发送的 JSON

开始一轮：

```json
{"type":"turn_start","request_id":"turn-001"}
```

提交一轮：

```json
{"type":"turn_commit","request_id":"turn-001"}
```

取消采集、手动打断和保活：

```json
{"type":"turn_cancel","request_id":"turn-001"}
{"type":"interrupt","request_id":"turn-001"}
{"type":"ping","ts":1720000000}
```

### 4.4 设备发送的二进制帧

每条 WebSocket 二进制消息的第一个字节表示媒体类型：

- `0x01`：后续数据为 PCM16LE、16000Hz、单声道。
- `0x02`：后续数据为一张完整 JPEG。

建议顺序：`turn_start` → 连续音频帧 → 0~3 张 JPEG → `turn_commit`。只有 `turn_commit` 会触发模型回答。

音频要求与建议：

- 音频是裸 PCM，不带 WAV 文件头；格式为有符号 16 位小端、16000Hz、单声道。
- 每个 WebSocket 二进制消息前加一个字节 `0x01`，后面建议放 20ms（640 字节）或 40ms（1280 字节）音频。
- 有效音频至少 8000 字节（250ms），默认最多 960000 字节（30 秒）。
- JPEG 必须在同一条 WebSocket 二进制消息中完整发送，前面加一个字节 `0x02`；不允许拆成多条协议消息。
- 图片单张最大 2MB，每轮最多 3 张、合计最大 6MB。没有图片时只发音频即可。

### 4.5 服务返回

控制事件包括：

- `connected`：连接成功及媒体参数。
- `state`：`listening`、`capturing`、`thinking`、`speaking`、`cooldown`。
- `turn_ready`、`image_received`、`text_delta`、`text_done`。
- `audio_start`、`audio_done`、`turn_done`。
- `interrupt_ack`、`turn_ignored`、`error`。

`audio_start` 与 `audio_done` 之间的二进制消息没有类型前缀，内容固定为 PCM16LE、24000Hz、单声道，可直接送入 ESP32 I2S 播放队列。

一轮成功响应的典型顺序：

```text
state(capturing) → turn_ready → state(thinking)
→ text_delta(多次) → audio_start → state(speaking)
→ PCM 二进制帧(多次) → text_done → audio_done → turn_done
→ state(cooldown) → state(listening)
```

文本事件示例：

```json
{"type":"text_delta","request_id":"turn-001","text":"你好"}
{"type":"text_done","request_id":"turn-001","text":"你好，需要我帮你看什么？","stats":{"first_token_ms":820,"total_ms":1450}}
{"type":"audio_start","request_id":"turn-001","format":"pcm_s16le","sample_rate":24000,"channels":1}
{"type":"audio_done","request_id":"turn-001"}
{"type":"turn_done","request_id":"turn-001"}
```

### 4.6 错误处理

| code | 原因 | 固件处理 |
| --- | --- | --- |
| `INVALID_JSON` / `UNKNOWN_EVENT` | 控制消息格式或类型错误 | 记录并修正固件协议，不重连 |
| `NO_ACTIVE_TURN` | 未开始一轮就提交 | 等待 `listening`，重新发送 `turn_start` |
| `INVALID_AUDIO` / `AUDIO_TOO_LARGE` | PCM 长度或时长不合法 | 丢弃本轮并重新采集 |
| `INVALID_IMAGE` / `IMAGE_TOO_LARGE` | JPEG 不完整或过大 | 本轮不发该图片，可继续提交音频 |
| `TOO_MANY_IMAGES` / `IMAGES_TOO_LARGE` | 图片数量或总大小超限 | 最多保留 3 张并压缩 |
| `MODEL_UNAVAILABLE` / `GATEWAY_ERROR` | 模型链路不可用 | `retryable=true` 时退避后重试/重连 |
| `TTS_FAILED` | 语音合成失败 | 显示文本，等待 `turn_done` 后进入下一轮 |

任何 `error` 都包含 `retryable`。设备应保留 `request_id`，只处理当前轮次的事件；旧轮次迟到的数据直接丢弃。

## 5. ESP32 固件对接流程

### 5.1 初始化

1. 初始化 Wi-Fi、摄像头、麦克风/I2S RX、扬声器/I2S TX 和播放环形缓冲区。
2. 从 NVS 读取 `device_token`、唯一 `device_id` 和服务器地址；不要把生产 token 打印到串口日志。
3. 校时并校验证书，连接 `wss://huanwoshidai.com.cn/app-api/campus/esp32/assistant/ws`。
4. 建议优先在请求头放 `Authorization: Bearer <token>` 和 `X-Device-Id`；库不支持自定义头时使用查询参数。

### 5.2 一轮采集

```text
按键按下/唤醒
  → 发送 turn_start
  → 等 turn_ready
  → 每 20~40ms 发送 [0x01][PCM]
  → 需要视觉时抓拍 JPEG，发送 [0x02][完整 JPEG]
  → VAD 静音或按键松开
  → 发送 turn_commit
```

上传音频期间不要进行采样率转换；麦克风若输出 32 位 I2S 数据，应先在设备端转换为饱和的 PCM16LE。摄像头图片建议使用 QVGA/VGA JPEG，优先把单张控制在 200KB 内，以降低延迟和内存占用。

### 5.3 接收与播放

收到 `audio_start` 后，将 I2S TX 配置为 24000Hz、16 位、单声道。之后每个二进制 WebSocket 消息都是音频，不含 `0x01`/`0x02` 前缀。推荐至少准备 8~16KB 环形缓冲，累计 40~80ms 后开始播放；缓冲不足时补零，收到 `interrupt_ack` 时立即清空。

### 5.4 request_id 规则

建议格式为 `<device-id>-<启动计数>-<轮次>`，最长 100 字符，例如 `xiao-01-18-0032`。一次连接内必须唯一，方便后台按设备和轮次定位每一段耗时。

## 6. 联调和验收

### 6.1 健康检查

```bash
curl --fail --silent --show-error \
  https://huanwoshidai.com.cn/app-api/campus/esp32/assistant/health
```

期望 `code=0`、`enabled=true`、`configured=true`。该接口不验证某个具体设备 token，只表示服务端配置项齐全。

### 6.2 完整 WSS、模型、TTS 冒烟测试

仓库提供只依赖 Python 标准库的脚本：

```bash
python3 scripts/esp32-assistant-smoke.py \
  --url wss://huanwoshidai.com.cn/app-api/campus/esp32/assistant/ws \
  --token "$CAMPUS_ESP32_DEVICE_TOKEN" \
  --device-id esp32-smoke-01 \
  --seconds 1 \
  --output esp32-smoke-output.wav
```

默认上传 1 秒静音；要验证真实语音和视觉输入，可增加：

```bash
python3 scripts/esp32-assistant-smoke.py \
  --url wss://huanwoshidai.com.cn/app-api/campus/esp32/assistant/ws \
  --token "$CAMPUS_ESP32_DEVICE_TOKEN" \
  --pcm question-16k-mono.pcm \
  --jpeg camera.jpg \
  --output answer-24k-mono.wav
```

脚本打印 `RESULT` 且 `audio_bytes` 大于 0，即表示设备鉴权、媒体上行、方舟模型、TTS 和音频下行全部成功。输出文件固定封装为 PCM16LE/24000Hz/单声道 WAV。

### 6.3 验收标准

1. 错误 token 的 WSS 握手返回 `401`，正确 token 返回 `101`。
2. 建连后 5 秒内收到 `connected` 和 `state=listening`。
3. 每轮收到 `turn_ready`；250ms 以下音频返回 `turn_ignored`，有效音频进入 `thinking`。
4. 正常轮次收到 `text_done`、非空音频、`audio_done`、`turn_done`。
5. `interrupt` 能在 1 秒内停止设备播放并收到 `interrupt_ack`。
6. 后台日志能按相同 `device_id`、`request_id` 查到该轮记录和各阶段耗时。

## 7. YAML 配置

配置已加入：

```text
yudao-server/src/main/resources/application.yaml
```

上线至少设置以下变量：

```bash
CAMPUS_ESP32_ASSISTANT_ENABLED=true
CAMPUS_ESP32_DEVICE_TOKENS=<随机设备token，多个用逗号分隔>
CAMPUS_VOLC_ARK_API_KEY=<火山方舟 API Key>
CAMPUS_VOLC_ARK_MODEL=doubao-seed-2-1-pro-260915
CAMPUS_VOLC_ARK_MODEL_URL=https://ark.cn-beijing.volces.com/api/v3/responses
CAMPUS_VOLC_ASR_APP_ID=<火山AppId>
CAMPUS_VOLC_ASR_ACCESS_TOKEN=<火山AccessToken>
CAMPUS_VOLC_TTS_APP_ID=<火山AppId>
CAMPUS_VOLC_TTS_ACCESS_TOKEN=<火山AccessToken>
CAMPUS_VOLC_TTS_RESOURCE_ID=seed-tts-2.0
CAMPUS_VOLC_TTS_VOICE_TYPE=zh_female_roumeinvyou_uranus_bigtts
```

`CAMPUS_VOLC_ARK_MODEL` 应填写方舟控制台中已开通的模型或推理接入点 ID。当前使用 `doubao-seed-2-1-pro-260915`，输入为 ASR 文字和 JPEG 图片。`CAMPUS_VOLC_ARK_API_KEY` 只放在服务器环境变量（例如 `/opt/campus-platform/backend/campus.env`），不要写入 Git。

为兼容尚未更新的生产环境变量，代码检测到官方 Chat Completions 地址或遗留的 `doubao-seed-2-1-turbo-260628` 时，会分别迁移到 Responses 地址和 `doubao-seed-2-1-pro-260915`；仍建议同步更新服务器环境变量。

网关默认走方舟 Responses HTTP 流式接口，发送 ASR 转写文字和 `input_image` 图片内容块，不向模型直接发送音频。如果仍需兼容旧的自建模型，可把 `CAMPUS_VOLC_ARK_MODEL_URL` 改成 `ws://` 或 `wss://` 地址，此时会沿用原有 WebSocket 模型协议。

当前默认资源为 `seed-tts-2.0`，音色为火山“如梦”（`zh_female_roumeinvyou_uranus_bigtts`）。预置的 `zh_...` 音色必须搭配 TTS 资源；`seed-icl-2.0` 只用于已复刻的音色 ID（通常为 `S_...`）。如需切换音色或资源，可分别覆盖 `CAMPUS_VOLC_TTS_VOICE_TYPE`、`CAMPUS_VOLC_TTS_RESOURCE_ID`。

当前模型调用依赖 ASR 转写，生产环境必须保持：

```bash
CAMPUS_ESP32_ASR_ENABLED=true
```

### 7.1 切换多模态模型

模型配置在 `yudao-server/src/main/resources/application.yaml` 中有默认值，当前默认直接可用：

```yaml
campus:
  esp32-assistant:
    enabled: ${CAMPUS_ESP32_ASSISTANT_ENABLED:true}
    model-protocol: ${CAMPUS_LLM_PROTOCOL:chat}
    model-url: ${CAMPUS_LLM_MODEL_URL:https://<workspace>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions}
    model-name: ${CAMPUS_LLM_MODEL:qwen3-vl-flash}
    model-token: ${CAMPUS_LLM_API_KEY:<API Key>}
    model-max-tokens: ${CAMPUS_LLM_MAX_TOKENS:200}
```

如需覆盖，用环境变量即可（见下表；`CAMPUS_LLM_*` 优先于旧的 `CAMPUS_VOLC_ARK_*`）：

```bash
CAMPUS_LLM_PROTOCOL=chat          # auto | responses | chat
CAMPUS_LLM_MODEL_URL=<模型地址>
CAMPUS_LLM_MODEL=<模型名>
CAMPUS_LLM_API_KEY=<模型密钥>
CAMPUS_LLM_MAX_TOKENS=200
```

- `model-protocol: auto` 按地址推断：地址包含 `/chat/completions` 走 OpenAI 兼容 Chat 报文，其余走方舟 Responses 报文；当前默认显式配置为 `chat`。
- Responses 报文：`instructions` + `input[].content[]`，图片块为 `input_image`，上限字段为 `max_output_tokens`。
- Chat 报文：`messages[]`（system + user），图片为 `{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}`，上限字段为 `max_tokens`。设备上传的 JPEG 已经封装为 data URL，可直接内嵌。
- 流式响应两种协议都能解析：Responses 的 `response.output_text.delta` 与 Chat 的 `choices[0].delta.content`，`[DONE]` 均表示结束。
- `enabled` 默认已改为 `true`，凭证齐全时网关直接可用；需要临时下线设备链路时设 `CAMPUS_ESP32_ASSISTANT_ENABLED=false`。
- 模型密钥以默认值形式随代码提交，属于有意为之；若 Key 外泄，去控制台轮换后同步修改 `application.yaml` 默认值。
- 切到第三方 Chat 接口（例如阿里云 PAI-EAS 部署的 GLM、Qwen-VL、vLLM）：

```bash
CAMPUS_LLM_PROTOCOL=chat
CAMPUS_LLM_MODEL_URL=http://<eas-host>/api/predict/<service>/v1/chat/completions
CAMPUS_LLM_MODEL=GLM-5.3-flash
CAMPUS_LLM_API_KEY=<EAS 服务调用 Token>
```

切换后用 `GET /app-api/campus/esp32/assistant/health` 确认 `modelProtocol` / `modelProvider`，再跑 6.2 的冒烟脚本即可。如果第三方接口返回非流式 JSON，或使用了非 OpenAI 字段（例如 `reasoning_content`、自定义鉴权头），需要按本文档补充适配。

第三方接口可先用独立脚本验证通不通（纯标准库，覆盖非流式文本、流式文本、流式图文三类请求）：

```bash
python3 scripts/llm-endpoint-smoke.py \
  --url "http://<eas-host>/api/predict/<service>/v1/chat/completions" \
  --model "GLM-5.3-flash" \
  --api-key "<服务调用 Token>" \
  --image ./camera.jpg     # 可选；缺省用内置占位 JPEG，只验证报文格式
```

### 7.2 已验证兼容的模型候选

以下都是 OpenAI 兼容 `chat/completions` + `image_url`（支持 data URL）+ `stream`，只需填 `CAMPUS_LLM_*` 即可切换，无需改代码：

| 提供方 | model-url | 模型名 | 说明 |
| --- | --- | --- | --- |
| 阿里百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | `qwen3-vl-flash` | 首推：VL 家族最快最便宜，适合导览短问短答 |
| 阿里 MaaS 专属网关 | `https://<ws-xxxx>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions` | 同上 | 一个 Key 覆盖 255 个模型（千问 / GLM / Kimi / DeepSeek / Omni / ASR / TTS），便于收敛供应商 |
| 阿里百炼 | 同上 | `qwen3-vl-plus` | 细节识别更准，但单轮图文约 4s，设备交互偏慢 |
| 阿里百炼 | 同上 | `qwen-vl-ocr` | 实测最快（图文 1.1s），展板文字场景首选 |
| 阿里百炼 | 同上 | `qwen-vl-max-latest` | 老一代 VL，兼容性最稳 |
| 智谱开放平台 | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4.6v` / `glm-4.5v` | 中文 OCR 强，后者极便宜 |
| 火山方舟 | `https://ark.cn-beijing.volces.com/api/v3/responses` | `doubao-seed-*`（填推理接入点 ID） | 走 Responses 协议，`model-protocol` 保持 `auto` / `responses` |
| 自建（PAI-EAS / vLLM） | `http://<host>/v1/chat/completions` | `Qwen3-VL-32B-Instruct` 等 | 数据不出内网，Qwen3-VL 8B 级单卡可跑 |

图片体积注意：JPEG 转 base64 会膨胀约 33%，单轮 3 张 2MB 图片的请求体接近 8MB，切换供应商前要确认其单请求 body 上限。

#### 静态照片实测对比（2048x1365 / 496KB，北京地域，2026-09-18）

| 模型 | 流式文本首包 | 流式图文（含 128 分片） | 备注 |
| --- | --- | --- | --- |
| `qwen-vl-ocr` | 334ms | **1125ms**（63 分片） | 最快，描述具体，适合"看展板/看标牌" |
| `qwen3-vl-flash` | 502ms | **1437ms** | 速度与泛化最佳平衡，默认选择 |
| `qwen3-vl-plus` | 902ms | 4329ms | 精度更高但延迟超出设备交互舒适区 |

选择建议：默认 `qwen3-vl-flash`；确认识别校园标牌 / 展板文字为主的场景可换 `qwen-vl-ocr`；仅在准确率优先、能接受 4s 延迟时用 `qwen3-vl-plus`。

同为 MaaS 网关，`/apps/anthropic` 是 Anthropic Messages 协议（`/v1/messages` + `x-api-key` 头），本项目网关不适用，仅供 Claude Code 类客户端使用。

如需把 ASR + TTS 两步也省掉（模型直接吃音频、吐音频），要改走 Omni 链路：百炼 `qwen3-omni-flash`（HTTP，`modalities=["text","audio"]` 且必须 `stream=true`）或 `qwen3-omni-flash-realtime`（WebSocket 全双工）。这属于链路改造，需要替换 `DoubaoAsrClient` 与 `DoubaoTtsClient` 两段；若改用 MaaS 网关，可用同 Key 的 `qwen3-asr-flash-realtime` 与 `qwen3-tts-flash-realtime` 一并替换火山语音链路。

`output-audio-chunk-bytes: 1920` 表示每个音频帧最多约 40ms 的 24kHz PCM。

音频下发节奏由两个参数控制：

- `playback-pace-percent: 300`：下发速率上限（相对实时的百分比）。**必须大于 100**，否则设备侧播放缓冲永远接近为空，任何 Wi-Fi 抖动都会变成播报断续。
- `output-audio-lead-millis: 1200`：目标播放缓冲领先量。网关先以速率上限把缓冲填到该值，之后维持这一领先量，既不饿到设备也不撑爆设备内存。

诊断方法：逐帧统计音频到达间隔与传输效率。

```bash
python3 scripts/audio-pacing-probe.py --url wss://<host>/app-api/campus/esp32/assistant/ws \
  --token <设备 Token> --pcm question-16k.pcm --jpeg campus.jpg
```

`传输效率` 需明显大于 100%（健康值 150%~300%），`大于 80ms 的间隔` 应为 0 次。实测旧配置（速率 90%）下效率仅 111%，即缓冲以每帧约 4ms 的速度累积，前十几秒几乎为空，这正是设备端「一卡一卡」的根源。

设备侧若仍有断续，按以下顺序排查固件：

1. 关闭 Wi-Fi 省电：`esp_wifi_set_ps(WIFI_PS_NONE)`，这是最常见原因（省电唤醒会带来 100~300ms 停顿）。
2. 收到 `audio_start` 后先缓存约 1 秒再启动 I2S 播放，不要在首帧就开播。
3. 加大 I2S DMA 缓冲（如 `dma_buf_count=8`、`dma_buf_len=512`），并把播放任务固定到单独核心、提高优先级。
4. 统计 I2S DMA underrun 次数：持续增长说明缓冲不足；为 0 则问题在解码或网络接收线程。

也可以直接把实际值写入部署环境的 YAML；不要把正式凭证提交到公开仓库。

### 7.3 本机联调启动

模型配置已在 `application.yaml` 默认值里，本机启动不需要额外设置模型环境变量。前提是 MySQL 与 Redis 已在本机运行：

```powershell
cd C:\campus-xiaohongshu-miniapp
& "C:\Program Files\Java\jdk1.8.0_172\bin\java.exe" -jar campus-platform\yudao-server\target\yudao-server.jar
```

仓库根目录的 `.env.local.ps1`（已被 `.gitignore` 忽略）用于存放本机专属设置，需要点源加载：

```powershell
. .\.env.local.ps1                 # 只含 JAVA_HOME 与数据库连接
```

如果本机数据库密码不是默认的 `123456`，需在 `.env.local.ps1` 中取消注释并填写 `CAMPUS_DB_PASSWORD`，否则启动会报 `Access denied for user 'root'@'localhost'`。

改动 Java 代码后必须重新打包，否则 `target/yudao-server.jar` 仍是旧逻辑：

```powershell
cd C:\campus-xiaohongshu-miniapp\campus-platform
.\mvnw.cmd -o -pl yudao-server -am package -DskipTests
```

启动成功后验证：

```powershell
(Invoke-RestMethod http://localhost:48080/app-api/campus/esp32/assistant/health).data |
  Select-Object enabled, configured, model, modelProtocol, modelProvider
```

## 8. Nginx 配置

校园平台和普通 HTTP 接口使用同一端口时，在现有站点配置中确保 WebSocket 升级头透传：

```nginx
location /app-api/campus/esp32/assistant/ws {
    proxy_pass http://127.0.0.1:48080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 900s;
    proxy_send_timeout 900s;
}
```

## 9. 后台链路日志

管理后台“校园运营 → ESP32链路日志”展示每轮请求的图片、用户提问、模型回答，以及采集、提交模型、ASR、模型首 token、模型总耗时、TTS 首包、TTS 输出和本轮总耗时。列表显示问答摘要，点击“详情”查看完整问答和图片，点击图片放大查看。

数据库按顺序执行 `sql/mysql/campus-esp32-log-upgrade.sql` 和 `sql/mysql/campus-esp32-log-content-upgrade.sql`。自动部署已包含这两个幂等脚本，并在迁移前备份现有日志表。新内容字段仅作用于升级后的轮次；升级前未保存过的图片和问答无法恢复。

页面对应接口为：

```text
GET /admin-api/campus/esp32/log/page
GET /admin-api/campus/esp32/log/summary
GET /admin-api/campus/esp32/log/get?id=日志编号
GET /admin-api/campus/esp32/log/image?id=图片编号
```

用户提问取自本轮 ASR 转写全文；回答取自模型本轮返回的全文。`asrStatus` 区分 `PENDING`（转写中）、`SUCCESS`、`FAILED`、`DISABLED`，旧日志该字段为空。ASR 成功后才会提交模型；失败时本轮不会调用模型。

图片异步保存到独立的私有数据库表，每轮最多 3 张 JPEG，单张最多 2MB。列表和详情仅返回图片数量、编号与大小，图片内容通过单独的鉴权接口读取，要求已登录且有 `campus:esp32-log:query` 权限。前端用携带登录身份的请求加载图片，关闭详情时释放临时预览地址，不生成公开图片链接。

列表新增 `questionText`、`answerText`（最多 160 字的摘要）、`asrStatus`、`contentRecorded`、`storedImageCount`。详情返回完整问答和 `images: [{id, imageIndex, sizeBytes, mimeType}]`；`contentRecorded=false` 表示该轮未启用内容保存，`storedImageCount` 表示实际已保存图片数，不等同于设备上报数量。图片接口返回原始 `image/jpeg`，设置禁止缓存。

设备 Token 和原始语音不写入日志。数据库写入故障仍以对话不中断为优先，排障时应同时检查服务端日志。

耗时字段定义：

| 字段 | 起止点 |
| --- | --- |
| `captureMs` | 收到 `turn_start` 到收到 `turn_commit` |
| `submitMs` | 网关组装数据并提交上游模型所用时间 |
| `asrMs` | ASR 请求总耗时；完成转写后才提交模型 |
| `modelFirstTokenMs` | 提交模型到收到第一个文本增量 |
| `modelTotalMs` | 提交模型到收到 `text_done` |
| `ttsFirstAudioMs` | 本轮开始到收到第一包 TTS 音频 |
| `ttsAudioMs` | 第一包 TTS 音频到 TTS 完成 |
| `totalMs` | 收到 `turn_start` 到发送 `turn_done` |

## 10. 编译与上线检查

在 `campus-platform` 目录执行：

```bash
./mvnw -pl yudao-server -am -DskipTests package
```

上线后依次检查：

1. 健康接口返回 `enabled=true`、`configured=true`。
2. 设备收到 `connected` 和 `state=listening`。
3. 按键提问后日志出现 `ESP32_TURN_SUBMITTED`。
4. 模型有返回时出现 `ESP32_MODEL_DONE`。
5. 第一段 TTS 下发时出现 `ESP32_FIRST_TTS_AUDIO`。
6. ASR 成功后出现 `ESP32_ASR_COMPLETED`，随后模型和 TTS 阶段正常完成。
