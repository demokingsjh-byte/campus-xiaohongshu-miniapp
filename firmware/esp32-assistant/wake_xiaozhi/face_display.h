#pragma once
#include <stdint.h>

/* 1.28 寸 GC9A01 圆屏（240x240）表情：用眼睛动画表示设备状态。
 * 依赖 “GFX Library for Arduino”（Arduino_GFX）。没接屏幕时 SPI 写出无副作用。 */

enum class FaceMood : uint8_t {
  SLEEPY,     // 未联网 / 重连中：半闭眼
  IDLE,       // 待机聆听：眨眼、偶尔左右看
  ATTENTIVE,  // 正在听你说：眼睛睁大
  THINKING,   // 等模型回答：眼睛往上看，下面三个点
  SPEAKING,   // 播报中：笑眼 + 嘴巴开合
  ERROR_FACE, // 硬件或服务异常：X X
};

void faceBegin();
void faceSetMood(FaceMood mood);
