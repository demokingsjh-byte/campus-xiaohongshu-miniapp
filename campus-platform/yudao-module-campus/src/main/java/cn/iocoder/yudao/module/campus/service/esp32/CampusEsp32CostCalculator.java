package cn.iocoder.yudao.module.campus.service.esp32;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 按已落库的实际用量估算费用：模型 Token（上游返回）、ASR 上传音频时长、TTS 回答字数。
 *
 * <p>公式对用量线性，因此同一函数既可用于单轮，也可用于 SUM 聚合后的汇总行。
 * 缺失的用量不按 0 伪造：模型未返回计量时模型费为 null。</p>
 */
final class CampusEsp32CostCalculator {

    static final String REALTIME_PIPELINE = "omni-realtime";
    /** PCM16LE / 16kHz / 单声道：每秒 32000 字节。 */
    private static final double INPUT_AUDIO_BYTES_PER_SECOND = 32000D;
    private static final double MILLION = 1_000_000D;

    private final CampusEsp32PricingProperties pricing;

    CampusEsp32CostCalculator(CampusEsp32PricingProperties pricing) {
        this.pricing = pricing == null ? new CampusEsp32PricingProperties() : pricing;
    }

    /**
     * 写入 modelCost / asrCost / ttsCost / totalCost（元，保留 6 位小数）。
     *
     * @param row 需包含 pipelineMode、usageStatus、各 Token 字段、asrAudioBytes、ttsChars
     */
    void apply(Map<String, Object> row) {
        boolean realtime = REALTIME_PIPELINE.equals(text(row.get("pipelineMode")));
        Double model = "REPORTED".equals(text(row.get("usageStatus"))) || row.containsKey("aggregated")
                ? modelCost(row, realtime) : null;
        Double asr = realtime ? null : asrCost(number(row.get("asrAudioBytes")));
        Double tts = realtime ? null : ttsCost(number(row.get("ttsChars")));
        row.put("modelCost", round(model));
        row.put("asrCost", round(asr));
        row.put("ttsCost", round(tts));
        row.put("totalCost", round(sum(model, asr, tts)));
    }

    private Double modelCost(Map<String, Object> row, boolean realtime) {
        Long input = number(row.get("inputTokens"));
        Long output = number(row.get("outputTokens"));
        if (input == null && output == null) {
            return null;
        }
        if (realtime) {
            Long inText = number(row.get("inputTextTokens"));
            Long inAudio = number(row.get("inputAudioTokens"));
            Long inImage = number(row.get("inputImageTokens"));
            Long inVideo = number(row.get("inputVideoTokens"));
            Long outText = number(row.get("outputTextTokens"));
            Long outAudio = number(row.get("outputAudioTokens"));
            boolean hasModality = inText != null || inAudio != null || inImage != null
                    || inVideo != null || outText != null || outAudio != null;
            if (!hasModality) {
                // 未返回分项时按最低单价给出下限估算，避免把整轮当作 0 元。
                return (value(input) * pricing.getRealtimeInputTextPerMillion()
                        + value(output) * pricing.getRealtimeOutputTextPerMillion()) / MILLION;
            }
            return ((value(inText) + value(inImage) + value(inVideo)) * pricing.getRealtimeInputTextPerMillion()
                    + value(inAudio) * pricing.getRealtimeInputAudioPerMillion()
                    + value(outText) * pricing.getRealtimeOutputTextPerMillion()
                    + value(outAudio) * pricing.getRealtimeOutputAudioPerMillion()) / MILLION;
        }
        long cached = Math.min(value(number(row.get("inputCachedTokens"))), value(input));
        return ((value(input) - cached) * pricing.getModelInputPerMillion()
                + cached * pricing.getModelCachedInputPerMillion()
                + value(output) * pricing.getModelOutputPerMillion()) / MILLION;
    }

    private Double asrCost(Long audioBytes) {
        if (audioBytes == null || audioBytes <= 0) {
            return null;
        }
        return audioBytes / INPUT_AUDIO_BYTES_PER_SECOND / 3600D * pricing.getAsrPerHour();
    }

    private Double ttsCost(Long chars) {
        if (chars == null || chars <= 0) {
            return null;
        }
        return chars / 10000D * pricing.getTtsPerTenThousandChars();
    }

    private static Double sum(Double... values) {
        Double total = null;
        for (Double value : values) {
            if (value != null) {
                total = (total == null ? 0D : total) + value;
            }
        }
        return total;
    }

    private static BigDecimal round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static Long number(Object value) {
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return number < 0 ? null : number;
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
