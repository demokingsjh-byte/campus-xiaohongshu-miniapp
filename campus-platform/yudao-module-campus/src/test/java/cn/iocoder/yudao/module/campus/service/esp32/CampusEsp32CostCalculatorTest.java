package cn.iocoder.yudao.module.campus.service.esp32;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CampusEsp32CostCalculatorTest {

    private final CampusEsp32CostCalculator calculator =
            new CampusEsp32CostCalculator(new CampusEsp32PricingProperties());

    @Test
    void shouldPriceArkTokensAsrAudioAndTtsChars() {
        Map<String, Object> row = new HashMap<>();
        row.put("pipelineMode", "responses");
        row.put("usageStatus", "REPORTED");
        row.put("inputTokens", 1_000_000L);
        row.put("inputCachedTokens", 250_000L);
        row.put("outputTokens", 100_000L);
        row.put("asrAudioBytes", 32_000L * 3600);   // 1 小时 16kHz PCM
        row.put("ttsChars", 5_000L);

        calculator.apply(row);

        // 750k × 0.8 + 250k × 0.16 + 100k × 2.7 = 0.6 + 0.04 + 0.27
        assertMoney("0.910000", row.get("modelCost"));
        assertMoney("1.000000", row.get("asrCost"));
        assertMoney("1.500000", row.get("ttsCost"));
        assertMoney("3.410000", row.get("totalCost"));
    }

    @Test
    void shouldNotInventModelCostWhenUsageWasNotReported() {
        Map<String, Object> row = new HashMap<>();
        row.put("pipelineMode", "responses");
        row.put("usageStatus", "UNAVAILABLE");
        row.put("ttsChars", 10_000L);

        calculator.apply(row);

        assertNull(row.get("modelCost"));
        assertNull(row.get("asrCost"));
        assertMoney("3.000000", row.get("ttsCost"));
        assertMoney("3.000000", row.get("totalCost"));
    }

    @Test
    void shouldPriceRealtimeModalitiesWithoutSeparateAsrOrTts() {
        Map<String, Object> row = new HashMap<>();
        row.put("pipelineMode", "omni-realtime");
        row.put("usageStatus", "REPORTED");
        row.put("inputTokens", 300_000L);
        row.put("outputTokens", 200_000L);
        row.put("inputTextTokens", 100_000L);
        row.put("inputAudioTokens", 200_000L);
        row.put("outputTextTokens", 100_000L);
        row.put("outputAudioTokens", 100_000L);
        row.put("asrAudioBytes", 32_000L);
        row.put("ttsChars", 100L);

        calculator.apply(row);

        // 100k × 1.5 + 200k × 6 + 100k × 4.5 + 100k × 12 = 0.15 + 1.2 + 0.45 + 1.2
        assertMoney("3.000000", row.get("modelCost"));
        assertNull(row.get("asrCost"));
        assertNull(row.get("ttsCost"));
        assertMoney("3.000000", row.get("totalCost"));
    }

    @Test
    void shouldLeaveTotalEmptyWhenNothingIsBillable() {
        Map<String, Object> row = new HashMap<>();
        row.put("pipelineMode", "responses");
        row.put("usageStatus", "PENDING");

        calculator.apply(row);

        assertNull(row.get("totalCost"));
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(new BigDecimal(expected), actual);
    }
}
