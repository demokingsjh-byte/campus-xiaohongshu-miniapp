package cn.iocoder.yudao.module.campus.framework.esp32;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Esp32ProactivePolicyTest {

    private final CampusEsp32AssistantProperties properties = new CampusEsp32AssistantProperties();

    @Test
    void shouldStayQuietAtNightAcrossMidnight() {
        assertTrue(Esp32ProactivePolicy.inQuietHours(LocalTime.of(23, 0), properties));
        assertTrue(Esp32ProactivePolicy.inQuietHours(LocalTime.of(7, 59), properties));
        assertFalse(Esp32ProactivePolicy.inQuietHours(LocalTime.of(8, 0), properties));
        assertFalse(Esp32ProactivePolicy.inQuietHours(LocalTime.of(15, 0), properties));
        assertEquals("proactive_quiet_hours", policyAt("2026-11-05T23:30").decide("d1", properties));
    }

    @Test
    void shouldBackOffWhenCheckInsAreIgnoredAndResetOnReply() {
        MutableClock clock = new MutableClock("2026-11-05T09:00");
        Esp32ProactivePolicy policy = new Esp32ProactivePolicy(clock);

        assertNull(policy.decide("d1", properties));
        policy.onSpoken("d1");
        clock.advance(Duration.ofMinutes(59));
        assertEquals("proactive_too_soon", policy.decide("d1", properties));
        clock.advance(Duration.ofMinutes(1));
        assertNull(policy.decide("d1", properties));

        // 第二次开口仍没人理：下一次至少等 2 小时。
        policy.onSpoken("d1");
        clock.advance(Duration.ofMinutes(90));
        assertEquals("proactive_too_soon", policy.decide("d1", properties));
        clock.advance(Duration.ofMinutes(30));
        assertNull(policy.decide("d1", properties));

        // 用户回话后恢复为 1 小时。
        policy.onSpoken("d1");
        policy.onUserTurn("d1");
        clock.advance(Duration.ofMinutes(60));
        assertNull(policy.decide("d1", properties));
        assertNull(policy.decide("other-device", properties));
    }

    @Test
    void shouldRespectDisableSwitch() {
        properties.setProactiveEnabled(false);
        assertEquals("proactive_disabled", policyAt("2026-11-05T10:00").decide("d1", properties));
    }

    @Test
    void shouldTreatSilentMarkerAndBlankAsNoSpeech() {
        assertTrue(Esp32AssistantPromptPolicy.isProactiveSilent("[SILENT]"));
        assertTrue(Esp32AssistantPromptPolicy.isProactiveSilent(" silent "));
        assertTrue(Esp32AssistantPromptPolicy.isProactiveSilent(""));
        assertFalse(Esp32AssistantPromptPolicy.isProactiveSilent("还在写作业呀？"));
    }

    private static Esp32ProactivePolicy policyAt(String beijingTime) {
        return new Esp32ProactivePolicy(new MutableClock(beijingTime));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(String beijingTime) {
            this.now = ZonedDateTime.of(java.time.LocalDateTime.parse(beijingTime),
                    Esp32ProactivePolicy.BEIJING).toInstant();
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return Esp32ProactivePolicy.BEIJING;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
