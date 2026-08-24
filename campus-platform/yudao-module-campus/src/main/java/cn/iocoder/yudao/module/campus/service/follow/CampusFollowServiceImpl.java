package cn.iocoder.yudao.module.campus.service.follow;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.follow.vo.CampusFollowUserRespVO;
import cn.iocoder.yudao.module.campus.service.notification.CampusNotificationService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusFollowServiceImpl implements CampusFollowService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileApi fileApi;
    private final CampusNotificationService campusNotificationService;

    public CampusFollowServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi,
                                   CampusNotificationService campusNotificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
        this.campusNotificationService = campusNotificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setFollow(Long userId, Long targetUserId, boolean active) {
        requireUserId(userId);
        if (targetUserId == null || targetUserId <= 0)
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "被关注用户不存在");
        if (userId.equals(targetUserId))
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不能关注自己");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetUserId", targetUserId)
                .addValue("operator", String.valueOf(userId));
        if (active) {
            boolean alreadyFollowing = isFollowing(userId, targetUserId);
            Long targetTenantId = findActiveUserTenantId(targetUserId);
            if (targetTenantId == null)
                throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "被关注用户不存在");
            Long userTenantId = findActiveUserTenantId(userId);
            if (userTenantId == null)
                throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "当前账号不存在");
            if (!userTenantId.equals(targetTenantId))
                throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "只能关注同校用户");
            params.addValue("tenantId", userTenantId);
            jdbcTemplate.update("INSERT INTO campus_user_follow (user_id, follow_user_id, tenant_id, creator, updater,"
                            + " create_time, update_time, deleted) VALUES (:userId, :targetUserId, :tenantId, :operator,"
                            + " :operator, NOW(), NOW(), b'0') ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id),"
                            + " updater = VALUES(updater), update_time = NOW(), deleted = b'0'", params);
            if (!alreadyFollowing) {
                String actorNickname = findActiveUserNickname(userId);
                campusNotificationService.createInteraction(targetUserId, userTenantId, userId, actorNickname,
                        "FOLLOW", actorNickname + "关注了你", "关注了你", "SYSTEM", null);
            }
        } else {
            jdbcTemplate.update("UPDATE campus_user_follow SET deleted = b'1', updater = :operator, update_time = NOW()"
                    + " WHERE user_id = :userId AND follow_user_id = :targetUserId AND deleted = b'0'", params);
        }
        return active;
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        requireUserId(userId);
        if (targetUserId == null || targetUserId <= 0 || userId.equals(targetUserId))
            return false;
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_user_follow"
                        + " WHERE user_id = :userId AND follow_user_id = :targetUserId AND deleted = b'0'",
                new MapSqlParameterSource().addValue("userId", userId).addValue("targetUserId", targetUserId), Long.class);
        return count != null && count > 0;
    }

    @Override
    public long getFollowingCount(Long userId) {
        requireUserId(userId);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_user_follow f"
                        + " JOIN campus_miniapp_user u ON u.id = f.follow_user_id AND u.deleted = b'0'"
                        + " WHERE f.user_id = :userId AND f.deleted = b'0'",
                new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public PageResult<CampusFollowUserRespVO> getFollowingPage(Long userId, Integer pageNo, Integer pageSize) {
        requireUserId(userId);
        int safePageNo = Math.max(pageNo == null ? 1 : pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 50 : pageSize, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_user_follow f"
                + " JOIN campus_miniapp_user u ON u.id = f.follow_user_id AND u.deleted = b'0'"
                + " WHERE f.user_id = :userId AND f.deleted = b'0'", params, Long.class);
        params.addValue("offset", (safePageNo - 1) * safePageSize).addValue("pageSize", safePageSize);
        List<CampusFollowUserRespVO> list = jdbcTemplate.queryForList(
                        "SELECT f.follow_user_id AS user_id, u.nickname, u.avatar, u.school_name, u.campus_name,"
                                + " f.create_time, EXISTS(SELECT 1 FROM campus_user_follow reverse_follow"
                                + " WHERE reverse_follow.user_id = f.follow_user_id"
                                + " AND reverse_follow.follow_user_id = f.user_id AND reverse_follow.deleted = b'0') AS mutual"
                                + " FROM campus_user_follow f JOIN campus_miniapp_user u"
                                + " ON u.id = f.follow_user_id AND u.deleted = b'0'"
                                + " WHERE f.user_id = :userId AND f.deleted = b'0'"
                                + " ORDER BY f.create_time DESC, f.id DESC LIMIT :offset, :pageSize", params)
                .stream().map(this::toResp).collect(Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    private Long findActiveUserTenantId(Long userId) {
        List<Long> rows = jdbcTemplate.queryForList("SELECT tenant_id FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", userId), Long.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String findActiveUserNickname(Long userId) {
        List<String> rows = jdbcTemplate.queryForList("SELECT nickname FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", userId), String.class);
        return rows.isEmpty() ? "校园同学" : StrUtil.blankToDefault(rows.get(0), "校园同学");
    }

    private CampusFollowUserRespVO toResp(Map<String, Object> row) {
        CampusFollowUserRespVO vo = new CampusFollowUserRespVO();
        vo.setUserId(toLong(row.get("user_id")));
        vo.setNickname(StrUtil.blankToDefault(value(row, "nickname"), "校园同学"));
        vo.setAvatar(refreshFileUrl(value(row, "avatar")));
        vo.setSchoolName(value(row, "school_name"));
        vo.setCampusName(value(row, "campus_name"));
        vo.setMutual(toBoolean(row.get("mutual")));
        vo.setFollowedAt(toLocalDateTime(row.get("create_time")));
        return vo;
    }

    private String refreshFileUrl(String url) {
        if (StrUtil.isBlank(url))
            return "";
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

    private static long toLong(Object value) {
        if (value == null)
            return 0L;
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean)
            return (Boolean) value;
        if (value instanceof Number)
            return ((Number) value).intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp)
            return ((Timestamp) value).toLocalDateTime();
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }
}
