package cn.iocoder.yudao.module.campus.service.esp32;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;

import java.util.List;
import java.util.Map;

public interface CampusEsp32LogService {

    Long startTurn(String sessionId, String deviceId, String clientIp, String requestId);

    void markSubmitted(Long logId, int audioBytes, int imageCount, long captureMs, long submitMs);

    void markAsr(Long logId, long asrMs, boolean success);

    /**
     * 记录 ASR 结果。问题文本只写入受权限保护的后台日志，不通过设备协议回传。
     */
    void markAsr(Long logId, long asrMs, boolean success, String questionText);

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
