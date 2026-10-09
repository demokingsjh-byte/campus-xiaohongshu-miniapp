# ESP32 每轮 Token 用量与费用观察

更新：2026-10-09。该改动只涉及网关、日志数据库、管理后台与部署迁移；不更换模型、不改变音频流、3 张图片或会话记忆，不需要重烧固件。

## 后台入口与读取范围

进入“校园运营 → ESP32链路日志”：列表新增每轮输入、输出和总 Token；详情新增用量卡片；顶部新增累计 Token 和已返回统计的轮数。累计数据使用当前设备、请求、状态、时间筛选，缺失字段不参与该字段求和，也不按 0 补齐。

接口仍使用现有鉴权与 `campus:esp32-log:query` 权限：

| 接口 | 返回内容 |
| --- | --- |
| `GET /admin-api/campus/esp32/log/page` | 各轮用量状态、响应 ID、计数，不带 usage JSON |
| `GET /admin-api/campus/esp32/log/get?id=...` | 以上字段及规范化计量 JSON |
| `GET /admin-api/campus/esp32/log/summary` | 已返回/未获得统计轮数及输入、输出、总 Token 累计 |

## 采集实现与口径

默认实时模型目前配置为阿里云百炼北京地域的 `qwen3.8-omni-flash-realtime`，实际每轮模型以日志 `modelName` 为准。网关读取官方完成事件的 `response.usage`，不从字数、音频大小、录音时长估算。官方字段定义见 [服务端事件](https://help.aliyun.com/zh/model-studio/server-events)。

| API 字段 | 含义 |
| --- | --- |
| `usageStatus` | 等待/已返回/未获得统计；历史未采集为空 |
| `usageResponseId` | 计量对应的上游响应 ID |
| `inputTokens`, `outputTokens`, `totalTokens` | 上游输入、输出、总量，各字段独立保存 |
| `inputTextTokens`, `inputAudioTokens`, `inputImageTokens`, `inputVideoTokens` | 上游明确返回的输入模态计数 |
| `inputCachedTokens` | 缓存输入，已包含于输入总量，不能重复相加 |
| `outputTextTokens`, `outputAudioTokens` | 上游明确返回的输出模态计数 |
| `usageJson` | 仅数字白名单的规范化 JSON，最大 8KiB，不是完整原始响应 |

实现位于 `Esp32TokenUsage`、`OmniRealtimeClient`、`Esp32AssistantWebSocketHandler` 和 `CampusEsp32LogServiceImpl`。兼容复数 `input_tokens_details`/`output_tokens_details` 与旧单数形式；仅接收非负整数，拒绝负数、字符串、小数和越界数。真实 0 保留；没有返回总量时不以输入+输出伪造总量。

`response.created` 将响应 ID 绑定原轮次；`response.done` 在清理活动轮次前采集，包括有 usage 的失败或取消响应。重复完成事件不会累计两次，迟到旧响应不采用新轮日志 ID。用量持久化独立异步执行，异常不阻塞模型音频返回。

状态规则：`PENDING` 等待完成事件；取得任一有效计数字段为 `REPORTED`，不保证每个分项齐全；结束、取消、失败或断线仍未取得计数时为 `UNAVAILABLE`，异步保存或迟到 usage 仍可更新。旧记录 NULL 明确显示“历史无统计”，不能恢复未留存的昨日用量。详情刚完成时可刷新查看最新状态。

协议要求每轮 `request_id` 唯一。当前固件使用设备 ID、运行毫秒数与轮次序号生成唯一 ID；第三方客户端复用同会话 ID 可能复用日志行，不属于安全重试方式。近期响应关联最多保留 64 个，设备日志关联最多保留 16 轮；超出关联窗口的迟到用量可能无法记录。

## 与费用的关系

输入 Token 可能包含系统提示与保留的历史音频、图片、文字，并非仅本轮新说的一句话；长会话的逐轮输入量需重点观察。官方历史输入规则见 [Realtime 使用文档](https://help.aliyun.com/zh/model-studio/realtime)。

以下为 2026-10-09 官方北京地域标价（元/百万 Token），不是账户折扣后实付价：

| 模型 | 输入文本/图片/视频 | 输入音频 | 输出文本 | 输出音频 |
| --- | ---: | ---: | ---: | ---: |
| `qwen3.8-omni-flash-realtime` | 1.5 | 6 | 4.5 | 12 |

该模型输出语音及对应文字分别计费。价格及免费额度条件以 [官方模型价格](https://help.aliyun.com/zh/model-studio/model-pricing) 为准。后台当前只采集默认实时回答模型的 usage；独立旁路 ASR/补录、回退图文链路及独立 TTS 尚未组成完整账单，因此不能用这些 Token 直接断言昨天 10 元的具体成因，也不能据此判断欠费。

官方“入门型 AI 通用节省计划”有首购示例：10 元抵 20 元；27 元抵 60 元分 3 个月发放，每月 20 元，未用完的月额度不结转。付款后不能退订。适用范围按 AI 通用计划的阿里直供模型范围判断，购买前在自己的结算页确认 Omni 模型计费项、地域与账户资格；不要误买排除该模型的普通大语言模型计划。见 [官方节省计划说明](https://help.aliyun.com/zh/model-studio/savings-plan-and-resource-package)。Coding Plan 面向编码工具，不应用于 ESP32 后台实时调用，见 [Coding Plan FAQ](https://help.aliyun.com/zh/model-studio/coding-plan-faq)。此次没有购买套餐或更改计费配置。

## 部署与用户验收

新增 `campus-platform/sql/mysql/campus-esp32-log-token-upgrade.sql`：13 个 nullable 字段，幂等增列，不删除历史数据、不回填估算。`Campus Deploy` 已包含打包复制，`migrate-server.sh` 在日志内容迁移后执行；沿用部署前日志表备份。需要先迁移再运行新服务。旧版本没有写入 Token，所以升级前的轮次不会自动补齐。

本次按用户要求只做编译、构建尝试与静态复核，不运行模型/对话/数据库测试。部署后由用户正常说话验收：

1. 完成一轮，刷新列表或详情，看输入/输出/总量及分项是否与规范化 usage 一致。
2. 多轮连续对话，观察历史输入是否逐轮增长；结合输出音频 Token 判断成本来源。
3. 对旧记录确认显示“历史无统计”；没有返回的字段显示横线而非 0。
4. 中断回答后，如上游返回 usage，原请求仍显示计数；如果没有，显示“未获得统计”。

不要为了验证统计额外消耗模型额度；使用正常业务对话即可。
