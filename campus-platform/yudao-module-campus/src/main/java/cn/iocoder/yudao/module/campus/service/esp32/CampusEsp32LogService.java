package cn.iocoder.yudao.module.campus.service.esp32;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;

import java.util.List;
import java.util.Map;

public interface CampusEsp32LogService {

    Long startTurn(String sessionId, String deviceId, String clientIp, String requestId,
                   String pipelineMode, String modelName);

    void markSubmitted(Long logId, int audioBytes, int imageCount, long captureMs, long submitMs,
                       Long speechEndMs, Long speechEndToCommitMs);

    /** 设备实测的首包接收与首个 I2S 写入时刻（均相对本轮开始）。 */
    void markDevicePlaybackMetrics(Long logId, Long firstAudioReceivedMs, Long firstPlaybackMs);

    void markAsr(Long logId, long asrMs, boolean success);

    /**
     * 记录 ASR 结果。问题文本只写入受权限保护的后台日志，不通过设备协议回传。
     */
    void markAsr(Long logId, long asrMs, boolean success, String questionText);

    /** 原生实时模型的旁路转写；失败不影响已成功的回答。 */
    void markRealtimeTranscript(Long logId, long asrMs, String questionText);

    /** 转写事件长时间未返回时结束日志等待，不改变回答状态。 */
    void markRealtimeTranscriptUnavailable(Long logId);

    /** 轮次被取消或中断时停止等待旁路转写。 */
    void markRealtimeTranscriptSkipped(Long logId);

    void markAsrDisabled(Long logId);

    void markModelDone(Long logId, Long modelTotalMs, Long modelFirstTokenMs);

    /** 记录模型最终回答文本，保留完整内容供后台排查。 */
    void markModelDone(Long logId, Long modelTotalMs, Long modelFirstTokenMs, String answerText);

    /** 异步保存本轮上传的 JPEG 图片，避免阻塞设备回答。 */
    void saveImages(Long logId, List<byte[]> images);

    byte[] getImage(Long imageId);

    void markTtsFirstAudio(Long logId, long elapsedMs);

    void markCompleted(Long logId, long totalMs, Long ttsAudioMs);

    void markIgnored(Long logId, int audioBytes, int imageCount, String reason);

    void markInterrupted(Long logId, Long totalMs);

    void markFailed(Long logId, String code, String message, Long totalMs);

    void markDisconnected(Long logId, Long totalMs);

    PageResult<Map<String, Object>> getPage(CampusEsp32LogPageReqVO reqVO);

    Map<String, Object> get(Long id);

    Map<String, Object> getSummary(CampusEsp32LogPageReqVO reqVO);
}
