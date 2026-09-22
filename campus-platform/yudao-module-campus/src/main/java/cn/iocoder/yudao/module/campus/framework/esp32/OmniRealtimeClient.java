package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Qwen-Omni-Realtime 双向 WebSocket。输入是 16 kHz 单声道 PCM16LE 与 JPEG，
 * 输出是 24 kHz 单声道 PCM16LE。用户转写只是旁路日志，不参与模型推理。
 *
 * <p>文档：https://help.aliyun.com/en/model-studio/realtime 与
 * https://help.aliyun.com/en/model-studio/client-events 。这里使用手动分轮，
 * 由设备的 turn_commit 决定何时让模型回答。</p>
 */
@Slf4j
@Component
public class OmniRealtimeClient {

    private static final int MAX_PENDING_IMAGES = 5;
    // 官方限制：Base64 后单张图片不超过 256 KiB。
    private static final int MAX_JPEG_BYTES = 180 * 1024;
    private static final String INSTRUCTIONS =
            "你是陪伴型视觉助手小智，称呼用户为主人。语气亲切自然、口语化。"
                    + "只有用户明确让你看、识别或问题必须依赖画面时才描述图片；"
                    + "如果同一轮有多张图片，优先以最后一张为当前画面，前面的仅作变化参考。"
                    + "日常闲聊尽量一句话，视觉问答最多两句话、50 字以内；"
                    + "直接回答重点，不重复问题，不编造看不清的细节。";

    @Resource
    private CampusEsp32AssistantProperties properties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public interface Listener {
        void onReady(String model);

        void onUserTranscript(String requestId, String transcript, long elapsedMs);

        void onAssistantTextDelta(String requestId, String delta);

        void onAssistantTextDone(String requestId, String text, long modelElapsedMs, long firstTokenMs);

        /** PCM16LE, 24 kHz, mono; matches the device's output sample rate. */
        void onAssistantAudio(String requestId, byte[] pcm);

        void onResponseDone(String requestId);

        void onFailure(Throwable error);
    }

