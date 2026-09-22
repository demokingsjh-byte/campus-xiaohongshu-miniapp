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
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ESP32-S3 视听说闭环：原生音视频实时模型，或旧版 ASR + 图文模型 + TTS 回退链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Esp32AssistantWebSocketHandler extends AbstractWebSocketHandler {

    /** 音频下发等待的最大单次睡眠，避免长时间占用会话锁。 */
    private static final long MAX_PACING_SLEEP_MILLIS = 40L;

    private final CampusEsp32AssistantProperties properties;
    private final DoubaoAsrClient asrClient;
    private final DoubaoTtsClient ttsClient;
    private final GuideModelClient modelClient;
    private final OmniRealtimeClient omniClient;
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
        if (properties.isRealtimeProtocol()) {
            context.omniSession = omniClient.connect(new OmniRealtimeClient.Listener() {
                @Override
                public void onReady(String model) {
                    ObjectNode upstream = JsonUtils.getObjectMapper().createObjectNode();
                    upstream.put("model", model);
                    handleModelConnected(context, upstream);
                }

                @Override
                public void onUserTranscript(String requestId, String transcript, long elapsedMs) {
                    Long logId = context.realtimeLogIds.remove(requestId);
                    mediaExecutor.execute(() -> logService.markRealtimeTranscript(logId, elapsedMs, transcript));
                }

                @Override
                public void onAssistantTextDelta(String requestId, String delta) {
                    handleOmniTextDelta(context, requestId, delta);
                }

                @Override
                public void onAssistantTextDone(String requestId, String answer,
                                                long modelElapsedMs, long firstTokenMs) {
                    handleOmniTextDone(context, requestId, answer, modelElapsedMs, firstTokenMs);
                }

                @Override
                public void onAssistantAudio(String requestId, byte[] pcm) {
                    handleOmniAudio(context, requestId, pcm);
                }

                @Override
                public void onResponseDone(String requestId) {
                    handleOmniResponseDone(context, requestId);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    handleModelFailure(context, throwable);
                }
            });
            return;
        }
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
            commitTurn(context, event);
        } else if ("turn_metrics".equals(type)) {
            recordDeviceTurnMetrics(context, event);
        } else if ("turn_cancel".equals(type)) {
            cancelCapture(context, event.path("reason").asText());
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
            image.put("max_count", properties.getMaxImageCount());
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
            Long logId = logService.startTurn(context.session.getId(), context.deviceId, context.clientIp, id,
                    properties.getResolvedModelProtocol(), properties.isRealtimeProtocol()
                            ? properties.getRealtimeModelName() : properties.getModelName());
            context.turn = new TurnBuffer(id, logId);
            if (logId != null) {
                context.deviceMetricLogIds.put(id, logId);
                if (context.deviceMetricLogIds.size() > 16) {
                    context.deviceMetricLogIds.remove(context.deviceMetricLogIds.keySet().iterator().next());
                }
            }
            closeAsrLocked(context);
            if (properties.isRealtimeProtocol()) {
                if (context.omniSession == null || !context.omniSession.beginTurn(id)) {
                    logService.markFailed(logId, "MODEL_UNAVAILABLE", "实时模型会话不可用", null);
                    context.turn = null;
                    sendErrorLocked(context, "MODEL_UNAVAILABLE", "实时模型会话不可用", true, id);
                    return;
                }
                context.realtimeAnswer.setLength(0);
                context.realtimeModelDone = false;
                context.ttsFirstAudioAt = 0L;
                resetAudioPacing(context);
                if (logId != null) {
                    context.realtimeLogIds.put(id, logId);
                }
            } else if (properties.isAsrEnabled()) {
                // 旧图文链路仍在采集期间并行喂流式 ASR。
                context.asrStream = asrClient.startStream();
            }
            setStateLocked(context, DeviceState.CAPTURING, id, "正在聆听");
            sendJsonLocked(context, event("turn_ready", "request_id", id));
            // 启动静音自动提交兜底：到点后若检测到持续静音就主动 commitTurn。
            if (silenceCommitMillis() > 0) {
                context.turn.silenceCheckFuture = scheduler.schedule(
                        () -> runSilenceCheck(context),
                        silenceCommitMillis(), TimeUnit.MILLISECONDS);
            }
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
        // 服务端静音自动提交：兜底设备固件 VAD 失灵导致的多轮采集拖延。
        // 每收到一帧就做能量检测；只有检测到语音才重置静音计时。
        // 静音帧仍持续上行时，反复重置任务会让兜底永远无法触发。
        detectSilenceAndReschedule(context, payload);
        if (properties.isRealtimeProtocol()) {
            if (context.omniSession == null || !context.omniSession.appendAudio(payload)) {
                handleModelFailure(context, new IllegalStateException("实时模型音频流发送失败"));
            }
        } else {
            // 旧图文链路实时喂给 ASR；commit 时等待最终转写。
            DoubaoAsrClient.AsrStream stream = context.asrStream;
            if (stream != null) {
                stream.appendAudio(payload);
            }
        }
    }

    private void detectSilenceAndReschedule(DeviceContext context, byte[] payload) {
        TurnBuffer turn = context.turn;
        if (turn == null) {
            return;
        }
        int threshold = properties.getSilenceEnergyThreshold();
        long commitMillis = silenceCommitMillis();
        boolean hasSpeech = false;
        if (threshold > 0 && payload.length >= 2) {
            int max = 0;
            for (int i = 0; i + 1 < payload.length; i += 2) {
                int lo = payload[i] & 0xff;
                int hi = payload[i + 1];
                int sample = (hi << 8) | lo;
                int abs = sample < 0 ? -sample : sample;
                if (abs > max) {
                    max = abs;
                    if (max >= threshold) {
                        hasSpeech = true;
                        break;
                    }
                }
            }
        }
        if (hasSpeech) {
            turn.speechDetected = true;
            turn.lastSpeechAtNanos = System.nanoTime();
        }
        if (commitMillis <= 0) {
            return;
        }
        if (!shouldScheduleSilenceCheck(hasSpeech, turn.silenceCheckFuture)) {
            return;
        }
        if (hasSpeech && turn.silenceCheckFuture != null) {
            turn.silenceCheckFuture.cancel(false);
        }
        // 首次检查时音频可能尚不足最短长度；后续静音帧可补触发检查。
        long silenceMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - turn.lastSpeechAtNanos);
        turn.silenceCheckFuture = scheduler.schedule(() -> runSilenceCheck(context),
                Math.max(0L, commitMillis - silenceMs), TimeUnit.MILLISECONDS);
    }

    private long silenceCommitMillis() {
        long configured = properties.getSilenceCommitMillis();
        // 实时链路以设备端 700 ms 静音判定和提交前末张抓拍为准。网关只做较晚的
        // 故障兜底，避免抢先 commit 导致最新一张图落在模型提交之后。
        return properties.isRealtimeProtocol() && configured > 0
                ? Math.max(3500L, configured) : configured;
    }

    static boolean shouldScheduleSilenceCheck(boolean hasSpeech, ScheduledFuture<?> current) {
        return hasSpeech || current == null || current.isDone();
    }

    private void runSilenceCheck(DeviceContext context) {
        synchronized (context.lock) {
            TurnBuffer turn = context.turn;
            if (turn == null || turn.silenceCommitted
                    || context.state != DeviceState.CAPTURING) {
                return;
            }
            long commitMillis = silenceCommitMillis();
            if (commitMillis <= 0) {
                return;
            }
            long silenceMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - turn.lastSpeechAtNanos);
            if (silenceMs < commitMillis) {
                return;
            }
            if (!turn.speechDetected || turn.pcm.size() < Esp32ProtocolUtils.MIN_AUDIO_BYTES) {
                // 还没攒到最小音频量（用户可能只是按了按钮没说话），不主动 commit，
                // 让原本的设备端 turn_commit 或最大时长限制来收尾。
                return;
            }
            turn.silenceCommitted = true;
            log.info("[ESP32_SILENCE_AUTO_COMMIT] deviceId={} requestId={} silenceMs={} audioBytes={}",
                    context.deviceId, turn.requestId, silenceMs, turn.pcm.size());
            // 通知设备：这是网关主动提交的，固件可据此统计兜底触发率。
            Map<String, Object> committed = event("silence_committed", "request_id", turn.requestId);
            committed.put("reason", "silence");
            committed.put("silence_ms", silenceMs);
            sendJsonLocked(context, committed);
            commitTurn(context, null);
        }
    }

    private void appendImage(DeviceContext context, byte[] payload) {
        TurnBuffer turn = context.turn;
        if (!Esp32ProtocolUtils.isCompleteJpeg(payload)) {
            sendErrorLocked(context, "INVALID_IMAGE", "图片必须是完整 JPEG 帧", false,
                    turn.requestId);
            return;
        }
        if (payload.length > properties.getMaxImageBytes()) {
            sendErrorLocked(context, "IMAGE_TOO_LARGE",
                    "单张图片不能超过 " + (properties.getMaxImageBytes() / 1024 / 1024) + "MB", false,
                    turn.requestId);
            return;
        }
        if (turn.images.size() >= properties.getMaxImageCount()) {
            sendErrorLocked(context, "TOO_MANY_IMAGES",
                    "单轮最多提交 " + properties.getMaxImageCount() + " 张图片", false,
                    turn.requestId);
            return;
        }
        int totalBytes = payload.length;
        for (byte[] image : turn.images) {
            totalBytes += image.length;
        }
        if (totalBytes > properties.getMaxTotalImageBytes()) {
            sendErrorLocked(context, "IMAGES_TOO_LARGE",
                    "单轮图片合计不能超过 " + (properties.getMaxTotalImageBytes() / 1024 / 1024) + "MB", false,
                    turn.requestId);
            return;
        }
        byte[] modelImage = null;
        if (properties.isRealtimeProtocol()) {
            modelImage = prepareRealtimeImage(payload);
            if (modelImage == null) {
                sendErrorLocked(context, "IMAGE_TOO_LARGE_FOR_MODEL",
                        "图片压缩后仍超过实时模型单帧上限", false, turn.requestId);
                return;
            }
        }
        if (modelImage != null) {
            if (context.omniSession == null || !context.omniSession.appendImage(modelImage)) {
                handleModelFailure(context, new IllegalStateException("实时模型图片流发送失败"));
                return;
            }
        }
        turn.images.add(payload);
        Map<String, Object> received = event("image_received", "request_id", turn.requestId);
        received.put("count", turn.images.size());
        sendJsonLocked(context, received);
    }

    private void commitTurn(DeviceContext context, JsonNode commitEvent) {
        TurnData turn;
        DoubaoAsrClient.AsrStream asrStream;
        Long speechEndMs = optionalNonNegativeLong(commitEvent, "speech_end_ms");
        Long speechEndToCommitMs = optionalNonNegativeLong(commitEvent, "speech_end_to_commit_ms");
        synchronized (context.lock) {
            asrStream = null;
            if (context.turn == null || context.state != DeviceState.CAPTURING) {
                sendErrorLocked(context, "NO_ACTIVE_TURN", "请先发送 turn_start", false, null);
                return;
            }
            turn = context.turn.snapshot();
            context.turn = null;
            if (turn.pcm.length < Esp32ProtocolUtils.MIN_AUDIO_BYTES) {
                logService.markIgnored(turn.logId, turn.pcm.length, turn.images.size(), "audio_too_short");
                closeAsrLocked(context);
                if (context.omniSession != null) {
                    context.omniSession.interrupt();
                }
                finishRealtimeTranscript(context, turn.requestId, true);
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
            // 摘走本轮的流式 ASR 会话；后续音频帧不再进入（已在采集阶段喂完）。
            asrStream = context.asrStream;
            context.asrStream = null;
        }
        long captureMs = elapsedMillis(turn.startedAt);
        if (properties.isRealtimeProtocol()) {
            mediaExecutor.execute(() -> logService.saveImages(turn.logId, turn.images));
            long submitStartedAt = System.nanoTime();
            if (context.omniSession == null || !context.omniSession.commit()) {
                if (context.omniSession != null) {
                    context.omniSession.interrupt();
                }
                finishRealtimeTranscript(context, turn.requestId, false);
                handleModelFailure(context, new IllegalStateException("实时模型输入提交失败"));
                return;
            }
            logService.markSubmitted(turn.logId, turn.pcm.length, turn.images.size(), captureMs,
                    elapsedMillis(submitStartedAt), speechEndMs, speechEndToCommitMs);
            log.info("[ESP32_OMNI_TURN_SUBMITTED] deviceId={} requestId={} audioBytes={} imageCount={} captureMs={}",
                    context.deviceId, turn.requestId, turn.pcm.length, turn.images.size(), captureMs);
            return;
        }
        byte[] wav = Esp32ProtocolUtils.pcm16LeToWav(
                turn.pcm, Esp32ProtocolUtils.INPUT_SAMPLE_RATE);

        // 提前建好 TTS 连接：ASR 与模型阶段要花 2~3 秒，足够把连接与建会话的握手跑完，
        // 这样首个文字分片一到就能直接开始合成，省掉首帧音频前的握手往返。
        prewarmTts(context, turn.requestId);

        // 图片体积较大，异步落库，不能阻塞 ASR、模型和设备播放。
        mediaExecutor.execute(() -> logService.saveImages(turn.logId, turn.images));

        // 当前 Turbo 图文模型不接受设备音频，必须先转写，再提交“文字 + 图片”。
        if (properties.isAsrEnabled()) {
            final DoubaoAsrClient.AsrStream stream = asrStream;
            mediaExecutor.execute(() -> transcribeAndSubmit(context, turn, wav, captureMs, stream,
                    speechEndMs, speechEndToCommitMs));
        } else {
            logService.markAsrDisabled(turn.logId);
            failBeforeModel(context, turn, "ASR_DISABLED", "当前模型需要先开启语音转写");
        }
    }

    /**
     * 优先走流式会话收尾（音频已在采集阶段实时喂给火山），失败则回退到整段批处理。
     */
    private String transcribe(DeviceContext context, TurnData turn, byte[] wav,
                              DoubaoAsrClient.AsrStream asrStream, long startedAt) throws Exception {
        if (asrStream != null && asrStream.isUsable()) {
            try {
                String text = asrStream.finish();
                log.info("[ESP32_ASR_STREAMED] deviceId={} requestId={} elapsedMs={} text={}",
                        context.deviceId, turn.requestId, elapsedMillis(startedAt),
                        abbreviate(text, 160));
                return text;
            } catch (Exception exception) {
                log.warn("[ESP32_ASR_STREAM_FAILED] deviceId={} requestId={} elapsedMs={} 回退批处理",
                        context.deviceId, turn.requestId, elapsedMillis(startedAt), exception);
            } finally {
                closeQuietly(asrStream);
            }
        }
        return asrClient.transcribe(wav);
    }

    private void closeQuietly(DoubaoAsrClient.AsrStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // 会话清理失败不影响主流程。
            }
        }
    }

    /** 关闭并摘走当前设备持有的流式 ASR 会话（须持有 context.lock）。 */
    private void closeAsrLocked(DeviceContext context) {
        DoubaoAsrClient.AsrStream stream = context.asrStream;
        context.asrStream = null;
        closeQuietly(stream);
    }

    private void transcribeAndSubmit(DeviceContext context, TurnData turn, byte[] wav, long captureMs,
                                     DoubaoAsrClient.AsrStream asrStream,
                                     Long speechEndMs, Long speechEndToCommitMs) {
        long startedAt = System.nanoTime();
        String transcript;
        try {
            transcript = transcribe(context, turn, wav, asrStream, startedAt);
            transcript = transcript == null ? "" : transcript.trim();
            if (transcript.isEmpty()) {
                throw new IllegalStateException("火山 ASR 未返回转写文字");
            }
            long asrMs = elapsedMillis(startedAt);
            logService.markAsr(turn.logId, asrMs, true, transcript);
            log.info("[ESP32_ASR_COMPLETED] deviceId={} requestId={} elapsedMs={} transcript={}",
                    context.deviceId, turn.requestId, asrMs, abbreviate(transcript, 160));
        } catch (Exception exception) {
            long asrMs = elapsedMillis(startedAt);
            logService.markAsr(turn.logId, asrMs, false, null);
            log.warn("[ESP32_ASR_FAILED] deviceId={} requestId={} elapsedMs={}",
                    context.deviceId, turn.requestId, asrMs, exception);
            failBeforeModel(context, turn, "ASR_FAILED", "没有听清，请再说一次");
            return;
        }

        synchronized (context.lock) {
            if (!turn.requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(turn.requestId)) {
                return;
            }
        }
        Map<String, Object> chat = event("chat", "request_id", turn.requestId);
        chat.put("question", transcript);
        List<String> images = new ArrayList<>();
        for (byte[] image : turn.images) {
            byte[] payload = downscaleForModel(image);
            images.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(payload));
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
        logService.markSubmitted(turn.logId, turn.pcm.length, turn.images.size(), captureMs, submitMs,
                speechEndMs, speechEndToCommitMs);
        log.info("[ESP32_TURN_SUBMITTED] deviceId={} requestId={} audioBytes={} imageCount={} submitMs={}",
                context.deviceId, turn.requestId, turn.pcm.length, turn.images.size(),
                submitMs);

    }

    private void failBeforeModel(DeviceContext context, TurnData turn, String code, String message) {
        synchronized (context.lock) {
            if (!turn.requestId.equals(context.activeRequestId)) {
                return;
            }
            logService.markFailed(turn.logId, code, message, elapsedMillis(turn.startedAt));
            closeTtsLocked(context);
            closeAsrLocked(context);
            sendErrorLocked(context, code, message, true, turn.requestId);
            sendJsonLocked(context, event("turn_done", "request_id", turn.requestId));
            context.activeRequestId = "";
            context.activeLogId = null;
            setStateLocked(context, DeviceState.LISTENING, turn.requestId, "可以提问了");
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
                // 预热过的连接也要走一次，才能补发 audio_start 给设备并重置本轮播报状态。
                if (context.tts == null || !requestId.equals(context.ttsRequestId)
                        || !context.ttsAnnounced) {
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
                logService.markModelDone(context.activeLogId, modelTotalMs, modelFirstTokenMs,
                        event.path("text").asText(""));
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

    /**
     * 预热 TTS 连接：只建立连接与会话，不发送任何文字，也不会给设备发 audio_start。
     *
     * <p>火山双向流式 TTS 需要 START_CONNECTION、START_SESSION 两轮往返才会就绪。
     * 把这段握手挪到 ASR/模型阶段并行完成，首帧音频前的等待可明显缩短。</p>
     */
    private void prewarmTts(DeviceContext context, String requestId) {
        synchronized (context.lock) {
            if (context.tts != null && requestId.equals(context.ttsRequestId)) {
                return;
            }
            closeTtsLocked(context);
            context.ttsRequestId = requestId;
            context.ttsFirstAudio = true;
            context.ttsHasText = false;
            context.ttsFirstAudioAt = 0L;
            context.pendingTtsText.setLength(0);
            resetAudioPacing(context);
            context.tts = ttsClient.start(
                    audio -> onTtsAudio(context, requestId, audio),
                    () -> onTtsCompleted(context, requestId),
                    throwable -> onTtsFailed(context, requestId, throwable));
            context.ttsAnnounced = false;
        }
    }

    private void startTtsLocked(DeviceContext context, String requestId) {
        // 已预热过同一轮次的连接就直接复用，只有缺失时才现建。
        if (context.tts == null || !requestId.equals(context.ttsRequestId)) {
            closeTtsLocked(context);
            context.ttsRequestId = requestId;
            context.tts = ttsClient.start(
                    audio -> onTtsAudio(context, requestId, audio),
                    () -> onTtsCompleted(context, requestId),
                    throwable -> onTtsFailed(context, requestId, throwable));
        }
        context.ttsFirstAudio = true;
        context.ttsHasText = false;
        context.ttsFirstAudioAt = 0L;
        context.pendingTtsText.setLength(0);
        resetAudioPacing(context);
        context.ttsAnnounced = true;
        Map<String, Object> start = event("audio_start", "request_id", requestId);
        start.put("format", "pcm_s16le");
        start.put("sample_rate", Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE);
        start.put("channels", 1);
        sendJsonLocked(context, start);
    }

    private void flushTtsTextLocked(DeviceContext context, boolean force) {
        if (context.tts == null || context.pendingTtsText.length() == 0) {
            return;
        }
        char last = context.pendingTtsText.charAt(context.pendingTtsText.length() - 1);
        // 首个分片放宽阈值，让 TTS 尽早开始合成；后续恢复较长阈值，避免切得太碎影响韵律。
        int threshold = context.ttsHasText ? 24 : 6;
        if (!force && context.pendingTtsText.length() < threshold
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
        synchronized (context.lock) {
            // 预热连接在还没开始播报时失败，不要直接判定整轮失败：
            // 清理掉它即可，首个文字分片到达时会重新建连。
            if (!context.ttsHasText) {
                log.warn("[ESP32_TTS_PREWARM_FAILED] deviceId={} requestId={}",
                        context.deviceId, requestId, throwable);
                context.tts = null;
                context.ttsRequestId = "";
                return;
            }
            if (!requestId.equals(context.activeRequestId)) {
                return;
            }
            log.warn("[ESP32_TTS_FAILED] deviceId={} requestId={}",
                    context.deviceId, requestId, throwable);
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

    private void cancelCapture(DeviceContext context, String reason) {
        synchronized (context.lock) {
            if (context.turn == null || context.state != DeviceState.CAPTURING) {
                Map<String, Object> busy = event("busy", "state", context.state.value);
                busy.put("msg", "当前没有可取消的采集轮次");
                sendJsonLocked(context, busy);
                return;
            }
            String requestId = context.turn.requestId;
            String logReason = normalizeCancelReason(reason);
            logService.markIgnored(context.turn.logId, context.turn.pcm.size(), context.turn.images.size(),
                    logReason);
            closeAsrLocked(context);
            if (context.omniSession != null) {
                context.omniSession.interrupt();
            }
            finishRealtimeTranscript(context, requestId, true);
            context.turn = null;
            setStateLocked(context, DeviceState.LISTENING, requestId,
                    "已取消，请重新提问");
        }
    }

    private void handleOmniTextDelta(DeviceContext context, String requestId, String delta) {
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId) || delta == null || delta.isEmpty()) {
                return;
            }
            context.realtimeAnswer.append(delta);
            sendJsonLocked(context, event("text_delta", "request_id", requestId, "text", delta));
        }
    }

    private void handleOmniTextDone(DeviceContext context, String requestId, String answer,
                                    long modelElapsedMs, long firstTokenMs) {
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId) || context.realtimeModelDone) {
                return;
            }
            String finalAnswer = answer == null || answer.isEmpty()
                    ? context.realtimeAnswer.toString() : answer;
            context.realtimeModelDone = true;
            sendJsonLocked(context, event("text_done", "request_id", requestId, "text", finalAnswer));
            logService.markModelDone(context.activeLogId, modelElapsedMs, firstTokenMs, finalAnswer);
        }
    }

    private void handleOmniAudio(DeviceContext context, String requestId, byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId)) {
                return;
            }
            if (context.ttsFirstAudioAt == 0L) {
                Map<String, Object> start = event("audio_start", "request_id", requestId);
                start.put("format", "pcm_s16le");
                start.put("sample_rate", Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE);
                start.put("channels", 1);
                sendJsonLocked(context, start);
                context.ttsFirstAudioAt = System.nanoTime();
                setStateLocked(context, DeviceState.SPEAKING, requestId, "AI 正在回答");
                logService.markTtsFirstAudio(context.activeLogId,
                        TimeUnit.NANOSECONDS.toMillis(context.ttsFirstAudioAt - context.turnStartedAt));
            }
            sendPacedAudioLocked(context, pcm);
        }
    }

    private void handleOmniResponseDone(DeviceContext context, String requestId) {
        boolean completedWithAudio = false;
        synchronized (context.lock) {
            if (!requestId.equals(context.activeRequestId)
                    || context.interruptedRequests.contains(requestId)) {
                return;
            }
            if (!context.realtimeModelDone) {
                handleOmniTextDone(context, requestId, context.realtimeAnswer.toString(), 0L, 0L);
            }
            if (context.ttsFirstAudioAt == 0L) {
                logService.markFailed(context.activeLogId, "MODEL_AUDIO_EMPTY", "实时模型没有返回音频",
                        elapsedMillis(context.turnStartedAt));
                sendErrorLocked(context, "MODEL_AUDIO_EMPTY", "没有收到语音回答，请再试一次", true, requestId);
                sendJsonLocked(context, event("turn_done", "request_id", requestId));
                context.activeRequestId = "";
                context.activeLogId = null;
                setStateLocked(context, DeviceState.LISTENING, requestId, "请再说一次");
            } else {
                sendJsonLocked(context, event("audio_done", "request_id", requestId));
                sendJsonLocked(context, event("turn_done", "request_id", requestId));
                long totalMs = elapsedMillis(context.turnStartedAt);
                logService.markCompleted(context.activeLogId, totalMs,
                        elapsedMillis(context.ttsFirstAudioAt));
                context.activeLogId = null;
                setStateLocked(context, DeviceState.COOLDOWN, requestId, "即将恢复聆听");
                completedWithAudio = true;
            }
        }
        scheduler.schedule(() -> finishRealtimeTranscript(context, requestId, false),
                15, TimeUnit.SECONDS);
        if (!completedWithAudio) {
            return;
        }
        scheduler.schedule(() -> {
            synchronized (context.lock) {
                if (context.state != DeviceState.COOLDOWN
                        || !requestId.equals(context.activeRequestId)) {
                    return;
                }
                context.activeRequestId = "";
                setStateLocked(context, DeviceState.LISTENING, requestId, "可以提问了");
            }
        }, Math.max(0, properties.getCooldownMillis()), TimeUnit.MILLISECONDS);
    }

    /** 结束仍在等待的旁路转写；该状态不改变实时模型回答是否成功。 */
    private void finishRealtimeTranscript(DeviceContext context, String requestId, boolean skipped) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        Long pendingLogId = context.realtimeLogIds.remove(requestId);
        if (pendingLogId == null) {
            return;
        }
        if (skipped) {
            logService.markRealtimeTranscriptSkipped(pendingLogId);
        } else {
            logService.markRealtimeTranscriptUnavailable(pendingLogId);
        }
    }

    static String normalizeCancelReason(String reason) {
        return "wake_no_speech".equals(reason) || "no_speech_timeout".equals(reason)
                ? reason : "capture_cancelled";
    }

    private void interrupt(DeviceContext context, String requestedId) {
        synchronized (context.lock) {
            String fallbackRequestId = !context.activeRequestId.isEmpty()
                    ? context.activeRequestId : context.turn == null ? "" : context.turn.requestId;
            String requestId = requestedId == null || requestedId.trim().isEmpty()
                    ? fallbackRequestId : requestedId.trim();
            if (!requestId.isEmpty()) {
                context.interruptedRequests.add(requestId);
                if (context.modelSession != null) {
                    context.modelSession.send(event("interrupt", "request_id", requestId));
                }
                if (context.omniSession != null) {
                    context.omniSession.interrupt();
                }
                finishRealtimeTranscript(context, requestId, true);
            }
            closeTtsLocked(context);
            closeAsrLocked(context);
            Long interruptedLogId = context.activeLogId != null ? context.activeLogId
                    : context.turn == null ? null : context.turn.logId;
            long interruptedStartedAt = context.turnStartedAt > 0 ? context.turnStartedAt
                    : context.turn == null ? 0L : context.turn.startedAt;
            logService.markInterrupted(interruptedLogId, interruptedStartedAt > 0
                    ? elapsedMillis(interruptedStartedAt) : null);
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

    private void recordDeviceTurnMetrics(DeviceContext context, JsonNode event) {
        String requestId = event.path("request_id").asText("");
        Long firstAudioReceivedMs = optionalNonNegativeLong(event, "first_audio_received_ms");
        Long firstPlaybackMs = optionalNonNegativeLong(event, "first_i2s_write_ms");
        synchronized (context.lock) {
            Long logId = context.deviceMetricLogIds.get(requestId);
            if (requestId.isEmpty() || logId == null) {
                return;
            }
            logService.markDevicePlaybackMetrics(logId,
                    firstAudioReceivedMs, firstPlaybackMs);
        }
    }

    private void ping(DeviceContext context, JsonNode event) {
        Map<String, Object> ping = new LinkedHashMap<>();
        ping.put("type", "ping");
        if (event.has("timestamp")) {
            ping.put("timestamp", event.get("timestamp").asLong());
        }
        if (properties.isRealtimeProtocol()) {
            ping.put("type", "pong");
            synchronized (context.lock) {
                sendJsonLocked(context, ping);
            }
            return;
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
            for (Long pendingLogId : context.realtimeLogIds.values()) {
                logService.markRealtimeTranscriptUnavailable(pendingLogId);
            }
            context.realtimeLogIds.clear();
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
        context.ttsAnnounced = false;
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

    /**
     * 提交给模型前把图片缩到长边上限。
     *
     * <p>视觉 token 数与像素正相关：缩小图片同时降低模型首 token 延迟和每轮成本。
     * 落库保存的仍是设备原始 JPEG，只有模型入参会降采样。</p>
     */
    private byte[] downscaleForModel(byte[] jpeg) {
        int maxEdge = properties.getModelImageMaxEdge();
        if (maxEdge <= 0 || jpeg.length == 0) {
            return jpeg;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (source == null) {
                return jpeg;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            int longest = Math.max(width, height);
            if (longest <= maxEdge) {
                return jpeg;
            }
            double scale = (double) maxEdge / longest;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage scaled = new BufferedImage(
                    targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(scaled, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] result = output.toByteArray();
            log.info("[ESP32_IMAGE_DOWNSCALED] {}x{} -> {}x{} bytes={} -> {}",
                    width, height, targetWidth, targetHeight, jpeg.length, result.length);
            return result.length > 0 ? result : jpeg;
        } catch (Exception exception) {
            log.warn("[ESP32_IMAGE_DOWNSCALE_FAILED] bytes={}", jpeg.length, exception);
            return jpeg;
        }
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

    /** 重置本轮音频下发节奏状态（领先量从下一次发送重新计时）。 */
    private void resetAudioPacing(DeviceContext context) {
        context.audioStartedAt = 0L;
        context.audioLastSendAt = 0L;
        context.audioSentMillis = 0L;
    }

    /**
     * 按「播放缓冲领先量」下发音频，而不是简单按实时速率下发。
     *
     * <p>设备端播放缓冲 ≈ 已发送音频时长 − 已流逝时间。旧的固定 90% 实时速率下，这个领先量
     * 每块只增长约 4ms：前十几秒缓冲几乎为空，任何 Wi-Fi 抖动或唤醒延迟都会直接变成播报断续。
     * 现在先以最快 {@code playback-pace-percent} 的速率把缓冲填到
     * {@code output-audio-lead-millis}，之后维持该领先量，兼顾流畅与设备内存。</p>
     */
    private void sendPacedAudioLocked(DeviceContext context, byte[] audio) {
        if (!context.session.isOpen()) {
            return;
        }
        int configuredChunk = Math.max(2, properties.getOutputAudioChunkBytes());
        int chunkBytes = configuredChunk - configuredChunk % 2;
        long leadTargetMillis = Math.max(0L, properties.getOutputAudioLeadMillis());
        long speedPercent = Math.max(100L, properties.getPlaybackPacePercent());
        long now = System.nanoTime();
        if (context.audioStartedAt == 0L) {
            context.audioStartedAt = now;
            context.audioLastSendAt = now;
            context.audioSentMillis = 0L;
        }
        try {
            for (int offset = 0; offset < audio.length; offset += chunkBytes) {
                int size = Math.min(chunkBytes, audio.length - offset);
                size -= size % 2;
                if (size <= 0) {
                    continue;
                }
                long chunkMillis = Math.max(1L, Math.round(size * 1000.0
                        / (Esp32ProtocolUtils.OUTPUT_SAMPLE_RATE * 2)));
                long minIntervalNanos = chunkMillis * 100_000L / speedPercent;
                waitForSendWindow(context, minIntervalNanos, leadTargetMillis);
                if (!context.session.isOpen()) {
                    return;
                }
                context.session.sendMessage(new BinaryMessage(
                        ByteBuffer.wrap(audio, offset, size), true));
                context.audioSentMillis += chunkMillis;
                context.audioLastSendAt = System.nanoTime();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            log.warn("[ESP32_SEND_AUDIO_FAILED] deviceId={} sessionId={}",
                    context.deviceId, context.session.getId(), exception);
        }
    }

    /** 同时满足「发送速率上限」与「缓冲领先量上限」两个条件的等待。 */
    private void waitForSendWindow(DeviceContext context, long minIntervalNanos, long leadTargetMillis)
            throws InterruptedException {
        while (true) {
            long current = System.nanoTime();
            long sinceLastSend = current - context.audioLastSendAt;
            long leadMillis = context.audioSentMillis
                    - TimeUnit.NANOSECONDS.toMillis(current - context.audioStartedAt);
            if (sinceLastSend >= minIntervalNanos && leadMillis < leadTargetMillis) {
                return;
            }
            long waitMillis;
            if (leadMillis >= leadTargetMillis) {
                waitMillis = leadMillis - leadTargetMillis;
            } else {
                waitMillis = TimeUnit.NANOSECONDS.toMillis(minIntervalNanos - sinceLastSend) + 1L;
            }
            Thread.sleep(Math.min(Math.max(1L, waitMillis), MAX_PACING_SLEEP_MILLIS));
            if (!context.session.isOpen()) {
                return;
            }
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

    /** Omni Realtime 单帧 Base64 最多 256 KB，原始 JPEG 留余量控制在 180 KiB 内。 */
    private byte[] prepareRealtimeImage(byte[] jpeg) {
        final int maxRawBytes = 180 * 1024;
        byte[] candidate = downscaleForModel(jpeg);
        if (candidate.length <= maxRawBytes) {
            return candidate;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(candidate));
            if (source == null) {
                return null;
            }
            int longest = Math.max(source.getWidth(), source.getHeight());
            for (int edge : new int[]{Math.min(longest, 640), 480, 320}) {
                if (edge <= 0 || edge > longest) {
                    continue;
                }
                double scale = (double) edge / longest;
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = scaled.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
                graphics.dispose();
                for (float quality : new float[]{0.75f, 0.60f, 0.45f}) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                        writer.setOutput(stream);
                        writer.write(null, new IIOImage(scaled, null, null), param);
                    } finally {
                        writer.dispose();
                    }
                    if (output.size() <= maxRawBytes) {
                        return output.toByteArray();
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("[ESP32_OMNI_IMAGE_COMPRESS_FAILED] bytes={}", jpeg.length, exception);
        }
        return null;
    }

    private static Map<String, Object> event(String type, String firstKey, Object firstValue,
                                              String secondKey, Object secondValue) {
        Map<String, Object> event = event(type, firstKey, firstValue);
        event.put(secondKey, secondValue);
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

    private static Long optionalNonNegativeLong(JsonNode event, String field) {
        if (event == null || !event.has(field) || !event.get(field).canConvertToLong()) {
            return null;
        }
        long value = event.get(field).asLong();
        return value < 0 ? null : value;
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
            closeAsrLocked(context);
            if (context.modelSession != null) {
                context.modelSession.close();
            }
            if (context.omniSession != null) {
                context.omniSession.close();
            }
            for (Long pendingLogId : context.realtimeLogIds.values()) {
                logService.markRealtimeTranscriptUnavailable(pendingLogId);
            }
            context.realtimeLogIds.clear();
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
                if (context.omniSession != null) {
                    context.omniSession.close();
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
        private final StringBuilder realtimeAnswer = new StringBuilder();
        private final Map<String, Long> realtimeLogIds = new ConcurrentHashMap<>();
        /** 设备首播遥测可能在模型完成事件之后到达，保留少量近期请求的日志映射。 */
        private final Map<String, Long> deviceMetricLogIds = new LinkedHashMap<>();
        private DeviceState state = DeviceState.CONNECTING;
        private TurnBuffer turn;
        private GuideModelClient.ModelSession modelSession;
        private OmniRealtimeClient.Session omniSession;
        private DoubaoTtsClient.TtsStream tts;
        private String ttsRequestId = "";
        private String activeRequestId = "";
        private boolean ttsFirstAudio;
        private boolean ttsHasText;
        private boolean realtimeModelDone;
        /** 本轮的 audio_start 是否已经发给设备。 */
        private boolean ttsAnnounced;
        private DoubaoAsrClient.AsrStream asrStream;
        private long turnStartedAt;
        private long ttsFirstAudioAt;
        /** 音频下发节奏控制：本轮流式音频的起始时间与已下发音频时长。 */
        private long audioStartedAt;
        private long audioLastSendAt;
        private long audioSentMillis;
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
        /** 本轮最后一次检测到语音（能量 ≥ 阈值）的时间，用于服务端静音自动提交。 */
        private long lastSpeechAtNanos = startedAt;
        /** 至少检测到过一次有效语音；纯静音永不由网关兜底抢先提交。 */
        private boolean speechDetected;
        /** 是否已经因为持续静音触发过自动提交，防止反复 commitTurn。 */
        private boolean silenceCommitted;
        /** 静音检查的延迟任务，收到新音频或结束采集时会重新挂或取消。 */
        private ScheduledFuture<?> silenceCheckFuture;

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
