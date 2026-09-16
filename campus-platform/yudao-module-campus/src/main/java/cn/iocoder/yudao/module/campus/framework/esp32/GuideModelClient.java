package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.BufferedSource;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多模态导览模型客户端。
 *
 * <p>默认使用火山方舟 Chat Completions 流式接口；model-url 仍可配置为
 * ws:// 地址以兼容原有自建模型协议。</p>
 */
@Component
public class GuideModelClient {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    @Resource
    private CampusEsp32AssistantProperties properties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public ModelSession connect(ModelEventListener eventListener) {
        boolean ark = isHttpUrl(properties.getModelUrl());
        ModelSession session = new ModelSession(eventListener, ark);
        if (ark) {
            session.connected = true;
            ObjectNode connected = JsonUtils.getObjectMapper().createObjectNode();
            connected.put("type", "connected");
            connected.put("model", properties.getModelName());
            connected.put("provider", "volcengine-ark");
            eventListener.onConnected(connected);
            return session;
        }
        Request request = new Request.Builder()
                .url(properties.getModelUrl())
                .header("Authorization", "Bearer " + properties.getModelToken())
                .build();
        session.webSocket = httpClient.newWebSocket(request, session.listener);
        return session;
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public final class ModelSession implements AutoCloseable {
        private final ModelEventListener eventListener;
        private final boolean ark;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile WebSocket webSocket;
        private volatile boolean connected;
        private volatile Call activeCall;
        private volatile ArkStreamState activeState;

        private final WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                JsonNode event;
                try {
                    event = JsonUtils.getObjectMapper().readTree(text);
                } catch (Exception exception) {
                    eventListener.onFailure(new IllegalStateException("模型返回了无效 JSON", exception));
                    return;
                }
                if (!connected) {
                    if (!"connected".equals(event.path("type").asText())) {
                        eventListener.onFailure(new IllegalStateException(
                                event.path("msg").asText("模型拒绝连接")));
                        close();
                        return;
                    }
                    connected = true;
                    eventListener.onConnected(event);
                    return;
                }
                eventListener.onEvent(event);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                if (terminal.compareAndSet(false, true)) {
                    eventListener.onFailure(throwable);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (terminal.compareAndSet(false, true)) {
                    eventListener.onFailure(new IllegalStateException(
                            "模型连接已关闭：" + code + " " + reason));
                }
            }
        };

        private ModelSession(ModelEventListener eventListener, boolean ark) {
            this.eventListener = eventListener;
            this.ark = ark;
        }

        public boolean send(Map<String, Object> event) {
            if (ark) {
                return sendToArk(event);
            }
            WebSocket socket = webSocket;
            return connected && socket != null && socket.send(JsonUtils.toJsonString(event));
        }

        private boolean sendToArk(Map<String, Object> event) {
            if (!connected || terminal.get()) {
                return false;
            }
            String type = stringValue(event.get("type"));
            if ("ping".equals(type)) {
                ObjectNode pong = JsonUtils.getObjectMapper().createObjectNode();
                pong.put("type", "pong");
                if (event.get("timestamp") != null) {
                    pong.put("timestamp", longValue(event.get("timestamp")));
                }
                eventListener.onEvent(pong);
                return true;
            }
            if ("interrupt".equals(type)) {
                interruptArk(stringValue(event.get("request_id")));
                return true;
            }
            if (!"chat".equals(type)) {
                return true;
            }
            Call previous = activeCall;
            if (previous != null && !previous.isCanceled()) {
                return false;
            }

            String requestId = stringValue(event.get("request_id"));
            ObjectNode requestBody;
            try {
                requestBody = buildArkRequest(event);
            } catch (Exception exception) {
                emitArkError(requestId, "ARK_REQUEST_BUILD_FAILED", exception.getMessage());
                return true;
            }
            ArkStreamState state = new ArkStreamState(requestId);
            Request request = new Request.Builder()
                    .url(properties.getModelUrl())
                    .header("Authorization", "Bearer " + properties.getModelToken())
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(JSON_MEDIA_TYPE, requestBody.toString()))
                    .build();
            final Call call = httpClient.newCall(request);
            activeCall = call;
            activeState = state;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {
                    try {
                        if (!call.isCanceled() && !terminal.get()) {
                            state.emitError("ARK_REQUEST_FAILED", exception.getMessage());
                        }
                    } finally {
                        clearActive(call, state);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response safeResponse = response) {
                        if (call.isCanceled() || terminal.get()) {
                            return;
                        }
                        if (!safeResponse.isSuccessful()) {
                            String detail = safeResponse.body() == null
                                    ? "HTTP " + safeResponse.code()
                                    : safeResponse.body().string();
                            state.emitError("ARK_HTTP_" + safeResponse.code(),
                                    abbreviate(detail, 500));
                            return;
                        }
                        ResponseBody body = safeResponse.body();
                        if (body == null) {
                            state.emitError("ARK_EMPTY_RESPONSE", "火山方舟返回了空响应");
                            return;
                        }
                        consumeSse(body.source(), state);
                        state.emitDone();
                    } catch (Exception exception) {
                        if (!call.isCanceled() && !terminal.get()) {
                            state.emitError("ARK_RESPONSE_PARSE_FAILED", exception.getMessage());
                        }
                    } finally {
                        clearActive(call, state);
                    }
                }
            });
            return true;
        }

        private ObjectNode buildArkRequest(Map<String, Object> event) {
            ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
            body.put("model", properties.getModelName());
            body.put("stream", true);
            body.put("max_tokens", 512);

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", "你是校园智能导览助手。请结合用户问题和图片内容回答，使用简洁、准确的中文。"
                    + "如果图片无法确认，不要编造具体信息。");

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");

            String audio = stringValue(event.get("audio"));
            if (!audio.isEmpty()) {
                ObjectNode audioPart = content.addObject();
                audioPart.put("type", "input_audio");
                ObjectNode inputAudio = audioPart.putObject("input_audio");
                inputAudio.put("data", stripAudioDataUri(audio));
                inputAudio.put("format", audioFormat(event, audio));
            }

            Object images = event.get("images");
            if (images instanceof Iterable<?>) {
                for (Object image : (Iterable<?>) images) {
                    String imageUrl = stringValue(image);
                    if (imageUrl.isEmpty()) {
                        continue;
                    }
                    ObjectNode imagePart = content.addObject();
                    imagePart.put("type", "image_url");
                    imagePart.putObject("image_url").put("url", imageUrl);
                }
            }

            ObjectNode prompt = content.addObject();
            prompt.put("type", "text");
            String question = stringValue(event.get("question")).trim();
            prompt.put("text", question.isEmpty()
                    ? "请理解用户语音并直接回答；如有图片，优先说明图片中能够确认的内容。"
                    : "用户问题：" + question + "\n请直接回答，并优先说明图片中能够确认的内容。");
            return body;
        }

        private void consumeSse(BufferedSource source, ArkStreamState state) throws IOException {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                } else if (line.trim().isEmpty()) {
                    flushSseData(data, state);
                } else if (line.trim().startsWith("{")) {
                    // 兼容网关或测试代理未返回标准 SSE 分隔行的情况。
                    flushSseData(data, state);
                    processSseData(line.trim(), state);
                }
            }
            flushSseData(data, state);
        }

        private void flushSseData(StringBuilder data, ArkStreamState state) {
            if (data.length() == 0) {
                return;
            }
            processSseData(data.toString(), state);
            data.setLength(0);
        }

        private void processSseData(String value, ArkStreamState state) {
            if ("[DONE]".equals(value)) {
                state.emitDone();
                return;
            }
            try {
                JsonNode chunk = JsonUtils.getObjectMapper().readTree(value);
                if (chunk.hasNonNull("error")) {
                    state.emitError("ARK_MODEL_ERROR", abbreviate(errorMessage(chunk.path("error")), 500));
                    return;
                }
                String eventType = chunk.path("type").asText();
                if ("response.output_text.delta".equals(eventType)) {
                    state.emitDelta(chunk.path("delta").asText(""));
                    return;
                }
                if ("response.output_text.done".equals(eventType)) {
                    String text = chunk.path("text").asText("");
                    if (!text.isEmpty() && state.isEmpty()) {
                        state.emitDelta(text);
                    }
                    return;
                }
                if ("response.completed".equals(eventType)) {
                    state.emitDone();
                    return;
                }
                if ("response.failed".equals(eventType) || "response.incomplete".equals(eventType)
                        || "error".equals(eventType)) {
                    state.emitError("ARK_MODEL_ERROR", abbreviate(errorMessage(chunk), 500));
                    return;
                }

                JsonNode choices = chunk.path("choices");
                if (!choices.isArray() || choices.size() == 0) {
                    return;
                }
                JsonNode delta = choices.get(0).path("delta");
                String text = extractText(delta.path("content"));
                if (!text.isEmpty()) {
                    state.emitDelta(text);
                }
            } catch (Exception exception) {
                state.emitError("ARK_INVALID_STREAM_EVENT", "火山方舟返回了无效流式数据");
            }
        }

        private String errorMessage(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "模型返回错误";
            }
            String message = node.path("message").asText("");
            if (!message.isEmpty()) {
                return message;
            }
            JsonNode error = node.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                message = error.path("message").asText("");
                if (!message.isEmpty()) {
                    return message;
                }
            }
            JsonNode incomplete = node.path("incomplete_details");
            if (!incomplete.isMissingNode() && !incomplete.isNull()) {
                String reason = incomplete.path("reason").asText("");
                if (!reason.isEmpty()) {
                    return "模型返回不完整：" + reason;
                }
            }
            return node.isTextual() ? node.asText() : "模型返回错误";
        }

        private String extractText(JsonNode content) {
            if (content == null || content.isNull()) {
                return "";
            }
            if (content.isTextual()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder result = new StringBuilder();
                for (JsonNode item : content) {
                    if (item.has("text")) {
                        result.append(item.path("text").asText());
                    }
                }
                return result.toString();
            }
            return "";
        }

        private void interruptArk(String requestId) {
            ArkStreamState state = activeState;
            if (state == null || (!requestId.isEmpty() && !requestId.equals(state.requestId))) {
                return;
            }
            Call call = activeCall;
            if (call != null) {
                call.cancel();
            }
            state.emitInterrupted();
            clearActive(call, state);
        }

        private void clearActive(Call call, ArkStreamState state) {
            if (activeCall == call) {
                activeCall = null;
            }
            if (activeState == state) {
                activeState = null;
            }
        }

        private void emitArkError(String requestId, String code, String message) {
            ObjectNode error = JsonUtils.getObjectMapper().createObjectNode();
            error.put("type", "error");
            if (requestId != null && !requestId.isEmpty()) {
                error.put("request_id", requestId);
            }
            error.put("code", code);
            error.put("message", message == null || message.isEmpty() ? "火山方舟请求失败" : message);
            eventListener.onEvent(error);
        }

        @Override
        public void close() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            Call call = activeCall;
            if (call != null) {
                call.cancel();
            }
            ArkStreamState state = activeState;
            if (state != null) {
                state.cancelSilently();
            }
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.close(1000, "device closed");
            }
        }

        private final class ArkStreamState {
            private final String requestId;
            private final long startedAt = System.nanoTime();
            private final StringBuilder text = new StringBuilder();
            private final AtomicBoolean completed = new AtomicBoolean();
            private volatile long firstTokenAt;

            private ArkStreamState(String requestId) {
                this.requestId = requestId;
            }

            private void emitDelta(String delta) {
                if (completed.get() || delta == null || delta.isEmpty()) {
                    return;
                }
                if (firstTokenAt == 0L) {
                    firstTokenAt = System.nanoTime();
                }
                text.append(delta);
                ObjectNode event = JsonUtils.getObjectMapper().createObjectNode();
                event.put("type", "text_delta");
                event.put("request_id", requestId);
                event.put("text", delta);
                eventListener.onEvent(event);
            }

            private void emitDone() {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                ObjectNode event = JsonUtils.getObjectMapper().createObjectNode();
                event.put("type", "text_done");
                event.put("request_id", requestId);
                event.put("text", text.toString());
                ObjectNode stats = event.putObject("stats");
                stats.put("total_ms", elapsedMillis(startedAt));
                if (firstTokenAt == 0L) {
                    stats.putNull("first_token_ms");
                } else {
                    stats.put("first_token_ms", elapsedMillis(startedAt, firstTokenAt));
                }
                eventListener.onEvent(event);
            }

            private boolean isEmpty() {
                return text.length() == 0;
            }

            private void emitError(String code, String message) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                ObjectNode event = JsonUtils.getObjectMapper().createObjectNode();
                event.put("type", "error");
                event.put("request_id", requestId);
                event.put("code", code);
                event.put("message", message == null || message.isEmpty()
                        ? "火山方舟请求失败" : message);
                eventListener.onEvent(event);
            }

            private void emitInterrupted() {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                ObjectNode event = JsonUtils.getObjectMapper().createObjectNode();
                event.put("type", "interrupted");
                event.put("request_id", requestId);
                eventListener.onEvent(event);
            }

            private void cancelSilently() {
                completed.set(true);
            }
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String stripAudioDataUri(String audio) {
        if (!audio.startsWith("data:")) {
            return audio;
        }
        int comma = audio.indexOf(',');
        return comma < 0 ? audio : audio.substring(comma + 1);
    }

    private static String audioFormat(Map<String, Object> event, String audio) {
        String configured = stringValue(event.get("audio_format"));
        if (!configured.isEmpty()) {
            return configured.toLowerCase(Locale.ROOT);
        }
        String lower = audio.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:audio/mpeg") || lower.startsWith("data:audio/mp3")) {
            return "mp3";
        }
        if (lower.startsWith("data:audio/aac")) {
            return "aac";
        }
        if (lower.startsWith("data:audio/x-m4a") || lower.startsWith("data:audio/mp4")) {
            return "m4a";
        }
        return "wav";
    }

    private static long longValue(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return elapsedMillis(startedAt, System.nanoTime());
    }

    private static long elapsedMillis(long startedAt, long endedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(endedAt - startedAt));
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public interface ModelEventListener {
        void onConnected(JsonNode event);

        void onEvent(JsonNode event);

        void onFailure(Throwable throwable);
    }

    @PreDestroy
    public void destroy() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
