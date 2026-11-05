package cn.iocoder.yudao.module.campus.framework.esp32;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 主动搭话的“分寸”：设备空闲到点只会来问一次，是否放行由这里决定。
 *
 * <ul>
 *     <li>夜间静默时段不打扰；</li>
 *     <li>两次真正开口之间至少间隔 {@code proactiveMinIntervalMinutes}；</li>
 *     <li>开口后用户一直没回话，间隔逐次翻倍（最多 8 倍），用户一说话就恢复。</li>
 * </ul>
 * 状态按设备号保存在内存里，服务重启后从头计算，最坏只是多问一次。
 */
final class Esp32ProactivePolicy {

    static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final Clock clock;
    private final Map<String, DeviceState> devices = new ConcurrentHashMap<>();

    Esp32ProactivePolicy() {
        this(Clock.system(BEIJING));
    }

    Esp32ProactivePolicy(Clock clock) {
        this.clock = clock;
    }

    /** @return null 表示放行；否则为跳过原因（写进 turn_ignored.reason 便于排查）。 */
    String decide(String deviceId, CampusEsp32AssistantProperties properties) {
        if (!properties.isProactiveEnabled()) {
            return "proactive_disabled";
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(BEIJING));
        if (inQuietHours(now.toLocalTime(), properties)) {
            return "proactive_quiet_hours";
        }
        DeviceState state = devices.get(deviceId);
        if (state == null || state.lastSpokenAtMillis == 0L) {
            return null;
        }
        long base = TimeUnit.MINUTES.toMillis(Math.max(1, properties.getProactiveMinIntervalMinutes()));
        int doublings = Math.min(3, Math.max(0, state.unanswered - 1));
        long required = base << doublings;
        if (clock.millis() - state.lastSpokenAtMillis < required) {
            return "proactive_too_soon";
        }
        return null;
    }

    /** 主动开口后调用：用户回话前再次开口都算“没回应”。 */
    void onSpoken(String deviceId) {
        DeviceState state = devices.computeIfAbsent(deviceId, ignored -> new DeviceState());
        state.lastSpokenAtMillis = clock.millis();
        state.unanswered++;
    }

    /** 用户自己发起的轮次：说明愿意聊，退避清零。 */
    void onUserTurn(String deviceId) {
        DeviceState state = devices.get(deviceId);
        if (state != null) {
            state.unanswered = 0;
        }
    }

    static boolean inQuietHours(LocalTime now, CampusEsp32AssistantProperties properties) {
        LocalTime start = parse(properties.getProactiveQuietStart());
        LocalTime end = parse(properties.getProactiveQuietEnd());
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        // 支持跨午夜（如 22:30 ~ 08:00）。
        return start.isBefore(end)
                ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }

    private static LocalTime parse(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class DeviceState {
        private volatile long lastSpokenAtMillis;
        private volatile int unanswered;
    }
}
