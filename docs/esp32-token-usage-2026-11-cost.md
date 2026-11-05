# ESP32 后台费用估算（2026-11-05）

## 背景

2026-11-05 默认模型切到火山方舟 `doubao-seed-2-1-lite`（Responses 协议）后，Token 统计只接在旧 qwen omni 实时链路，方舟轮次全部没有计量。本次补齐方舟 usage 采集，并在“校园运营 → ESP32链路日志”新增费用。

## 采集

- `GuideModelClient` 读取 Responses 终态事件 `response.completed / failed / incomplete` 中的 `response.usage` 与 `response.id`，随 `text_done` / `error` 交给网关；网关用既有 `Esp32TokenUsage` 规范化后写入日志，下发设备前移除。
- `responses` 协议的新轮次起始状态为 `PENDING`；chat 兼容协议暂不采集。
- 切换前（2026-11-05 修复部署前）的方舟轮次没有 usage，无法补回，模型费显示为横线。

## 费用口径

费用 = 配置单价 × 已落库的实际用量，读取时计算，不新增表字段；改单价后历史行按新单价重算。

| 项目 | 用量来源 | 默认单价（官方后付费标价） | 配置项（`campus.esp32-assistant.pricing.*` / 环境变量） |
| --- | --- | --- | --- |
| 方舟模型输入 | `input_tokens - input_cached_tokens` | 0.8 元/百万 Token | `model-input-per-million` / `CAMPUS_PRICE_MODEL_INPUT` |
| 方舟缓存命中 | `input_cached_tokens` | 0.16 元/百万 Token | `model-cached-input-per-million` / `CAMPUS_PRICE_MODEL_CACHED_INPUT` |
| 方舟模型输出 | `output_tokens` | 2.7 元/百万 Token | `model-output-per-million` / `CAMPUS_PRICE_MODEL_OUTPUT` |
| 火山流式 ASR | `audio_bytes ÷ 32000` 秒（ASR 关闭时不计） | 1 元/小时 | `asr-per-hour` / `CAMPUS_PRICE_ASR_PER_HOUR` |
| 火山 TTS 2.0 | 回答字数 `CHAR_LENGTH(answer_text)` | 3 元/万字符 | `tts-per-ten-thousand-chars` / `CAMPUS_PRICE_TTS_PER_10K_CHARS` |
| 历史 qwen omni | 各模态 Token | 1.5 / 6 / 4.5 / 12 元/百万 | `realtime-*-per-million` |

注意：

- 默认单价不含账户折扣、资源包与免费额度；有折扣或资源包时按实际单价覆盖，实付以云厂商账单为准。
- 方舟按输入长度分档计价，默认取最低档；输入很长（多图、长会话）时可能低估，可按账单单价调整。
- 模型未返回计量时模型费为空，不按 0 计入；ASR/TTS 仍按实际音频与字数计算。
- 实时链路旁路转写、异步补录 ASR 不计入。
