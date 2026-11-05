#pragma once

/* 复制为 device_config.h 后填写；device_config.h 已被 .gitignore 排除，不要提交。 */
#define AI_GUIDE_GATEWAY_HOST "huanwoshidai.com.cn"
#define AI_GUIDE_GATEWAY_PORT 443
/* 与服务端 CAMPUS_ESP32_DEVICE_TOKENS 中的某一项一致。 */
#define AI_GUIDE_DEVICE_TOKEN "replace-with-device-token"
#define AI_GUIDE_SETUP_AP_NAME "AI-GUIDE-SETUP"
#define AI_GUIDE_SETUP_AP_PASSWORD "replace-with-setup-ap-password"
#define AI_GUIDE_MAX_TURN_MS 8000UL
/* 1 = 不校验服务端证书（当前线上固件的做法，有被同网段冒充的风险）；0 = 校验。 */
#define AI_GUIDE_TLS_INSECURE 1
