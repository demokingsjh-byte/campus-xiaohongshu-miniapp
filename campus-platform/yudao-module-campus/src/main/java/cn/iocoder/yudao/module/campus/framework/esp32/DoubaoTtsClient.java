package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 火山双向流式 TTS 客户端，输出 ESP32 可直接写入 I2S 的 24kHz PCM。
 */
@Component
public class DoubaoTtsClient {

    private static final int FULL_CLIENT = 1;
    private static final int AUDIO_ONLY_SERVER = 11;
    private static final int ERROR = 15;
    private static final int START_CONNECTION = 1;
    private static final int FINISH_CONNECTION = 2;
    private static final int CONNECTION_STARTED = 50;
    private static final int CONNECTION_FAILED = 51;
    private static final int START_SESSION = 100;
    private static final int FINISH_SESSION = 102;
    private static final int SESSION_STARTED = 150;
    private static final int SESSION_FINISHED = 152;
    private static final int SESSION_FAILED = 153;
    private static final int TASK_REQUEST = 200;
    private static final List<Integer> SESSION_EVENTS = Arrays.asList(150, 152, 153, 350, 351, 352);
    private static final List<Integer> CONNECTION_EVENTS = Arrays.asList(50, 51, 52);

    @Resource
    private CampusEsp32AssistantProperties properties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public TtsStream start(Consumer<byte[]> audioConsumer, Runnable completed,
                           Consumer<Throwable> failed) {
        TtsStream stream = new TtsStream(audioConsumer, completed, failed);
        Request request = new Request.Builder()
                .url(properties.getTtsUrl())
                .header("X-Api-App-Key", properties.getTtsAppId())
                .header("X-Api-Access-Key", properties.getTtsAccessToken())
                .header("X-Api-Resource-Id", properties.getTtsResourceId())
                .header("X-Api-Connect-Id", UUID.randomUUID().toString().replace("-", ""))
                .build();
        stream.webSocket = httpClient.newWebSocket(request, stream.listener);
        return stream;
    }

    public class TtsStream implements AutoCloseable {
        private final String sessionId = UUID.randomUUID().toString().replace("-", "");
        private final Consumer<byte[]> audioConsumer;
        private final Runnable completed;
        private final Consumer<Throwable> failed;
        private final List<String> pendingTexts = new ArrayList<>();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile WebSocket webSocket;
        private boolean ready;
        private boolean finishRequested;
        private boolean hasText;

