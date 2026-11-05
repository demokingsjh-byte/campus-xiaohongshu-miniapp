package cn.iocoder.yudao.module.campus.service.esp32;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ESP32 链路费用估算单价（人民币）。默认值为 2026-11 官方后付费标价，
 * 不含账户折扣、资源包与免费额度；实际账单以火山引擎 / 阿里云账单为准。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.esp32-assistant.pricing")
public class CampusEsp32PricingProperties {

    /** 火山方舟 doubao-seed-2.1-lite：输入（非音频），元/百万 Token。 */
    private double modelInputPerMillion = 0.8;
    /** 火山方舟缓存命中输入，元/百万 Token。 */
    private double modelCachedInputPerMillion = 0.16;
    /** 火山方舟输出，元/百万 Token。 */
    private double modelOutputPerMillion = 2.7;

    /** 历史 qwen omni realtime 链路：输入文本/图片/视频，元/百万 Token。 */
    private double realtimeInputTextPerMillion = 1.5;
    /** 历史 qwen omni realtime 链路：输入音频，元/百万 Token。 */
    private double realtimeInputAudioPerMillion = 6;
    /** 历史 qwen omni realtime 链路：输出文本，元/百万 Token。 */
    private double realtimeOutputTextPerMillion = 4.5;
    /** 历史 qwen omni realtime 链路：输出音频，元/百万 Token。 */
    private double realtimeOutputAudioPerMillion = 12;

    /** 火山流式语音识别，元/小时（按上传音频时长）。 */
    private double asrPerHour = 1.0;
    /** 火山语音合成 2.0，元/万字符（按送入 TTS 的回答字数）。 */
    private double ttsPerTenThousandChars = 3.0;
}
