#include <Arduino.h>
#include <math.h>
#include <ArduinoJson.h>
#include <WebSocketsClient.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include <Preferences.h>
#include <ESP_I2S.h>
#include <esp_camera.h>
#include <esp_timer.h>
#include <esp_heap_caps.h>
#include <esp_system.h>
#include <esp_wn_iface.h>
#include <esp_wn_models.h>
#include <model_path.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/stream_buffer.h>
#include <freertos/semphr.h>
#include <driver/i2s_common.h>

#include "esp32_ai_guide_protocol.h"
#include "device_config.h"

/* Seeed Studio XIAO ESP32S3 Sense 摄像头引脚。 */
static constexpr int CAMERA_PIN_PWDN = -1;
static constexpr int CAMERA_PIN_RESET = -1;
static constexpr int CAMERA_PIN_XCLK = 10;
static constexpr int CAMERA_PIN_SIOD = 40;
static constexpr int CAMERA_PIN_SIOC = 39;
static constexpr int CAMERA_PIN_D7 = 48;
static constexpr int CAMERA_PIN_D6 = 11;
static constexpr int CAMERA_PIN_D5 = 12;
static constexpr int CAMERA_PIN_D4 = 14;
static constexpr int CAMERA_PIN_D3 = 16;
static constexpr int CAMERA_PIN_D2 = 18;
static constexpr int CAMERA_PIN_D1 = 17;
static constexpr int CAMERA_PIN_D0 = 15;
static constexpr int CAMERA_PIN_VSYNC = 38;
static constexpr int CAMERA_PIN_HREF = 47;
static constexpr int CAMERA_PIN_PCLK = 13;

/* Sense 扩展板板载 PDM 麦克风。 */
static constexpr int MICROPHONE_PIN_CLK = 42;
static constexpr int MICROPHONE_PIN_DATA = 41;

/* MAX98357A：按当前实际焊接，D8=BCLK、D9=LRC、D10=DIN。 */
static constexpr int SPEAKER_PIN_BCLK = 7;   // XIAO D8 = GPIO7
static constexpr int SPEAKER_PIN_LRC = 8;    // XIAO D9 = GPIO8
static constexpr int SPEAKER_PIN_DATA = 9;   // XIAO D10 = GPIO9

/* XIAO 板载 BOOT 键与用户灯。用户灯为低电平点亮。 */
static constexpr int ACTION_BUTTON_PIN = 0;
static constexpr int STATUS_LED_PIN = 21;

I2SClass microphoneI2S;
I2SClass speakerI2S;
static constexpr size_t MICROPHONE_CHUNK_BYTES = 1024;
// Cover the 1.8s turn_ready window plus 512ms pre-roll without overflowing PCM.
static constexpr size_t UPLOAD_BUFFER_BYTES = 128 * 1024;
static constexpr size_t PRE_ROLL_BUFFER_BYTES = 16 * 1024;  // 16k PCM 约 512ms
/* 麦克风为 PCM16LE/16 kHz/单声道。环境电视声可远高于固定阈值，
 * 因此结束判定还参考本轮声音峰值，并设单轮最长时长兜底。 */
static constexpr float AUTO_VAD_RMS_THRESHOLD = 100.0f;
static constexpr int32_t AUTO_VAD_PEAK_THRESHOLD = 200;
static constexpr uint32_t FOLLOW_UP_ONSET_MS = 96;
static constexpr uint32_t AUTO_MIN_SPEECH_MS = 300;
static constexpr uint32_t AUTO_VOICE_RESUME_MS = 96;
// 700ms 会把句中换气/思考停顿当成说完；1100ms 多等约 0.4 秒，换来不抢话。
static constexpr uint32_t AUTO_SILENCE_COMMIT_MS = 1100;
static constexpr uint32_t AUTO_NO_SPEECH_TIMEOUT_MS = 500;
// WakeNet finishes the wake phrase before a turn starts. The longer timeout
// leaves enough time to begin the actual question after the LED acknowledgement.
static constexpr uint32_t WAKE_NO_SPEECH_TIMEOUT_MS = 3000;
static constexpr uint32_t CAMERA_MIDDLE_SHOT_MS = 400;
static constexpr uint32_t CAMERA_FINAL_PREFETCH_QUIET_MS = 320;
static constexpr uint32_t CAMERA_FINAL_GRACE_MS = 280;
static constexpr uint8_t DESIRED_CAMERA_SHOTS = 3;
// Omni Realtime limits a Base64 image to 256 KB; 180 KiB raw leaves encoding headroom.
static constexpr size_t MAX_CAMERA_JPEG_BYTES = 180 * 1024;
static constexpr size_t PLAYBACK_MAX_MONO_SAMPLES = 1024;
static constexpr size_t PLAYBACK_BUFFER_BYTES = 128 * 1024;
static constexpr size_t PLAYBACK_PREBUFFER_BYTES = 3840;  // 80 ms at PCM16 / 24 kHz.
static constexpr uint32_t TURN_READY_TIMEOUT_MS = 1800;
static constexpr uint32_t FOLLOW_UP_WINDOW_MS = 60000;
static constexpr uint32_t FOLLOW_UP_GUARD_MS = 160;
static constexpr unsigned WAKE_HISTORY_SILENCE_CHUNKS = 32;
static constexpr uint32_t CONTROL_WAIT_TIMEOUT_MS = 2500;
static constexpr uint32_t WAKE_LED_PULSE_MS = 60;

enum class DevicePhase : uint8_t {
  DISCONNECTED,
  LISTENING,
  REQUESTING,
  CAPTURING,
  THINKING,
  SPEAKING,
  COOLDOWN,
  ERROR_STATE,
};

WebSocketsClient webSocket;
StreamBufferHandle_t uploadBuffer = nullptr;
StreamBufferHandle_t preRollBuffer = nullptr;
SemaphoreHandle_t preRollMutex = nullptr;
StreamBufferHandle_t playbackBuffer = nullptr;
SemaphoreHandle_t playbackMutex = nullptr;
TaskHandle_t playbackTaskHandle = nullptr;
static StaticStreamBuffer_t playbackControl;
static uint8_t *playbackMonoBytes = nullptr;
static volatile bool playbackTaskWriting = false;
static volatile bool playbackDrained = true;
static volatile bool playbackFailurePending = false;
static volatile uint32_t playbackGeneration = 0;
static volatile uint32_t playbackFinishedAt = 0;
static volatile uint32_t i2sSentEvents = 0;
static volatile uint32_t i2sEventBytes = 0;
static uint32_t i2sTotalDmaBytes = 0;
static bool playbackDmaEventsReady = false;
QueueHandle_t cameraFrameQueue = nullptr;
QueueHandle_t cameraRequestQueue = nullptr;
TaskHandle_t microphoneTaskHandle = nullptr;
TaskHandle_t cameraTaskHandle = nullptr;

struct CameraRequest {
  uint32_t sequence;
  uint8_t shot;
  uint32_t generation;
};

struct CameraPacket {
  uint32_t sequence;
  uint8_t shot;
  uint32_t generation;
  uint8_t *data;
  size_t length;
};

volatile DevicePhase phase = DevicePhase::DISCONNECTED;
volatile bool microphoneCaptureEnabled = false;
volatile bool playbackStreamOpen = false;

bool cameraReady = false;
bool speakerReady = false;
bool websocketConnected = false;
bool buttonPressed = false;
bool buttonStablePressed = false;
bool serialTurnRequested = false;
uint32_t buttonChangedAt = 0;
uint32_t captureStartedAt = 0;
uint32_t captureReadyAt = 0;
uint32_t lastPingAt = 0;
uint32_t lastStatusPrintAt = 0;
uint32_t turnSequence = 0;
String deviceId;
String activeRequestId;

// One microphone task owns both I2S reads and WakeNet inference.
static const esp_wn_iface_t *wakeEngine = nullptr;
static model_iface_data_t *wakeModel = nullptr;
static srmodel_list_t *wakeModels = nullptr;
static int16_t *wakeSamples = nullptr;
static size_t wakeChunk = 0;
static volatile bool wakeReady = false;
static volatile bool wakePending = false;
// 播报/思考期间检测到“你好小智”：由主循环执行打断，麦克风任务只置位。
static volatile bool bargeInRequested = false;
static volatile bool followUpPending = false;
static volatile bool wakeTurn = false;
/* 主动搭话：服务端在 connected.features.proactive.idle_ms 下发空闲阈值，0 表示关闭。
 * 设备只负责“空闲到点就拍一张看看”；是否开口、静默时段与频率由服务端决定。 */
static uint32_t proactiveIdleMs = 0;
static bool proactiveTurn = false;
static uint32_t lastInteractionAt = 0;
static constexpr uint32_t PROACTIVE_CAMERA_GRACE_MS = 2000;
/* 当前 WakeNet 库的 clean() 会在第一轮回答后访问非法队列并崩溃。
 * 只由麦克风任务用普通 detect() 逐块喂静音清历史，不直接重置模型。 */
static volatile uint32_t wakeDetectionCount = 0;
static volatile uint32_t wakeLastResetCount = 0;
static volatile bool speechSeen = false;
static volatile uint32_t lastSpeechAt = 0;
static volatile uint32_t lastAnyVoiceAt = 0;
static volatile float capturePeakRms = 0.0f;
static volatile uint32_t captureConsecutiveQuietMs = 0;
static volatile uint32_t listenSince = 0;
static volatile uint32_t followUpUntil = 0;
static volatile uint32_t followUpNotBefore = 0;
static volatile float listeningNoiseRms = 45.0f;
static volatile uint32_t followUpDetectedVoiceMs = 0;
static uint32_t captureInitialVoiceMs = 0;
static uint32_t controlWaitSince = 0;
static bool cancelAwaitingAck = false;
static uint32_t retryListeningAt = 0;
static bool fatalHardwareError = false;
static bool serverListening = false;
static bool serverSupportsPlaybackFinished = false;
static bool serverAwaitingPlaybackFinished = false;
static bool turnNetworkDone = false;
static bool playbackFinishedSent = false;
static bool waitingPlaybackCompletion = false;
static bool wakeInitAttempted = false;
static bool uploadFinishing = false;
static bool serverTurnReady = false;
static bool turnFailed = false;
static uint8_t cameraFramesSentForTurn = 0;
static uint8_t cameraFramesAcknowledgedForTurn = 0;
static uint8_t cameraFramesRequestedForTurn = 0;
static uint8_t cameraTargetCount = 0;
static bool audioFrameSentForTurn = false;
static bool cameraFinalRequestedForSilence = false;
static uint32_t cameraFinalGeneration = 0;
static uint32_t cameraFinalRequestSpeechAt = 0;
static CameraPacket deferredFinalFrame = {};
static CameraPacket pendingCameraFrame = {};
static uint32_t serverSilenceFallbackCount = 0;
static uint32_t serverMaxImageCount = 1;
static uint32_t uploadFinishAt = 0;
static uint32_t turnSpeechEndAt = 0;
static uint8_t pendingAudioFrame[1 + MICROPHONE_CHUNK_BYTES] = {};
/* WebSocket 回调运行在 loopTask；4KB 播放数组若放在函数栈上会在
 * audio_start 后叠加 JSON/WebSocket 调用栈，触发 stack canary 重启。 */
