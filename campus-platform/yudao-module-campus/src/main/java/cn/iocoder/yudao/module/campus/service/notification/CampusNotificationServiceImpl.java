package cn.iocoder.yudao.module.campus.service.notification;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.app.notification.vo.CampusNotificationRespVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final FileApi fileApi;

    public CampusNotificationServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
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
        // 本人给自己内容点赞也属于“我的获赞”，需要生成真实明细供数量和列表共同使用。
        // 其他本人触发的评论、回复等通知仍不重复提醒自己。
        boolean selfLike = recipientUserId != null && recipientUserId.equals(actorUserId)
                && ("LIKE".equals(eventType) || "COMMENT_LIKE".equals(eventType));
        if (recipientUserId == null || actorUserId == null
                || (recipientUserId.equals(actorUserId) && !selfLike))
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
        return "SELECT n.id, n.type, n.event_type, n.actor_nickname, u.avatar AS actor_avatar,"
                + " EXISTS(SELECT 1 FROM campus_user_follow f WHERE f.user_id = n.user_id"
                + " AND f.follow_user_id = n.actor_user_id AND f.deleted = b'0') AS mutual,"
                + " n.title, n.content, n.create_time, n.read_time, n.target_type, n.target_id,"
                + " p.images_json AS target_images"
                + " FROM campus_notification n LEFT JOIN campus_miniapp_user u"
                + " ON u.id = n.actor_user_id AND u.deleted = b'0'"
                + " LEFT JOIN campus_post p ON p.id = n.target_id"
                + " AND n.target_type IN ('POST', 'PRODUCT') AND p.deleted = b'0'"
                + " WHERE n.user_id = :userId AND n.deleted = b'0'";
    }

    private CampusNotificationRespVO toResp(Map<String, Object> row) {
        CampusNotificationRespVO vo = new CampusNotificationRespVO();
        LocalDateTime createdAt = toLocalDateTime(row.get("create_time"));
        vo.setId(toLong(row.get("id"))); vo.setType(value(row, "type"));
        vo.setEventType(value(row, "event_type")); vo.setActorNickname(value(row, "actor_nickname"));
        vo.setActorAvatar(refreshFileUrl(value(row, "actor_avatar")));
        vo.setMutual(toBoolean(row.get("mutual")));
        vo.setTitle(value(row, "title")); vo.setContent(value(row, "content")); vo.setCreatedAt(createdAt);
        vo.setTime(relativeTime(createdAt)); vo.setRead(row.get("read_time") != null);
        vo.setTargetType(value(row, "target_type")); vo.setTargetId(toLongObject(row.get("target_id")));
        vo.setTargetImage(refreshFileUrl(firstImage(value(row, "target_images"))));
        return vo;
    }

    private String firstImage(String imagesJson) {
        if (StrUtil.isBlank(imagesJson)) return "";
        List<String> images = JsonUtils.parseObjectQuietly(imagesJson, new TypeReference<List<String>>() { });
        return images == null || images.isEmpty() ? "" : StrUtil.blankToDefault(images.get(0), "");
    }

    private String refreshFileUrl(String url) {
        if (StrUtil.isBlank(url)) return "";
        try {
            return fileApi.presignGetUrl(url, null);
        } catch (RuntimeException ex) {
            return url;
        }
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

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
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
