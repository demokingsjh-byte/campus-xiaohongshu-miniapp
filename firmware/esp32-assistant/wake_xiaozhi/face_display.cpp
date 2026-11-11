#include "face_display.h"

#include <Arduino.h>
#include <math.h>
#include <esp_heap_caps.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

/* arduino-cli 的依赖库扫描不认 __has_include，必须直接 include 才会链接该库。
 * 需要安装 “GFX Library for Arduino”（已验证 1.6.9）。 */
#include <Arduino_GFX_Library.h>
#define FACE_HAS_GFX 1

/* 引脚：见 README「屏幕接线」。D0 留给电池电压检测，D5 留给外置按键。 */
static constexpr int FACE_PIN_BL = 2;     // D1 背光 PWM
static constexpr int FACE_PIN_CS = 3;     // D2
static constexpr int FACE_PIN_DC = 4;     // D3
static constexpr int FACE_PIN_RST = 5;    // D4
static constexpr int FACE_PIN_SCK = 43;   // D6
static constexpr int FACE_PIN_MOSI = 44;  // D7
static constexpr int32_t FACE_SPI_HZ = 40000000;
/* 部分 GC9A01 模块颜色反相（黑底变白底）时把它改成 false。 */
static constexpr bool FACE_PANEL_IPS = true;

/* 只刷眼睛所在的中间区域，帧缓冲放 PSRAM，避免占用 TLS / WakeNet 需要的内部 RAM。 */
static constexpr int16_t REGION_X = 20;
static constexpr int16_t REGION_Y = 45;
static constexpr int16_t REGION_W = 200;
static constexpr int16_t REGION_H = 150;
static constexpr uint32_t FRAME_INTERVAL_MS = 66;  // ~15 fps，静止画面不重刷

static constexpr uint16_t rgb565(uint8_t r, uint8_t g, uint8_t b) {
  return static_cast<uint16_t>(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3));
}
static constexpr uint16_t COLOR_BG = rgb565(0, 0, 0);
static constexpr uint16_t COLOR_EYE = rgb565(235, 250, 255);
static constexpr uint16_t COLOR_EYE_LISTEN = rgb565(120, 230, 255);
static constexpr uint16_t COLOR_CHEEK = rgb565(255, 120, 150);
static constexpr uint16_t COLOR_MOUTH = rgb565(255, 140, 160);
static constexpr uint16_t COLOR_ERROR = rgb565(255, 80, 80);
static constexpr uint16_t COLOR_DIM = rgb565(120, 140, 150);

static volatile FaceMood requestedMood = FaceMood::SLEEPY;

void faceSetMood(FaceMood mood) { requestedMood = mood; }

#if FACE_HAS_GFX

static Arduino_DataBus *faceBus = nullptr;
static Arduino_GFX *facePanel = nullptr;
static uint16_t *frame = nullptr;

/* ---------- 极简软件绘图（直接画进 PSRAM 帧缓冲） ---------- */
static void fillRect(int x, int y, int w, int h, uint16_t c) {
  int x0 = max(0, x), y0 = max(0, y);
  int x1 = min<int>(REGION_W, x + w), y1 = min<int>(REGION_H, y + h);
  for (int yy = y0; yy < y1; ++yy) {
    uint16_t *row = frame + yy * REGION_W;
    for (int xx = x0; xx < x1; ++xx) row[xx] = c;
  }
}

static void fillEllipse(int cx, int cy, int rx, int ry, uint16_t c) {
  if (rx <= 0 || ry <= 0) return;
  for (int dy = -ry; dy <= ry; ++dy) {
    const float t = 1.0f - (dy * dy) / float(ry * ry);
    const int half = int(rx * sqrtf(t > 0 ? t : 0) + 0.5f);
    fillRect(cx - half, cy + dy, half * 2 + 1, 1, c);
  }
}

static void fillRoundRect(int x, int y, int w, int h, int r, uint16_t c) {
  r = min(r, min(w, h) / 2);
  if (r <= 0) { fillRect(x, y, w, h, c); return; }
  for (int yy = 0; yy < h; ++yy) {
    int inset = 0;
    const int fromEdge = yy < r ? r - yy : (yy >= h - r ? yy - (h - r - 1) : 0);
    if (fromEdge > 0) {
      const float d = float(fromEdge) - 0.5f;
      inset = r - int(sqrtf(float(r * r) - d * d) + 0.5f);
    }
    fillRect(x + inset, y + yy, w - inset * 2, 1, c);
  }
}

