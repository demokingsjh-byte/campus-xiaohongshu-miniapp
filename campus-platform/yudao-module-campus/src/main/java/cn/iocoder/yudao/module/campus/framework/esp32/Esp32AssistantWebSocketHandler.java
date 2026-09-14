package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.service.esp32.CampusEsp32LogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ESP32-S3 视听说闭环：设备媒体接入、模型转发、异步 ASR 记录和 TTS PCM 回传。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Esp32AssistantWebSocketHandler extends AbstractWebSocketHandler {

    private final CampusEsp32AssistantProperties properties;
    private final DoubaoAsrClient asrClient;
    private final DoubaoTtsClient ttsClient;
    private final GuideModelClient modelClient;
    private final CampusEsp32LogService logService;

    private final Map<String, DeviceContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeIps = new ConcurrentHashMap<>();
    private final ExecutorService mediaExecutor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = String.valueOf(session.getAttributes().getOrDefault(
                Esp32DeviceHandshakeInterceptor.ATTR_DEVICE_ID, "unknown"));
        String clientIp = String.valueOf(session.getAttributes().getOrDefault(
                Esp32DeviceHandshakeInterceptor.ATTR_CLIENT_IP, "unknown"));
        AtomicInteger count = activeIps.computeIfAbsent(clientIp, key -> new AtomicInteger());
        if (count.incrementAndGet() > properties.getMaxConnectionsPerIp()) {
            count.decrementAndGet();
            session.close(new CloseStatus(4429, "Too many active devices"));
            return;
        }

        DeviceContext context = new DeviceContext(session, deviceId, clientIp);
        sessions.put(session.getId(), context);
        log.info("[ESP32_SESSION_OPEN] sessionId={} deviceId={} clientIp={}",
                session.getId(), deviceId, clientIp);
        context.modelSession = modelClient.connect(new GuideModelClient.ModelEventListener() {
            @Override
            public void onConnected(JsonNode event) {
                handleModelConnected(context, event);
            }

            @Override
            public void onEvent(JsonNode event) {
                handleModelEvent(context, event);
            }

            @Override
            public void onFailure(Throwable throwable) {
                handleModelFailure(context, throwable);
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DeviceContext context = sessions.get(session.getId());
        if (context == null) {
            return;
        }
        JsonNode event;
        try {
            event = JsonUtils.getObjectMapper().readTree(message.getPayload());
        } catch (Exception exception) {
            sendError(context, "INVALID_JSON", "控制消息不是有效 JSON", false, null);
            return;
        }
        String type = event.path("type").asText();
        if ("turn_start".equals(type)) {
            startTurn(context, event.path("request_id").asText());
        } else if ("turn_commit".equals(type)) {
            commitTurn(context);
        } else if ("turn_cancel".equals(type)) {
            cancelCapture(context);
        } else if ("interrupt".equals(type)) {
            interrupt(context, event.path("request_id").asText());
        } else if ("ping".equals(type)) {
            ping(context, event);
        } else {
            sendError(context, "UNKNOWN_EVENT", "未知控制消息", false, null);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DeviceContext context = sessions.get(session.getId());
        if (context == null) {
            return;
        }
        ByteBuffer buffer = message.getPayload();
        if (!buffer.hasRemaining()) {
            return;
        }
        byte frameType = buffer.get();
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        synchronized (context.lock) {
            if (context.state != DeviceState.CAPTURING || context.turn == null) {
                sendJsonLocked(context, event("media_ignored", "reason", "not_capturing"));
                return;
            }
            if (frameType == Esp32ProtocolUtils.AUDIO_FRAME) {
                appendAudio(context, payload);
            } else if (frameType == Esp32ProtocolUtils.IMAGE_FRAME) {
                appendImage(context, payload);
            } else {
                sendErrorLocked(context, "UNKNOWN_BINARY_FRAME", "未知二进制帧类型", false, null);
            }
        }
    }

    private void handleModelConnected(DeviceContext context, JsonNode upstream) {
        synchronized (context.lock) {
            Map<String, Object> connected = event("connected", "session_id", context.session.getId());
            connected.put("device_id", context.deviceId);
            connected.put("protocol_version", "esp32-av/1.0");
            connected.put("mode", "half_duplex");
            connected.put("model", upstream.path("model").asText(null));
            connected.put("input_audio", mediaDescription(1, "pcm_s16le",
                    Esp32ProtocolUtils.INPUT_SAMPLE_RATE));
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("binary_prefix", 2);
            image.put("format", "jpeg");
            image.put("max_count", Esp32ProtocolUtils.MAX_IMAGE_COUNT);
            connected.put("input_image", image);
            connected.put("output_audio", mediaDescription(null, "pcm_s16le",
                    Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE));
            sendJsonLocked(context, connected);
            setStateLocked(context, DeviceState.LISTENING, null, "可以提问了");
        }
    }

    private void startTurn(DeviceContext context, String requestId) {
        synchronized (context.lock) {
            if (context.state != DeviceState.LISTENING || context.turn != null) {
                Map<String, Object> busy = event("busy", "state", context.state.value);
                busy.put("msg", "当前轮次尚未结束");
                sendJsonLocked(context, busy);
                return;
            }
            String id = requestId == null || requestId.trim().isEmpty()
                    ? "esp32_turn_" + UUID.randomUUID().toString().replace("-", "")
                    : requestId.trim().substring(0, Math.min(100, requestId.trim().length()));
            Long logId = logService.startTurn(context.session.getId(), context.deviceId, context.clientIp, id);
            context.turn = new TurnBuffer(id, logId);
            setStateLocked(context, DeviceState.CAPTURING, id, "正在聆听");
            sendJsonLocked(context, event("turn_ready", "request_id", id));
        }
    }

    private void appendAudio(DeviceContext context, byte[] payload) {
        if (payload.length == 0) {
            return;
        }
        if ((payload.length & 1) != 0) {
            sendErrorLocked(context, "INVALID_AUDIO", "PCM16 音频字节数必须为偶数", false,
                    context.turn.requestId);
            return;
        }
        int maxBytes = Math.max(1, properties.getMaxAudioSeconds())
                * Esp32ProtocolUtils.INPUT_SAMPLE_RATE * 2;
        if (context.turn.pcm.size() + payload.length > maxBytes) {
            sendErrorLocked(context, "AUDIO_TOO_LARGE", "单轮音频超过时长限制", false,
                    context.turn.requestId);
            return;
        }
        context.turn.pcm.write(payload, 0, payload.length);
    }

    private void appendImage(DeviceContext context, byte[] payload) {
        TurnBuffer turn = context.turn;
        if (!Esp32ProtocolUtils.isCompleteJpeg(payload)) {
            sendErrorLocked(context, "INVALID_IMAGE", "图片必须是完整 JPEG 帧", false,
                    turn.requestId);
            return;
        }
        if (payload.length > Esp32ProtocolUtils.MAX_IMAGE_BYTES) {
            sendErrorLocked(context, "IMAGE_TOO_LARGE", "单张图片不能超过 2MB", false,
                    turn.requestId);
            return;
        }
        if (turn.images.size() >= Esp32ProtocolUtils.MAX_IMAGE_COUNT) {
            sendErrorLocked(context, "TOO_MANY_IMAGES", "单轮最多提交 3 张图片", false,
                    turn.requestId);
            return;
        }
        int totalBytes = payload.length;
        for (byte[] image : turn.images) {
            totalBytes += image.length;
        }
        if (totalBytes > Esp32ProtocolUtils.MAX_TOTAL_IMAGE_BYTES) {
            sendErrorLocked(context, "IMAGES_TOO_LARGE", "单轮图片合计不能超过 6MB", false,
                    turn.requestId);
            return;
        }
        turn.images.add(payload);
        Map<String, Object> received = event("image_received", "request_id", turn.requestId);
        received.put("count", turn.images.size());
        sendJsonLocked(context, received);
    }

    private void commitTurn(DeviceContext context) {
        TurnData turn;
        synchronized (context.lock) {
            if (context.turn == null || context.state != DeviceState.CAPTURING) {
                sendErrorLocked(context, "NO_ACTIVE_TURN", "请先发送 turn_start", false, null);
                return;
            }
            turn = context.turn.snapshot();
            context.turn = null;
            if (turn.pcm.length < Esp32ProtocolUtils.MIN_AUDIO_BYTES) {
                logService.markIgnored(turn.logId, turn.pcm.length, turn.images.size(), "audio_too_short");
                Map<String, Object> ignored = event("turn_ignored", "request_id", turn.requestId);
                ignored.put("reason", "audio_too_short");
                sendJsonLocked(context, ignored);
                setStateLocked(context, DeviceState.LISTENING, turn.requestId,
                        "没有听清，请再说一次");
                log.info("[ESP32_TURN_IGNORED] deviceId={} requestId={} reason={} audioBytes={} imageCount={}",
                        context.deviceId, turn.requestId, "audio_too_short", turn.pcm.length,
                        turn.images.size());
                return;
            }
            context.activeRequestId = turn.requestId;
            context.activeLogId = turn.logId;
            context.turnStartedAt = turn.startedAt;
            setStateLocked(context, DeviceState.THINKING, turn.requestId, "正在理解");
        }
        long captureMs = elapsedMillis(turn.startedAt);
        byte[] wav = Esp32ProtocolUtils.pcm16LeToWav(
                turn.pcm, Esp32ProtocolUtils.INPUT_SAMPLE_RATE);
        Map<String, Object> chat = event("chat", "request_id", turn.requestId);
        chat.put("audio", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav));
        List<String> images = new ArrayList<>();
        for (byte[] image : turn.images) {
            images.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image));
        }
        chat.put("images", images);
        chat.put("output_audio", false);
        Map<String, Object> modelContext = new LinkedHashMap<>();
        modelContext.put("client", "esp32-s3");
        modelContext.put("device_id", context.deviceId);
        chat.put("context", modelContext);
        long submitStartedAt = System.nanoTime();
        if (context.modelSession == null || !context.modelSession.send(chat)) {
            logService.markFailed(turn.logId, "MODEL_UNAVAILABLE", "模型连接不可用",
                    elapsedMillis(turn.startedAt));
            sendError(context, "MODEL_UNAVAILABLE", "模型连接不可用", true, turn.requestId);
            synchronized (context.lock) {
                context.activeRequestId = "";
                context.activeLogId = null;
                setStateLocked(context, DeviceState.LISTENING, turn.requestId,
                        "服务异常，请重试");
            }
            return;
        }
        long submitMs = elapsedMillis(submitStartedAt);
        logService.markSubmitted(turn.logId, turn.pcm.length, turn.images.size(), captureMs, submitMs);
        log.info("[ESP32_TURN_SUBMITTED] deviceId={} requestId={} audioBytes={} imageCount={} submitMs={}",
                context.deviceId, turn.requestId, turn.pcm.length, turn.images.size(),
                captureMs);

        // 模型原生支持音频输入，ASR 仅异步补充文字日志，失败不能阻断模型回答。
        if (properties.isAsrEnabled()) {
            mediaExecutor.execute(() -> transcribeInBackground(context, turn.requestId, turn.logId, wav));
        }
    }

    private void transcribeInBackground(DeviceContext context, String requestId, Long logId, byte[] wav) {
        long startedAt = System.nanoTime();
        try {
            String transcript = asrClient.transcribe(wav).trim();
            logService.markAsr(logId, elapsedMillis(startedAt), true);
            log.info("[ESP32_ASR_COMPLETED] deviceId={} requestId={} elapsedMs={} transcript={}",
                    context.deviceId, requestId, elapsedMillis(startedAt),
                    abbreviate(transcript, 160));
        } catch (Exception exception) {
            logService.markAsr(logId, elapsedMillis(startedAt), false);
            log.warn("[ESP32_ASR_FAILED] deviceId={} requestId={} elapsedMs={}",
                    context.deviceId, requestId, elapsedMillis(startedAt), exception);
        }
    }

    private void handleModelEvent(DeviceContext context, JsonNode event) {
        String type = event.path("type").asText();
        String requestId = event.path("request_id").asText(context.activeRequestId);
        synchronized (context.lock) {
            if ("pong".equals(type)) {
                sendTextLocked(context, event.toString());
                return;
            }
            if ("audio_start".equals(type) || "audio_delta".equals(type)
                    || "audio_done".equals(type)) {
                return;
            }
            if (context.interruptedRequests.contains(requestId)) {
                if ("text_done".equals(type) || "interrupted".equals(type)
                        || "error".equals(type)) {
                    context.interruptedRequests.remove(requestId);
                }
                return;
            }
            if ("text_delta".equals(type)) {
                String delta = event.path("text").asText(event.path("delta").asText(""));
                if (context.tts == null || !requestId.equals(context.ttsRequestId)) {
                    startTtsLocked(context, requestId);
                }
                context.pendingTtsText.append(delta);
                flushTtsTextLocked(context, false);
                sendTextLocked(context, event.toString());
                return;
            }
            if ("text_done".equals(type)) {
                ObjectNode safeEvent = event.deepCopy();
                safeEvent.remove("audio");
                sendTextLocked(context, safeEvent.toString());
                if (context.tts == null || !requestId.equals(context.ttsRequestId)) {
                    startTtsLocked(context, requestId);
                }
                if (!context.ttsHasText && context.pendingTtsText.length() == 0) {
                    context.pendingTtsText.append(event.path("text").asText(""));
                }
                flushTtsTextLocked(context, true);
                if (context.tts != null) {
                    context.tts.finish();
                }
                Long modelTotalMs = nullableStatMillis(event.path("stats").path("total_ms"));
                Long modelFirstTokenMs = nullableStatMillis(event.path("stats").path("first_token_ms"));
                logService.markModelDone(context.activeLogId, modelTotalMs, modelFirstTokenMs);
                log.info("[ESP32_MODEL_DONE] deviceId={} requestId={} totalMs={} firstTokenMs={} text={}",
                        context.deviceId, requestId,
                        event.path("stats").path("total_ms").asLong(-1),
                        event.path("stats").path("first_token_ms").asLong(-1),
                        abbreviate(event.path("text").asText(""), 160));
                return;
            }
            if ("interrupted".equals(type)) {
                closeTtsLocked(context);
                logService.markInterrupted(context.activeLogId, elapsedMillis(context.turnStartedAt));
                context.activeRequestId = "";
                context.activeLogId = null;
                sendTextLocked(context, event.toString());
                setStateLocked(context, DeviceState.LISTENING, requestId,
                        "已停止，请开始提问");
                return;
            }
            if ("error".equals(type)) {
                closeTtsLocked(context);
                logService.markFailed(context.activeLogId, "MODEL_ERROR",
                        abbreviate(event.path("message").asText("模型服务返回错误"), 255),
                        elapsedMillis(context.turnStartedAt));
                context.activeRequestId = "";
                context.activeLogId = null;
                sendTextLocked(context, event.toString());
                setStateLocked(context, DeviceState.LISTENING, requestId,
                        "服务异常，请重试");
                return;
            }
            sendTextLocked(context, event.toString());
        }
    }

    private void startTtsLocked(DeviceContext context, String requestId) {
        closeTtsLocked(context);
        context.ttsRequestId = requestId;
        context.ttsFirstAudio = true;
        context.ttsHasText = false;
        context.ttsFirstAudioAt = 0L;
        context.pendingTtsText.setLength(0);
        Map<String, Object> start = event("audio_start", "request_id", requestId);
        start.put("format", "pcm_s16le");
        start.put("sample_rate", Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE);
        start.put("channels", 1);
        sendJsonLocked(context, start);
        context.tts = ttsClient.start(
                audio -> onTtsAudio(context, requestId, audio),
                () -> onTtsCompleted(context, requestId),
                throwable -> onTtsFailed(context, requestId, throwable));
    }

    private void flushTtsTextLocked(DeviceContext context, boolean force) {
        if (context.tts == null || context.pendingTtsText.length() == 0) {
            return;
        }
        char last = context.pendingTtsText.charAt(context.pendingTtsText.length() - 1);
        if (!force && context.pendingTtsText.length() < 24
                && "，。！？；,.!?;".indexOf(last) < 0) {
            return;
        }
        String text = context.pendingTtsText.toString();
        context.pendingTtsText.setLength(0);
        context.ttsHasText = true;
        context.tts.appendText(text);
    }

    private void onTtsAudio(DeviceContext context, String requestId, byte[] audio) {
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId)) {
                return;
            }
            if (context.ttsFirstAudio) {
                context.ttsFirstAudio = false;
                context.ttsFirstAudioAt = System.nanoTime();
                setStateLocked(context, DeviceState.SPEAKING, requestId, "AI 正在讲解");
                logService.markTtsFirstAudio(context.activeLogId,
                        TimeUnit.NANOSECONDS.toMillis(context.ttsFirstAudioAt - context.turnStartedAt));
                log.info("[ESP32_FIRST_TTS_AUDIO] deviceId={} requestId={} elapsedMs={}",
                        context.deviceId, requestId,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - context.turnStartedAt));
            }
            sendPacedAudioLocked(context, audio);
        }
    }

    private void onTtsCompleted(DeviceContext context, String requestId) {
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId)) {
                return;
            }
            sendJsonLocked(context, event("audio_done", "request_id", requestId));
            sendJsonLocked(context, event("turn_done", "request_id", requestId));
            Long logId = context.activeLogId;
            long totalMs = elapsedMillis(context.turnStartedAt);
            Long ttsAudioMs = context.ttsFirstAudioAt > 0
                    ? elapsedMillis(context.ttsFirstAudioAt) : null;
            logService.markCompleted(logId, totalMs, ttsAudioMs);
            context.activeLogId = null;
            context.tts = null;
            context.ttsRequestId = "";
            setStateLocked(context, DeviceState.COOLDOWN, requestId, "即将恢复聆听");
        }
        scheduler.schedule(() -> {
            synchronized (context.lock) {
                if (context.state != DeviceState.COOLDOWN
                        || !requestId.equals(context.activeRequestId)) {
                    return;
                }
                context.activeRequestId = "";
                context.activeLogId = null;
                setStateLocked(context, DeviceState.LISTENING, requestId, "可以提问了");
            }
        }, Math.max(0, properties.getCooldownMillis()), TimeUnit.MILLISECONDS);
    }

    private void onTtsFailed(DeviceContext context, String requestId, Throwable throwable) {
        log.warn("[ESP32_TTS_FAILED] deviceId={} requestId={}",
                context.deviceId, requestId, throwable);
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)) {
                return;
            }
            logService.markFailed(context.activeLogId, "TTS_FAILED", "语音合成暂时不可用",
                    elapsedMillis(context.turnStartedAt));
            sendErrorLocked(context, "TTS_FAILED", "语音合成暂时不可用", true, requestId);
            sendJsonLocked(context, event("turn_done", "request_id", requestId));
            context.tts = null;
            context.ttsRequestId = "";
            context.activeRequestId = "";
            context.activeLogId = null;
            setStateLocked(context, DeviceState.LISTENING, requestId, "可以提问了");
        }
    }

    private void cancelCapture(DeviceContext context) {
        synchronized (context.lock) {
            if (context.turn == null || context.state != DeviceState.CAPTURING) {
                Map<String, Object> busy = event("busy", "state", context.state.value);
                busy.put("msg", "当前没有可取消的采集轮次");
                sendJsonLocked(context, busy);
                return;
            }
            String requestId = context.turn.requestId;
            logService.markIgnored(context.turn.logId, context.turn.pcm.size(), context.turn.images.size(),
                    "capture_cancelled");
            context.turn = null;
            setStateLocked(context, DeviceState.LISTENING, requestId,
                    "已取消，请重新提问");
        }
    }

    private void interrupt(DeviceContext context, String requestedId) {
        synchronized (context.lock) {
            String requestId = requestedId == null || requestedId.trim().isEmpty()
                    ? context.activeRequestId : requestedId.trim();
            if (!requestId.isEmpty()) {
                context.interruptedRequests.add(requestId);
                if (context.modelSession != null) {
                    context.modelSession.send(event("interrupt", "request_id", requestId));
                }
            }
            closeTtsLocked(context);
            logService.markInterrupted(context.activeLogId, context.turnStartedAt > 0
                    ? elapsedMillis(context.turnStartedAt) : null);
            context.activeRequestId = "";
            context.activeLogId = null;
            context.turn = null;
            sendJsonLocked(context, event("interrupt_ack", "request_id", requestId));
            setStateLocked(context, DeviceState.LISTENING, requestId,
                    "已停止，请开始提问");
            log.info("[ESP32_INTERRUPTED] deviceId={} requestId={}",
                    context.deviceId, requestId);
        }
    }

    private void ping(DeviceContext context, JsonNode event) {
        Map<String, Object> ping = new LinkedHashMap<>();
        ping.put("type", "ping");
        if (event.has("timestamp")) {
            ping.put("timestamp", event.get("timestamp").asLong());
        }
        if (context.modelSession == null || !context.modelSession.send(ping)) {
            ping.put("type", "pong");
            synchronized (context.lock) {
                sendJsonLocked(context, ping);
            }
        }
    }

    private void handleModelFailure(DeviceContext context, Throwable throwable) {
        if (!sessions.containsKey(context.session.getId())) {
            return;
        }
        log.warn("[ESP32_MODEL_FAILED] deviceId={} sessionId={}",
                context.deviceId, context.session.getId(), throwable);
        synchronized (context.lock) {
            logService.markFailed(context.activeLogId, "MODEL_FAILED", "模型服务暂时不可用",
                    context.turnStartedAt > 0 ? elapsedMillis(context.turnStartedAt) : null);
            sendErrorLocked(context, "GATEWAY_ERROR", "模型服务暂时不可用", true,
                    context.activeRequestId);
            try {
                context.session.close(new CloseStatus(1011, "model unavailable"));
            } catch (IOException ignored) {
                // 连接已经关闭。
            }
        }
    }

    private void closeTtsLocked(DeviceContext context) {
        if (context.tts != null) {
            context.tts.close();
        }
        context.tts = null;
        context.ttsRequestId = "";
        context.pendingTtsText.setLength(0);
        context.ttsHasText = false;
    }

    private void setStateLocked(DeviceContext context, DeviceState state,
                                String requestId, String message) {
        context.state = state;
        Map<String, Object> event = event("state", "state", state.value);
        if (requestId != null && !requestId.isEmpty()) {
            event.put("request_id", requestId);
        }
        if (message != null && !message.isEmpty()) {
            event.put("message", message);
        }
        sendJsonLocked(context, event);
    }

    private void sendError(DeviceContext context, String code, String message,
                           boolean retryable, String requestId) {
        synchronized (context.lock) {
            sendErrorLocked(context, code, message, retryable, requestId);
        }
    }

    private void sendErrorLocked(DeviceContext context, String code, String message,
                                 boolean retryable, String requestId) {
        Map<String, Object> error = event("error", "code", code);
        error.put("msg", message);
        error.put("retryable", retryable);
        if (requestId != null && !requestId.isEmpty()) {
            error.put("request_id", requestId);
        }
        sendJsonLocked(context, error);
    }

    private void sendJsonLocked(DeviceContext context, Map<String, Object> event) {
        sendTextLocked(context, JsonUtils.toJsonString(event));
    }

    private void sendTextLocked(DeviceContext context, String text) {
        if (!context.session.isOpen()) {
            return;
        }
        try {
            context.session.sendMessage(new TextMessage(text));
        } catch (IOException exception) {
            log.warn("[ESP32_SEND_TEXT_FAILED] deviceId={} sessionId={}",
                    context.deviceId, context.session.getId(), exception);
        }
    }

    private void sendPacedAudioLocked(DeviceContext context, byte[] audio) {
        if (!context.session.isOpen()) {
            return;
        }
        try {
            int configuredChunk = Math.max(2, properties.getOutputAudioChunkBytes());
            int chunkBytes = configuredChunk - configuredChunk % 2;
            for (int offset = 0; offset < audio.length; offset += chunkBytes) {
                int size = Math.min(chunkBytes, audio.length - offset);
                size -= size % 2;
                if (size <= 0) {
                    continue;
                }
                context.session.sendMessage(new BinaryMessage(
                        ByteBuffer.wrap(audio, offset, size), true));
                long pcmMillis = Math.max(1L, Math.round(size * 1000.0
                        / (Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE * 2)
                        * Math.max(1, properties.getPlaybackPacePercent()) / 100.0));
                Thread.sleep(pcmMillis);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            log.warn("[ESP32_SEND_AUDIO_FAILED] deviceId={} sessionId={}",
                    context.deviceId, context.session.getId(), exception);
        }
    }

    private static Map<String, Object> event(String type, String key, Object value) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        if (key != null) {
            event.put(key, value);
        }
        return event;
    }

    private static Map<String, Object> mediaDescription(Integer prefix, String format,
                                                         int sampleRate) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (prefix != null) {
            result.put("binary_prefix", prefix);
        }
        result.put("format", format);
        result.put("sample_rate", sampleRate);
        result.put("channels", 1);
        return result;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static Long nullableStatMillis(JsonNode value) {
        if (value == null || !value.isNumber() || value.asLong() < 0) {
            return null;
        }
        return value.asLong();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DeviceContext context = sessions.remove(session.getId());
        if (context == null) {
            return;
        }
        synchronized (context.lock) {
            Long logId = context.activeLogId != null ? context.activeLogId
                    : context.turn == null ? null : context.turn.logId;
            long startedAt = context.turnStartedAt > 0 ? context.turnStartedAt
                    : context.turn == null ? 0L : context.turn.startedAt;
            logService.markDisconnected(logId, startedAt > 0 ? elapsedMillis(startedAt) : null);
            closeTtsLocked(context);
            if (context.modelSession != null) {
                context.modelSession.close();
            }
        }
        AtomicInteger count = activeIps.get(context.clientIp);
        if (count != null && count.decrementAndGet() <= 0) {
            activeIps.remove(context.clientIp, count);
        }
        log.info("[ESP32_SESSION_CLOSED] sessionId={} deviceId={} code={} reason={}",
                session.getId(), context.deviceId, status.getCode(), status.getReason());
    }

    @PreDestroy
    public void destroy() {
        for (DeviceContext context : sessions.values()) {
            synchronized (context.lock) {
                closeTtsLocked(context);
                if (context.modelSession != null) {
                    context.modelSession.close();
                }
            }
        }
        mediaExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    private enum DeviceState {
        CONNECTING("connecting"),
        LISTENING("listening"),
        CAPTURING("capturing"),
        THINKING("thinking"),
        SPEAKING("speaking"),
        COOLDOWN("cooldown");

        private final String value;

        DeviceState(String value) {
            this.value = value;
        }
    }

    private static class DeviceContext {
        private final Object lock = new Object();
        private final WebSocketSession session;
        private final String deviceId;
        private final String clientIp;
        private final Set<String> interruptedRequests = ConcurrentHashMap.newKeySet();
        private final StringBuilder pendingTtsText = new StringBuilder();
        private DeviceState state = DeviceState.CONNECTING;
        private TurnBuffer turn;
        private GuideModelClient.ModelSession modelSession;
        private DoubaoTtsClient.TtsStream tts;
        private String ttsRequestId = "";
        private String activeRequestId = "";
        private boolean ttsFirstAudio;
        private boolean ttsHasText;
        private long turnStartedAt;
        private long ttsFirstAudioAt;
        private Long activeLogId;

        private DeviceContext(WebSocketSession session, String deviceId, String clientIp) {
            this.session = session;
            this.deviceId = deviceId;
            this.clientIp = clientIp;
        }
    }

    private static class TurnBuffer {
        private final String requestId;
        private final Long logId;
        private final long startedAt = System.nanoTime();
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private final List<byte[]> images = new ArrayList<>();

        private TurnBuffer(String requestId, Long logId) {
            this.requestId = requestId;
            this.logId = logId;
        }

        private TurnData snapshot() {
            return new TurnData(requestId, logId, startedAt, pcm.toByteArray(), new ArrayList<>(images));
        }
    }

    private static class TurnData {
        private final String requestId;
        private final Long logId;
        private final long startedAt;
        private final byte[] pcm;
        private final List<byte[]> images;

        private TurnData(String requestId, Long logId, long startedAt, byte[] pcm, List<byte[]> images) {
            this.requestId = requestId;
            this.logId = logId;
            this.startedAt = startedAt;
            this.pcm = pcm;
            this.images = Collections.unmodifiableList(images);
        }
    }
}
