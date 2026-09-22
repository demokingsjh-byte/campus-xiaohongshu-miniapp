# ESP32-S3 视听说机器人中间服务

## 1. 目标与边界

本服务位于 `yudao-module-campus`。默认链路使用 `qwen3.8-omni-flash-realtime`：设备的 PCM 音频与 JPEG 图片在同一条上游实时会话中持续输入，模型直接流式返回回答文字和 24kHz PCM。用户语音转写由实时会话旁路产生，只用于后台日志，不是模型回答的前置条件。

它是校园平台内的一条独立设备链路，不修改原 AI_GUIDE H5 视频通话接口。设备仍以 `turn_start` / `turn_commit` 手动分轮，摄像头画面变化不会单独触发回答。`chat` / `responses` 保留为回退协议；回退时才使用“火山 ASR → 图文模型 → 火山 TTS”的旧链路。

## 2. 完整流程

```text
ESP32-S3                      campus-platform                  Qwen Omni Realtime
   │                                │                                │
   ├── WebSocket + 设备 token ─────>│                                │
   │<── connected / listening ──────┤<──── 上游实时会话已就绪 ───────┤
   │                                │                                │
   ├── turn_start ─────────────────>│                                │
   ├── 0x01 + PCM 音频帧 ──────────>├── input_audio_buffer.append ──>│
   ├── 0x02 + JPEG（目标 3 张）─────>├── input_image_buffer.append ──>│
   ├── turn_commit ────────────────>├── commit / response.create ───>│
   │                                │<── 回答文字增量 + 音频增量 ─────┤
   │<── text_delta（与音频可交错）───┤                                │
   │<── audio_start ────────────────┤                                │
   │<── PCM16LE / 24k 二进制流 ─────┤                                │
   │<── text_done / audio_done ─────┤                                │
   │<── turn_done ──────────────────┤                                │
   │       （默认冷却 1250ms）        │                                │
   │<── listening ──────────────────┤                                │
   │                                │<── 用户语音旁路转写（可晚到）─────┤
```

关键设计：

- 每个设备连接对应一条上游 Omni Realtime WebSocket，同一连接保留多轮上下文；设备协议仍是半双工，回答期间不继续上传下一轮媒体。
- PCM 在采集期间直接转发给模型，不等待 `turn_commit` 才上传整段录音。手动提交后，模型直接生成文字和音频，不再串行等待独立 ASR 与 TTS。
- `input_audio_transcription` 使用 `qwen3-asr-flash-realtime` 生成用户问题文字。该结果是日志旁路；即使转写晚到或失败，已开始的模型回答不受影响。
- 默认每轮目标 3 张图片：开始、中途、提交前各一张；模型以最后一张作为当前画面，前两张只用于观察变化。图片是离散快照，不等同实时视频。
- 上游实时接口要求图片体积更小。网关会把送模型的副本缩放、压缩到安全范围，后台仍异步保存设备上传的原始 JPEG。
- `chat` / `responses` 回退模式仍在采集期流式喂火山 ASR，提交后等待最终文字，再调用图文模型并用火山 TTS 合成音频。只有该回退链路中，ASR 才是回答前置步骤。
- 返回音频采用网关限速分片下发；`playback-pace-percent` 和 `output-audio-lead-millis` 控制发送节奏。当前固件直接写入 I2S，并未实现独立的设备播放环形缓冲。
- `cooldown-millis`（默认 1250）用于回答结束后留出播放尾音与重新收音的间隔；具体是否足够需由设备实测确认。
- AI 播放期间采用半双工，不自动采集下一轮；设备发送 `interrupt` 可手动打断。
- 网关一轮最多接收 5 张 JPEG，单张最大 2MB、总计最大 8MB（可配置）；当前固件本轮目标为 `min(3, connected.input_image.max_count)`。
- 服务端静音自动提交：网关在每帧 PCM 上做能量检测，用于兜底设备固件 VAD。旧链路配置默认 1500ms；实时链路至少等待 3500ms，给提交前第三张图留出时间。触发时记录 `[ESP32_SILENCE_AUTO_COMMIT]` 日志。

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

