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
    /**
     * turn_start 后迟迟没有检测到语音时，自动释放该轮。
     * 用于防止设备麦克风任务异常后一直占用 CAPTURING，导致后续轮次全部 busy。
     */
    private int noSpeechCaptureTimeoutMillis = 8000;
    private int maxConnectionsPerIp = 20;
    /**
     * 播报结束到恢复聆听的冷却时长。音频按领先量下发，设备侧还有约 1.2 秒缓冲未播完，
     * 冷却必须覆盖这段尾音，否则设备可能在自己的播报声里打开麦克风（自激）。
     */
    private int cooldownMillis = 1250;
    private int outputAudioChunkBytes = 1920;
    /**
     * 音频下发速率上限（相对实时的百分比）。≥100 才能在设备侧建立播放缓冲；
     * 低于 100 会让缓冲长期接近为空，网络抖动直接表现为播报断续。
     */
    private int playbackPacePercent = 300;
    /**
     * 目标播放缓冲领先量（毫秒）。网关先按速率上限把缓冲填到该值，之后维持该领先量，
     * 设备才有余量吸收 Wi-Fi 唤醒延迟与网络抖动。
     */
    private int outputAudioLeadMillis = 1200;

    /**
     * 单轮可接收的图片张数上限。设备端 ESP32 摄像头适合拍场景的若干帧，
     * 默认 5 张可在多角度对比 / 序列描述场景下使用，单张按 {@link #maxImageBytes} 限。
     */
    private int maxImageCount = 5;
    /** 单张图片体积上限（字节）。 */
    private int maxImageBytes = 2 * 1024 * 1024;
    /** 单轮所有图片体积合计上限（字节）。 */
    private int maxTotalImageBytes = 8 * 1024 * 1024;

    /**
     * 服务端静音自动提交阈值（毫秒）。连续静音达到该时长且本轮已超过
     * {@link Esp32ProtocolUtils#MIN_AUDIO_BYTES} 时，网关会主动调用 commitTurn，
     * 用来兜底设备固件 VAD「说完」判定失效导致的多轮采集拖延。
     * 设为 0 表示关闭此能力。
     */
    private int silenceCommitMillis = 1500;
    /**
     * 服务端 VAD 能量阈值（int16 最大绝对值）。阈值越大越不敏感，
     * 适合噪音偏大的环境；阈值越小越容易把轻微声音判定为说话。
     */
    private int silenceEnergyThreshold = 200;

    /** 非实时回退模型地址：支持 OpenAI Chat Completions 或火山方舟 Responses。 */
    private String modelUrl = RESPONSES_API;
    private String modelName = PRO_MODEL;
    private String modelToken = "";
    /** 原生双向音视频模型；和旧图文链路地址分开，便于必要时回退。 */
    private String realtimeModelUrl = "";
    private String realtimeModelName = "qwen3.8-omni-flash-realtime";
    private String realtimeVoice = "Tina";
    /** 可单独指定实时模型 Key；留空时兼容使用 model-token。 */
    private String realtimeModelToken = "";
    /** 实时模型旁路转写缺失时，用已有火山 ASR 异步补录日志；绝不阻塞回答。 */
    private boolean realtimeTranscriptFallbackEnabled = true;
    /**
     * 模型请求协议：omni-realtime（音频与图片双向流，模型直接输出文字和音频）/
     * auto（仅对非实时 model-url 推断）/ responses（火山方舟 Responses）/
     * chat（OpenAI 兼容 /chat/completions，例如 Qwen-VL、GLM、vLLM）。
     */
    private String modelProtocol = "auto";
    /**
     * 单轮回答的最大输出 token 数（Responses 走 max_output_tokens，Chat 走 max_tokens）。
     * 设备是语音播报场景，回答越长用户等待和播报时间越长、TTS 成本越高，
     * 因此默认收紧到 200，约合 80~150 个汉字。
     */
    private int modelMaxTokens = 200;
    /**
     * 提交给模型的图片长边上限（像素）。超过则服务端等比缩小后再编码，
     * 用于降低模型首 token 延迟与视觉 token 成本；设为 0 或负数表示不缩放。
     */
    private int modelImageMaxEdge = 1024;

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
     * <p>显式配置 {@code omni-realtime} / {@code chat} / {@code responses} 时优先使用配置值；
     * {@code auto} 则按 model-url 推断：包含 {@code /chat/completions} 走 OpenAI 兼容协议，
     * 其余走 Responses 协议。auto 不会隐式启用实时链路。</p>
     */
    public String getResolvedModelProtocol() {
        String value = modelProtocol == null ? "" : modelProtocol.trim().toLowerCase();
        if ("chat".equals(value) || "responses".equals(value)
                || "omni-realtime".equals(value)) {
            return value;
        }
        String url = getModelUrl();
        return url != null && url.contains("/chat/completions") ? "chat" : "responses";
    }

    /** 是否使用 OpenAI 兼容的 /chat/completions 协议。 */
    public boolean isChatProtocol() {
        return "chat".equals(getResolvedModelProtocol());
    }

    public boolean isRealtimeProtocol() {
        return "omni-realtime".equals(getResolvedModelProtocol());
    }

    public String getResolvedRealtimeModelToken() {
        return hasText(realtimeModelToken) ? realtimeModelToken : modelToken;
    }

    /** 供日志与健康检查展示的模型提供方标识。 */
    public String getModelProvider() {
        String url = isRealtimeProtocol() ? getRealtimeModelUrl() : getModelUrl();
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
        if (!enabled || getDeviceTokenList().isEmpty()) {
            return false;
        }
        if (isRealtimeProtocol()) {
            return hasText(getResolvedRealtimeModelToken())
                    && hasText(realtimeModelUrl) && hasText(realtimeModelName)
                    && hasText(realtimeVoice);
        }
        return hasText(modelToken) && hasText(modelUrl) && hasText(modelName)
                && asrEnabled && hasText(asrAppId) && hasText(asrAccessToken)
                && hasText(ttsAppId) && hasText(ttsAccessToken)
                && hasText(ttsVoiceType);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
