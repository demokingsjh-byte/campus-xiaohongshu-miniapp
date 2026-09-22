package cn.iocoder.yudao.module.campus.service.esp32;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * ESP32 链路耗时日志。日志表未升级时降级为空操作，避免影响设备对话链路。
 */
@Slf4j
@Service
public class CampusEsp32LogServiceImpl implements CampusEsp32LogService {

    private static final String TABLE = "campus_esp32_assistant_log";
    private static final AtomicBoolean PERSISTENCE_WARNING_LOGGED = new AtomicBoolean();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CampusEsp32LogServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long startTurn(String sessionId, String deviceId, String clientIp, String requestId) {
        return safe(() -> {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("sessionId", limit(sessionId, 64))
                    .addValue("deviceId", limit(deviceId, 64))
                    .addValue("clientIp", limit(clientIp, 64))
                    .addValue("requestId", limit(requestId, 100));
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update("INSERT INTO " + TABLE
                            + " (session_id, device_id, request_id, client_ip, status, content_recorded,"
                            + " asr_status, question_text, answer_text, creator, updater, tenant_id)"
                            + " VALUES (:sessionId, :deviceId, :requestId, :clientIp, 'CAPTURING', b'1',"
                            + " 'PENDING', NULL, NULL, '', '', 0)"
                            + " ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), status = 'CAPTURING',"
                            + " audio_bytes = 0, image_count = 0, capture_ms = NULL, submit_ms = NULL,"
                            + " asr_ms = NULL, model_first_token_ms = NULL, model_total_ms = NULL,"
                            + " tts_first_audio_ms = NULL, tts_audio_ms = NULL, total_ms = NULL,"
                            + " error_code = NULL, error_message = NULL, content_recorded = b'1',"
                            + " asr_status = 'PENDING', question_text = NULL, answer_text = NULL,"
                            + " update_time = NOW(), deleted = b'0'",
                    params, keyHolder, new String[]{"id"});
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        }, null);
    }

    @Override
    public void markSubmitted(Long logId, int audioBytes, int imageCount, long captureMs, long submitMs) {
        update(logId, "status = 'SUBMITTED', audio_bytes = :audioBytes, image_count = :imageCount,"
                        + " capture_ms = :captureMs, submit_ms = :submitMs",
                new MapSqlParameterSource("audioBytes", Math.max(0, audioBytes))
                        .addValue("imageCount", Math.max(0, imageCount))
                        .addValue("captureMs", nonNegative(captureMs))
                        .addValue("submitMs", nonNegative(submitMs)));
    }

    @Override
    public void markAsr(Long logId, long asrMs, boolean success) {
        markAsr(logId, asrMs, success, null);
    }

    @Override
    public void markAsr(Long logId, long asrMs, boolean success, String questionText) {
        if (success) {
            update(logId, "asr_ms = :asrMs, asr_status = 'SUCCESS', content_recorded = b'1',"
                            + " question_text = :questionText",
                    new MapSqlParameterSource("asrMs", nonNegative(asrMs))
                            .addValue("questionText", limitText(questionText)));
        } else {
            update(logId, "asr_ms = :asrMs, asr_status = 'FAILED', content_recorded = b'1',"
                            + " error_code = COALESCE(error_code, 'ASR_FAILED')",
                    new MapSqlParameterSource("asrMs", nonNegative(asrMs)));
        }
    }

    @Override
    public void markAsrDisabled(Long logId) {
        update(logId, "asr_status = 'DISABLED', content_recorded = b'1'",
                new MapSqlParameterSource());
    }

    @Override
    public void markModelDone(Long logId, Long modelTotalMs, Long modelFirstTokenMs) {
        markModelDone(logId, modelTotalMs, modelFirstTokenMs, null);
    }

    @Override
    public void markModelDone(Long logId, Long modelTotalMs, Long modelFirstTokenMs, String answerText) {
        update(logId, "status = 'MODEL_DONE', model_total_ms = :modelTotalMs,"
                        + " model_first_token_ms = :modelFirstTokenMs, content_recorded = b'1',"
                        + " answer_text = :answerText",
                new MapSqlParameterSource("modelTotalMs", nullableNonNegative(modelTotalMs))
                        .addValue("modelFirstTokenMs", nullableNonNegative(modelFirstTokenMs))
                        .addValue("answerText", limitText(answerText)));
    }

    @Override
    public void saveImages(Long logId, List<byte[]> images) {
        if (logId == null || images == null || images.isEmpty()) {
            return;
        }
        safe(() -> {
            int imageIndex = 0;
            for (byte[] image : images) {
                if (image == null || image.length == 0) {
                    imageIndex++;
                    continue;
                }
                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("logId", logId)
                        .addValue("imageIndex", imageIndex)
                        .addValue("mimeType", "image/jpeg")
                        .addValue("sizeBytes", image.length)
                        .addValue("imageData", image);
                jdbcTemplate.update("INSERT INTO campus_esp32_assistant_log_image"
                                + " (log_id, image_index, mime_type, size_bytes, image_data, creator, updater, tenant_id)"
                                + " VALUES (:logId, :imageIndex, :mimeType, :sizeBytes, :imageData, '', '', 0)"
                                + " ON DUPLICATE KEY UPDATE mime_type = :mimeType, size_bytes = :sizeBytes,"
                                + " image_data = :imageData, updater = '', update_time = NOW(), deleted = b'0'",
                        params);
                imageIndex++;
            }
            update(logId, "content_recorded = b'1'", new MapSqlParameterSource());
            return null;
        });
    }

