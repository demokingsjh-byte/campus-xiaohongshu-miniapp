# ESP32-S3 视听说机器人中间服务

## 1. 目标与边界

本服务位于 `yudao-module-campus`，默认通过火山方舟 Chat Completions 调用多模态模型，并复用火山 ASR 和火山 TTS，给 XIAO ESP32-S3 Sense 提供“听、看、理解、说”的闭环。

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
   │                                ├── WAV + JPEG ────────────────> 火山方舟多模态模型
   │                                ├── WAV（异步）────────────────> 火山 ASR
   │                                │<── text_delta / text_done ─── 多模态模型
   │                                ├── 分句文字流 ────────────────> 火山 TTS
   │<── audio_start ────────────────┤                                │
   │<── PCM16LE / 24k 二进制流 ─────┤<── PCM 音频流 ─────────────── 火山 TTS
   │<── audio_done / turn_done ─────┤                                │
   │        （等待 650ms）           │                                │
   │<── listening ──────────────────┤                                │
```

关键设计：

- `turn_commit` 后将 WAV 音频和本轮 JPEG 图片转成方舟 Chat Completions 的多模态消息，一次请求完成语音理解和图片理解。
- 方舟接口使用流式响应，网关将每个文本增量转换为设备协议的 `text_delta`，因此仍可按句启动 TTS。
- ASR 与模型并行，只记录用户说话文字和耗时；ASR 失败不会阻断回答。
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

### 4.1 设备发送的 JSON

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

### 4.2 设备发送的二进制帧

每条 WebSocket 二进制消息的第一个字节表示媒体类型：

- `0x01`：后续数据为 PCM16LE、16000Hz、单声道。
- `0x02`：后续数据为一张完整 JPEG。

建议顺序：`turn_start` → 连续音频帧 → 0~3 张 JPEG → `turn_commit`。只有 `turn_commit` 会触发模型回答。

### 4.3 服务返回

控制事件包括：

- `connected`：连接成功及媒体参数。
- `state`：`listening`、`capturing`、`thinking`、`speaking`、`cooldown`。
- `turn_ready`、`image_received`、`text_delta`、`text_done`。
- `audio_start`、`audio_done`、`turn_done`。
- `interrupt_ack`、`turn_ignored`、`error`。

`audio_start` 与 `audio_done` 之间的二进制消息没有类型前缀，内容固定为 PCM16LE、24000Hz、单声道，可直接送入 ESP32 I2S 播放队列。

## 5. YAML 配置

配置已加入：

```text
yudao-server/src/main/resources/application.yaml
```

上线至少设置以下变量：

```bash
CAMPUS_ESP32_ASSISTANT_ENABLED=true
CAMPUS_ESP32_DEVICE_TOKENS=<随机设备token，多个用逗号分隔>
CAMPUS_VOLC_ARK_API_KEY=<火山方舟 API Key>
CAMPUS_VOLC_ARK_MODEL=doubao-seed-2-0-mini-260428
CAMPUS_VOLC_ARK_MODEL_URL=https://ark.cn-beijing.volces.com/api/v3/chat/completions
CAMPUS_VOLC_ASR_APP_ID=<火山AppId>
CAMPUS_VOLC_ASR_ACCESS_TOKEN=<火山AccessToken>
CAMPUS_VOLC_TTS_APP_ID=<火山AppId>
CAMPUS_VOLC_TTS_ACCESS_TOKEN=<火山AccessToken>
CAMPUS_VOLC_TTS_VOICE_TYPE=zh_female_roumeinvyou_uranus_bigtts
```

`CAMPUS_VOLC_ARK_MODEL` 应填写方舟控制台中已开通的模型或推理接入点 ID。默认示例为 `doubao-seed-2-0-mini-260428`；需要更强推理能力时可以改成已开通的 Seed Pro 接入点。`CAMPUS_VOLC_ARK_API_KEY` 只放在服务器环境变量（例如 `/opt/campus-platform/backend/campus.env`），不要写入 Git。

网关默认走方舟 HTTP 流式接口。如果仍需兼容旧的自建模型，可把 `CAMPUS_VOLC_ARK_MODEL_URL` 改成 `ws://` 或 `wss://` 地址，此时会沿用原有 WebSocket 模型协议；HTTP 地址则按方舟 Chat Completions 协议发送 `input_audio`、`image_url` 和 `text` 内容块。

当前默认资源为 `seed-icl-2.0`，音色为火山“如梦”（`zh_female_roumeinvyou_uranus_bigtts`）；如需切换其他音色，只覆盖 `CAMPUS_VOLC_TTS_VOICE_TYPE` 即可。

如果暂时不需要用户语音转文字日志，可设：

```bash
CAMPUS_ESP32_ASR_ENABLED=false
```

`output-audio-chunk-bytes: 1920` 表示每次最多发送约 40ms 的 24kHz PCM；`playback-pace-percent: 90` 表示按实际播放时长的 90% 节流，通常兼顾连续播放和设备缓冲安全。

也可以直接把实际值写入部署环境的 YAML；不要把正式凭证提交到公开仓库。

## 6. Nginx 配置

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

## 7. 后台链路日志

执行 `sql/mysql/campus-esp32-log-upgrade.sql` 后，管理后台“校园运营 → ESP32链路日志”会展示每轮请求的设备编号、请求编号、状态及以下耗时：采集、提交模型、ASR、模型首 token、模型总耗时、TTS 首包、TTS 输出和本轮总耗时。

页面对应接口为：

```text
GET /admin-api/campus/esp32/log/page
GET /admin-api/campus/esp32/log/summary
GET /admin-api/campus/esp32/log/get?id=日志编号
```

日志不会落库 device_token、音频、图片或对话原文；数据库未执行升级脚本时，设备链路仍可运行，但后台不会有记录。

## 8. 编译与上线检查

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
6. ASR 日志异步出现 `ESP32_ASR_COMPLETED`，其失败不影响前五步。