`configured=true` 只表示当前协议需要的配置项已经填全，不代表此刻已成功连接上游。默认实时链路还会返回 `pipeline=native-realtime-audio`、`modelInput=audio,image`、`modelOutput=text,audio`、`transcriptionMode=sidecar-async`；回退链路为 `pipeline=asr-vision-tts`、`transcriptionMode=blocking-before-model`。输入与输出音频格式分别固定为 PCM16LE/16kHz/单声道和 PCM16LE/24kHz/单声道。

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
2. 按键或唤醒后立即开始本地预录并发送 `turn_start`；收到 `turn_ready` 前不得向网关发媒体，收到后先冲刷预录缓冲，再上传后续音频，避免吞掉问题开头。
3. 采集期间异步抓拍开始、中途、提交前 3 张图片。停止说话后最多给末张图约 280ms 宽限，随后立即发送 `turn_commit`；图片异常不能把语音提交重新拖慢。
4. 收到 `audio_start` 后打开 24kHz I2S 播放；当前固件将二进制 PCM 直接写入 I2S。
5. 收到 `audio_done` 后等待音频播放结束；收到 `turn_done` 后等待服务再次下发 `listening`。
6. 播放中需要打断时发送 `interrupt`，收到 `interrupt_ack` 后停止播放。
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
  "model": "qwen3.8-omni-flash-realtime",
  "input_audio": {"binary_prefix": 1, "format": "pcm_s16le", "sample_rate": 16000, "channels": 1},
  "input_image": {"binary_prefix": 2, "format": "jpeg", "max_count": 5},
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

建议顺序：`turn_start` → `turn_ready` → 连续音频帧并穿插 3 张 JPEG → `turn_commit`。只有 `turn_commit` 会触发模型回答；`max_count` 是网关上限，不是要求固件每轮必须拍满。

音频要求与建议：

- 音频是裸 PCM，不带 WAV 文件头；格式为有符号 16 位小端、16000Hz、单声道。
- 每个 WebSocket 二进制消息前加一个字节 `0x01`，后面建议放 20ms（640 字节）或 40ms（1280 字节）音频。
- 有效音频至少 8000 字节（250ms），默认最多 960000 字节（30 秒）。
- JPEG 必须在同一条 WebSocket 二进制消息中完整发送，前面加一个字节 `0x02`；不允许拆成多条协议消息。
- 网关接收的原始图片单张最大 2MB，每轮最多 5 张、合计最大 8MB（可通过 `campus.esp32-assistant.max-image-*` 调整）。为减少上行与实时模型压缩开销，固件应把单张 JPEG 控制在 180KB 左右；没有图片时只发音频即可。

### 4.5 服务返回

控制事件包括：

- `connected`：连接成功及媒体参数。
- `state`：`listening`、`capturing`、`thinking`、`speaking`、`cooldown`。
- `turn_ready`、`image_received`、`text_delta`、`text_done`。
- `audio_start`、`audio_done`、`turn_done`。
- `interrupt_ack`、`turn_ignored`、`silence_committed`、`error`。

`audio_start` 与 `audio_done` 之间的二进制消息没有类型前缀，内容固定为 PCM16LE、24000Hz、单声道，可直接送入 ESP32 I2S 播放队列。

一轮成功响应的典型顺序：

```text
state(capturing) → turn_ready → state(thinking)
→ text_delta / audio_start / PCM 二进制帧（文字与音频可交错）
→ text_done → audio_done → turn_done
→ state(cooldown) → state(listening)
```

文本事件示例：

