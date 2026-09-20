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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
        WebSocket webSocket = openSession(requestId, incoming, opened);
        try {
            if (!opened.await(8, TimeUnit.SECONDS)) {
                throw new IllegalStateException("连接火山 ASR 超时");
            }
            Map<String, Object> config = buildAsrConfig(requestId, "wav");
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

    /**
     * 打开一条流式 ASR 会话：在采集阶段就把音频实时喂给火山，
     * 设备说完时服务端基本已转写完毕，commit 后只需等待最终结果。
     *
     * <p>任一环节出错都不要抛出到调用方——调用方会在 commit 时
     * 回退到 {@link #transcribe(byte[])} 批处理路径，行为退回旧版本。</p>
     */
    public AsrStream startStream() {
        try {
            return new AsrStream();
        } catch (Exception exception) {
            return null;
        }
    }

    private WebSocket openSession(String requestId, BlockingQueue<Incoming> incoming,
                                  CountDownLatch opened) {
        Request request = new Request.Builder()
                .url(properties.getAsrUrl())
                .header("X-Api-App-Key", properties.getAsrAppId())
                .header("X-Api-Access-Key", properties.getAsrAccessToken())
                .header("X-Api-Resource-Id", properties.getAsrResourceId())
                .header("X-Api-Request-Id", requestId)
                .build();
        return httpClient.newWebSocket(request, new WebSocketListener() {
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
    }

    /**
     * 流式转写会话。音频帧随到随发；最后一个分片会扣留到 {@link #finish()}，
     * 以「真实音频 + last 标记」结束，与批处理协议完全一致。
     */
    public class AsrStream implements AutoCloseable {

        private final String requestId = UUID.randomUUID().toString();
        private final BlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>();
        private final List<byte[]> pendingAudio = new ArrayList<>();
        private final WebSocket webSocket;
        /** 最近收到但尚未下发的分片：finish 时以 last 标记发出。 */
        private byte[] tail;
        private boolean ready;
        private boolean failed;
        private boolean finished;

        private AsrStream() throws Exception {
            CountDownLatch opened = new CountDownLatch(1);
            this.webSocket = openSession(requestId, incoming, opened);
            // 握手与配置下发放在后台线程，不阻塞设备的音频接收线程。
            Thread opener = new Thread(() -> {
                try {
                    if (!opened.await(8, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("连接火山 ASR 超时");
                    }
                    Map<String, Object> config = buildAsrConfig(requestId, "pcm");
                    webSocket.send(ByteString.of(frame(0x01, 0x00, 0x01, 0x01,
                            gzip(JsonUtils.toJsonByte(config)))));
                    // 等待配置确认，之后开始下发音频。
                    receive(incoming, 8);
                    flushReady();
                } catch (Exception exception) {
                    markFailed();
                }
            }, "esp32-asr-open-" + requestId);
            opener.setDaemon(true);
            opener.start();
        }

        /** 会话是否还可继续使用。 */
        public synchronized boolean isUsable() {
            return !failed && !finished;
        }

        /** 设备音频帧随到随发；未就绪时先缓存。 */
        public synchronized void appendAudio(byte[] pcm) {
            if (pcm == null || pcm.length == 0 || failed || finished) {
                return;
            }
            if (!ready) {
                pendingAudio.add(pcm);
                return;
            }
            try {
                sendTail(pcm);
            } catch (IOException exception) {
                // 发送失败即放弃本会话，commit 时回退到批处理转写。
                failed = true;
            }
        }

        /** 发出扣留的最后一个分片并等待最终识别结果。 */
        public String finish() throws Exception {
            byte[] last;
            synchronized (this) {
                if (failed) {
                    throw new IllegalStateException("流式 ASR 会话已失败");
                }
                if (finished) {
                    throw new IllegalStateException("流式 ASR 会话已结束");
                }
                last = tail != null ? tail : new byte[2];
                tail = null;
                finished = true;
            }
            webSocket.send(ByteString.of(frame(0x02, 0x02, 0x00, 0x01, gzip(last))));
            String text = "";
            while (true) {
                AsrResponse response = decode(receive(incoming, 8));
                String candidate = extractText(response.payload);
                if (!candidate.isEmpty()) {
                    text = candidate;
                }
                if (response.finished) {
                    break;
                }
            }
            webSocket.close(1000, "done");
            if (text.trim().isEmpty()) {
                throw new IllegalStateException("火山 ASR 未返回转写文字");
            }
            return text.trim();
        }

        private synchronized void sendTail(byte[] pcm) throws IOException {
            if (tail != null && tail.length > 0) {
                webSocket.send(ByteString.of(
                        frame(0x02, 0x00, 0x00, 0x01, gzip(tail))));
            }
            // 分片对齐到服务端建议大小，避免过碎。
            int offset = 0;
            while (pcm.length - offset > AUDIO_CHUNK_BYTES) {
                byte[] chunk = Arrays.copyOfRange(pcm, offset, offset + AUDIO_CHUNK_BYTES);
                webSocket.send(ByteString.of(
                        frame(0x02, 0x00, 0x00, 0x01, gzip(chunk))));
                offset += AUDIO_CHUNK_BYTES;
            }
            tail = Arrays.copyOfRange(pcm, offset, pcm.length);
        }

        private synchronized void flushReady() {
            ready = true;
            try {
                for (byte[] pcm : pendingAudio) {
                    sendTail(pcm);
                }
            } catch (IOException exception) {
                // 发送失败即放弃本会话，commit 时回退到批处理转写。
                failed = true;
            }
            pendingAudio.clear();
        }

        private void markFailed() {
            synchronized (this) {
                failed = true;
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                finished = true;
            }
            try {
                webSocket.close(1000, "done");
            } catch (Exception ignored) {
                // 连接已经不在了。
            }
        }
    }

    private static Map<String, Object> buildAsrConfig(String requestId, String format) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("user", singleton("uid", requestId));
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", format);
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
