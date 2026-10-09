package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 只读取上游实际返回的 Token 计量，不按字数、音频时长或分项推算。 */
final class Esp32TokenUsage {

    private static final int MAX_USAGE_JSON_BYTES = 8 * 1024;
    private static final String[] TOTAL_FIELDS = {"input_tokens", "output_tokens", "total_tokens"};
    private static final String[] DETAIL_FIELDS = {
            "text_tokens", "audio_tokens", "image_tokens", "video_tokens", "cached_tokens",
            "reasoning_tokens", "accepted_prediction_tokens", "rejected_prediction_tokens"
    };

    private final Map<String, Long> tokens;
    private final String usageJson;

    private Esp32TokenUsage(Map<String, Long> tokens, String usageJson) {
        this.tokens = Collections.unmodifiableMap(tokens);
        this.usageJson = usageJson;
    }

    static Esp32TokenUsage parse(JsonNode usage) {
        Map<String, Long> tokens = new LinkedHashMap<>();
        if (usage == null || !usage.isObject()) {
            return new Esp32TokenUsage(tokens, null);
        }
        putValid(tokens, "inputTokens", usage.path("input_tokens"));
        putValid(tokens, "outputTokens", usage.path("output_tokens"));
        putValid(tokens, "totalTokens", usage.path("total_tokens"));
        JsonNode input = details(usage, "input_tokens_details", "input_token_details");
        JsonNode output = details(usage, "output_tokens_details", "output_token_details");
        putValid(tokens, "inputTextTokens", input.path("text_tokens"));
        putValid(tokens, "inputAudioTokens", input.path("audio_tokens"));
        putValid(tokens, "inputImageTokens", input.path("image_tokens"));
        putValid(tokens, "inputVideoTokens", input.path("video_tokens"));
        putValid(tokens, "inputCachedTokens", input.path("cached_tokens"));
        putValid(tokens, "outputTextTokens", output.path("text_tokens"));
        putValid(tokens, "outputAudioTokens", output.path("audio_tokens"));

        // 白名单仅保留计量数字，不能把原 response、文本、音频或任意凭证字段落库。
        ObjectNode safe = JsonUtils.getObjectMapper().createObjectNode();
        copyNumbers(usage, safe, TOTAL_FIELDS);
        copyDetails(input, safe, "input_tokens_details");
        copyDetails(output, safe, "output_tokens_details");
        String json = safe.size() == 0 ? null : JsonUtils.toJsonString(safe);
        if (json != null && json.getBytes(StandardCharsets.UTF_8).length > MAX_USAGE_JSON_BYTES) {
            json = null;
        }
        return new Esp32TokenUsage(tokens, json);
    }

    Map<String, Long> getTokens() {
        return tokens;
    }

    String getUsageJson() {
        return usageJson;
    }

    private static JsonNode details(JsonNode usage, String plural, String singular) {
        JsonNode value = usage.path(plural);
        return value.isObject() ? value : usage.path(singular);
    }

    private static void putValid(Map<String, Long> tokens, String key, JsonNode value) {
        Long count = nonNegativeLong(value);
        if (count != null) {
            tokens.put(key, count);
        }
    }

    private static void copyNumbers(JsonNode source, ObjectNode target, String[] fields) {
        for (String field : fields) {
            Long count = nonNegativeLong(source.path(field));
            if (count != null) {
                target.put(field, count);
            }
        }
    }

    private static void copyDetails(JsonNode source, ObjectNode target, String name) {
        if (!source.isObject()) {
            return;
        }
        ObjectNode safe = JsonUtils.getObjectMapper().createObjectNode();
        copyNumbers(source, safe, DETAIL_FIELDS);
        JsonNode cacheDetails = source.path("cached_tokens_details");
        if (cacheDetails.isObject()) {
            ObjectNode cache = JsonUtils.getObjectMapper().createObjectNode();
            copyNumbers(cacheDetails, cache, DETAIL_FIELDS);
            if (cache.size() > 0) {
                safe.set("cached_tokens_details", cache);
            }
        }
        if (safe.size() > 0) {
            target.set(name, safe);
        }
    }

    private static Long nonNegativeLong(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            return null;
        }
        long count = value.longValue();
        return count >= 0 ? count : null;
    }
}