        private final WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(ByteString.of(packMessage(FULL_CLIENT, START_CONNECTION,
                        new LinkedHashMap<>(), null)));
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    handleEvent(unpackMessage(bytes.toByteArray()));
                } catch (Throwable throwable) {
                    fail(throwable);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                fail(throwable);
            }
        };

        private TtsStream(Consumer<byte[]> audioConsumer, Runnable completed,
                          Consumer<Throwable> failed) {
            this.audioConsumer = audioConsumer;
            this.completed = completed;
            this.failed = failed;
        }

        public synchronized void appendText(String text) {
            if (terminal.get() || text == null || text.trim().isEmpty()) {
                return;
            }
            pendingTexts.add(text.trim());
            flushPending();
        }

        public synchronized void finish() {
            if (terminal.get()) {
                return;
            }
            finishRequested = true;
            flushPending();
        }

        private synchronized void handleEvent(TtsEvent event) {
            if (event.messageType == ERROR
                    || event.eventType == CONNECTION_FAILED
                    || event.eventType == SESSION_FAILED) {
                fail(new IllegalStateException(event.errorText()));
                return;
            }
            if (event.eventType == CONNECTION_STARTED) {
                webSocket.send(ByteString.of(packMessage(FULL_CLIENT, START_SESSION,
                        buildRequest(START_SESSION, ""), sessionId)));
                return;
            }
            if (event.eventType == SESSION_STARTED && sessionId.equals(event.sessionId)) {
                ready = true;
                flushPending();
                return;
            }
            if (event.messageType == AUDIO_ONLY_SERVER
                    && sessionId.equals(event.sessionId)
                    && event.payload.length > 0) {
                audioConsumer.accept(event.payload);
                return;
            }
            if (event.eventType == SESSION_FINISHED && sessionId.equals(event.sessionId)) {
                complete();
            }
        }

        private synchronized void flushPending() {
            if (!ready || terminal.get()) {
                return;
            }
            for (String text : pendingTexts) {
                hasText = true;
                webSocket.send(ByteString.of(packMessage(FULL_CLIENT, TASK_REQUEST,
                        buildRequest(TASK_REQUEST, text), sessionId)));
            }
            pendingTexts.clear();
            if (finishRequested) {
                if (!hasText) {
                    fail(new IllegalStateException("TTS 播报文字为空"));
                    return;
                }
                finishRequested = false;
                webSocket.send(ByteString.of(packMessage(FULL_CLIENT, FINISH_SESSION,
                        new LinkedHashMap<>(), sessionId)));
            }
        }

        private Map<String, Object> buildRequest(int event, String text) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("user", singleton("uid", UUID.randomUUID().toString().replace("-", "")));
            request.put("namespace", "BidirectionalTTS");
            request.put("event", event);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("speaker", properties.getTtsVoiceType());
            Map<String, Object> audioParams = new LinkedHashMap<>();
            audioParams.put("format", "pcm");
            audioParams.put("sample_rate", Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE);
            audioParams.put("speech_rate", 0);
            params.put("audio_params", audioParams);
            Map<String, Object> cacheConfig = new LinkedHashMap<>();
            cacheConfig.put("text_type", 1);
            cacheConfig.put("use_cache", true);
            Map<String, Object> additions = new LinkedHashMap<>();
            additions.put("disable_markdown_filter", false);
            additions.put("cache_config", cacheConfig);
            params.put("additions", JsonUtils.toJsonString(additions));
            params.put("text", text);
            request.put("req_params", params);
            return request;
        }

        private void complete() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.send(ByteString.of(packMessage(FULL_CLIENT, FINISH_CONNECTION,
                        new LinkedHashMap<>(), null)));
                socket.close(1000, "done");
            }
            completed.run();
        }

        private void fail(Throwable throwable) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.cancel();
            }
            failed.accept(throwable);
        }

        @Override
        public void close() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.cancel();
            }
        }
    }

    static byte[] packMessage(int messageType, int eventType, Map<String, Object> payload,
                              String sessionId) {
        byte[] session = sessionId == null ? new byte[0]
                : sessionId.getBytes(StandardCharsets.UTF_8);
        byte[] json = JsonUtils.toJsonByte(payload);
        int size = 4 + 4 + json.length + (sessionId == null ? 0 : 4 + session.length);
        ByteBuffer buffer = ByteBuffer.allocate(4 + size).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x11);
        buffer.put((byte) ((messageType << 4) | 0x04));
        buffer.put((byte) 0x10);
        buffer.put((byte) 0x00);
        buffer.putInt(eventType);
        if (sessionId != null) {
            buffer.putInt(session.length);
            buffer.put(session);
        }
        buffer.putInt(json.length);
        buffer.put(json);
        return buffer.array();
    }

    static TtsEvent unpackMessage(byte[] data) {
        if (data == null || data.length < 8) {
            throw new IllegalStateException("火山 TTS 返回了无效数据");
        }
        int headerSize = (data[0] & 0x0F) * 4;
        int messageType = (data[1] & 0xF0) >> 4;
        int flags = data[1] & 0x0F;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buffer.position(headerSize);
        Integer eventType = null;
        if ((flags & 0x04) != 0) {
            requireRemaining(buffer, 4);
            eventType = buffer.getInt();
        }
        String sessionId = null;
        if (eventType != null
                && (SESSION_EVENTS.contains(eventType) || CONNECTION_EVENTS.contains(eventType))) {
            requireRemaining(buffer, 4);
            int idLength = buffer.getInt();
            if (idLength < 0 || idLength > buffer.remaining()) {
                throw new IllegalStateException("火山 TTS 会话标识长度无效");
            }
            byte[] id = new byte[idLength];
            buffer.get(id);
            if (SESSION_EVENTS.contains(eventType)) {
                sessionId = new String(id, StandardCharsets.UTF_8);
            }
        }
        byte[] payload = new byte[0];
        if (buffer.remaining() >= 4) {
            int payloadLength = buffer.getInt();
            if (payloadLength < 0 || payloadLength > buffer.remaining()) {
                throw new IllegalStateException("火山 TTS 响应长度无效");
            }
            payload = new byte[payloadLength];
            buffer.get(payload);
        }
        return new TtsEvent(messageType, eventType, sessionId, payload);
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static void requireRemaining(ByteBuffer buffer, int count) {
        if (buffer.remaining() < count) {
            throw new IllegalStateException("火山 TTS 响应字段不完整");
        }
    }

    @PreDestroy
    public void destroy() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    @AllArgsConstructor
    static class TtsEvent {
        private final int messageType;
        private final Integer eventType;
        private final String sessionId;
        private final byte[] payload;

        private String errorText() {
            String value = new String(payload, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? "火山 TTS 请求失败" : value;
        }
    }
}
