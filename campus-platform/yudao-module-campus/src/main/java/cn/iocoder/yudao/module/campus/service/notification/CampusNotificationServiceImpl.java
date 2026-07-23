package cn.iocoder.yudao.module.campus.service.notification;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.notification.vo.CampusNotificationRespVO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusNotificationServiceImpl implements CampusNotificationService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CampusNotificationServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<CampusNotificationRespVO> getPage(Long userId, String type, Integer pageNo, Integer pageSize) {
        requireUserId(userId);
        String typeCondition = StrUtil.isBlank(type) ? "" : " AND n.type = :type";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
        if (StrUtil.isNotBlank(type))
            params.addValue("type", type);
        int safePageNo = Math.max(pageNo == null ? 1 : pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 50 : pageSize, 1), 100);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_notification n"
                + " WHERE n.user_id = :userId AND n.deleted = b'0'" + typeCondition, params, Long.class);
        params.addValue("offset", (safePageNo - 1) * safePageSize).addValue("pageSize", safePageSize);
        List<CampusNotificationRespVO> list = jdbcTemplate.queryForList(selectSql() + typeCondition
                        + " ORDER BY n.create_time DESC, n.id DESC LIMIT :offset, :pageSize", params)
                .stream().map(this::toResp).collect(Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    public Long getUnreadCount(Long userId) {
        requireUserId(userId);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_notification"
                + " WHERE user_id = :userId AND read_time IS NULL AND deleted = b'0'",
                new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, Long userId) {
        requireUserId(userId);
        int updated = jdbcTemplate.update("UPDATE campus_notification SET read_time = COALESCE(read_time, NOW()),"
                        + " updater = :operator, update_time = NOW() WHERE id = :id AND user_id = :userId"
                        + " AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", id).addValue("userId", userId)
                        .addValue("operator", String.valueOf(userId)));
        if (updated == 0)
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "通知不存在或无权操作");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        requireUserId(userId);
        jdbcTemplate.update("UPDATE campus_notification SET read_time = NOW(), updater = :operator,"
                        + " update_time = NOW() WHERE user_id = :userId AND read_time IS NULL AND deleted = b'0'",
                new MapSqlParameterSource().addValue("userId", userId).addValue("operator", String.valueOf(userId)));
    }

    @Override
    public void createInteraction(Long recipientUserId, Long tenantId, Long actorUserId, String actorNickname,
                                  String eventType, String title, String content, String targetType, Long targetId) {
        if (recipientUserId == null || actorUserId == null || recipientUserId.equals(actorUserId))
            return;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", recipientUserId).addValue("tenantId", tenantId)
                .addValue("actorUserId", actorUserId).addValue("actorNickname", StrUtil.blankToDefault(actorNickname, "校园同学"))
                .addValue("type", "INTERACTION").addValue("eventType", eventType)
                .addValue("title", title).addValue("content", content)
                .addValue("targetType", targetType).addValue("targetId", targetId)
                .addValue("operator", String.valueOf(actorUserId));
        jdbcTemplate.update("INSERT INTO campus_notification (user_id, tenant_id, actor_user_id, actor_nickname,"
                        + " type, event_type, title, content, target_type, target_id, read_time, creator, updater,"
                        + " create_time, update_time, deleted) VALUES (:userId, :tenantId, :actorUserId,"
                        + " :actorNickname, :type, :eventType, :title, :content, :targetType, :targetId, NULL,"
                        + " :operator, :operator, NOW(), NOW(), b'0')", params);
    }

    private String selectSql() {
        return "SELECT n.id, n.type, n.event_type, n.actor_nickname, n.title, n.content, n.create_time,"
                + " n.read_time, n.target_type, n.target_id FROM campus_notification n"
                + " WHERE n.user_id = :userId AND n.deleted = b'0'";
    }

    private CampusNotificationRespVO toResp(Map<String, Object> row) {
        CampusNotificationRespVO vo = new CampusNotificationRespVO();
        LocalDateTime createdAt = toLocalDateTime(row.get("create_time"));
        vo.setId(toLong(row.get("id"))); vo.setType(value(row, "type"));
        vo.setEventType(value(row, "event_type")); vo.setActorNickname(value(row, "actor_nickname"));
        vo.setTitle(value(row, "title")); vo.setContent(value(row, "content")); vo.setCreatedAt(createdAt);
        vo.setTime(relativeTime(createdAt)); vo.setRead(row.get("read_time") != null);
        vo.setTargetType(value(row, "target_type")); vo.setTargetId(toLongObject(row.get("target_id")));
        return vo;
    }

    private static void requireUserId(Long userId) {
        if (userId == null)
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "请先登录");
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Long toLongObject(Object value) {
        if (value == null) return null;
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static long toLong(Object value) {
        Long parsed = toLongObject(value);
        return parsed == null ? 0L : parsed;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }

    private static String relativeTime(LocalDateTime createdAt) {
        if (createdAt == null) return "刚刚";
        long minutes = Math.max(Duration.between(createdAt, LocalDateTime.now()).toMinutes(), 0);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        return days < 7 ? days + "天前" : createdAt.toLocalDate().toString();
    }
}