```json
{"type":"text_delta","request_id":"turn-001","text":"你好"}
{"type":"text_done","request_id":"turn-001","text":"你好，需要我帮你看什么？"}
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
| `IMAGE_TOO_LARGE_FOR_MODEL` | 图片压缩后仍超过实时模型单帧上限 | 降低分辨率或 JPEG 质量；本轮可继续提交音频 |
| `TOO_MANY_IMAGES` / `IMAGES_TOO_LARGE` | 图片数量或总大小超限 | 最多保留 `max-image-count` 张，超过后本轮忽略后续图片 |
| `MODEL_UNAVAILABLE` / `GATEWAY_ERROR` | 模型链路不可用 | `retryable=true` 时退避后重试/重连 |
| `MODEL_AUDIO_EMPTY` | 实时模型未返回回答音频 | 结束本轮并提示用户重试 |
| `TTS_FAILED` | chat/responses 回退链路语音合成失败 | 显示文本，等待 `turn_done` 后进入下一轮 |

任何 `error` 都包含 `retryable`。设备应保留 `request_id`，只处理当前轮次的事件；旧轮次迟到的数据直接丢弃。

## 5. ESP32 固件对接流程

### 5.1 初始化

1. 初始化 Wi-Fi、摄像头、麦克风/I2S RX 和扬声器/I2S TX。
2. 从 NVS 读取 `device_token`、唯一 `device_id` 和服务器地址；不要把生产 token 打印到串口日志。
3. 校时并校验证书，连接 `wss://huanwoshidai.com.cn/app-api/campus/esp32/assistant/ws`。
4. 建议优先在请求头放 `Authorization: Bearer <token>` 和 `X-Device-Id`；库不支持自定义头时使用查询参数。

### 5.2 一轮采集

```text
按键按下/唤醒
  → 立即本地预录并发送 turn_start
  → 等 turn_ready（此时只缓存，不上传）
  → 冲刷预录音频，再每 20~40ms 发送 [0x01][PCM]
  → 异步抓拍开始/中途/提交前 3 张 JPEG，发送 [0x02][完整 JPEG]
  → VAD 静音或按键松开
  → 末张图最多宽限约 280ms，发送 turn_commit
```

上传音频期间不要进行采样率转换；麦克风若输出 32 位 I2S 数据，应先在设备端转换为饱和的 PCM16LE。摄像头图片建议使用 QVGA/VGA JPEG，优先把单张控制在 180KB 内。抓拍任务与音频采集、WebSocket 上行解耦，图片失败或变慢时优先保证音频按时提交。

### 5.3 接收与播放

收到 `audio_start` 后，将 I2S TX 配置为 24000Hz、16 位、单声道。之后每个二进制 WebSocket 消息都是音频，不含 `0x01`/`0x02` 前缀。当前固件直接写入 I2S；若实测 Wi-Fi 抖动造成断续，再考虑增加小型播放缓冲。

### 5.4 request_id 规则

建议格式为 `<device-id>-<启动计数>-<轮次>`，最长 100 字符，例如 `xiao-01-18-0032`。一次连接内必须唯一，方便后台按设备和轮次定位每一段耗时。

## 6. 联调和验收

### 6.1 健康检查

```bash
curl --fail --silent --show-error \
  https://huanwoshidai.com.cn/app-api/campus/esp32/assistant/health
```

期望 `code=0`、`enabled=true`、`configured=true`。该接口不验证某个具体设备 token，只表示服务端配置项齐全。

### 6.2 完整 WSS、实时模型与音频下行冒烟测试

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

脚本打印 `RESULT` 且 `audio_bytes` 大于 0，即表示设备鉴权、媒体上行、当前模型链路和音频下行全部成功。默认实时链路的音频由模型直接生成；只有回退链路会经过独立 TTS。输出文件固定封装为 PCM16LE/24000Hz/单声道 WAV。

### 6.3 验收标准