static int16_t *playbackStereoSamples = nullptr;
static size_t pendingAudioFrameLength = 0;
static uint32_t firstAudioReceivedAt = 0;
static uint32_t firstI2sWriteAt = 0;
static uint32_t metricsTurnStartedAt = 0;
static bool turnMetricsPending = false;
static String metricsRequestId;
static uint8_t wakeLedPulseStep = 0;
static uint32_t wakeLedPulseNextAt = 0;
static volatile bool uploadOverflow = false;
static StaticStreamBuffer_t uploadControl, preRollControl;

static void setStatusLed(bool enabled);
static void clearPlayback();
static bool playbackBusy();

static bool conversationActive(uint32_t now) {
  return followUpUntil != 0 && static_cast<int32_t>(followUpUntil - now) > 0;
}

static void renewConversation() {
  followUpUntil = millis() + FOLLOW_UP_WINDOW_MS;
}

static void resetPreRoll() {
  if (preRollBuffer && preRollMutex &&
      xSemaphoreTake(preRollMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    xStreamBufferReset(preRollBuffer);
    xSemaphoreGive(preRollMutex);
  }
}

// The microphone keeps the newest 512 ms, including quiet consonants before VAD onset.
static void appendPreRoll(const uint8_t *pcm, size_t length) {
  if (!preRollBuffer || !preRollMutex ||
      xSemaphoreTake(preRollMutex, 0) != pdTRUE) return;
  uint8_t discarded[MICROPHONE_CHUNK_BYTES];
  while (xStreamBufferSpacesAvailable(preRollBuffer) < length) {
    if (xStreamBufferReceive(preRollBuffer, discarded, sizeof(discarded), 0) == 0) break;
  }
  xStreamBufferSend(preRollBuffer, pcm, length, 0);
  xSemaphoreGive(preRollMutex);
}
static void discardDeferredFinalFrame();
static void discardPendingCameraFrame();
static void resetPendingUploads();
static void abortTurnForTransport(const char *reason);

/* 这是“软关机”：关闭 Wi-Fi、音频与模型后进入深度睡眠，电池仍会有极低待机耗电。
 * BOOT(GPIO0) 被按住为低电平时可唤醒并重新启动。 */
static void enterDeepSleep(const char *reason) {
  Serial.printf("[电源] %s，设备进入休眠；按 BOOT 键可重新启动\n", reason);
  microphoneCaptureEnabled = false;
  clearPlayback();
  setStatusLed(false);
  webSocket.disconnect();
  WiFi.disconnect(false);
  esp_sleep_enable_ext0_wakeup(GPIO_NUM_0, 0);
  delay(120);
  esp_deep_sleep_start();
}

static void initWakeWord() {
  wakeInitAttempted = true;
  wakeModels = esp_srmodel_init("model");
  if (!wakeModels) { Serial.println("[唤醒] 缺少模型分区，按键仍可用"); return; }
  char *name = esp_srmodel_filter(wakeModels, "wn9", "nihaoxiaozhi");
  if (!name) { Serial.println("[唤醒] 未找到你好小智模型"); return; }
  wakeEngine = esp_wn_handle_from_name(name);
  if (!wakeEngine) return;
  wakeModel = wakeEngine->create(name, DET_MODE_90);
  if (!wakeModel) { Serial.println("[唤醒] 模型内存分配失败"); return; }
  wakeChunk = wakeEngine->get_samp_chunksize(wakeModel);
  if (!wakeChunk || wakeEngine->get_channel_num(wakeModel) != 1 ||
      wakeEngine->get_samp_rate(wakeModel) != 16000) {
    Serial.println("[唤醒] 模型音频格式不匹配"); return;
  }
  wakeSamples = static_cast<int16_t *>(heap_caps_malloc(wakeChunk * sizeof(int16_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  if (!wakeSamples) return;
  listenSince = millis();
  wakeReady = true;
  Serial.printf("[唤醒] 已就绪：你好小智，chunk=%u，heap=%u，PSRAM=%u\n",
                unsigned(wakeChunk), ESP.getFreeHeap(), ESP.getFreePsram());
}

static void setStatusLed(bool enabled) {
  digitalWrite(STATUS_LED_PIN, enabled ? LOW : HIGH);
}

static const char *phaseName(DevicePhase value) {
  switch (value) {
    case DevicePhase::DISCONNECTED: return "未连接";
    case DevicePhase::LISTENING: return "可以提问";
    case DevicePhase::REQUESTING: return "等待服务端";
    case DevicePhase::CAPTURING: return "正在录音";
    case DevicePhase::THINKING: return "AI 正在思考";
    case DevicePhase::SPEAKING: return "AI 正在讲解";
    case DevicePhase::COOLDOWN: return "等待扬声器尾音";
    case DevicePhase::ERROR_STATE: return "服务异常";
  }
  return "未知";
}

static void setLocalPhase(DevicePhase value, const char *reason = nullptr) {
  if (value != DevicePhase::CAPTURING && value != DevicePhase::REQUESTING) {
    discardDeferredFinalFrame();
    resetPendingUploads();
  }
  if (value == DevicePhase::LISTENING || value == DevicePhase::DISCONNECTED || value == DevicePhase::ERROR_STATE) {
    uploadFinishing = false;
    serverTurnReady = false;
    wakeTurn = false;
    wakePending = false;
    proactiveTurn = false;
    listenSince = millis();
    if (value == DevicePhase::ERROR_STATE && fatalHardwareError) {
      followUpPending = false;
      followUpUntil = 0;
      followUpNotBefore = 0;
    }
    if (value == DevicePhase::DISCONNECTED) followUpPending = false;
  }
  phase = value;
  setStatusLed(value == DevicePhase::LISTENING || value == DevicePhase::CAPTURING);
  Serial.printf("[状态] %s", phaseName(value));
  if (reason && reason[0]) {
    Serial.printf("：%s", reason);
  }
  Serial.println();
}

static void clearPlayback() {
  playbackStreamOpen = false;
  ++playbackGeneration;
  turnNetworkDone = false;
  waitingPlaybackCompletion = false;
  playbackFinishedSent = false;
  playbackDrained = false;
  if (playbackBuffer && playbackMutex &&
      xSemaphoreTake(playbackMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
    xStreamBufferReset(playbackBuffer);
    xSemaphoreGive(playbackMutex);
  }
  if (playbackTaskHandle) xTaskNotifyGive(playbackTaskHandle);
}

static bool playbackBusy() {
  return playbackStreamOpen || playbackTaskWriting || !playbackDrained;
}

static void updateWakeLedPulse() {
  if (!wakeLedPulseStep || millis() < wakeLedPulseNextAt) return;
  setStatusLed((wakeLedPulseStep & 1U) == 0);
  wakeLedPulseNextAt = millis() + WAKE_LED_PULSE_MS;
  if (++wakeLedPulseStep > 4) {
    wakeLedPulseStep = 0;
    setStatusLed(phase == DevicePhase::LISTENING || phase == DevicePhase::CAPTURING);
  }
}

static bool initMicrophone() {
  microphoneI2S.setPinsPdmRx(MICROPHONE_PIN_CLK, MICROPHONE_PIN_DATA);
  microphoneI2S.setTimeout(40);
  const bool ok = microphoneI2S.begin(
      I2S_MODE_PDM_RX,
      AI_GUIDE_AUDIO_INPUT_RATE,
      I2S_DATA_BIT_WIDTH_16BIT,
      I2S_SLOT_MODE_MONO);
  Serial.printf("[自检] 麦克风：%s (ESP_I2S PDM RX, CLK=%d DATA=%d)\n",
                ok ? "正常" : "失败", MICROPHONE_PIN_CLK, MICROPHONE_PIN_DATA);
  return ok;
}

static bool IRAM_ATTR onI2sSent(i2s_chan_handle_t handle, i2s_event_data_t *event, void *context) {
  ++i2sSentEvents;
  if (event) i2sEventBytes = event->size;
  BaseType_t higherPriorityWoken = pdFALSE;
  if (playbackTaskHandle) vTaskNotifyGiveFromISR(playbackTaskHandle, &higherPriorityWoken);
  return higherPriorityWoken == pdTRUE;
}

static bool initSpeaker() {
  speakerI2S.setPins(SPEAKER_PIN_BCLK, SPEAKER_PIN_LRC, SPEAKER_PIN_DATA);
  speakerI2S.setTimeout(1000);
  bool ok = speakerI2S.begin(
      I2S_MODE_STD,
      AI_GUIDE_AUDIO_OUTPUT_RATE,
      I2S_DATA_BIT_WIDTH_16BIT,
      I2S_SLOT_MODE_STEREO);
  if (ok) {
    i2s_chan_info_t info = {};
    i2s_event_callbacks_t callbacks = {};
    callbacks.on_sent = onI2sSent;
    const esp_err_t disabled = i2s_channel_disable(speakerI2S.txChan());
    const esp_err_t registered = disabled == ESP_OK
        ? i2s_channel_register_event_callback(speakerI2S.txChan(), &callbacks, nullptr)
        : disabled;
    const esp_err_t infoResult = i2s_channel_get_info(speakerI2S.txChan(), &info);
    if (disabled == ESP_OK) ok = i2s_channel_enable(speakerI2S.txChan()) == ESP_OK;
    playbackDmaEventsReady = ok && registered == ESP_OK && infoResult == ESP_OK &&
                             info.total_dma_buf_size > 0;
    i2sTotalDmaBytes = info.total_dma_buf_size;
    ok = ok && playbackDmaEventsReady;
    Serial.printf("[播放] DMA 完成事件=%s，缓冲环=%u bytes\n",
                  playbackDmaEventsReady ? "已启用" : "失败", unsigned(i2sTotalDmaBytes));
  }
  Serial.printf("[自检] 扬声器：%s (ESP_I2S STD TX, BCLK=%d LRC=%d DIN=%d)\n",
                ok ? "正常" : "失败",
                SPEAKER_PIN_BCLK, SPEAKER_PIN_LRC, SPEAKER_PIN_DATA);
  return ok;
}

/*
 * 通过串口输入字母 t 可播放短提示音，用于独立检查功放、接线和扬声器。
 * 测试音不经过网络或模型，不会影响正常的语音对话状态。
 */
static void playLocalTone(int toneFrequency, int toneAmplitude, int toneDurationMs) {
  if (!speakerReady || playbackBusy()) {
    Serial.println("[扬声器测试] 音频输出尚未就绪");
    return;
  }

  constexpr size_t framesPerChunk = 240;
  int16_t stereoSamples[framesPerChunk * 2];
  const uint32_t totalFrames =
      static_cast<uint32_t>(AI_GUIDE_AUDIO_OUTPUT_RATE) * toneDurationMs / 1000;
  const uint32_t halfPeriod = AI_GUIDE_AUDIO_OUTPUT_RATE / toneFrequency / 2;

  for (uint32_t frameOffset = 0; frameOffset < totalFrames;
       frameOffset += framesPerChunk) {
    const size_t frameCount =
        min(static_cast<uint32_t>(framesPerChunk), totalFrames - frameOffset);
    for (size_t i = 0; i < frameCount; ++i) {
      const int16_t sample =
          (((frameOffset + i) / halfPeriod) % 2 == 0) ? toneAmplitude : -toneAmplitude;
      stereoSamples[i * 2] = sample;
      stereoSamples[i * 2 + 1] = sample;
    }
    speakerI2S.write(reinterpret_cast<const uint8_t *>(stereoSamples),
                     frameCount * 2 * sizeof(int16_t));
  }
}

static void playLocalTestTone() {
  Serial.println("[扬声器测试] 开始播放 1000Hz 提示音");
  playLocalTone(1000, 10000, 220);
  Serial.println("[扬声器测试] 播放完成");
}

/*
 * Only the playback task writes network PCM to I2S. It processes every byte;
 * callback packet sizes do not define audio length or truncate the packet tail.
 */
static void playPcmChunkDirect(const uint8_t *data, size_t length) {
  if (!speakerReady || !data || length < 2) {
    return;
  }

  length &= ~static_cast<size_t>(1);
  if (!playbackStereoSamples) {
    return;
  }
  const size_t monoCount = min(length / sizeof(int16_t), PLAYBACK_MAX_MONO_SAMPLES);
  const int16_t *mono = reinterpret_cast<const int16_t *>(data);
  int32_t peak = 0;
  int64_t sum = 0;
  uint64_t sumSquares = 0;
  uint32_t clipped = 0;

  for (size_t i = 0; i < monoCount; ++i) {
    const int32_t raw = mono[i];
    const int32_t absRaw = raw >= 0 ? raw : -raw;
    peak = max(peak, absRaw);
    sum += raw;
    sumSquares += static_cast<uint64_t>(raw * static_cast<int64_t>(raw));

    int32_t amplified = raw * 4;
    if (amplified > 32767) {
      amplified = 32767;
      ++clipped;
    } else if (amplified < -32768) {
      amplified = -32768;
      ++clipped;
    }
    playbackStereoSamples[i * 2] = static_cast<int16_t>(amplified);
    playbackStereoSamples[i * 2 + 1] = static_cast<int16_t>(amplified);
  }

  static uint32_t chunkNumber = 0;
  ++chunkNumber;
  const float rms = monoCount
                        ? sqrtf(static_cast<float>(sumSquares) / monoCount)
                        : 0.0f;
  if (chunkNumber <= 3 || chunkNumber % 25 == 0) {
    Serial.printf("[PCM诊断] 包=%lu bytes=%u samples=%u peak=%ld rms=%.1f mean=%.1f clip=%lu 首样本=",
                  static_cast<unsigned long>(chunkNumber),
                  static_cast<unsigned>(length),
                  static_cast<unsigned>(monoCount),
                  static_cast<long>(peak),
                  rms,
                  monoCount ? static_cast<float>(sum) / monoCount : 0.0f,
                  static_cast<unsigned long>(clipped));
    for (size_t i = 0; i < min(monoCount, static_cast<size_t>(8)); ++i) {
      Serial.printf("%d%s", mono[i], i + 1 < min(monoCount, static_cast<size_t>(8)) ? "," : "");
    }
    Serial.println();
  }

  size_t bytesWritten = 0;
  const esp_err_t writeResult = i2s_channel_write(speakerI2S.txChan(),
      reinterpret_cast<const uint8_t *>(playbackStereoSamples),
      monoCount * 2 * sizeof(int16_t), &bytesWritten, 500);
  if (bytesWritten > 0 && firstI2sWriteAt == 0 && metricsTurnStartedAt != 0) {
    firstI2sWriteAt = millis();
    turnMetricsPending = true;
  }
  if (chunkNumber <= 3 || bytesWritten != monoCount * 2 * sizeof(int16_t)) {
    Serial.printf("[PCM诊断] I2S requested=%u written=%u lastError=%d\n",
                  static_cast<unsigned>(monoCount * 2 * sizeof(int16_t)),
                  static_cast<unsigned>(bytesWritten),
                  speakerI2S.lastError());
  }
  if (writeResult != ESP_OK || bytesWritten != monoCount * 2 * sizeof(int16_t)) {
    playbackFailurePending = true;
  }
}

static bool initCamera() {
  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = CAMERA_PIN_D0;
  config.pin_d1 = CAMERA_PIN_D1;
  config.pin_d2 = CAMERA_PIN_D2;
  config.pin_d3 = CAMERA_PIN_D3;
  config.pin_d4 = CAMERA_PIN_D4;
  config.pin_d5 = CAMERA_PIN_D5;
  config.pin_d6 = CAMERA_PIN_D6;
  config.pin_d7 = CAMERA_PIN_D7;
  config.pin_xclk = CAMERA_PIN_XCLK;
  config.pin_pclk = CAMERA_PIN_PCLK;
  config.pin_vsync = CAMERA_PIN_VSYNC;
  config.pin_href = CAMERA_PIN_HREF;
  config.pin_sccb_sda = CAMERA_PIN_SIOD;
  config.pin_sccb_scl = CAMERA_PIN_SIOC;
  config.pin_pwdn = CAMERA_PIN_PWDN;
  config.pin_reset = CAMERA_PIN_RESET;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  /* QVGA 对物品识别足够，且比 VGA 双缓冲更适合 XIAO 同时运行 TLS、WakeNet 与 I2S。 */
  config.frame_size = FRAMESIZE_QVGA;
  config.jpeg_quality = 14;
  config.fb_count = 1;
  config.grab_mode = CAMERA_GRAB_LATEST;
  config.fb_location = psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;

  esp_err_t result = esp_camera_init(&config);
  Serial.printf("[自检] 摄像头：%s (%d)，PSRAM：%s\n",
                result == ESP_OK ? "正常" : "失败",
                result,
                psramFound() ? "正常" : "未检测到");
  if (result == ESP_OK) {
    sensor_t *sensor = esp_camera_sensor_get();
    Serial.printf("[摄像头] PID=0x%04X，QVGA/JPEG，quality=14，单缓冲\n",
                  sensor ? static_cast<unsigned>(sensor->id.PID) : 0U);
  }
  return result == ESP_OK;
}

/* 拍照在独立任务中进行；只有 Arduino 主循环操作 WebSocket。 */
static bool captureCameraPacket(const CameraRequest &request, CameraPacket &packet) {
  const int64_t captureStartedUs = esp_timer_get_time();
  if (!cameraReady) {
    Serial.println("[摄像头] 尚未就绪，重试初始化");
    cameraReady = initCamera();
    if (!cameraReady) return false;
  }

  camera_fb_t *frame = esp_camera_fb_get();
  if (!frame) {
    Serial.println("[摄像头] 抓拍失败，重置驱动后重试一次");
    esp_camera_deinit();
    cameraReady = false;
    vTaskDelay(pdMS_TO_TICKS(100));
    cameraReady = initCamera();
    if (!cameraReady) return false;
    frame = esp_camera_fb_get();
    if (!frame) {
      Serial.println("[摄像头] 重试抓拍失败，本轮按纯语音提交");
      return false;
    }
  }

  const bool validJpeg = frame->format == PIXFORMAT_JPEG && frame->len >= 4 &&
                         frame->buf[0] == 0xFF && frame->buf[1] == 0xD8 &&
                         frame->buf[frame->len - 2] == 0xFF &&
                         frame->buf[frame->len - 1] == 0xD9;
  if (!validJpeg || frame->len > MAX_CAMERA_JPEG_BYTES) {
    Serial.printf("[摄像头] JPEG 无效或超过 180KiB：%u 字节\n",
                  static_cast<unsigned>(frame->len));
    esp_camera_fb_return(frame);
    return false;
  }

  uint8_t *data = static_cast<uint8_t *>(
      heap_caps_malloc(frame->len + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  if (!data) {
    Serial.println("[摄像头] 图片缓冲分配失败，本轮按纯语音提交");
    esp_camera_fb_return(frame);
    return false;
  }
  data[0] = AI_GUIDE_FRAME_JPEG;
  memcpy(data + 1, frame->buf, frame->len);
  packet = {request.sequence, request.shot, request.generation, data, frame->len + 1};
  const int64_t nowUs = esp_timer_get_time();
  const int64_t frameUs = static_cast<int64_t>(frame->timestamp.tv_sec) * 1000000LL +
                          static_cast<int64_t>(frame->timestamp.tv_usec);
  const int64_t ageMs = frameUs > 0 && nowUs >= frameUs ? (nowUs - frameUs) / 1000 : -1;
  Serial.printf("[摄像头] 抓拍 shot=%u，%ux%u，bytes=%u，capture_ms=%llu，age_ms=%lld，PSRAM=%u\n",
                static_cast<unsigned>(request.shot), static_cast<unsigned>(frame->width),
                static_cast<unsigned>(frame->height), static_cast<unsigned>(frame->len),
                static_cast<unsigned long long>((nowUs - captureStartedUs) / 1000),
                static_cast<long long>(ageMs), ESP.getFreePsram());
  esp_camera_fb_return(frame);
  return true;
}

static void cameraTask(void *parameter) {
  CameraRequest request = {};
  while (true) {
    if (xQueueReceive(cameraRequestQueue, &request, portMAX_DELAY) != pdTRUE) continue;
    CameraPacket packet = {};
    if (captureCameraPacket(request, packet)) {
      if (xQueueSend(cameraFrameQueue, &packet, pdMS_TO_TICKS(250)) != pdTRUE) {
        Serial.println("[摄像头] 图片队列已满，丢弃本帧");
        free(packet.data);
      }
    }
  }
}

static void microphoneTask(void *parameter) {
  int16_t pcmSamples[MICROPHONE_CHUNK_BYTES / 2];
  uint8_t *pcm = reinterpret_cast<uint8_t *>(pcmSamples);
  uint32_t lastDiagAt = 0;
  size_t wakeUsed = 0;
  bool wasListening = false;
  unsigned wakeWarmup = 0;
  unsigned wakeSkipChunks = 0;
  uint32_t observedCaptureStart = 0;
  uint32_t voiceEvidenceMs = 0;
  uint32_t voicedRunMs = 0;
  uint32_t followUpVoiceMs = 0;
  uint32_t followUpLastVoiceAt = 0;
  float dcPreviousInput = 0.0f;
  float dcPreviousOutput = 0.0f;

  while (true) {
    const size_t bytesRead = microphoneI2S.readBytes(
        reinterpret_cast<char *>(pcm), sizeof(pcmSamples));

    if (bytesRead == 0) {
      if (millis() - lastDiagAt >= 500) {
        lastDiagAt = millis();
        Serial.println("[MIC] bytes=0");
      }
      continue;
    }

    /* 滑动 PCM 帧的 Peak / RMS，供本地 VAD 和麦克风诊断使用。 */
    const int16_t *samples = reinterpret_cast<const int16_t *>(pcm);
    const size_t sampleCount = bytesRead / sizeof(int16_t);
    int32_t peak = 0;
    uint64_t sumSquares = 0;
    for (size_t i = 0; i < sampleCount; ++i) {
      const float input = pcmSamples[i];
      const float filtered = input - dcPreviousInput + 0.995f * dcPreviousOutput;
      dcPreviousInput = input;
      dcPreviousOutput = filtered;
      pcmSamples[i] = static_cast<int16_t>(fmaxf(-32768.0f, fminf(32767.0f, filtered)));
      const int32_t value = pcmSamples[i];
      const int32_t absValue = value >= 0 ? value : -value;
      if (absValue > peak) {
        peak = absValue;
      }
      sumSquares += static_cast<uint64_t>(value * static_cast<int64_t>(value));
    }
    const float rms = sampleCount > 0
                          ? sqrtf(static_cast<float>(sumSquares) / sampleCount)
                          : 0.0f;

    const uint32_t now = millis();
    const uint32_t chunkMs = max(uint32_t(1), static_cast<uint32_t>(bytesRead / 32));
    const bool localListening = phase == DevicePhase::LISTENING && !playbackBusy();
    if (localListening) appendPreRoll(pcm, bytesRead);
    // A slowly rising noise floor does not follow each speech syllable upward.
    if (localListening && !followUpPending && !wakePending) {
      const float blend = rms < listeningNoiseRms ? 0.04f : 0.003f;
      listeningNoiseRms = fmaxf(25.0f, fminf(1500.0f,
          listeningNoiseRms + blend * (rms - listeningNoiseRms)));
    }
    bool wakeDetectedThisChunk = false;
    const bool listening = wakeReady && localListening &&
                           !wakePending && wakeWarmup == 0 && now - listenSince > 250;
    // 半双工没有回声消除，不能靠音量判断用户插话；只认唤醒词，喇叭回采不会念出“你好小智”。
    const bool bargeInListening = wakeReady && !wakePending && !bargeInRequested &&
                                  wakeWarmup == 0 &&
                                  (phase == DevicePhase::THINKING || phase == DevicePhase::SPEAKING);
    if (wakeWarmup && wakeReady && wakeEngine && wakeModel) {
      // One zero block per real MIC read: no tight reset loop and no PCM mutation.
      // Usually finishes during capture/playback; VAD and pre-roll below stay live.
      memset(wakeSamples, 0, wakeChunk * sizeof(int16_t));
      wakeEngine->detect(wakeModel, wakeSamples);
      wakeUsed = 0;
      if (--wakeWarmup == 0) {
        wakeLastResetCount = wakeDetectionCount;
        Serial.printf("[唤醒] 静音历史清理完成，累计唤醒 %u 次\n",
                      static_cast<unsigned>(wakeLastResetCount));
      }
    } else if (listening || bargeInListening) {
      if (!wasListening) { wakeUsed = 0; }
      for (size_t i = 0; i < sampleCount; ++i) {
        wakeSamples[wakeUsed++] = pcmSamples[i];
        if (wakeUsed == wakeChunk) {
          wakeUsed = 0;
          const int detected = wakeEngine->detect(wakeModel, wakeSamples);
          if (detected > 0 && bargeInListening) {
            bargeInRequested = true;
            ++wakeDetectionCount;
            wakeWarmup = WAKE_HISTORY_SILENCE_CHUNKS;
            Serial.println("[唤醒] 播报中检测到你好小智，请求打断");
            break;
          }
          if (detected > 0) {
            resetPreRoll();
            wakeSkipChunks = 0;  // 当前检测块已排除；保留后续问题的第一个音节。
            wakePending = true;
            ++wakeDetectionCount;
            wakeWarmup = WAKE_HISTORY_SILENCE_CHUNKS;
            wakeDetectedThisChunk = true;
            Serial.println("[唤醒] 检测到你好小智");
            break;
          }
        }
      }
    } else { wakeUsed = 0; }
    wasListening = listening || bargeInListening;

    /* 回答后的一小段连续追问窗口：不需要再次说唤醒词。 */
    const bool followUpListening = localListening &&
                                   static_cast<int32_t>(now - followUpNotBefore) >= 0 &&
                                   conversationActive(now) &&
                                   !followUpPending && !wakePending;
    if (followUpListening) {
      const float onsetThreshold = fmaxf(80.0f, listeningNoiseRms * 1.8f + 20.0f);
      const bool hasVoice = rms >= onsetThreshold && peak >= 160;
      if (hasVoice) {
        if (now - followUpLastVoiceAt > 160) followUpVoiceMs = 0;
        followUpLastVoiceAt = now;
        followUpVoiceMs += chunkMs;
      } else if (now - followUpLastVoiceAt > 160) {
        followUpVoiceMs = 0;
      }
      if (followUpVoiceMs >= FOLLOW_UP_ONSET_MS) {
        followUpDetectedVoiceMs = followUpVoiceMs;
        followUpPending = true;
        Serial.printf("[连续对话] 检测到追问 rms=%.1f noise=%.1f，保留滚动预录\n",
                      rms, listeningNoiseRms);
      }
    } else {
      followUpVoiceMs = 0;
    }

    if (microphoneCaptureEnabled &&
        (phase == DevicePhase::REQUESTING || phase == DevicePhase::CAPTURING)) {
      if (observedCaptureStart != captureStartedAt) {
        observedCaptureStart = captureStartedAt;
        voiceEvidenceMs = captureInitialVoiceMs;
        voicedRunMs = 0;
        capturePeakRms = 0.0f;
        captureConsecutiveQuietMs = 0;
      }
      const float voiceThreshold = fmaxf(70.0f, listeningNoiseRms * 1.5f + 20.0f);
      const bool hasVoice = rms >= voiceThreshold &&
                             peak >= AUTO_VAD_PEAK_THRESHOLD;
      // 历史峰值必须衰减，不能让刚才的大声持续掩盖现在的轻声句尾。
      capturePeakRms = fmaxf(rms, capturePeakRms * 0.995f);
      const float adaptiveQuietThreshold = fmaxf(
          voiceThreshold, fminf(capturePeakRms * 0.20f, voiceThreshold * 2.0f));
      const bool quiet = rms < adaptiveQuietThreshold ||
                         peak < AUTO_VAD_PEAK_THRESHOLD;
      if (quiet) voicedRunMs = 0;
      if (hasVoice && !quiet) {
        const uint32_t gapMs = now - lastAnyVoiceAt;
        if (!speechSeen && gapMs > 350) voiceEvidenceMs = 0;
        if (gapMs > 120) voicedRunMs = 0;
        lastAnyVoiceAt = now;
        voiceEvidenceMs += chunkMs;
        voicedRunMs += chunkMs;
        if (!speechSeen && voiceEvidenceMs >= AUTO_MIN_SPEECH_MS) {
          speechSeen = true;
          lastSpeechAt = now;
          Serial.printf("[VAD] 有效语音达到 %u ms\n", static_cast<unsigned>(voiceEvidenceMs));
        } else if (speechSeen) {
          lastSpeechAt = now;
        }
      } else if (now - lastAnyVoiceAt > 120) {
        voicedRunMs = 0;
      }
      if (speechSeen) {
        if (quiet) {
          captureConsecutiveQuietMs += chunkMs;
        } else {
          captureConsecutiveQuietMs = 0;
        }
      }
    } else {
      observedCaptureStart = 0;
      voiceEvidenceMs = 0;
      voicedRunMs = 0;
      captureConsecutiveQuietMs = 0;
    }

    if (microphoneCaptureEnabled && millis() - lastDiagAt >= 1000) {
      lastDiagAt = millis();
      Serial.printf("[MIC] bytes=%u peak=%ld rms=%.1f quiet=%u buffer=%u\n",
                    static_cast<unsigned>(bytesRead),
                    static_cast<long>(peak),
                    rms,
                    static_cast<unsigned>(captureConsecutiveQuietMs),
                    uploadBuffer
                        ? static_cast<unsigned>(xStreamBufferBytesAvailable(uploadBuffer))
                        : 0U);
    }

    if (uploadBuffer && microphoneCaptureEnabled) {
      const size_t queued = xStreamBufferSend(uploadBuffer, pcm, bytesRead, 0);
      if (queued != bytesRead) {
        uploadOverflow = true;
        Serial.printf("[MIC] 上传缓冲不足，丢弃 %u 字节\n",
                      static_cast<unsigned>(bytesRead - queued));
      }
    }
  }
}

// This core has no wait_all_done API. Observe real TX EOF callbacks for a full
// DMA ring after the final write, so every slot containing the tail has left I2S.
static bool waitForDmaTail(uint32_t generation) {
  if (!playbackDmaEventsReady || !i2sEventBytes) return false;
  const uint32_t firstEvent = i2sSentEvents;
  const uint32_t requiredEvents =
      (i2sTotalDmaBytes + i2sEventBytes - 1) / i2sEventBytes + 1;
  const uint32_t startedAt = millis();
  while (i2sSentEvents - firstEvent < requiredEvents) {
    if (playbackGeneration != generation) return false;
    if (millis() - startedAt > 1000) return false;
    ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(20));
  }
  return true;
}

static void playbackTask(void *parameter) {
  uint32_t generation = playbackGeneration;
  bool started = false;
  bool hasWritten = false;
  while (true) {
    if (generation != playbackGeneration) {
      generation = playbackGeneration;
      started = false;
      // Cancelled audio is overwritten with one full silent DMA ring before re-arm.
      if (hasWritten) {
        playbackTaskWriting = true;
        memset(playbackStereoSamples, 0, PLAYBACK_MAX_MONO_SAMPLES * 4);
        size_t remaining = i2sTotalDmaBytes;
        while (remaining > 0) {
          const size_t wanted = min(remaining, PLAYBACK_MAX_MONO_SAMPLES * 4);
          size_t written = 0;
          if (i2s_channel_write(speakerI2S.txChan(), playbackStereoSamples,
                                wanted, &written, 500) != ESP_OK || written != wanted) {
            playbackFailurePending = true;
            break;
          }
          remaining -= written;
        }
        if (!waitForDmaTail(generation) && generation == playbackGeneration)
          playbackFailurePending = true;
        playbackTaskWriting = false;
      }
      hasWritten = false;
    }
    size_t available = 0;
    size_t read = 0;
    if (xSemaphoreTake(playbackMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
      available = xStreamBufferBytesAvailable(playbackBuffer);
      if (!started && (available >= PLAYBACK_PREBUFFER_BYTES ||
                       (!playbackStreamOpen && available > 0))) started = true;
      if (started && available > 0) {
        read = xStreamBufferReceive(playbackBuffer, playbackMonoBytes,
            PLAYBACK_MAX_MONO_SAMPLES * sizeof(int16_t), 0);
        playbackTaskWriting = read > 0;
      }
      xSemaphoreGive(playbackMutex);
    }
    if (read > 0) {
      if (generation == playbackGeneration) {
        playPcmChunkDirect(playbackMonoBytes, read);
        hasWritten = true;
      }
      playbackTaskWriting = false;
      continue;
    }
    if (!playbackStreamOpen && available == 0 && !playbackDrained) {
      playbackTaskWriting = true;
      const bool drained = !hasWritten || waitForDmaTail(generation);
      if (xSemaphoreTake(playbackMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
        if (generation == playbackGeneration && !playbackStreamOpen &&
            xStreamBufferBytesAvailable(playbackBuffer) == 0) {
        if (drained) {
          playbackFinishedAt = millis();
          playbackDrained = true;
          hasWritten = false;
          started = false;
        } else playbackFailurePending = true;
        }
        xSemaphoreGive(playbackMutex);
      }
      playbackTaskWriting = false;
    }
    ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(10));
  }
}

static void queuePlayback(const uint8_t *data, size_t length) {
  if (!data || !length || !playbackStreamOpen) return;
  if ((length & 1U) || !playbackBuffer || !playbackMutex ||
      xSemaphoreTake(playbackMutex, pdMS_TO_TICKS(10)) != pdTRUE) {
    playbackFailurePending = true;
    return;
  }
  const bool hasSpace = length <= xStreamBufferSpacesAvailable(playbackBuffer);
  const size_t queued = hasSpace ? xStreamBufferSend(playbackBuffer, data, length, 0) : 0;
  if (queued != length) {
    // Never silently drop a PCM tail: recover this request on the main loop.
    playbackFailurePending = true;
    Serial.printf("[播放] 有界队列无法接收完整音频包 bytes=%u，取消并恢复\n", unsigned(length));
  }
  xSemaphoreGive(playbackMutex);
  if (playbackTaskHandle) xTaskNotifyGive(playbackTaskHandle);
}

static bool sendControl(const String &json) {
  if (!websocketConnected) {
    return false;
  }
  Serial.printf("[上行] %s\n", json.c_str());
  return webSocket.sendTXT(json.c_str(), json.length());
}

static void discardDeferredFinalFrame() {
  if (deferredFinalFrame.data) free(deferredFinalFrame.data);
  deferredFinalFrame = {};
}

static void discardPendingCameraFrame() {
  if (pendingCameraFrame.data) free(pendingCameraFrame.data);
  pendingCameraFrame = {};
}

static void resetPendingUploads() {
  discardPendingCameraFrame();
  pendingAudioFrameLength = 0;
}

static void abortTurnForTransport(const char *reason) {
  Serial.printf("[网络] 本轮中断 request=%s，reason=%s，PCM积压=%u，PSRAM=%u\n",
                activeRequestId.c_str(), reason,
                uploadBuffer ? static_cast<unsigned>(xStreamBufferBytesAvailable(uploadBuffer)) : 0U,
                ESP.getFreePsram());
  microphoneCaptureEnabled = false;
  uploadFinishing = false;
  uploadOverflow = false;
  resetPendingUploads();
  webSocket.disconnect();  // 不提交残缺音频/图片，重连后再开始新轮。
  setLocalPhase(DevicePhase::DISCONNECTED, "上传中断，等待重连");
}

/* false may follow a partial WebSocket header/payload write. Never resend the
 * whole frame or send commit on that connection; reconnect with a new turn. */
static bool sendMediaFrameOnce(uint8_t *payload, size_t length, const char *kind) {
  const uint32_t startedAt = millis();
  const bool sent = webSocket.sendBIN(payload, length);
  const uint32_t elapsed = millis() - startedAt;
  if (!sent || elapsed >= 200) {
    Serial.printf("[网络] 媒体发送 request=%s，kind=%s，bytes=%u，send_ms=%u，ok=%u，PCM积压=%u\n",
                  activeRequestId.c_str(), kind, static_cast<unsigned>(length),
                  static_cast<unsigned>(elapsed), sent ? 1U : 0U,
                  uploadBuffer ? static_cast<unsigned>(xStreamBufferBytesAvailable(uploadBuffer)) : 0U);
  }
  if (!sent) {
    abortTurnForTransport(kind);
    return false;
  }
  return true;
}

static void pollPlaybackCompletion() {
  if (playbackFailurePending) {
    playbackFailurePending = false;
    clearPlayback();
    abortTurnForTransport("播放队列或 I2S 异常，取消本轮并重新连接");
    return;
  }
  if (waitingPlaybackCompletion && turnNetworkDone && playbackDrained && !playbackTaskWriting) {
    // A new gateway announces its cooldown ACK phase before we send the receipt.
    // Legacy gateways need no receipt; their LISTENING is still gated by local drain.
    if (serverSupportsPlaybackFinished && !turnFailed &&
        !serverAwaitingPlaybackFinished && !serverListening) return;
    if (!playbackFinishedSent) {
      if (serverSupportsPlaybackFinished && serverAwaitingPlaybackFinished && !turnFailed) {
        if (!sendControl(String("{\"type\":\"playback_finished\",\"request_id\":\"") +
                         activeRequestId + "\"}")) {
          abortTurnForTransport("播放完成确认发送失败");
          return;
        }
      }
      playbackFinishedSent = true;
      renewConversation();
      followUpNotBefore = max(millis(), static_cast<uint32_t>(playbackFinishedAt)) + FOLLOW_UP_GUARD_MS;
      Serial.println("[连续对话] 本机 PCM 队列与 I2S 尾音已完成，60 秒内可直接追问");
    }
    if (serverListening) {
      waitingPlaybackCompletion = false;
      activeRequestId = "";
      setLocalPhase(DevicePhase::LISTENING, "播放已结束，请直接继续提问");
    }
  } else if (!waitingPlaybackCompletion && serverListening &&
             phase == DevicePhase::COOLDOWN && !playbackBusy()) {
    followUpNotBefore = millis() + FOLLOW_UP_GUARD_MS;
    setLocalPhase(DevicePhase::LISTENING, "已恢复，可以继续提问");
  }
}

static uint32_t cameraGraceMs() {
  // 主动搭话没有语音可并行，抓拍本身就是本轮全部内容，给相机足够时间。
  return proactiveTurn ? PROACTIVE_CAMERA_GRACE_MS : CAMERA_FINAL_GRACE_MS;
}

static void beginTurn(bool usePreRoll = false, bool preRollSpeechConfirmed = false,
                      bool proactive = false) {
  if (!websocketConnected || !serverListening || playbackBusy() || phase != DevicePhase::LISTENING) {
    Serial.printf("[按键] 当前状态“%s”不能开始提问\n", phaseName(phase));
    return;
  }
  proactiveTurn = proactive;
  lastInteractionAt = millis();

  ++turnSequence;
  retryListeningAt = 0;
  serverAwaitingPlaybackFinished = false;
  turnNetworkDone = false;
  waitingPlaybackCompletion = false;
  playbackFinishedSent = false;
  turnFailed = false;
  cameraFramesSentForTurn = 0;
  cameraFramesAcknowledgedForTurn = 0;
  cameraFramesRequestedForTurn = 0;
  audioFrameSentForTurn = false;
  serverTurnReady = false;
  cameraFinalRequestedForSilence = false;
  ++cameraFinalGeneration;
  cameraFinalRequestSpeechAt = 0;
  discardDeferredFinalFrame();
  resetPendingUploads();
  uploadOverflow = false;
  cameraTargetCount = static_cast<uint8_t>(min(
      static_cast<uint32_t>(proactiveTurn ? 1 : DESIRED_CAMERA_SHOTS), serverMaxImageCount));
  microphoneCaptureEnabled = false;
  xStreamBufferReset(uploadBuffer);
  if (cameraRequestQueue) xQueueReset(cameraRequestQueue);
  if (cameraFrameQueue) {
    CameraPacket stale = {};
    while (xQueueReceive(cameraFrameQueue, &stale, 0) == pdTRUE) free(stale.data);
  }
  followUpPending = false;
  // 主动搭话不开连续对话窗口：没开口时不能让环境噪声直接触发提问；
  // 开口播报完后由 pollPlaybackCompletion 正常续期，用户可直接回话。
  if (!proactiveTurn) renewConversation();
  cancelAwaitingAck = false;
  controlWaitSince = millis();
  activeRequestId = deviceId + "-" + String(millis()) + "-" + String(turnSequence);
  if (turnSequence % 10 == 0) {
    Serial.printf("[统计] 最近 %u 轮服务端静音兜底触发 %u 次\n",
                  static_cast<unsigned>(turnSequence),
                  static_cast<unsigned>(serverSilenceFallbackCount));
  }
  captureStartedAt = millis();
  metricsTurnStartedAt = captureStartedAt;
  firstAudioReceivedAt = 0;
  firstI2sWriteAt = 0;
  turnMetricsPending = false;
  metricsRequestId = activeRequestId;
  captureReadyAt = 0;
  captureInitialVoiceMs = preRollSpeechConfirmed ? followUpDetectedVoiceMs : 0;
  lastSpeechAt = captureStartedAt;
  lastAnyVoiceAt = captureStartedAt;
  speechSeen = false;
  captureConsecutiveQuietMs = 0;
  setLocalPhase(DevicePhase::REQUESTING, "正在申请录音，已缓存问题开头");
  size_t preRollBytes = 0;
  if (usePreRoll && preRollBuffer && preRollMutex &&
      xSemaphoreTake(preRollMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
    uint8_t preRollChunk[MICROPHONE_CHUNK_BYTES];
    size_t read = 0;
    while ((read = xStreamBufferReceive(preRollBuffer, preRollChunk,
                                         sizeof(preRollChunk), 0)) > 0) {
      preRollBytes += xStreamBufferSend(uploadBuffer, preRollChunk, read, 0);
    }
    xSemaphoreGive(preRollMutex);
    // Only actual voiced onset counts as evidence; the 512 ms rolling buffer also
    // contains quiet samples and must not turn a short noise burst into speech.
    if (preRollSpeechConfirmed && captureInitialVoiceMs >= AUTO_MIN_SPEECH_MS) {
      speechSeen = true;
      lastSpeechAt = millis();
    }
    Serial.printf("[音频] 预录回灌 %u 字节（仅新轮次，非唤醒词）\n",
                  static_cast<unsigned>(preRollBytes));
  }
  /* 只从唤醒完成、新轮次开始后预录；turn_ready 前不向网关发送音频。
   * 64KB 上行队列可容纳约 2 秒 PCM，避免网络握手吃掉问题开头。 */
  microphoneCaptureEnabled = !proactiveTurn;  // 主动搭话只拍照，不录音。
  setStatusLed(true);
  serverListening = false;
  if (proactiveTurn) Serial.println("[主动] 空闲已久，拍一张看看要不要搭话");
  if (!sendControl(String("{\"type\":\"turn_start\",\"request_id\":\"") + activeRequestId +
                   "\",\"playback_finished\":" + (serverSupportsPlaybackFinished ? "true" : "false") +
                   (proactiveTurn ? ",\"proactive\":true" : "") + "}")) {
    abortTurnForTransport("turn_start 发送失败");
  }
}

static void requestCameraShot() {
  if (!cameraTaskHandle || !cameraRequestQueue ||
      cameraFramesRequestedForTurn >= cameraTargetCount) return;
  CameraRequest request = {turnSequence,
                           static_cast<uint8_t>(cameraFramesRequestedForTurn + 1), 0};
  if (xQueueSend(cameraRequestQueue, &request, 0) == pdTRUE) {
    ++cameraFramesRequestedForTurn;
    Serial.printf("[摄像头] 请求第 %u/%u 张抓拍\n",
                  static_cast<unsigned>(request.shot),
                  static_cast<unsigned>(cameraTargetCount));
  } else {
    Serial.println("[摄像头] 抓拍请求队列已满");
  }
}

static void requestFinalCameraShot() {
  if (!cameraTaskHandle || !cameraRequestQueue || cameraTargetCount != 3 ||
      cameraFramesRequestedForTurn < 2 || cameraFinalRequestedForSilence) return;
  const uint32_t generation = ++cameraFinalGeneration;
  CameraRequest request = {turnSequence, 3, generation};
  if (xQueueSend(cameraRequestQueue, &request, 0) == pdTRUE) {
    cameraFramesRequestedForTurn = 3;
    cameraFinalRequestedForSilence = true;
    cameraFinalRequestSpeechAt = lastSpeechAt;
    Serial.println("[摄像头] 静音窗口提前抓拍第 3 张，提交前若继续说话会重拍");
  } else {
    Serial.println("[摄像头] 第 3 张抓拍请求队列已满");
  }
}

static void refreshFinalCameraShotOnSpeech() {
  if (!cameraFinalRequestedForSilence ||
      lastSpeechAt == cameraFinalRequestSpeechAt) return;
  cameraFinalRequestedForSilence = false;
  ++cameraFinalGeneration;  // 队列内正在处理的旧照片也随之失效。
  discardDeferredFinalFrame();
  Serial.println("[摄像头] 用户继续说话，作废旧末张，等待新的静音窗口");
}

static void flushCameraFrame() {
  if (!cameraFrameQueue || phase != DevicePhase::CAPTURING || !websocketConnected) return;
  // Only discard before attempting a write; a failed write must reconnect.
  if (uploadFinishing && millis() - uploadFinishAt >= cameraGraceMs()) {
    discardPendingCameraFrame();
    discardDeferredFinalFrame();
    return;
  }
  /* 有效语音开始后才发送，避免空采集被图片上传阻塞。 */
  if (!audioFrameSentForTurn || (!speechSeen && !uploadFinishing)) return;
  // PCM 优先；图片每次 loop 最多发一帧，避免阻塞后续实时音频。
  if (pendingAudioFrameLength || xStreamBufferBytesAvailable(uploadBuffer) > 2 * MICROPHONE_CHUNK_BYTES) return;
  if (!pendingCameraFrame.data) {
    CameraPacket packet = {};
    if (xQueueReceive(cameraFrameQueue, &packet, 0) == pdTRUE) {
      if (packet.sequence != turnSequence ||
          (packet.shot == 3 && packet.generation != cameraFinalGeneration)) {
        free(packet.data);
        return;
      }
      if (packet.shot == 3 && !uploadFinishing) {
        discardDeferredFinalFrame();
        deferredFinalFrame = packet;
        return;
      }
      pendingCameraFrame = packet;
    } else if (uploadFinishing && deferredFinalFrame.data) {
      pendingCameraFrame = deferredFinalFrame;
      deferredFinalFrame = {};
    } else {
      return;
    }
  }
  if (sendMediaFrameOnce(pendingCameraFrame.data, pendingCameraFrame.length, "JPEG_SEND_FAILED")) {
    ++cameraFramesSentForTurn;
    Serial.printf("[摄像头] 第 %u 张 JPEG 已发送：%u 字节\n",
                  static_cast<unsigned>(pendingCameraFrame.shot),
                  static_cast<unsigned>(pendingCameraFrame.length - 1));
    discardPendingCameraFrame();
  }
}

static void flushUploadAudio() {
  if (!websocketConnected || !uploadBuffer || phase != DevicePhase::CAPTURING) {
    return;
  }
  if (!pendingAudioFrameLength) {
    const size_t available = xStreamBufferBytesAvailable(uploadBuffer);
    if (!available) return;
    const size_t read = xStreamBufferReceive(
        uploadBuffer,
        pendingAudioFrame + 1,
        min(available, MICROPHONE_CHUNK_BYTES),
        0);
    if (!read) return;
    pendingAudioFrame[0] = AI_GUIDE_FRAME_PCM;
    pendingAudioFrameLength = read + 1;
  }
  if (sendMediaFrameOnce(pendingAudioFrame, pendingAudioFrameLength, "PCM_SEND_FAILED")) {
    audioFrameSentForTurn = true;
    pendingAudioFrameLength = 0;
  }
}

static void finishTurn() {
  if (phase != DevicePhase::CAPTURING || uploadFinishing) {
    return;
  }
  microphoneCaptureEnabled = false;
  turnSpeechEndAt = speechSeen ? lastSpeechAt : 0;
  uploadFinishing = true;
  uploadFinishAt = millis();
  /* 第 3 张通常已与 VAD 静音判定并行抓拍。只有短句/按键提早结束时
   * 才在这里补请求；主流程不再为临时抓拍串行等待 1.2 秒。 */
  if (cameraTaskHandle && cameraRequestQueue) {
    while (cameraFramesRequestedForTurn < min(cameraTargetCount, uint8_t(2))) {
      const uint8_t before = cameraFramesRequestedForTurn;
      requestCameraShot();
      if (cameraFramesRequestedForTurn == before) break;
    }
    if (cameraTargetCount == 3) requestFinalCameraShot();
  }
}

static void cancelEmptyTurn() {
  if (phase != DevicePhase::CAPTURING || !microphoneCaptureEnabled) return;
  microphoneCaptureEnabled = false;
  uploadFinishing = false;
  xStreamBufferReset(uploadBuffer);
  Serial.printf("[VAD] %u ms 内没有有效声音，取消空采集%s\n",
                static_cast<unsigned>(wakeTurn ? WAKE_NO_SPEECH_TIMEOUT_MS
                                               : AUTO_NO_SPEECH_TIMEOUT_MS),
                wakeTurn ? "（唤醒后）" : "");
  cancelAwaitingAck = true;
  controlWaitSince = millis();
  if (!sendControl(String("{\"type\":\"turn_cancel\",\"request_id\":\"") + activeRequestId +
                   "\",\"reason\":\"" + (wakeTurn ? "wake_no_speech" : "no_speech_timeout") + "\"}")) {
    abortTurnForTransport("turn_cancel 发送失败");
    return;
  }
  setLocalPhase(DevicePhase::REQUESTING, "空采集已取消，等待服务端确认");
}

static void completeUpload() {
  if (!uploadFinishing) return;
  if (!websocketConnected || phase != DevicePhase::CAPTURING) { uploadFinishing = false; return; }
  if (millis() - uploadFinishAt > 5000) {
    uploadFinishing = false;
    webSocket.disconnect();
    return;
  }
  if (millis() - uploadFinishAt < 80 || xStreamBufferBytesAvailable(uploadBuffer) ||
      pendingAudioFrameLength) return;
  if (pendingCameraFrame.data) {
    if (millis() - uploadFinishAt < cameraGraceMs()) return;
    discardPendingCameraFrame();  // No write has been attempted on this frame.
    discardDeferredFinalFrame();
  }
  /* 正常路径第三张已预抓拍；相机异常时最多宽限 280ms，绝不让图片
   * 故障把语音提交重新拖慢 1.2 秒。 */
  if (cameraTaskHandle && cameraFramesSentForTurn < cameraTargetCount &&
      millis() - uploadFinishAt < cameraGraceMs()) return;
  if (cameraFramesSentForTurn < cameraTargetCount) {
    Serial.printf("[摄像头] 提交限时：目标 %u 张、实发 %u 张；优先保证语音响应\n",
                  static_cast<unsigned>(cameraTargetCount),
                  static_cast<unsigned>(cameraFramesSentForTurn));
  }
  uploadFinishing = false;
  Serial.printf("[音频] turn_commit：停止采集到提交 %u ms\n",
                static_cast<unsigned>(millis() - uploadFinishAt));
  const uint32_t speechEndMs = turnSpeechEndAt ? turnSpeechEndAt - captureStartedAt : 0;
  const uint32_t speechEndToCommitMs = turnSpeechEndAt ? millis() - turnSpeechEndAt : 0;
  if (!sendControl(String("{\"type\":\"turn_commit\",\"request_id\":\"") + activeRequestId +
                   "\",\"speech_end_ms\":" + String(speechEndMs) +
                   ",\"speech_end_to_commit_ms\":" + String(speechEndToCommitMs) + "}")) {
    abortTurnForTransport("turn_commit 发送失败");
    return;
  }
  setLocalPhase(DevicePhase::THINKING, "问题已提交");
}

static void interruptTurn() {
  if (!websocketConnected || activeRequestId.isEmpty()) {
    return;
  }
  microphoneCaptureEnabled = false;
  clearPlayback();
  sendControl(String("{\"type\":\"interrupt\",\"request_id\":\"") + activeRequestId + "\"}");
  Serial.println("[按键] 已请求停止讲解");
}

static void applyServerState(const char *state, const char *message) {
  if (!state) {
    return;
  }
  if (strcmp(state, "listening") == 0) {
    serverListening = true;
    retryListeningAt = 0;
    serverAwaitingPlaybackFinished = false;
    cancelAwaitingAck = false;
    controlWaitSince = 0;
    microphoneCaptureEnabled = false;
    if (playbackBusy() || (waitingPlaybackCompletion && !playbackFinishedSent)) {
      setLocalPhase(DevicePhase::COOLDOWN, "等待本机语音播放完成");
    } else {
      activeRequestId = "";
      setLocalPhase(DevicePhase::LISTENING, message);
    }
  } else if (strcmp(state, "capturing") == 0) {
    serverListening = false;
    /* turn_start 后立即在本机缓存音频，但服务端 turn_ready 前绝不上传。 */
    if (serverTurnReady && microphoneCaptureEnabled) {
      setLocalPhase(DevicePhase::CAPTURING, message);
    } else {
      Serial.println("[状态] 服务端已进入录音态，等待 turn_ready 后上传预录音频");
    }
  } else if (strcmp(state, "thinking") == 0) {
    serverListening = false;
    microphoneCaptureEnabled = false;
    setLocalPhase(DevicePhase::THINKING, message);
  } else if (strcmp(state, "speaking") == 0) {
    serverListening = false;
    microphoneCaptureEnabled = false;
    setLocalPhase(DevicePhase::SPEAKING, message);
  } else if (strcmp(state, "cooldown") == 0) {
    serverListening = false;
    setLocalPhase(DevicePhase::COOLDOWN, message);
  }
}

static void handleTextMessage(uint8_t *payload, size_t length) {
  StaticJsonDocument<384> filter;
  filter["type"] = true;
  filter["state"] = true;
  filter["message"] = true;
  filter["msg"] = true;
  filter["code"] = true;
  filter["reason"] = true;
  filter["request_id"] = true;
  filter["input_image"]["max_count"] = true;
  filter["count"] = true;
  filter["silence_ms"] = true;
  filter["command"] = true;
  filter["action"] = true;
  filter["features"]["playback_finished"] = true;
  filter["features"]["proactive"]["idle_ms"] = true;
  filter["retryable"] = true;
  filter["retry_after_ms"] = true;
  filter["awaiting_playback_finished"] = true;

  StaticJsonDocument<768> doc;
  DeserializationError error = deserializeJson(
      doc,
      payload,
      length,
      DeserializationOption::Filter(filter));
  if (error) {
    Serial.printf("[下行] JSON 解析失败：%s\n", error.c_str());
    return;
  }

  const char *type = doc["type"] | "";
  const char *requestId = doc["request_id"] | "";
  const char *message = doc["message"] | doc["msg"] | "";
  Serial.printf("[下行] type=%s request=%s message=%s\n", type, requestId, message);

  // Delayed controls from an older request must not clear a newly started turn.
  if (requestId[0] && activeRequestId != String(requestId) &&
      strcmp(type, "connected") != 0 && strcmp(type, "device_command") != 0 &&
      strcmp(type, "command") != 0) {
    Serial.println("[下行] 忽略过期轮次控制消息");
    return;
  }

  if (strcmp(type, "connected") == 0) {
    serverSupportsPlaybackFinished = (doc["features"]["playback_finished"] | false) &&
        playbackDmaEventsReady && playbackTaskHandle != nullptr;
    Serial.println("[网络] AI 设备网关握手成功");
    serverMaxImageCount = doc["input_image"]["max_count"] | 1U;
    Serial.printf("[摄像头] 服务端允许每轮最多 %u 张图片\n",
                  static_cast<unsigned>(serverMaxImageCount));
    // 旧网关不下发该字段 → 0，保持关闭；最短 1 分钟，防止误配导致频繁拍照。
    const uint32_t idleMs = doc["features"]["proactive"]["idle_ms"] | 0U;
    proactiveIdleMs = idleMs == 0 ? 0 : max(idleMs, uint32_t(60000));
    if (lastInteractionAt == 0) lastInteractionAt = millis();
    Serial.printf("[主动] 主动搭话：%s，空闲 %u 秒后看一眼\n",
                  proactiveIdleMs ? "开启" : "关闭",
                  static_cast<unsigned>(proactiveIdleMs / 1000));
  } else if (strcmp(type, "state") == 0) {
    applyServerState(doc["state"] | "", message);
    if (strcmp(doc["state"] | "", "cooldown") == 0) {
      serverAwaitingPlaybackFinished = doc["awaiting_playback_finished"] | false;
    }
  } else if (strcmp(type, "busy") == 0) {
    if (phase != DevicePhase::REQUESTING) return;
    microphoneCaptureEnabled = false;
    serverTurnReady = false;
    uploadFinishing = false;
    cancelAwaitingAck = false;
    xStreamBufferReset(uploadBuffer);
    controlWaitSince = millis();
    retryListeningAt = millis() + min(uint32_t(2000),
        max(uint32_t(200), static_cast<uint32_t>(doc["retry_after_ms"] | 500U)));
    serverListening = strcmp(doc["state"] | "", "listening") == 0;
    setLocalPhase(DevicePhase::REQUESTING, "服务端忙，保留会话等待恢复");
  } else if (strcmp(type, "turn_ready") == 0) {
    if ((phase != DevicePhase::REQUESTING && phase != DevicePhase::CAPTURING) ||
        activeRequestId != String(requestId)) {
      return;
    }
    if (!buttonStablePressed && !serialTurnRequested && !wakeTurn && !proactiveTurn) {
      microphoneCaptureEnabled = false;
      xStreamBufferReset(uploadBuffer);
      sendControl("{\"type\":\"turn_cancel\"}");
      return;
    }
    serverTurnReady = true;
    controlWaitSince = 0;
    captureReadyAt = millis();
    if (proactiveTurn) {
      // 没有语音：拍一张后立即进入提交流程，由 completeUpload 在图片发出后 commit。
      setLocalPhase(DevicePhase::CAPTURING, "主动搭话：抓拍中");
      audioFrameSentForTurn = true;
      requestCameraShot();
      finishTurn();
      return;
    }
    Serial.printf("[音频] turn_ready：本地预录 %u ms，待上传 %u 字节\n",
                  static_cast<unsigned>(captureReadyAt - captureStartedAt),
                  static_cast<unsigned>(xStreamBufferBytesAvailable(uploadBuffer)));
    setLocalPhase(DevicePhase::CAPTURING, "正在录音，静音 0.7 秒自动提交");
    if (cameraTaskHandle && cameraTargetCount > 0) {
      requestCameraShot();
      Serial.println("[摄像头] 首张已异步请求；中段与静音窗口继续抓拍");
    } else {
      Serial.println("[摄像头] 未启用抓拍任务或服务端不接受图片，本轮纯语音");
    }
  } else if (strcmp(type, "audio_start") == 0) {
    clearPlayback();
    Serial.println("[播放] audio_start：独立任务与有界 PSRAM 音频队列");
    if (xSemaphoreTake(playbackMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
      playbackStreamOpen = true;
      playbackDrained = false;
      xSemaphoreGive(playbackMutex);
    } else playbackFailurePending = true;
    firstAudioReceivedAt = 0;
    firstI2sWriteAt = 0;
    setLocalPhase(DevicePhase::SPEAKING, "收到首段语音");
  } else if (strcmp(type, "audio_done") == 0) {
    playbackStreamOpen = false;
    if (playbackTaskHandle) xTaskNotifyGive(playbackTaskHandle);
  } else if (strcmp(type, "interrupt_ack") == 0) {
    clearPlayback();
    activeRequestId = "";
    serverListening = true;
    followUpNotBefore = millis() + FOLLOW_UP_GUARD_MS;
    setLocalPhase(DevicePhase::COOLDOWN, "已停止，等待本机尾音清理");
  } else if (strcmp(type, "image_received") == 0) {
    if (activeRequestId == String(requestId)) {
      cameraFramesAcknowledgedForTurn = static_cast<uint8_t>(doc["count"] | 1U);
      Serial.printf("[摄像头] 服务端确认图片已收到，count=%u\n",
                    static_cast<unsigned>(cameraFramesAcknowledgedForTurn));
    }
  } else if (strcmp(type, "silence_committed") == 0) {
    if (activeRequestId == String(requestId) && phase == DevicePhase::CAPTURING) {
      microphoneCaptureEnabled = false;
      uploadFinishing = false;
      xStreamBufferReset(uploadBuffer);
      ++serverSilenceFallbackCount;
      Serial.printf("[VAD] 服务端静音兜底提交：%u ms，累计 %u 次\n",
                    static_cast<unsigned>(doc["silence_ms"] | 0U),
                    static_cast<unsigned>(serverSilenceFallbackCount));
      setLocalPhase(DevicePhase::THINKING, "服务端已提交，等待回答");
    }
  } else if (strcmp(type, "turn_ignored") == 0) {
    turnFailed = true;
    microphoneCaptureEnabled = false;
    uploadFinishing = false;
    xStreamBufferReset(uploadBuffer);
    if (strcmp(doc["reason"] | "", "audio_too_short") == 0) {
      Serial.println("[提示] 语音太短，请说完整一句话再试");
    }
    serialTurnRequested = false;
    activeRequestId = "";
    cancelAwaitingAck = false;
    followUpNotBefore = millis() + FOLLOW_UP_GUARD_MS;
    serverListening = true;
    setLocalPhase(DevicePhase::LISTENING, doc["reason"] | "没有听清");
  } else if (strcmp(type, "turn_done") == 0) {
    if (cameraFramesSentForTurn != cameraFramesAcknowledgedForTurn) {
      Serial.printf("[摄像头] 本轮图片发送 %u 张、服务端确认 %u 张\n",
                    static_cast<unsigned>(cameraFramesSentForTurn),
                    static_cast<unsigned>(cameraFramesAcknowledgedForTurn));
    }
    serialTurnRequested = false;
    turnNetworkDone = true;
    waitingPlaybackCompletion = true;
    microphoneCaptureEnabled = false;
    setLocalPhase(DevicePhase::COOLDOWN, "模型输出结束，等待本机播放尾音");
  } else if (strcmp(type, "device_command") == 0 || strcmp(type, "command") == 0) {
    /* 服务端应在识别到“关机/休眠”意图后，发送此结构化指令。
     * 不从 TTS 文本猜测，以免 AI 说到“关机”二字时误关机。 */
    const char *command = doc["command"] | doc["action"] | "";
    Serial.printf("[设备命令] %s\n", command);
    if (strcmp(command, "sleep") == 0 || strcmp(command, "shutdown") == 0) {
      enterDeepSleep("收到服务端休眠命令");
    }
  } else if (strcmp(type, "error") == 0) {
    const char *code = doc["code"] | "UNKNOWN";
    Serial.printf("[服务错误] code=%s message=%s\n", code, message);
    if (strcmp(code, "INVALID_IMAGE") == 0 ||
        strcmp(code, "IMAGE_TOO_LARGE") == 0 ||
        strcmp(code, "IMAGE_TOO_LARGE_FOR_MODEL") == 0 ||
        strcmp(code, "TOO_MANY_IMAGES") == 0 ||
        strcmp(code, "IMAGES_TOO_LARGE") == 0) {
      Serial.println("[摄像头] 图片被服务端拒绝，本轮继续按语音处理");
      return;
    }
    turnFailed = true;
    microphoneCaptureEnabled = false;
    clearPlayback();
    serverListening = false;
    controlWaitSince = millis();
    retryListeningAt = millis() + 400;
    setLocalPhase(DevicePhase::REQUESTING, "服务异常，重连恢复当前会话");
  }
}

static void webSocketEvent(WStype_t type, uint8_t *payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      websocketConnected = false;
      serverListening = false;
      serverSupportsPlaybackFinished = false;
      serverAwaitingPlaybackFinished = false;
      retryListeningAt = 0;
      cancelAwaitingAck = false;
      controlWaitSince = 0;
      activeRequestId = "";
      microphoneCaptureEnabled = false;
      clearPlayback();
      Serial.printf("[网络] WebSocket 已断开，payload_len=%u", static_cast<unsigned>(length));
      if (payload && length > 0) {
        Serial.printf("，payload=%.*s", static_cast<int>(length), reinterpret_cast<char *>(payload));
      }
      Serial.println();
      setLocalPhase(DevicePhase::DISCONNECTED, "等待自动重连");
      break;
    case WStype_CONNECTED:
      websocketConnected = true;
      {
        // 连接路径带 device_token 查询参数，串口只打印 ? 之前的部分。
        String connectedPath = payload ? String(reinterpret_cast<char *>(payload)) : String();
        const int query = connectedPath.indexOf('?');
        if (query >= 0) connectedPath = connectedPath.substring(0, query) + "?device_token=***";
        Serial.printf("[网络] WebSocket 已连接：%s\n", connectedPath.c_str());
      }
      break;
    case WStype_TEXT:
      handleTextMessage(payload, length);
      break;
    case WStype_BIN:
      if (playbackStreamOpen && length > 0) {
        if (firstAudioReceivedAt == 0) firstAudioReceivedAt = millis();
        queuePlayback(payload, length);
      }
      break;
    case WStype_ERROR:
      Serial.printf("[网络] WebSocket 协议错误，payload_len=%u", static_cast<unsigned>(length));
      if (payload && length > 0) {
        Serial.printf("，payload=%.*s", static_cast<int>(length), reinterpret_cast<char *>(payload));
      }
      Serial.println();
      break;
    case WStype_PING:
      Serial.println("[网络] 收到 WebSocket PING");
      break;
    case WStype_PONG:
      Serial.println("[网络] 收到 WebSocket PONG");
      break;
    default:
      break;
  }
}

static void connectGateway() {
  String mac = WiFi.macAddress();
  mac.replace(":", "");
  deviceId = "xiao-s3-" + mac;

  // arduinoWebSockets + this gateway disconnects shortly after a successful
  // upgrade when custom Authorization headers are used. The gateway's
  // documented query authentication remains stable on ESP32.
  String path = String(AI_GUIDE_WS_PATH) +
                "?device_token=" + AI_GUIDE_DEVICE_TOKEN +
                "&device_id=" + deviceId;

  Serial.printf("[网络] 连接 wss://%s%s\n",
                AI_GUIDE_GATEWAY_HOST,
                AI_GUIDE_WS_PATH);

  webSocket.beginSSL(
      AI_GUIDE_GATEWAY_HOST,
      AI_GUIDE_GATEWAY_PORT,
      path.c_str()
  );

  webSocket.onEvent(webSocketEvent);
  webSocket.setReconnectInterval(3000);
  // The campus gateway uses the esp32-av/1.0 JSON ping/pong below.
  // Do not run a second WebSocket control-frame heartbeat: some proxy/backend
  // combinations do not return its PONG and the client then disconnects itself.
  webSocket.disableHeartbeat();
}

static void updateButton() {
  const bool rawPressed = digitalRead(ACTION_BUTTON_PIN) == LOW;
  if (rawPressed != buttonPressed) {
    buttonPressed = rawPressed;
    buttonChangedAt = millis();
  }
  if (millis() - buttonChangedAt < 35 || buttonStablePressed == buttonPressed) {
    return;
  }

  buttonStablePressed = buttonPressed;
  if (buttonStablePressed) {
    if (phase == DevicePhase::LISTENING) {
      beginTurn();
    } else if (phase == DevicePhase::THINKING || phase == DevicePhase::SPEAKING ||
               phase == DevicePhase::COOLDOWN) {
      interruptTurn();
    }
  } else if (phase == DevicePhase::CAPTURING) {
    finishTurn();
  }
}

void setup() {
  pinMode(STATUS_LED_PIN, OUTPUT);
  pinMode(ACTION_BUTTON_PIN, INPUT_PULLUP);
  setStatusLed(false);

  Serial.begin(115200);
  delay(800);
  Serial.println();
  Serial.println("========================================");
  Serial.println(" XIAO ESP32S3 Sense · AI 视听说终端");
  Serial.println("========================================");

  // Keep internal RAM available for TLS and WakeNet.
  auto *upStorage = static_cast<uint8_t *>(heap_caps_malloc(UPLOAD_BUFFER_BYTES + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  auto *preRollStorage = static_cast<uint8_t *>(heap_caps_malloc(PRE_ROLL_BUFFER_BYTES + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  auto *playbackStorage = static_cast<uint8_t *>(heap_caps_malloc(
      PLAYBACK_BUFFER_BYTES + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  playbackMonoBytes = static_cast<uint8_t *>(heap_caps_malloc(
      PLAYBACK_MAX_MONO_SAMPLES * sizeof(int16_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  playbackStereoSamples = static_cast<int16_t *>(heap_caps_malloc(
      PLAYBACK_MAX_MONO_SAMPLES * 2 * sizeof(int16_t),
      MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  uploadBuffer = upStorage ? xStreamBufferCreateStatic(UPLOAD_BUFFER_BYTES + 1, 1, upStorage, &uploadControl) : nullptr;
  preRollBuffer = preRollStorage ? xStreamBufferCreateStatic(PRE_ROLL_BUFFER_BYTES + 1, 1, preRollStorage, &preRollControl) : nullptr;
  preRollMutex = xSemaphoreCreateMutex();
  playbackBuffer = playbackStorage ? xStreamBufferCreateStatic(PLAYBACK_BUFFER_BYTES + 1,
      1, playbackStorage, &playbackControl) : nullptr;
  playbackMutex = xSemaphoreCreateMutex();
  cameraFrameQueue = xQueueCreate(DESIRED_CAMERA_SHOTS, sizeof(CameraPacket));
  cameraRequestQueue = xQueueCreate(DESIRED_CAMERA_SHOTS, sizeof(CameraRequest));
  if (!uploadBuffer || !preRollBuffer || !preRollMutex || !playbackStereoSamples ||
      !playbackBuffer || !playbackMutex || !playbackMonoBytes) {
    fatalHardwareError = true;
    Serial.println("[自检] 音频缓冲创建失败，设备停止启动");
    setLocalPhase(DevicePhase::ERROR_STATE);
    return;
  }

  const bool microphoneReady = initMicrophone();
  speakerReady = initSpeaker();
  cameraReady = initCamera();
  if (!microphoneReady || !speakerReady) {
    fatalHardwareError = true;
    setLocalPhase(DevicePhase::ERROR_STATE, "音频硬件初始化失败");
    return;
  }

  if (xTaskCreatePinnedToCore(microphoneTask, "microphone", 8192, nullptr, 3,
                              &microphoneTaskHandle, 0) != pdPASS) {
    fatalHardwareError = true;
    Serial.println("[自检] 麦克风任务创建失败，停止启动");
    setLocalPhase(DevicePhase::ERROR_STATE, "麦克风任务不可用");
    return;
  }
  if (cameraFrameQueue && cameraRequestQueue &&
      xTaskCreatePinnedToCore(cameraTask, "camera", 6144, nullptr, 1,
                              &cameraTaskHandle, 1) != pdPASS) {
    cameraTaskHandle = nullptr;
  }
  if (!cameraTaskHandle) {
    Serial.println("[自检] 摄像头任务创建失败，将只发送语音");
  }
  if (xTaskCreatePinnedToCore(playbackTask, "playback", 6144, nullptr, 4,
                            &playbackTaskHandle, 1) != pdPASS) {
    fatalHardwareError = true;
    Serial.println("[自检] 独立播放任务创建失败，停止启动");
    setLocalPhase(DevicePhase::ERROR_STATE, "播放任务不可用");
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setTxPower(WIFI_POWER_19_5dBm);
  WiFiManager wifiManager;
  Preferences preferences;
  preferences.begin("ai-guide", false);
  if (!preferences.getBool("wifi_init", false)) {
    /* 清理板子原测试程序遗留的 Wi-Fi，确保首次烧录一定出现配网热点。 */
    wifiManager.resetSettings();
    preferences.putBool("wifi_init", true);
    Serial.println("[配网] 已清理旧固件遗留的 Wi-Fi 配置");
  }
  preferences.end();
  /* 0 表示配网热点不超时；固定信道 1，提高手机兼容性。 */
  wifiManager.setConfigPortalTimeout(0);
  wifiManager.setConnectTimeout(8);
  wifiManager.setWiFiAPChannel(1);
  wifiManager.setTitle("AI 视听说终端配网");
  Serial.println("[配网] 如未保存 Wi-Fi，请用手机连接 AI-GUIDE-SETUP");
  bool connected = wifiManager.autoConnect(AI_GUIDE_SETUP_AP_NAME, AI_GUIDE_SETUP_AP_PASSWORD);
  if (!connected) {
    Serial.println("[配网] 配网超时，设备将在 3 秒后重启");
    delay(3000);
    ESP.restart();
  }

  Serial.printf("[网络] Wi-Fi 已连接：%s，IP=%s，信号=%d dBm\n",
                WiFi.SSID().c_str(),
                WiFi.localIP().toString().c_str(),
                WiFi.RSSI());
  connectGateway();
}

void loop() {
  if (Serial.available() > 0) {
    const char command = static_cast<char>(Serial.read());
    if (command == 't' || command == 'T') {
      playLocalTestTone();
    } else if (command == 'r' || command == 'R') {
      if (phase == DevicePhase::LISTENING) {
        serialTurnRequested = true;
        Serial.println("[串口调试] r：开始一轮录音");
        beginTurn();
      } else if (phase == DevicePhase::CAPTURING) {
        Serial.println("[串口调试] r：结束录音并提交");
        finishTurn();
        serialTurnRequested = false;
      } else {
        Serial.printf("[串口调试] 当前状态“%s”不接受 r\n", phaseName(phase));
      }
    }
  }

  webSocket.loop();
  pollPlaybackCompletion();
  updateWakeLedPulse();
  if (turnMetricsPending && websocketConnected && !metricsRequestId.isEmpty()) {
    turnMetricsPending = false;
    sendControl(String("{\"type\":\"turn_metrics\",\"request_id\":\"") + metricsRequestId +
                "\",\"first_audio_received_ms\":" +
                String(firstAudioReceivedAt - metricsTurnStartedAt) +
                ",\"first_i2s_write_ms\":" +
                String(firstI2sWriteAt - metricsTurnStartedAt) + "}");
  }
  if (uploadOverflow && (phase == DevicePhase::REQUESTING || phase == DevicePhase::CAPTURING)) {
    abortTurnForTransport("本机音频缓冲溢出");
  }
  if (phase == DevicePhase::REQUESTING && microphoneCaptureEnabled &&
      millis() - captureStartedAt >= TURN_READY_TIMEOUT_MS) {
    abortTurnForTransport("turn_ready 超时");
  }
  if (retryListeningAt && phase == DevicePhase::REQUESTING &&
      static_cast<int32_t>(millis() - retryListeningAt) >= 0) {
    retryListeningAt = 0;
    if (serverListening && !playbackBusy()) {
      activeRequestId = "";
      followUpNotBefore = millis() + FOLLOW_UP_GUARD_MS;
      setLocalPhase(DevicePhase::LISTENING, "服务端就绪，可直接继续提问");
    } else {
      abortTurnForTransport("等待服务恢复，重新建立会话");
    }
  }
  if (phase == DevicePhase::REQUESTING && !microphoneCaptureEnabled && controlWaitSince &&
      millis() - controlWaitSince >= CONTROL_WAIT_TIMEOUT_MS) {
    abortTurnForTransport(cancelAwaitingAck ? "取消确认超时" : "控制状态恢复超时");
  }
  if (!wakeInitAttempted && websocketConnected && phase == DevicePhase::LISTENING) initWakeWord();
  if ((wakePending || followUpPending) && websocketConnected && phase == DevicePhase::LISTENING) {
    const bool isFollowUp = followUpPending;
    wakePending = false;
    followUpPending = false;
    wakeTurn = true;
    if (isFollowUp) Serial.println("[连续对话] 已接收追问");
    beginTurn(true, isFollowUp);
    if (!isFollowUp) {
      // 阻塞提示音会吞掉紧接唤醒词的问题，并被麦克风回采。保持 LED 提示，
      // 立即预录直到 turn_ready；唤醒词本身仍不进入本轮音频。
      Serial.println("[唤醒] 已唤醒并预录问题，等待 turn_ready");
      wakeLedPulseStep = 1;
      wakeLedPulseNextAt = millis();
    }
  }
  if (proactiveIdleMs && cameraTaskHandle && websocketConnected && serverListening &&
      phase == DevicePhase::LISTENING && !wakePending && !followUpPending &&
      !playbackBusy() && !conversationActive(millis()) &&
      millis() - lastInteractionAt >= proactiveIdleMs) {
    wakeTurn = false;
    serialTurnRequested = false;
    beginTurn(false, false, true);
    lastInteractionAt = millis();  // 本次被拒或开不了口，也要再等一个完整空闲周期。
  }
  updateButton();
  if (bargeInRequested) {
    bargeInRequested = false;
    if (phase == DevicePhase::THINKING || phase == DevicePhase::SPEAKING) {
      interruptTurn();  // 打断后进入续聊窗口，直接说问题即可，无需再喊唤醒词。
    }
  }
  if (microphoneCaptureEnabled && phase == DevicePhase::CAPTURING) {
    const uint32_t now = millis();
    if (speechSeen && cameraTargetCount >= 2 &&
        cameraFramesRequestedForTurn == 1 &&
        now - captureReadyAt >= CAMERA_MIDDLE_SHOT_MS) {
      requestCameraShot();
    }
    refreshFinalCameraShotOnSpeech();
    if (speechSeen && cameraTargetCount == DESIRED_CAMERA_SHOTS &&
        cameraFramesRequestedForTurn >= 2 &&
        captureConsecutiveQuietMs >= CAMERA_FINAL_PREFETCH_QUIET_MS) {
      requestFinalCameraShot();
    }
    if (speechSeen && captureConsecutiveQuietMs >= AUTO_SILENCE_COMMIT_MS) {
      Serial.printf("[VAD] 连续静音 %u ms，距末次语音 %u ms，自动提交\n",
                    static_cast<unsigned>(captureConsecutiveQuietMs),
                    static_cast<unsigned>(now - lastSpeechAt));
      finishTurn();
    } else if (!speechSeen &&
               now - captureReadyAt >= (wakeTurn ? WAKE_NO_SPEECH_TIMEOUT_MS
                                                   : AUTO_NO_SPEECH_TIMEOUT_MS) &&
               now - lastAnyVoiceAt >= (wakeTurn ? WAKE_NO_SPEECH_TIMEOUT_MS
                                                : AUTO_NO_SPEECH_TIMEOUT_MS)) {
      cancelEmptyTurn();
    }
  }
  flushUploadAudio();
  flushCameraFrame();
  completeUpload();

  if (phase == DevicePhase::CAPTURING && microphoneCaptureEnabled &&
      millis() - captureStartedAt >= AI_GUIDE_MAX_TURN_MS) {
    Serial.println("[音频] 已达到单轮最长录音时间，自动提交");
    finishTurn();
  }

  if (websocketConnected && millis() - lastPingAt >= 10000) {
    lastPingAt = millis();
    sendControl(String("{\"type\":\"ping\",\"ts\":") + String(millis()) + "}");
  }

  if (millis() - lastStatusPrintAt >= 10000) {
    lastStatusPrintAt = millis();
    Serial.printf("[运行] 状态=%s Wi-Fi=%d dBm 空闲内存=%u PSRAM=%u\n",
                  phaseName(phase),
                  WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -127,
                  ESP.getFreeHeap(),
                  ESP.getFreePsram());
  }
  delay(2);
}
