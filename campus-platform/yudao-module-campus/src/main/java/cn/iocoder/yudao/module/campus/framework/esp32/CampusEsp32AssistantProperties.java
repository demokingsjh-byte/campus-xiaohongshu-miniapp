package cn.iocoder.yudao.module.campus.framework.esp32;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ESP32-S3 视听说网关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.esp32-assistant")
public class CampusEsp32AssistantProperties {

    private static final String RESPONSES_API = "https://ark.cn-beijing.volces.com/api/v3/responses";
    private static final String LEGACY_CHAT_API = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String PRO_MODEL = "doubao-seed-2-1-pro-260915";
    private static final String LEGACY_TURBO_MODEL = "doubao-seed-2-1-turbo-260628";

    private boolean enabled = false;
    private String path = "/app-api/campus/esp32/assistant/ws";
    /** 兼容 AI_GUIDE 原有固件，迁移时可不立即升级固件路径。 */
    private String legacyPath = "/ai_guide_service/api/v1/esp32/assistant/ws";
    private String deviceTokens = "";
    private int maxAudioSeconds = 30;
    private int maxConnectionsPerIp = 20;
    private int cooldownMillis = 650;
    private int outputAudioChunkBytes = 1920;
    private int playbackPacePercent = 90;

    /** 火山方舟 Responses 地址；如需兼容旧自建模型，可改为 ws:// 地址。 */
    private String modelUrl = RESPONSES_API;
    private String modelName = PRO_MODEL;
    private String modelToken = "";
    /**
     * 模型请求协议：auto（按 model-url 推断）/ responses（火山方舟 Responses）/
     * chat（OpenAI 兼容 /chat/completions，例如 PAI-EAS 部署的 GLM、Qwen-VL、vLLM）。
     */
    private String modelProtocol = "auto";
    /** 单轮回答的最大输出 token 数，Responses 走 max_output_tokens，Chat 走 max_tokens。 */
    private int modelMaxTokens = 512;

    private String asrUrl = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel";
    private boolean asrEnabled = true;
    private String asrAppId = "";
    private String asrAccessToken = "";
    private String asrResourceId = "volc.bigasr.sauc.duration";

    private String ttsUrl = "wss://openspeech.bytedance.com/api/v3/tts/bidirection";
    private String ttsAppId = "";
    private String ttsAccessToken = "";
    private String ttsResourceId = "seed-tts-2.0";
    /** 火山 TTS：如梦音色。可通过 CAMPUS_VOLC_TTS_VOICE_TYPE 覆盖。 */
    private String ttsVoiceType = "zh_female_roumeinvyou_uranus_bigtts";

    /** 将线上遗留的 Chat + Turbo 配置迁移到当前 Responses + Pro 链路。 */
    public void setModelUrl(String modelUrl) {
        this.modelUrl = LEGACY_CHAT_API.equals(modelUrl) ? RESPONSES_API : modelUrl;
    }

    public void setModelName(String modelName) {
        this.modelName = LEGACY_TURBO_MODEL.equals(modelName) ? PRO_MODEL : modelName;
    }

    /**
     * 解析实际生效的模型协议。
     *
     * <p>显式配置 {@code chat} / {@code responses} 时优先使用配置值；
     * {@code auto} 则按 model-url 推断：包含 {@code /chat/completions} 走 OpenAI 兼容协议，
     * 其余（含 ws:// 自建模型）走火山方舟 Responses 协议。</p>
     */
    public String getResolvedModelProtocol() {
        String value = modelProtocol == null ? "" : modelProtocol.trim().toLowerCase();
        if ("chat".equals(value) || "responses".equals(value)) {
            return value;
        }
        String url = getModelUrl();
        return url != null && url.contains("/chat/completions") ? "chat" : "responses";
    }

    /** 是否使用 OpenAI 兼容的 /chat/completions 协议。 */
    public boolean isChatProtocol() {
        return "chat".equals(getResolvedModelProtocol());
    }

    /** 供日志与健康检查展示的模型提供方标识。 */
    public String getModelProvider() {
        String url = getModelUrl();
        if (url == null || url.trim().isEmpty()) {
            return "unknown";
        }
        if (url.contains("volces.com")) {
            return "volcengine-ark";
        }
        try {
            String host = java.net.URI.create(url).getHost();
            return hasText(host) ? host : "openai-compatible";
        } catch (Exception ignored) {
            return "openai-compatible";
        }
    }

    public List<String> getDeviceTokenList() {
        if (deviceTokens == null || deviceTokens.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(deviceTokens.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isFullyConfigured() {
        return enabled
                && !getDeviceTokenList().isEmpty()
                && hasText(modelUrl)
                && hasText(modelName)
                && hasText(modelToken)
                && asrEnabled
                && hasText(asrAppId)
                && hasText(asrAccessToken)
                && hasText(ttsAppId)
                && hasText(ttsAccessToken)
                && hasText(ttsVoiceType);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
