package cn.iocoder.yudao.module.campus.framework.esp32;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 在 WebSocket 升级前校验 ESP32 独立设备 Token。
 */
@RequiredArgsConstructor
public class Esp32DeviceHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_DEVICE_ID = "esp32DeviceId";
    public static final String ATTR_CLIENT_IP = "esp32ClientIp";

    private final CampusEsp32AssistantProperties properties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!properties.isFullyConfigured()) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        String token = resolveToken(request);
        if (!matchesAnyToken(token, properties.getDeviceTokenList())) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String deviceId = firstNonBlank(
                request.getHeaders().getFirst("X-Device-Id"),
                UriComponentsBuilder.fromUri(request.getURI()).build()
                        .getQueryParams().getFirst("device_id"));
        attributes.put(ATTR_DEVICE_ID, cleanDeviceId(deviceId));
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        attributes.put(ATTR_CLIENT_IP,
                remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress());
        return true;
    }

    private static String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("device_token");
    }

    static boolean matchesAnyToken(String presented, List<String> candidates) {
        if (presented == null || presented.isEmpty()) {
            return false;
        }
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        for (String candidate : candidates) {
            if (MessageDigest.isEqual(actual, candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private static String cleanDeviceId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9_.:-]", "");
        return cleaned.isEmpty() ? "unknown" : cleaned.substring(0, Math.min(64, cleaned.length()));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无后置动作。
    }
}