static void thickLine(int x0, int y0, int x1, int y1, int t, uint16_t c) {
  const int steps = max(abs(x1 - x0), abs(y1 - y0));
  for (int i = 0; i <= steps; ++i) {
    const int x = x0 + (x1 - x0) * i / max(1, steps);
    const int y = y0 + (y1 - y0) * i / max(1, steps);
    fillRect(x - t / 2, y - t / 2, t, t, c);
  }
}

/* ---------- 表情参数：参数相同的帧直接跳过，不占 SPI ---------- */
struct FaceFrame {
  uint8_t mood;
  int8_t lookX, lookY;
  uint8_t open;    // 眼睛睁开程度 0..100
  uint8_t mouth;   // 嘴巴张开 0..14
  uint8_t dots;    // 思考点 0..3
  bool operator==(const FaceFrame &o) const {
    return mood == o.mood && lookX == o.lookX && lookY == o.lookY && open == o.open &&
           mouth == o.mouth && dots == o.dots;
  }
};

static constexpr int EYE_W = 50, EYE_H = 72, EYE_OFFSET = 46, EYE_CY = 62;

static void drawEye(int cx, int cy, int w, int h, int open, uint16_t color, bool happy) {
  const int eh = max(6, h * open / 100);
  fillRoundRect(cx - w / 2, cy - eh / 2, w, eh, w / 2, color);
  if (happy && eh > 20) {
    // 用背景色挖掉下半部分，得到 ^ ^ 笑眼
    fillEllipse(cx, cy + eh / 4 + 6, w / 2 + 8, eh / 2, COLOR_BG);
  }
}

static void render(const FaceFrame &f) {
  fillRect(0, 0, REGION_W, REGION_H, COLOR_BG);
  const int cx = REGION_W / 2;
  const int lx = cx - EYE_OFFSET + f.lookX;
  const int rx = cx + EYE_OFFSET + f.lookX;
  const int cy = EYE_CY + f.lookY;
  const FaceMood mood = static_cast<FaceMood>(f.mood);

  switch (mood) {
    case FaceMood::ERROR_FACE:
      for (int s : {-1, 1}) {
        const int ex = cx + s * EYE_OFFSET;
        thickLine(ex - 18, cy - 18, ex + 18, cy + 18, 9, COLOR_ERROR);
        thickLine(ex - 18, cy + 18, ex + 18, cy - 18, 9, COLOR_ERROR);
      }
      return;
    case FaceMood::SLEEPY:
      fillRoundRect(lx - EYE_W / 2, cy, EYE_W, 8, 4, COLOR_DIM);
      fillRoundRect(rx - EYE_W / 2, cy, EYE_W, 8, 4, COLOR_DIM);
      return;
    default:
      break;
  }

  const bool happy = mood == FaceMood::SPEAKING;
  const bool attentive = mood == FaceMood::ATTENTIVE;
  const uint16_t eyeColor = attentive ? COLOR_EYE_LISTEN : COLOR_EYE;
  const int w = attentive ? EYE_W + 6 : EYE_W;
  const int h = attentive ? EYE_H + 8 : (mood == FaceMood::THINKING ? EYE_H - 14 : EYE_H);
  drawEye(lx, cy, w, h, f.open, eyeColor, happy);
  drawEye(rx, cy, w, h, f.open, eyeColor, happy);

  if (mood == FaceMood::IDLE || happy) {
    fillEllipse(lx - 14, cy + 44, 12, 7, COLOR_CHEEK);
    fillEllipse(rx + 14, cy + 44, 12, 7, COLOR_CHEEK);
  }
  if (happy) {
    const int mh = 4 + f.mouth;
    fillRoundRect(cx - 11, cy + 50 - mh / 2, 22, mh, 6, COLOR_MOUTH);
  }
  if (mood == FaceMood::THINKING) {
    for (int i = 0; i < 3; ++i) {
      const uint16_t c = i < f.dots ? COLOR_EYE : COLOR_DIM;
      fillEllipse(cx - 22 + i * 22, cy + 58, 5, 5, c);
    }
  }
}