1. 错误 token 的 WSS 握手返回 `401`，正确 token 返回 `101`。
2. 建连后 5 秒内收到 `connected` 和 `state=listening`。
3. 每轮收到 `turn_ready`；250ms 以下音频返回 `turn_ignored`，有效音频进入 `thinking`。
4. 正常轮次收到 `text_done`、非空音频、`audio_done`、`turn_done`。
5. `interrupt` 能在 1 秒内停止设备播放并收到 `interrupt_ack`。
6. 后台日志能按相同 `device_id`、`request_id` 查到该轮记录、3 张图片和各阶段耗时；旁路转写晚到或失败不得把已完成回答标成主链路失败。
7. 固件上报设备计时后，后台可区分“网关首音频”和“设备扬声器首播”，并展示“说完 → 设备首播”；未收到设备计时时明确显示为空，不用网关时间冒充设备出声时间。

## 7. YAML 配置

配置已加入：

```text
yudao-server/src/main/resources/application.yaml
```

默认实时链路上线至少设置以下变量。生产凭证只放服务器环境文件（例如 `/opt/campus-platform/backend/campus.env`），不要新增到 Git：

```bash
CAMPUS_ESP32_ASSISTANT_ENABLED=true
CAMPUS_ESP32_DEVICE_TOKENS=<随机设备token，多个用逗号分隔>
CAMPUS_LLM_PROTOCOL=omni-realtime
CAMPUS_LLM_REALTIME_URL=wss://<workspace>.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime
CAMPUS_LLM_REALTIME_MODEL=qwen3.8-omni-flash-realtime
CAMPUS_LLM_REALTIME_VOICE=Tina
CAMPUS_LLM_REALTIME_API_KEY=<阿里云百炼 API Key>
```

`CAMPUS_LLM_PROTOCOL` 必须显式确认。生产环境若遗留 `CAMPUS_LLM_PROTOCOL=chat`，它会覆盖 `application.yaml` 的新默认值，部署新代码后仍然走旧链路。上线后通过 health 的 `pipeline`、`modelProtocol`、`model`、`modelInput`、`modelOutput` 核对实际配置。

默认实时链路不依赖 `CAMPUS_VOLC_ASR_*` 与 `CAMPUS_VOLC_TTS_*` 回答；这些变量仅供下述回退链路使用。`configured=true` 只表示字段齐全，上游连接是否可用仍以设备连接日志和真实请求为准。

### 7.1 实时链路配置

```yaml
campus:
  esp32-assistant:
    enabled: ${CAMPUS_ESP32_ASSISTANT_ENABLED:true}
    model-protocol: ${CAMPUS_LLM_PROTOCOL:omni-realtime}
    realtime-model-url: ${CAMPUS_LLM_REALTIME_URL:wss://<workspace>.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime}
    realtime-model-name: ${CAMPUS_LLM_REALTIME_MODEL:qwen3.8-omni-flash-realtime}
    realtime-voice: ${CAMPUS_LLM_REALTIME_VOICE:Tina}
    realtime-model-token: ${CAMPUS_LLM_REALTIME_API_KEY:}
    model-token: ${CAMPUS_LLM_API_KEY:<旧链路 API Key>}
```

网关建立连接时会把模型名作为 `model` 查询参数加入实时 URL。会话配置为手动分轮、PCM16LE/16kHz/单声道输入、PCM16LE/24kHz/单声道输出，并启用 `qwen3-asr-flash-realtime` 旁路转写。旁路转写可能在回答完成后才写入日志。

实时链路优先使用独立的 `realtime-model-token`；未配置时才回退到 `model-token`。生产环境应单独设置 `CAMPUS_LLM_REALTIME_API_KEY`，避免把阿里 Key 发送给旧图文模型提供方。

### 7.2 切回 ASR + 图文模型 + TTS

设置 `chat` 或 `responses` 后，网关恢复旧链路：采集期流式送火山 ASR，提交后把最终文字和 JPEG 交给图文模型，再由火山 TTS 生成音频。

