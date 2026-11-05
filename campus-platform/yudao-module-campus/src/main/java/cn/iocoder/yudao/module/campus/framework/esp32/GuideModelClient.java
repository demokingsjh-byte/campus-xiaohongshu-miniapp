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
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多模态模型客户端。
 *
 * <p>默认使用火山方舟 Responses 流式接口；model-url 也可指向任意 OpenAI 兼容的
 * {@code /chat/completions} 流式接口（例如 PAI-EAS 部署的 GLM、Qwen-VL、vLLM），
 * 此时自动切换为 Chat 报文格式。model-url 仍可配置为 ws:// 地址以兼容原有自建模型协议。</p>
 */
@Component
public class GuideModelClient {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    /** 与实时链路共用任务优先规则；多轮上文由 {@link ConversationMemory} 按设备附带。 */
    private static final String SYSTEM_PROMPT = Esp32AssistantPromptPolicy.INSTRUCTIONS;

    private static final String IMAGE_ONLY_PROMPT =
            "用户语音没能识别出来。请简短让用户重说当前问题，不要根据图片自行猜任务或描述环境。";

    private static final String NO_IMAGE_PROMPT =
            "用户语音没能识别出来，而且这轮没有图片。请简短让用户重说当前问题，不要默认要求看镜头。";

    private static final String NO_IMAGE_HINT =
            "\n本轮没有图片。无需图片的问题直接回答；只有问题确实缺少必要视觉信息时，才请用户补充相关题干或图片。";

    @Resource
    private CampusEsp32AssistantProperties properties;