    public Session connect(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Omni listener 不能为空");
        }
        String rawUrl = properties.getRealtimeModelUrl();
        if (rawUrl == null || (!rawUrl.startsWith("ws://") && !rawUrl.startsWith("wss://"))) {
            throw new IllegalArgumentException("实时模型地址必须是 ws:// 或 wss://");
        }
        // OkHttp 的 HttpUrl 只接受 http(s)，WebSocket 握手仍通过 newWebSocket 完成。
        String httpUrl = rawUrl.startsWith("wss://")
                ? "https://" + rawUrl.substring(6) : "http://" + rawUrl.substring(5);
        HttpUrl parsed = HttpUrl.parse(httpUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("实时模型地址无效");
        }
        String model = properties.getRealtimeModelName();
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("实时模型名称不能为空");
        }
        HttpUrl url = parsed.newBuilder().setQueryParameter("model", model).build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + properties.getResolvedRealtimeModelToken())
                .build();
        Session session = new Session(listener, model);
        session.webSocket = httpClient.newWebSocket(request, session.socketListener);
        return session;
    }

    @PreDestroy
    public void destroy() {
        httpClient.connectionPool().evictAll();
        httpClient.dispatcher().executorService().shutdown();
    }

    public final class Session implements AutoCloseable {
        private final Listener listener;
        private final String model;
        private final AtomicBoolean failureNotified = new AtomicBoolean();
        private final Map<String, Turn> turnsByItemId = new HashMap<>();
        private final ArrayDeque<String> itemOrder = new ArrayDeque<>();
        /** 上游不保证转写事件与 committed 回执先后顺序，先按 item_id 暂存。 */
        private final Map<String, JsonNode> earlyTranscriptEvents = new HashMap<>();
        private final ArrayDeque<String> earlyTranscriptOrder = new ArrayDeque<>();
        private volatile WebSocket webSocket;
        private boolean updateSent;
        private boolean ready;
        private boolean closed;
        private Turn active;

        private final WebSocketListener socketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                // 先等服务端 session.created，再发送 session.update。
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                try {
                    handle(JsonUtils.getObjectMapper().readTree(text));
                } catch (Exception exception) {
                    notifyFailure(new IllegalStateException("实时模型返回了无效事件", exception));
                }
            }

            @Override
            public void onFailure(WebSocket socket, Throwable error, Response response) {
                // 不把 Authorization 或上游响应体写入错误信息。
                String status = response == null ? "" : " (HTTP " + response.code() + ")";
                notifyFailure(new IllegalStateException("实时模型 WebSocket 失败" + status, error));
            }

            @Override
            public void onClosed(WebSocket socket, int code, String reason) {
                synchronized (Session.this) {
                    if (closed) {
                        return;
                    }
                }
                notifyFailure(new IllegalStateException("实时模型 WebSocket 意外关闭，状态码 " + code));
            }
        };

        private Session(Listener listener, String model) {
            this.listener = listener;
            this.model = model;
        }

        /** 仅在 session.updated 确认后接收新轮；同一连接保留跨轮上下文。 */
        public synchronized boolean beginTurn(String requestId) {
            if (!ready || closed || requestId == null || requestId.trim().isEmpty() || active != null) {
                return false;
            }
            active = new Turn(requestId);
            return true;
        }

        public synchronized boolean appendAudio(byte[] pcm) {
            Turn turn = active;
            if (!canAppend(turn) || pcm == null || pcm.length == 0) {
                return false;
            }
            ObjectNode event = event("input_audio_buffer.append");
            event.put("audio", Base64.getEncoder().encodeToString(pcm));
            if (!send(event)) {
                return false;
            }
            turn.audioSent = true;
            return true;
        }

        public synchronized boolean appendImage(byte[] jpeg) {
            Turn turn = active;
            if (!canAppend(turn) || jpeg == null || jpeg.length == 0
                    || jpeg.length > MAX_JPEG_BYTES || turn.imageCount >= MAX_PENDING_IMAGES) {
                return false;
            }
            turn.imageCount++;
            // 图片先仅保存在网关内存，commit 时再与本轮音频一起发送。这样未提交轮次
            // 被取消时，上游不会留下无法通过 input_audio_buffer.clear 清除的旧图像。
            turn.pendingImages.add(jpeg);
            return true;
        }

        public synchronized boolean commit() {
            Turn turn = active;
            if (!canAppend(turn) || !turn.audioSent) {
                return false;
            }
            // 官方要求至少先发送一块音频；音频已在采集期流式发送，这里紧接着发送
            // 本轮全部图片后统一 commit，保证模型只看到本轮完整的三帧集合。
            for (byte[] jpeg : turn.pendingImages) {
                if (!sendImage(jpeg)) {
                    return false;
                }
            }
            turn.pendingImages.clear();
            turn.committed = true;
            turn.committedAtNanos = System.nanoTime();
            if (!send(event("input_audio_buffer.commit"))) {
                turn.committed = false;
                return false;
            }
            return true;
        }

        /** 丢弃未提交输入；已生成回答时取消回答。保留整条上下文连接。 */
        public synchronized void interrupt() {
            Turn turn = active;
            if (turn == null || closed) {
                return;
            }
            turn.cancelled = true;
            if (turn.responseInProgress) {
                send(event("response.cancel"));
            } else if (!turn.committed) {
                send(event("input_audio_buffer.clear"));
                active = null;
            }
            // 已提交的轮次要等 committed/response.done 回执，防止旧回执被归到新轮。
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            ready = false;
            active = null;
            if (webSocket != null) {
                webSocket.close(1000, "done");
            }
        }

        private boolean canAppend(Turn turn) {
            return ready && !closed && turn != null && !turn.cancelled && !turn.committed;
        }

        private boolean sendImage(byte[] jpeg) {
            ObjectNode event = event("input_image_buffer.append");
            event.put("image", Base64.getEncoder().encodeToString(jpeg));
            return send(event);
        }

        private boolean send(ObjectNode event) {
            WebSocket socket = webSocket;
            // 调用方可能正持有 Session 锁；这里不能同步调用 Listener.onFailure。
            return !closed && socket != null && socket.send(JsonUtils.toJsonString(event));
        }

        private void handle(JsonNode event) {
            String type = event.path("type").asText();
            if ("session.created".equals(type)) {
                onSessionCreated();
                return;
            }
            if ("session.updated".equals(type)) {
                synchronized (this) {
                    if (closed || ready || !updateSent) {
                        return;
                    }
                    ready = true;
                }
                listener.onReady(event.path("session").path("model").asText(model));
                return;
            }
            if ("error".equals(type)) {
                JsonNode error = event.path("error");
                notifyFailure(new IllegalStateException("实时模型错误："
                        + error.path("code").asText("unknown") + " "
                        + error.path("message").asText("unknown")));
                return;
            }
            if ("input_audio_buffer.committed".equals(type)) {
                onCommitted(event);
                return;
            }
            if ("conversation.item.input_audio_transcription.completed".equals(type)
                    || "conversation.item.input_audio_transcription.failed".equals(type)) {
                onTranscript(event);
                return;
            }
            if ("response.created".equals(type)) {
                boolean cancel;
                synchronized (this) {
                    cancel = active != null && active.cancelled;
                    if (active != null) {
                        active.responseInProgress = true;
                    }
                }
                if (cancel) {
                    send(event("response.cancel"));
                }
                return;
            }
            if ("response.audio_transcript.delta".equals(type) || "response.text.delta".equals(type)) {
                onTextDelta(event);
                return;
            }
            if ("response.audio_transcript.done".equals(type) || "response.text.done".equals(type)) {
                onTextDone(event);
                return;
            }
            if ("response.audio.delta".equals(type)) {
                onAudio(event);
                return;
            }
            if ("response.done".equals(type)) {
                onDone(event);
            }
        }

        private void onSessionCreated() {
            synchronized (this) {
                if (closed || updateSent) {
                    return;
                }
                updateSent = true;
            }
            ObjectNode update = event("session.update");
            ObjectNode config = update.putObject("session");
            config.putArray("modalities").add("text").add("audio");
            config.put("instructions", INSTRUCTIONS);
            config.putNull("turn_detection");
            config.putObject("input_audio_transcription")
                    .put("model", "qwen3-asr-flash-realtime");
            ObjectNode audio = config.putObject("audio");
            ObjectNode inputFormat = audio.putObject("input").putObject("format");
            inputFormat.put("type", "pcm");
            inputFormat.put("sample_rate", 16000);
            inputFormat.put("sample_format", "s16le");
            inputFormat.put("channels", 1);
            inputFormat.put("packing", "interleaved");
            inputFormat.put("channel_layout", "mono");
            ObjectNode output = audio.putObject("output");
            String voice = properties.getRealtimeVoice();
            if (voice != null && !voice.trim().isEmpty()) {
                output.put("voice", voice.trim());
            }
            if (!send(update)) {
                notifyFailure(new IllegalStateException("实时模型会话配置发送失败"));
            }
        }

        private void onCommitted(JsonNode event) {
            Turn turn;
            boolean sent;
            JsonNode earlyTranscript = null;
            synchronized (this) {
                turn = active;
                if (turn == null || !turn.committed) {
                    return;
                }
                if (turn.cancelled) {
                    active = null;
                    return;
                }
                String itemId = event.path("item_id").asText("");
                if (!itemId.isEmpty()) {
                    turnsByItemId.put(itemId, turn);
                    itemOrder.addLast(itemId);
                    earlyTranscript = earlyTranscriptEvents.remove(itemId);
                    earlyTranscriptOrder.remove(itemId);
                    while (itemOrder.size() > 64) {
                        turnsByItemId.remove(itemOrder.removeFirst());
                    }
                }
                turn.responseRequestedAtNanos = System.nanoTime();
                sent = send(event("response.create"));
            }
            if (!sent) {
                notifyFailure(new IllegalStateException("实时模型 response.create 发送失败"));
            } else if (earlyTranscript != null) {
                onTranscript(earlyTranscript);
            }
        }

        private void onTranscript(JsonNode event) {
            Turn turn;
            String itemId = event.path("item_id").asText("");
            synchronized (this) {
                turn = turnsByItemId.get(itemId);
                if (turn == null) {
                    if (!itemId.isEmpty()) {
                        earlyTranscriptEvents.put(itemId, event);
                        earlyTranscriptOrder.remove(itemId);
                        earlyTranscriptOrder.addLast(itemId);
                        while (earlyTranscriptOrder.size() > 64) {
                            earlyTranscriptEvents.remove(earlyTranscriptOrder.removeFirst());
                        }
                    }
                    return;
                }
                if (turn.cancelled) {
                    return;
                }
            }
            String transcript = "conversation.item.input_audio_transcription.completed"
                    .equals(event.path("type").asText()) ? event.path("transcript").asText("") : "";
            if (transcript.isEmpty()) {
                log.warn("[ESP32_OMNI_TRANSCRIPT_UPSTREAM_EMPTY] requestId={} code={}",
                        turn.requestId, event.path("error").path("code").asText("empty_transcript"));
            }
            listener.onUserTranscript(turn.requestId, transcript,
                    elapsedMs(turn.committedAtNanos, System.nanoTime()));
        }

        private void onTextDelta(JsonNode event) {
            String delta = event.path("delta").asText("");
            Turn turn;
            synchronized (this) {
                turn = active;
                if (turn == null || turn.cancelled || delta.isEmpty()) {
                    return;
                }
                if (turn.firstTokenAtNanos == 0) {
                    turn.firstTokenAtNanos = System.nanoTime();
                }
                turn.text.append(delta);
            }
            listener.onAssistantTextDelta(turn.requestId, delta);
        }

        private void onTextDone(JsonNode event) {
            Turn turn;
            String text;
            long elapsed;
            long first;
            synchronized (this) {
                turn = active;
                if (turn == null || turn.cancelled || turn.textDone) {
                    return;
                }
                turn.textDone = true;
                text = event.path("transcript").asText(event.path("text").asText(turn.text.toString()));
                elapsed = elapsedMs(turn.responseRequestedAtNanos, System.nanoTime());
                first = elapsedMs(turn.responseRequestedAtNanos, turn.firstTokenAtNanos);
            }
            listener.onAssistantTextDone(turn.requestId, text, elapsed, first);
        }

        private void onAudio(JsonNode event) {
            Turn turn;
            byte[] pcm;
            synchronized (this) {
                turn = active;
                if (turn == null || turn.cancelled) {
                    return;
                }
                if (turn.firstTokenAtNanos == 0) {
                    turn.firstTokenAtNanos = System.nanoTime();
                }
            }
            try {
                pcm = Base64.getDecoder().decode(event.path("delta").asText());
            } catch (IllegalArgumentException exception) {
                notifyFailure(new IllegalStateException("实时模型音频 Base64 无效", exception));
                return;
            }
            if (pcm.length > 0) {
                listener.onAssistantAudio(turn.requestId, pcm);
            }
        }

        private void onDone(JsonNode event) {
            Turn turn;
            String text = null;
            long elapsed = 0;
            long first = 0;
            synchronized (this) {
                turn = active;
                if (turn == null) {
                    return;
                }
                if (turn.cancelled) {
                    active = null;
                    return;
                }
                if (!turn.textDone) {
                    turn.textDone = true;
                    text = turn.text.toString();
                    if (text.isEmpty()) {
                        text = outputTranscript(event.path("response"));
                    }
                    elapsed = elapsedMs(turn.responseRequestedAtNanos, System.nanoTime());
                    first = elapsedMs(turn.responseRequestedAtNanos, turn.firstTokenAtNanos);
                }
                active = null;
            }
            if (text != null) {
                listener.onAssistantTextDone(turn.requestId, text, elapsed, first);
            }
            String status = event.path("response").path("status").asText("completed");
            if (!"completed".equals(status)) {
                notifyFailure(new IllegalStateException("实时模型回答未完成：" + status));
            } else {
                listener.onResponseDone(turn.requestId);
            }
        }

        private void notifyFailure(Throwable error) {
            if (failureNotified.compareAndSet(false, true)) {
                synchronized (this) {
                    ready = false;
                }
                listener.onFailure(error);
            }
        }
    }

    private static ObjectNode event(String type) {
        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode();
        node.put("type", type);
        return node;
    }

    private static long elapsedMs(long start, long end) {
        return start == 0 || end == 0 ? 0 : Math.max(0, TimeUnit.NANOSECONDS.toMillis(end - start));
    }

    private static String outputTranscript(JsonNode response) {
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String transcript = part.path("transcript").asText("");
                        if (!transcript.isEmpty()) {
                            return transcript;
                        }
                    }
                }
            }
        }
        return "";
    }

    private static final class Turn {
        private final String requestId;
        private final List<byte[]> pendingImages = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private boolean audioSent;
        private boolean committed;
        private boolean cancelled;
        private boolean responseInProgress;
        private boolean textDone;
        private int imageCount;
        private long committedAtNanos;
        private long responseRequestedAtNanos;
        private long firstTokenAtNanos;

        private Turn(String requestId) {
            this.requestId = requestId;
        }
    }
}