```bash
CAMPUS_LLM_PROTOCOL=chat
CAMPUS_LLM_MODEL_URL=https://<workspace>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions
CAMPUS_LLM_MODEL=qwen3-vl-flash
CAMPUS_LLM_API_KEY=<该接口对应的 API Key>
CAMPUS_ESP32_ASR_ENABLED=true
CAMPUS_VOLC_ASR_APP_ID=<火山 AppId>
CAMPUS_VOLC_ASR_ACCESS_TOKEN=<火山 AccessToken>
CAMPUS_VOLC_TTS_APP_ID=<火山 AppId>
CAMPUS_VOLC_TTS_ACCESS_TOKEN=<火山 AccessToken>
CAMPUS_VOLC_TTS_RESOURCE_ID=seed-tts-2.0
CAMPUS_VOLC_TTS_VOICE_TYPE=zh_female_roumeinvyou_uranus_bigtts
```

- `model-protocol: auto` 只推断非实时 `model-url`：包含 `/chat/completions` 走 OpenAI 兼容 Chat，其余走 Responses；它不会自动启用 Omni Realtime。
- Responses 报文使用 `instructions` + `input[].content[]`，图片块为 `input_image`，上限字段为 `max_output_tokens`。
- Chat 报文使用 `messages[]`（system + user），图片为 `image_url` data URL，上限字段为 `max_tokens`。
- `scripts/llm-endpoint-smoke.py` 只验证非实时 Chat/Responses 图文接口，不验证 Omni Realtime。

### 7.3 回退链路可选图文模型

以下模型只用于 OpenAI 兼容 `chat/completions` + `image_url` + `stream` 回退链路：

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

回退链路选择建议：优先 `qwen3-vl-flash`；确认识别校园标牌 / 展板文字为主的场景可换 `qwen-vl-ocr`；仅在准确率优先、能接受更高延迟时用 `qwen3-vl-plus`。

同为 MaaS 网关，`/apps/anthropic` 是 Anthropic Messages 协议（`/v1/messages` + `x-api-key` 头），本项目网关不适用，仅供 Claude Code 类客户端使用。

默认生产方案已经使用 Omni Realtime，不能再把“HTTP 输出 stream=true”误当作流式输入；只有实时 WebSocket 会在用户说话期间持续接收音频。

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
2. 收到 `audio_start` 后先缓存约 40~80ms 再启动 I2S 播放；若现场 Wi-Fi 抖动明显，再按设备内存逐步增大。
3. 加大 I2S DMA 缓冲（如 `dma_buf_count=8`、`dma_buf_len=512`），并把播放任务固定到单独核心、提高优先级。
4. 统计 I2S DMA underrun 次数：持续增长说明缓冲不足；为 0 则问题在解码或网络接收线程。

也可以直接把实际值写入部署环境的 YAML；不要把正式凭证提交到公开仓库。

### 7.4 本机联调启动

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

管理后台“校园运营 → ESP32链路日志”展示每轮请求的图片、用户提问、模型回答，以及采集、提交、旁路转写、模型首响应、回答生成、网关首音频、音频输出和本轮总耗时。列表显示问答摘要，点击“详情”查看完整问答和图片，点击图片放大查看。

数据库按顺序执行 `sql/mysql/campus-esp32-log-upgrade.sql` 和 `sql/mysql/campus-esp32-log-content-upgrade.sql`。自动部署已包含这两个幂等脚本，并在迁移前备份现有日志表。新内容字段仅作用于升级后的轮次；升级前未保存过的图片和问答无法恢复。

页面对应接口为：

```text
GET /admin-api/campus/esp32/log/page
GET /admin-api/campus/esp32/log/summary
GET /admin-api/campus/esp32/log/get?id=日志编号
GET /admin-api/campus/esp32/log/image?id=图片编号
```

用户提问取自本轮语音转写全文；回答取自模型本轮返回的全文。`asrStatus` 区分 `PENDING`（转写中）、`SUCCESS`、`FAILED`、`DISABLED`，旧日志该字段可能为空。在默认实时链路中，转写只用于留存，回答可在转写完成前开始；`COMPLETED + asrStatus=FAILED` 表示回答成功但日志转写未返回，不是主链路失败。只有 chat/responses 回退链路必须先取得 ASR 文字才能调用图文模型。