    /** 按设备保存的最近问答；设备重连后仍能接上话题，空闲超时后自动清空。 */
    private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();

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
            connected.put("provider", properties.getModelProvider());
            connected.put("protocol", properties.getResolvedModelProtocol());
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
            ArkStreamState state = new ArkStreamState(requestId, memoryFor(event),
                    stringValue(event.get("question")), !collectImages(event).isEmpty(),
                    isProactive(event));
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
            return properties.isChatProtocol() ? buildChatRequest(event) : buildResponsesRequest(event);
        }

        /** 火山方舟 Responses：instructions + input[].content[]（input_image / input_text）。 */
        private ObjectNode buildResponsesRequest(Map<String, Object> event) {
            ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
            body.put("model", properties.getModelName());
            body.put("stream", true);
            body.put("max_output_tokens", properties.getModelMaxTokens());
            // doubao-seed 系列默认开启深度思考，思考 token 计入 max_output_tokens；
            // 语音播报预算仅约 200 token，开启时会被思考耗尽而无正文（response.incomplete）。
            body.putObject("thinking").put("type", "disabled");
            body.put("instructions", SYSTEM_PROMPT);

            ArrayNode input = body.putArray("input");
            appendHistory(input, event);
            ObjectNode user = input.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");

            List<String> images = collectImages(event);
            for (String imageUrl : images) {
                ObjectNode imagePart = content.addObject();
                imagePart.put("type", "input_image");
                imagePart.put("image_url", imageUrl);
            }

            ObjectNode prompt = content.addObject();
            prompt.put("type", "input_text");
            prompt.put("text", buildQuestionText(event, !images.isEmpty()));
            return body;
        }

        /**
         * OpenAI 兼容 /chat/completions：system + user.messages[]，
         * 图片使用 {@code image_url:{url}}，data URL 直接内嵌。
         */
        private ObjectNode buildChatRequest(Map<String, Object> event) {
            ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
            body.put("model", properties.getModelName());
            body.put("stream", true);
            body.put("max_tokens", properties.getModelMaxTokens());

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", SYSTEM_PROMPT);
            appendHistory(messages, event);

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");

            List<String> images = collectImages(event);
            for (String imageUrl : images) {
                ObjectNode imagePart = content.addObject();
                imagePart.put("type", "image_url");
                imagePart.putObject("image_url").put("url", imageUrl);
            }

            ObjectNode prompt = content.addObject();
            prompt.put("type", "text");
            prompt.put("text", buildQuestionText(event, !images.isEmpty()));
            return body;
        }

        /** 旧轮次只带文字（user/assistant 字符串消息），两种协议格式相同。 */
        private void appendHistory(ArrayNode target, Map<String, Object> event) {
            ConversationMemory memory = memoryFor(event);
            if (memory == null) {
                return;
            }
            for (String[] turn : memory.snapshot()) {
                ObjectNode user = target.addObject();
                user.put("role", "user");
                user.put("content", turn[0]);
                ObjectNode assistant = target.addObject();
                assistant.put("role", "assistant");
                assistant.put("content", turn[1]);
            }
        }

        private ConversationMemory memoryFor(Map<String, Object> event) {
            int maxTurns = properties.getModelHistoryTurns();
            if (maxTurns <= 0) {
                return null;
            }
            String deviceId = "";
            Object context = event.get("context");
            if (context instanceof Map<?, ?>) {
                deviceId = stringValue(((Map<?, ?>) context).get("device_id"));
            }
            String key = deviceId.isEmpty() ? "session:" + System.identityHashCode(this) : deviceId;
            long idleMillis = TimeUnit.MINUTES.toMillis(Math.max(1, properties.getModelHistoryIdleMinutes()));
            long now = System.currentTimeMillis();
            memories.values().removeIf(memory -> memory.isIdle(now, idleMillis));
            return memories.computeIfAbsent(key, ignored -> new ConversationMemory(maxTurns));
        }

        private List<String> collectImages(Map<String, Object> event) {
            List<String> images = new ArrayList<>();
            Object raw = event.get("images");
            if (raw instanceof Iterable<?>) {
                for (Object image : (Iterable<?>) raw) {
                    String imageUrl = stringValue(image);
                    if (!imageUrl.isEmpty()) {
                        images.add(imageUrl);
                    }
                }
            }
            return images;
        }

        /**
         * 组装本轮提问。没有画面时必须显式告知模型，否则模型会凭空编造画面内容
         * （实测未传图时它会描述出"图书馆、桌子、书本"等并不存在的内容）。
         */
        private String buildQuestionText(Map<String, Object> event, boolean hasImage) {
            return currentTimeHint() + buildQuestionBody(event, hasImage);
        }

        private String buildQuestionBody(Map<String, Object> event, boolean hasImage) {
            if (isProactive(event)) {
                return Esp32AssistantPromptPolicy.PROACTIVE_PROMPT;
            }
            String question = stringValue(event.get("question")).trim();
            if (!hasImage) {
                return question.isEmpty()
                        ? NO_IMAGE_PROMPT
                        : "主人说：" + question + NO_IMAGE_HINT;
            }
            return question.isEmpty()
                    ? IMAGE_ONLY_PROMPT
                    : "主人说：" + question
                            + Esp32AssistantPromptPolicy.QUESTION_HINT;
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
                state.captureUsage(chunk);
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
            // Responses 流式事件把 error / incomplete_details 嵌在 response 对象里。
            JsonNode response = node.path("response");
            if (response.isObject()) {
                String nested = errorMessage(response);
                if (!"模型返回错误".equals(nested)) {
                    return nested;
                }
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
            private volatile JsonNode usage;
            private volatile String responseId;
            private final ConversationMemory memory;
            private final String question;
            private final boolean hadImages;
            private final boolean proactive;

            private ArkStreamState(String requestId, ConversationMemory memory,
                                   String question, boolean hadImages, boolean proactive) {
                this.requestId = requestId;
                this.memory = memory;
                this.question = question;
                this.hadImages = hadImages;
                this.proactive = proactive;
            }

            /** 只有完整播报的问答才写入上文；听不清、报错、被打断和没开口的主动轮次不记。 */
            private void remember() {
                if (memory == null || text.length() == 0) {
                    return;
                }
                if (proactive) {
                    if (!Esp32AssistantPromptPolicy.isProactiveSilent(text.toString())) {
                        memory.add("（主人有一阵子没说话，你看了一眼摄像头后主动搭话）", text.toString());
                    }
                    return;
                }
                if (question.isEmpty()) {
                    return;
                }
                String userText = "主人说：" + question + (hadImages ? "（这轮你通过摄像头看到了画面）" : "");
                memory.add(userText, text.toString());
            }

            /** Responses 终态事件在 response.usage 返回实际计量；缺失时不估算。 */
            private void captureUsage(JsonNode chunk) {
                JsonNode response = chunk.path("response");
                if (!response.isObject()) {
                    return;
                }
                String id = response.path("id").asText("");
                if (!id.isEmpty()) {
                    responseId = id;
                }
                if (response.path("usage").isObject()) {
                    usage = response.path("usage");
                }
            }

            /** 计量随 text_done / error 交给网关落库；网关下发设备前会移除。 */
            private void attachUsage(ObjectNode event) {
                JsonNode current = usage;
                if (current != null) {
                    event.set("usage", current.deepCopy());
                }
                if (responseId != null) {
                    event.put("response_id", responseId);
                }
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
                attachUsage(event);
                remember();
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
                attachUsage(event);
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

    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};

    /** 模型本身不知道现在几点；每轮附上北京时间，问几点、早晚问候才不会乱说。 */
    static String currentTimeHint() {
        ZonedDateTime now = ZonedDateTime.now(Esp32ProactivePolicy.BEIJING);
        return String.format("（现在是北京时间 %d年%d月%d日 星期%s %02d:%02d）\n",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                WEEKDAYS[now.getDayOfWeek().getValue() - 1], now.getHour(), now.getMinute());
    }

    static boolean isProactive(Map<String, Object> event) {
        Object value = event.get("proactive");
        return Boolean.TRUE.equals(value) || "true".equals(stringValue(value));
    }

    /** 单个设备的最近若干轮文字问答，线程安全。 */
    static final class ConversationMemory {
        private final int maxTurns;
        private final Deque<String[]> turns = new ArrayDeque<>();
        private volatile long lastAt = System.currentTimeMillis();

        ConversationMemory(int maxTurns) {
            this.maxTurns = Math.max(1, maxTurns);
        }

        synchronized void add(String user, String assistant) {
            turns.addLast(new String[]{user, assistant});
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
            lastAt = System.currentTimeMillis();
        }

        synchronized List<String[]> snapshot() {
            return new ArrayList<>(turns);
        }

        boolean isIdle(long now, long idleMillis) {
            return now - lastAt > idleMillis;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