    @Override
    public byte[] getImage(Long imageId) {
        if (imageId == null) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "ESP32 日志图片不存在");
        }
        byte[] image = safe(() -> jdbcTemplate.queryForObject(
                "SELECT image_data FROM campus_esp32_assistant_log_image"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", imageId),
                (resultSet, rowNum) -> resultSet.getBytes("image_data")));
        if (image == null || image.length == 0) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "ESP32 日志图片不存在");
        }
        return image;
    }

    @Override
    public void markTtsFirstAudio(Long logId, long elapsedMs) {
        update(logId, "status = 'SPEAKING', tts_first_audio_ms = :elapsedMs",
                new MapSqlParameterSource("elapsedMs", nonNegative(elapsedMs)));
    }

    @Override
    public void markCompleted(Long logId, long totalMs, Long ttsAudioMs) {
        update(logId, "status = 'COMPLETED', total_ms = :totalMs, tts_audio_ms = :ttsAudioMs",
                new MapSqlParameterSource("totalMs", nonNegative(totalMs))
                        .addValue("ttsAudioMs", nullableNonNegative(ttsAudioMs)));
    }

    @Override
    public void markIgnored(Long logId, int audioBytes, int imageCount, String reason) {
        update(logId, "status = 'IGNORED', asr_status = 'SKIPPED',"
                        + " audio_bytes = :audioBytes, image_count = :imageCount,"
                        + " error_code = :errorCode, error_message = :errorMessage",
                new MapSqlParameterSource("audioBytes", Math.max(0, audioBytes))
                        .addValue("imageCount", Math.max(0, imageCount))
                        .addValue("errorCode", limit(reason, 64))
                        .addValue("errorMessage", limit(reason, 255)));
    }

    @Override
    public void markInterrupted(Long logId, Long totalMs) {
        update(logId, "status = 'INTERRUPTED', error_code = 'INTERRUPTED', total_ms = :totalMs",
                new MapSqlParameterSource("totalMs", nullableNonNegative(totalMs)));
    }

    @Override
    public void markFailed(Long logId, String code, String message, Long totalMs) {
        update(logId, "status = 'FAILED', error_code = :errorCode, error_message = :errorMessage,"
                        + " total_ms = :totalMs",
                new MapSqlParameterSource("errorCode", limit(code, 64))
                        .addValue("errorMessage", limit(message, 255))
                        .addValue("totalMs", nullableNonNegative(totalMs)));
    }

    @Override
    public void markDisconnected(Long logId, Long totalMs) {
        update(logId, "status = CASE WHEN status = 'FAILED' THEN status ELSE 'DISCONNECTED' END,"
                        + " error_code = CASE WHEN status = 'FAILED' THEN error_code ELSE 'DISCONNECTED' END,"
                        + " total_ms = :totalMs",
                new MapSqlParameterSource("totalMs", nullableNonNegative(totalMs)));
    }

    @Override
    public PageResult<Map<String, Object>> getPage(CampusEsp32LogPageReqVO reqVO) {
        int pageNo = Math.max(reqVO.getPageNo() == null ? 1 : reqVO.getPageNo(), 1);
        int pageSize = Math.min(Math.max(reqVO.getPageSize() == null ? 20 : reqVO.getPageSize(), 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", (pageNo - 1) * pageSize)
                .addValue("pageSize", pageSize);
        String where = buildWhere(reqVO, params);
        Long total = safe(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + where, params, Long.class), 0L);
        List<Map<String, Object>> list = safe(() -> jdbcTemplate.queryForList(selectSql() + where
                + " ORDER BY id DESC LIMIT :offset, :pageSize", params), Collections.emptyList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = safe(() -> jdbcTemplate.queryForList(selectDetailSql()
                + " WHERE id = :id AND deleted = b'0' LIMIT 1", new MapSqlParameterSource("id", id)));
        if (rows == null || rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "ESP32 链路日志不存在");
        }
        Map<String, Object> detail = rows.get(0);
        List<Map<String, Object>> images = safe(() -> jdbcTemplate.queryForList(
                "SELECT id, image_index AS imageIndex, size_bytes AS sizeBytes, mime_type AS mimeType"
                        + " FROM campus_esp32_assistant_log_image"
                        + " WHERE log_id = :logId AND deleted = b'0' ORDER BY image_index ASC",
                new MapSqlParameterSource("logId", id)), Collections.emptyList());
        detail.put("images", images);
        return detail;
    }

    @Override
    public Map<String, Object> getSummary(CampusEsp32LogPageReqVO reqVO) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(reqVO, params);
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("totalCount", 0);
        fallback.put("completedCount", 0);
        fallback.put("failedCount", 0);
        return safe(() -> jdbcTemplate.queryForMap("SELECT COUNT(*) AS totalCount,"
                + " SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,"
                + " SUM(CASE WHEN status IN ('FAILED', 'DISCONNECTED', 'INTERRUPTED') THEN 1 ELSE 0 END) AS failedCount,"
                + " ROUND(AVG(total_ms), 0) AS averageTotalMs,"
                + " ROUND(AVG(tts_first_audio_ms), 0) AS averageFirstAudioMs,"
                + " ROUND(AVG(CASE WHEN capture_ms IS NOT NULL AND tts_first_audio_ms IS NOT NULL"
                + " THEN GREATEST(tts_first_audio_ms - capture_ms, 0) ELSE NULL END), 0)"
                + " AS averageCommitToFirstAudioMs,"
                + " ROUND(AVG(model_total_ms), 0) AS averageModelMs, MAX(create_time) AS lastTime"
                + " FROM " + TABLE + where, params), fallback);
    }

    private String buildWhere(CampusEsp32LogPageReqVO reqVO, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE deleted = b'0'");
        if (StrUtil.isNotBlank(reqVO.getDeviceId())) {
            where.append(" AND device_id LIKE :deviceId");
            params.addValue("deviceId", "%" + reqVO.getDeviceId().trim() + "%");
        }
        if (StrUtil.isNotBlank(reqVO.getRequestId())) {
            where.append(" AND request_id LIKE :requestId");
            params.addValue("requestId", "%" + reqVO.getRequestId().trim() + "%");
        }
        if (StrUtil.isNotBlank(reqVO.getStatus())) {
            where.append(" AND status = :status");
            params.addValue("status", reqVO.getStatus().trim().toUpperCase());
        }
        if (reqVO.getCreateTimeStart() != null) {
            where.append(" AND create_time >= :createTimeStart");
            params.addValue("createTimeStart", reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            where.append(" AND create_time <= :createTimeEnd");
            params.addValue("createTimeEnd", reqVO.getCreateTimeEnd());
        }
        return where.toString();
    }

    private String selectSql() {
        return selectSql("LEFT(question_text, 160)", "LEFT(answer_text, 160)");
    }

    private String selectDetailSql() {
        return selectSql("question_text", "answer_text");
    }

    private String selectSql(String questionExpression, String answerExpression) {
        return "SELECT id, session_id AS sessionId, device_id AS deviceId, request_id AS requestId,"
                + " client_ip AS clientIp, status, audio_bytes AS audioBytes, image_count AS imageCount,"
                + " IF(content_recorded = b'1', TRUE, FALSE) AS contentRecorded,"
                + " COALESCE((SELECT COUNT(*) FROM campus_esp32_assistant_log_image li"
                + " WHERE li.log_id = " + TABLE + ".id AND li.deleted = b'0'), 0) AS storedImageCount,"
                + " asr_status AS asrStatus, " + questionExpression + " AS questionText,"
                + " " + answerExpression + " AS answerText,"
                + " capture_ms AS captureMs, submit_ms AS submitMs, asr_ms AS asrMs,"
                + " CASE WHEN capture_ms IS NOT NULL AND tts_first_audio_ms IS NOT NULL"
                + " THEN GREATEST(tts_first_audio_ms - capture_ms, 0) ELSE NULL END"
                + " AS commitToFirstAudioMs,"
                + " model_first_token_ms AS modelFirstTokenMs, model_total_ms AS modelTotalMs,"
                + " tts_first_audio_ms AS ttsFirstAudioMs, tts_audio_ms AS ttsAudioMs, total_ms AS totalMs,"
                + " error_code AS errorCode, error_message AS errorMessage, create_time AS createTime,"
                + " update_time AS updateTime FROM " + TABLE;
    }

    private void update(Long logId, String assignments, MapSqlParameterSource values) {
        if (logId == null) {
            return;
        }
        values.addValue("logId", logId);
        safe(() -> jdbcTemplate.update("UPDATE " + TABLE + " SET " + assignments
                + ", update_time = NOW() WHERE id = :logId AND deleted = b'0'", values), 0);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static Long nullableNonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String limitText(String value) {
        // MEDIUMTEXT can hold more, but an accidental unbounded upstream response should not
        // make a log row or the admin drawer unusable.
        return limit(value, 64 * 1024);
    }

    private <T> T safe(Work<T> work, T fallback) {
        try {
            return work.run();
        } catch (RuntimeException exception) {
            if (PERSISTENCE_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("ESP32 链路日志暂不可用，设备对话仍会继续；请执行 campus-esp32-log-upgrade.sql", exception);
            }
            return fallback;
        }
    }

    private <T> T safe(Work<T> work) {
        return safe(work, null);
    }

    @FunctionalInterface
    private interface Work<T> {
        T run();
    }
}