图片异步保存到独立的私有数据库表，每轮最多 5 张 JPEG（可通过 `campus.esp32-assistant.max-image-count` 调整），单张最多 2MB。列表和详情仅返回图片数量、编号与大小，图片内容通过单独的鉴权接口读取，要求已登录且有 `campus:esp32-log:query` 权限。前端用携带登录身份的请求加载图片，关闭详情时释放临时预览地址，不生成公开图片链接。

列表新增 `questionText`、`answerText`（最多 160 字的摘要）、`asrStatus`、`contentRecorded`、`storedImageCount`。详情返回完整问答和 `images: [{id, imageIndex, sizeBytes, mimeType}]`；`contentRecorded=false` 表示该轮未启用内容保存，`storedImageCount` 表示实际已保存图片数，不等同于设备上报数量。图片接口返回原始 `image/jpeg`，设置禁止缓存。

设备 Token 和原始语音不写入日志。数据库写入故障仍以对话不中断为优先，排障时应同时检查服务端日志。

耗时字段定义。为兼容历史表，部分数据库字段仍沿用 `asr` / `tts` 命名，后台展示以实际起止点为准：

| 字段 | 起止点 |
| --- | --- |
| `captureMs` | 网关收到 `turn_start` 到收到 `turn_commit`；包含说话、静音判定和提交前图片宽限，不等同纯录音时长 |
| `speechEndMs` | 设备判定说完时相对本轮开始的时间 |
| `speechEndToCommitMs` | 设备判定说完到发出 `turn_commit` 的时间 |
| `deviceFirstAudioMs` | 设备收到首个回答音频包时相对本轮开始的时间 |
| `deviceFirstPlaybackMs` | 设备首次成功写入 I2S 时相对本轮开始的时间；不等同实际声学出声时间 |
| `speechEndToPlaybackMs` | `deviceFirstPlaybackMs - speechEndMs`；设备指标缺失时为空 |
| `commitToFirstAudioMs` | `turn_commit` 到网关收到首个回答音频；由 `ttsFirstAudioMs - captureMs` 计算，不含设备播放缓冲 |
| `submitMs` | 网关发送提交事件所用时间；不表示上游完成推理 |
| `asrMs` | 实时链路为提交后旁路转写返回时间，不阻塞回答；回退链路为前置 ASR 收尾时间 |
| `modelFirstTokenMs` | 请求回答到首个文字或音频事件，后台应显示为“模型首响应”而非只称 token |
| `modelTotalMs` | 上游回答文字/音频转写完成时间；不等同整段音频已发送完毕 |
| `ttsFirstAudioMs` | 网关收到 `turn_start` 到收到首个回答音频；不代表设备扬声器已经出声 |
| `ttsAudioMs` | 网关收到首个回答音频到上游回答/音频发送结束 |
| `totalMs` | 收到 `turn_start` 到发送 `turn_done` |

固件在 `turn_commit` 中上报 `speech_end_ms`、`speech_end_to_commit_ms`，在 `turn_metrics` 中按 `request_id` 上报 `first_audio_received_ms` 和 `first_i2s_write_ms`。后台据此展示“说完 → 首次 I2S 写入”；设备未回传时显示空值，不用网关首包时间冒充设备播放时间。

## 10. 编译与上线检查

在 `campus-platform` 目录执行：

```bash
./mvnw -pl yudao-server -am -DskipTests package
```

上线后依次检查：

1. 健康接口返回 `enabled=true`、`configured=true`。
2. 设备收到 `connected` 和 `state=listening`。
3. 默认实时链路提交后日志出现 `ESP32_OMNI_TURN_SUBMITTED`；回退链路仍为 `ESP32_TURN_SUBMITTED`。
4. 模型有返回时，后台出现非空回答文字与回答音频；实时链路不等待旁路转写才开始回答。
5. 第一段回答音频到达网关后，`ttsFirstAudioMs` 有值；它只代表网关首包。
6. 用户转写最终变为 `SUCCESS`，或在旁路转写不可用时标为 `FAILED` 但不改变已经成功的回答状态。
