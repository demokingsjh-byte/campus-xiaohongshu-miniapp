package cn.iocoder.yudao.module.campus.service.esp32;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;

import java.util.Map;

public interface CampusEsp32LogService {

    Long startTurn(String sessionId, String deviceId, String clientIp, String requestId);

    void markSubmitted(Long logId, int audioBytes, int imageCount, long captureMs, long submitMs);

    void markAsr(Long logId, long asrMs, boolean success);

    void markModelDone(Long logId, Long modelTotalMs, Long modelFirstTokenMs);

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
