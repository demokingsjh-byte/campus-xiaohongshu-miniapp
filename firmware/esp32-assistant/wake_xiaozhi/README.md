# ESP32-S3 视听说终端固件（wake_xiaozhi）

硬件：Seeed XIAO ESP32S3 Sense（带摄像头扩展板）+ MAX98357A 功放。
服务端协议：`esp32-av/1.0`，见 [网关协议文档](../../../campus-platform/docs/esp32-assistant-gateway.md)。

## 功能

- 本地唤醒词 **你好小智**（WakeNet9 `wn9_nihaoxiaozhi`，模型在 `srmodels.bin`）。
- 唤醒后录音，静音 0.7 秒自动提交，每轮抓拍最多 3 张。
- 回答播完后 60 秒内可直接追问，无需再次唤醒。
- 主动搭话：服务端在 `connected.features.proactive.idle_ms` 下发空闲阈值，
  设备空闲到点只拍一张发给服务端；是否开口、夜间静默、频率退避都由服务端决定。
  旧网关不下发该字段时自动关闭。
- 服务端休眠命令：`{"type":"device_command","command":"sleep"}`。
- 串口日志不打印设备令牌。

## 文件

| 文件 | 说明 |
|---|---|
| `wake_xiaozhi.ino` | 主固件 |
| `esp32_ai_guide_protocol.h` | 协议常量 |
| `device_config.example.h` | 配置模板；复制为 `device_config.h` 填写（已被 git 忽略，含令牌和配网口令） |
| `partitions.csv` | 分区表，与线上板子逐字节一致 |
| `srmodels.bin` | 唤醒词模型分区镜像（从 2026-11-05 线上板子 flash 备份中提取） |

## 编译

ESP32 Arduino core **3.3.8**（线上固件同版本；国内用乐鑫镜像索引
`https://espressif.github.io/arduino-esp32/package_esp32_index_cn.json`，安装 `esp32:esp32@3.3.8-cn`）。
库：ArduinoJson 7.4.3、WebSockets 2.7.2（Links2004）、WiFiManager 2.0.17。

```bash
arduino-cli compile -b "esp32:esp32:XIAO_ESP32S3:PSRAM=opi,FlashSize=8M" --build-path build .
```

注意：XIAO_ESP32S3 的 `CDCOnBoot=default` 才是“启用 USB 串口”，`cdc` 反而是关闭。
同目录的 `partitions.csv` 会被自动使用，App 槽上限 3.3 MB。

## 烧录

只更新固件和模型分区，不动 NVS（Wi-Fi 配置保留）：

```bash
esptool --chip esp32s3 --port COM9 --baud 921600 write-flash 0x10000 build/wake_xiaozhi.ino.bin 0x670000 srmodels.bin
```

全新板子还需写入 `0x0` 的 bootloader 与 `0x8000` 的分区表（`build/` 下同名 `.bootloader.bin` / `.partitions.bin`），
首次启动会开 `AI-GUIDE-SETUP` 配网热点。

烧录前建议先整片备份，出问题可原样写回：

```bash
esptool --chip esp32s3 --port COM9 --baud 921600 read-flash 0 0x800000 board_full_flash.bin
```

打开串口会让板子复位一次，属正常现象。
