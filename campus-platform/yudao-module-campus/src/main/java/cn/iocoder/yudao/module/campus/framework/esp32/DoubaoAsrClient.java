package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 火山大模型流式 ASR 客户端。ESP32 音频在网关内封装成 WAV 后由这里转写。
 */
@Component
public class DoubaoAsrClient {

    private static final int AUDIO_CHUNK_BYTES = Esp32ProtocolUtils.INPUT_SAMPLE_RATE * 2 / 5;

    @Resource
    private CampusEsp32AssistantProperties properties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public String transcribe(byte[] wavAudio) throws Exception {
        if (wavAudio == null || wavAudio.length == 0) {
            throw new IllegalArgumentException("ASR 音频为空");
        }
        String requestId = UUID.randomUUID().toString();
        CountDownLatch opened = new CountDownLatch(1);
        BlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>();
        Request request = new Request.Builder()
                .url(properties.getAsrUrl())
                .header("X-Api-App-Key", properties.getAsrAppId())
                .header("X-Api-Access-Key", properties.getAsrAccessToken())
                .header("X-Api-Resource-Id", properties.getAsrResourceId())
                .header("X-Api-Request-Id", requestId)
                .build();
        WebSocket webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                opened.countDown();
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                incoming.offer(new Incoming(bytes.toByteArray(), null));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                incoming.offer(new Incoming(null, throwable));
                opened.countDown();
            }
        });

        try {
            if (!opened.await(8, TimeUnit.SECONDS)) {
                throw new IllegalStateException("连接火山 ASR 超时");
            }
            Map<String, Object> config = buildAsrConfig(requestId);
            webSocket.send(ByteString.of(frame(0x01, 0x00, 0x01, 0x01,
                    gzip(JsonUtils.toJsonByte(config)))));
            receive(incoming, 8);

            String text = "";
            for (int offset = 0; offset < wavAudio.length; offset += AUDIO_CHUNK_BYTES) {
                int end = Math.min(wavAudio.length, offset + AUDIO_CHUNK_BYTES);
                boolean last = end >= wavAudio.length;
                byte[] chunk = Arrays.copyOfRange(wavAudio, offset, end);
                webSocket.send(ByteString.of(frame(0x02, last ? 0x02 : 0x00,
                        0x00, 0x01, gzip(chunk))));
                AsrResponse response = decode(receive(incoming, 8));
                String candidate = extractText(response.payload);
                if (!candidate.isEmpty()) {
                    text = candidate;
                }
                while (last && !response.finished) {
                    response = decode(receive(incoming, 8));
                    candidate = extractText(response.payload);
                    if (!candidate.isEmpty()) {
                        text = candidate;
                    }
                }
            }
            if (text.trim().isEmpty()) {
                throw new IllegalStateException("火山 ASR 未返回转写文字");
            }
            return text.trim();
        } finally {
            webSocket.close(1000, "done");
        }
    }

    private static Map<String, Object> buildAsrConfig(String requestId) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("user", singleton("uid", requestId));
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", "wav");
        audio.put("codec", "raw");
        audio.put("rate", Esp32ProtocolUtils.INPUT_SAMPLE_RATE);
        audio.put("bits", 16);
        audio.put("channel", 1);
        config.put("audio", audio);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model_name", "bigmodel");
        request.put("enable_itn", true);
        request.put("enable_punc", true);
        request.put("enable_ddc", true);
        request.put("show_utterances", true);
        config.put("request", request);
        return config;
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static byte[] frame(int messageType, int flags, int serialization,
                                int compression, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x11);
        buffer.put((byte) ((messageType << 4) | flags));
        buffer.put((byte) ((serialization << 4) | compression));
        buffer.put((byte) 0x00);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] receive(BlockingQueue<Incoming> queue, int timeoutSeconds)
            throws Exception {
        Incoming incoming = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
        if (incoming == null) {
            throw new IllegalStateException("等待火山 ASR 响应超时");
        }
        if (incoming.error != null) {
            throw new IllegalStateException("火山 ASR 连接失败", incoming.error);
        }
        return incoming.data;
    }

    static AsrResponse decode(byte[] message) throws IOException {
        if (message == null || message.length < 8) {
            throw new IllegalStateException("火山 ASR 返回了不完整的数据包");
        }
        int headerSize = (message[0] & 0x0F) * 4;
        int messageType = (message[1] & 0xF0) >> 4;
        int flags = message[1] & 0x0F;
        int serialization = (message[2] & 0xF0) >> 4;
        int compression = message[2] & 0x0F;
        ByteBuffer buffer = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN);
        buffer.position(headerSize);
        Integer errorCode = null;
        if (messageType == 0x09 && (flags & 0x01) != 0) {
            requireRemaining(buffer, 4);
            buffer.getInt();
        } else if (messageType == 0x0F) {
            requireRemaining(buffer, 4);
            errorCode = buffer.getInt();
        }
        requireRemaining(buffer, 4);
        int payloadSize = buffer.getInt();
        if (payloadSize < 0 || payloadSize > buffer.remaining()) {
            throw new IllegalStateException("火山 ASR 响应长度无效");
        }
        byte[] payloadBytes = new byte[payloadSize];
        buffer.get(payloadBytes);
        if (compression == 0x01 && payloadBytes.length > 0) {
            payloadBytes = gunzip(payloadBytes);
        }
        JsonNode payload;
        if (serialization == 0x01 && payloadBytes.length > 0) {
            payload = JsonUtils.getObjectMapper().readTree(payloadBytes);
        } else {
            payload = JsonUtils.getObjectMapper().createObjectNode()
                    .put("message", new String(payloadBytes, StandardCharsets.UTF_8));
        }
        if (errorCode != null) {
            throw new IllegalStateException("火山 ASR 错误 " + errorCode + ": " + payload);
        }
        return new AsrResponse(payload, (flags & 0x02) != 0);
    }

    static String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual() && !text.asText().trim().isEmpty()) {
                return text.asText().trim();
            }
            for (String key : new String[]{"result", "utterances", "payload_msg"}) {
                String nested = extractText(node.get(key));
                if (!nested.isEmpty()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode item : node) {
                result.append(extractText(item));
            }
            return result.toString().trim();
        }
        return "";
    }

    private static void requireRemaining(ByteBuffer buffer, int count) {
        if (buffer.remaining() < count) {
            throw new IllegalStateException("火山 ASR 响应字段不完整");
        }
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(input);
        }
        return output.toByteArray();
    }

    private static byte[] gunzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    @PreDestroy
    public void destroy() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    @AllArgsConstructor
    private static class Incoming {
        private final byte[] data;
        private final Throwable error;
    }

    @AllArgsConstructor
    static class AsrResponse {
        private final JsonNode payload;
        private final boolean finished;
    }
}