static void faceTask(void *) {
  FaceFrame last{};
  bool hasLast = false;
  uint32_t nextBlinkAt = millis() + 2500;
  uint32_t blinkStartedAt = 0;
  uint32_t nextGlanceAt = millis() + 4000;
  int8_t glanceX = 0;
  uint8_t lastBrightness = 0;

  while (true) {
    const uint32_t now = millis();
    const FaceMood mood = requestedMood;
    FaceFrame f{};
    f.mood = static_cast<uint8_t>(mood);
    f.open = 100;

    // 眨眼：约 140ms 内闭上再睁开，2.5~5.5 秒随机一次
    if (mood == FaceMood::IDLE || mood == FaceMood::SPEAKING || mood == FaceMood::THINKING) {
      if (!blinkStartedAt && now >= nextBlinkAt) blinkStartedAt = now;
      if (blinkStartedAt) {
        const uint32_t t = now - blinkStartedAt;
        if (t >= 140) {
          blinkStartedAt = 0;
          nextBlinkAt = now + 2500 + esp_random() % 3000;
        } else {
          f.open = static_cast<uint8_t>(t < 70 ? 100 - t * 90 / 70 : 10 + (t - 70) * 90 / 70);
        }
      }
    }
    if (mood == FaceMood::IDLE) {
      if (now >= nextGlanceAt) {
        const int8_t choices[] = {0, 0, -12, 12};
        glanceX = choices[esp_random() % 4];
        nextGlanceAt = now + 3000 + esp_random() % 3000;
      }
      f.lookX = glanceX;
    } else {
      glanceX = 0;
    }
    if (mood == FaceMood::THINKING) {
      f.lookX = 10;
      f.lookY = -8;
      f.dots = static_cast<uint8_t>((now / 350) % 4);
    }
    if (mood == FaceMood::SPEAKING) {
      f.mouth = static_cast<uint8_t>(7 + 7 * sinf(now * 0.018f));
    }

    const uint8_t brightness = mood == FaceMood::SLEEPY ? 60 : 200;
    if (brightness != lastBrightness) {
      ledcWrite(FACE_PIN_BL, brightness);
      lastBrightness = brightness;
    }
    if (!hasLast || !(f == last)) {
      render(f);
      facePanel->draw16bitRGBBitmap(REGION_X, REGION_Y, frame, REGION_W, REGION_H);
      last = f;
      hasLast = true;
    }
    vTaskDelay(pdMS_TO_TICKS(FRAME_INTERVAL_MS));
  }
}

void faceBegin() {
  frame = static_cast<uint16_t *>(heap_caps_malloc(REGION_W * REGION_H * sizeof(uint16_t),
                                                    MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  if (!frame) {
    Serial.println("[屏幕] PSRAM 帧缓冲分配失败，跳过表情显示");
    return;
  }
  ledcAttach(FACE_PIN_BL, 5000, 8);
  ledcWrite(FACE_PIN_BL, 0);
  faceBus = new Arduino_ESP32SPI(FACE_PIN_DC, FACE_PIN_CS, FACE_PIN_SCK, FACE_PIN_MOSI,
                                 GFX_NOT_DEFINED, FSPI);
  facePanel = new Arduino_GC9A01(faceBus, FACE_PIN_RST, 0, FACE_PANEL_IPS);
  if (!facePanel->begin(FACE_SPI_HZ)) {
    Serial.println("[屏幕] GC9A01 初始化失败，跳过表情显示");
    return;
  }
  facePanel->fillScreen(COLOR_BG);
  // 优先级与 loop 相同、放在核心 1：不抢麦克风（核心 0）和播放任务。
  if (xTaskCreatePinnedToCore(faceTask, "face", 4096, nullptr, 1, nullptr, 1) != pdPASS) {
    Serial.println("[屏幕] 表情任务创建失败");
    return;
  }
  Serial.println("[屏幕] GC9A01 表情已启动");
}

#else

void faceBegin() {
  Serial.println("[屏幕] 未安装 GFX Library for Arduino，跳过表情显示");
}

#endif
