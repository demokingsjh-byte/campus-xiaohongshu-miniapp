package cn.iocoder.yudao.module.campus.service.analytics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsCampusRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsOverviewRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsRankRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsRecentEventRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsSummaryRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsTrendRespVO;
import cn.iocoder.yudao.module.campus.controller.app.analytics.vo.CampusAnalyticsTrackReqVO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusAnalyticsServiceImpl implements CampusAnalyticsService {

    private static final int ONLINE_WINDOW_MINUTES = 5;
    private static final int MAX_PROPERTIES_LENGTH = 8000;
    private static final String HEARTBEAT_EVENT = "HEARTBEAT";
    private static final String APP_HIDE_EVENT = "APP_HIDE";

    @Resource
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void track(Long userId, CampusAnalyticsTrackReqVO reqVO) {
        Long tenantId = getUserTenantId(userId);
        String eventName = reqVO.getEventName().trim().toUpperCase();
        String pagePath = trim(reqVO.getPagePath());
        String clientVersion = trim(reqVO.getClientVersion());
        String sourceScene = trim(reqVO.getSourceScene());
        boolean online = !APP_HIDE_EVENT.equals(eventName);

        MapSqlParameterSource sessionParams = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("sessionId", reqVO.getSessionId().trim())
                .addValue("pagePath", pagePath)
                .addValue("sourceScene", sourceScene)
                .addValue("clientVersion", clientVersion)
                .addValue("online", online)
                .addValue("endedAt", online ? null : LocalDateTime.now())
                .addValue("tenantId", tenantId);
        jdbcTemplate.update("INSERT INTO campus_analytics_session"
                + " (user_id, session_id, entry_page, last_page, source_scene, client_version, online_status,"
                + " first_seen_at, last_seen_at, ended_at, creator, updater, tenant_id)"
                + " VALUES (:userId, :sessionId, :pagePath, :pagePath, :sourceScene, :clientVersion, :online,"
                + " NOW(), NOW(), :endedAt, '', '', :tenantId)"
                + " ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), last_page = VALUES(last_page),"
                + " source_scene = VALUES(source_scene), client_version = VALUES(client_version),"
                + " online_status = VALUES(online_status), last_seen_at = NOW(), ended_at = VALUES(ended_at),"
                + " update_time = NOW(), deleted = b'0'", sessionParams);

        // 心跳只更新在线会话，避免事件明细表被每分钟心跳淹没。
        if (HEARTBEAT_EVENT.equals(eventName)) {
            return;
        }
        String propertiesJson = JsonUtils.toJsonString(
                reqVO.getProperties() == null ? Collections.emptyMap() : reqVO.getProperties());
        if (propertiesJson.length() > MAX_PROPERTIES_LENGTH) {
            propertiesJson = "{\"discarded\":\"payload_too_large\"}";
        }
        jdbcTemplate.update("INSERT INTO campus_analytics_event"
                        + " (user_id, session_id, event_name, page_path, properties_json, client_time,"
                        + " client_version, source_scene, creator, updater, tenant_id)"
                        + " VALUES (:userId, :sessionId, :eventName, :pagePath, :propertiesJson, :clientTime,"
                        + " :clientVersion, :sourceScene, '', '', :tenantId)",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("sessionId", reqVO.getSessionId().trim())
                        .addValue("eventName", eventName)
                        .addValue("pagePath", pagePath)
                        .addValue("propertiesJson", propertiesJson)
                        .addValue("clientTime", reqVO.getClientTime())
                        .addValue("clientVersion", clientVersion)
                        .addValue("sourceScene", sourceScene)
                        .addValue("tenantId", tenantId));
    }

    @Override
    public CampusAnalyticsOverviewRespVO getOverview(Long tenantId, Integer days) {
        int trendDays = Math.max(7, Math.min(days == null ? 7 : days, 30));
        LocalDate today = LocalDate.now();
        LocalDate trendStart = today.minusDays(trendDays - 1L);

        CampusAnalyticsOverviewRespVO result = new CampusAnalyticsOverviewRespVO();
        result.setToday(querySummary(today, tenantId, true));
        result.setYesterday(querySummary(today.minusDays(1), tenantId, false));
        result.setTrend(queryTrend(trendStart, today.plusDays(1), tenantId));
        result.setTopEvents(queryRank("event_name", today.atStartOfDay(), today.plusDays(1).atStartOfDay(), tenantId));
        result.setTopPages(queryRank("page_path", today.atStartOfDay(), today.plusDays(1).atStartOfDay(), tenantId));
        result.setRecentEvents(queryRecentEvents(tenantId));
        result.setCampuses(queryCampuses());
        result.setOnlineWindowMinutes(ONLINE_WINDOW_MINUTES);
        result.setServerTime(LocalDateTime.now());
        return result;
    }

    private Long getUserTenantId(Long userId) {
        if (userId == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "登录后才能上报小程序数据");
        }
        List<Long> tenantIds = jdbcTemplate.queryForList(
                "SELECT tenant_id FROM campus_miniapp_user WHERE id = :userId AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("userId", userId), Long.class);
        if (tenantIds.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "校园用户不存在");
        }
        return tenantIds.get(0);
    }

    private CampusAnalyticsSummaryRespVO querySummary(LocalDate date, Long tenantId, boolean includeOnline) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        MapSqlParameterSource params = periodParams(start, end, tenantId);
        String tenantClause = tenantClause(tenantId, "tenant_id");

        CampusAnalyticsSummaryRespVO summary = new CampusAnalyticsSummaryRespVO();
        summary.setTotalUsers(queryLong("SELECT COUNT(*) FROM campus_miniapp_user"
                + " WHERE deleted = b'0' AND create_time < :end" + tenantClause, params));
        summary.setRegisteredUsers(queryLong("SELECT COUNT(*) FROM campus_miniapp_user"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause, params));
        summary.setActiveUsers(queryLong("SELECT COUNT(DISTINCT user_id) FROM campus_analytics_event"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause, params));
        summary.setEventCount(queryLong("SELECT COUNT(*) FROM campus_analytics_event"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause, params));
        summary.setPostCount(queryLong("SELECT COUNT(*) FROM campus_post"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause, params));
        summary.setOrderCount(queryLong("SELECT COUNT(*) FROM campus_trade_order"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause, params));
        summary.setPaidOrderCount(queryLong("SELECT COUNT(*) FROM campus_trade_order"
                + " WHERE deleted = b'0' AND status IN (1, 2) AND paid_at >= :start AND paid_at < :end"
                + tenantClause, params));
        summary.setRevenueAmount(queryDecimal("SELECT COALESCE(SUM(amount), 0) FROM campus_trade_order"
                + " WHERE deleted = b'0' AND status IN (1, 2) AND paid_at >= :start AND paid_at < :end"
                + tenantClause, params));
        if (includeOnline) {
            summary.setOnlineUsers(queryLong("SELECT COUNT(DISTINCT user_id) FROM campus_analytics_session"
                    + " WHERE deleted = b'0' AND online_status = b'1'"
                    + " AND last_seen_at >= DATE_SUB(NOW(), INTERVAL " + ONLINE_WINDOW_MINUTES + " MINUTE)"
                    + tenantClause, params));
        } else {
            summary.setOnlineUsers(0L);
        }
        return summary;
    }

    private List<CampusAnalyticsTrendRespVO> queryTrend(LocalDate startDate, LocalDate endDate, Long tenantId) {
        Map<LocalDate, CampusAnalyticsTrendRespVO> trend = new LinkedHashMap<>();
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            CampusAnalyticsTrendRespVO item = new CampusAnalyticsTrendRespVO();
            item.setDate(date.toString());
            trend.put(date, item);
        }
        MapSqlParameterSource params = periodParams(startDate.atStartOfDay(), endDate.atStartOfDay(), tenantId);
        String tenantClause = tenantClause(tenantId, "tenant_id");

        applyCountTrend(trend, "SELECT DATE(create_time) stat_date, COUNT(*) metric FROM campus_miniapp_user"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause
                + " GROUP BY DATE(create_time)", params, "registered");
        applyEventTrend(trend, "SELECT DATE(create_time) stat_date, COUNT(DISTINCT user_id) active_users,"
                + " COUNT(*) event_count FROM campus_analytics_event WHERE deleted = b'0'"
                + " AND create_time >= :start AND create_time < :end" + tenantClause
                + " GROUP BY DATE(create_time)", params);
        applyCountTrend(trend, "SELECT DATE(create_time) stat_date, COUNT(*) metric FROM campus_post"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause
                + " GROUP BY DATE(create_time)", params, "post");
        applyCountTrend(trend, "SELECT DATE(create_time) stat_date, COUNT(*) metric FROM campus_trade_order"
                + " WHERE deleted = b'0' AND create_time >= :start AND create_time < :end" + tenantClause
                + " GROUP BY DATE(create_time)", params, "order");
        applyPaidTrend(trend, "SELECT DATE(paid_at) stat_date, COUNT(*) paid_count, COALESCE(SUM(amount), 0) revenue"
                + " FROM campus_trade_order WHERE deleted = b'0' AND status IN (1, 2)"
                + " AND paid_at >= :start AND paid_at < :end" + tenantClause
                + " GROUP BY DATE(paid_at)", params);
        return new ArrayList<>(trend.values());
    }

    private void applyCountTrend(Map<LocalDate, CampusAnalyticsTrendRespVO> trend, String sql,
                                 MapSqlParameterSource params, String metric) {
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params)) {
            CampusAnalyticsTrendRespVO item = trend.get(toLocalDate(row.get("stat_date")));
            if (item == null) {
                continue;
            }
            long value = toLong(row.get("metric"));
            if ("registered".equals(metric)) {
                item.setRegisteredUsers(value);
            } else if ("post".equals(metric)) {
                item.setPostCount(value);
            } else if ("order".equals(metric)) {
                item.setOrderCount(value);
            }
        }
    }

    private void applyEventTrend(Map<LocalDate, CampusAnalyticsTrendRespVO> trend, String sql,
                                 MapSqlParameterSource params) {
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params)) {
            CampusAnalyticsTrendRespVO item = trend.get(toLocalDate(row.get("stat_date")));
            if (item != null) {
                item.setActiveUsers(toLong(row.get("active_users")));
                item.setEventCount(toLong(row.get("event_count")));
            }
        }
    }

    private void applyPaidTrend(Map<LocalDate, CampusAnalyticsTrendRespVO> trend, String sql,
                                MapSqlParameterSource params) {
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params)) {
            CampusAnalyticsTrendRespVO item = trend.get(toLocalDate(row.get("stat_date")));
            if (item != null) {
                item.setPaidOrderCount(toLong(row.get("paid_count")));
                item.setRevenueAmount(toDecimal(row.get("revenue")));
            }
        }
    }

    private List<CampusAnalyticsRankRespVO> queryRank(String column, LocalDateTime start, LocalDateTime end,
                                                       Long tenantId) {
        MapSqlParameterSource params = periodParams(start, end, tenantId);
        String tenantClause = tenantClause(tenantId, "tenant_id");
        List<CampusAnalyticsRankRespVO> result = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT " + column + " name, COUNT(*) metric"
                + " FROM campus_analytics_event WHERE deleted = b'0' AND " + column + " <> ''"
                + " AND create_time >= :start AND create_time < :end" + tenantClause
                + " GROUP BY " + column + " ORDER BY metric DESC LIMIT 8", params)) {
            CampusAnalyticsRankRespVO item = new CampusAnalyticsRankRespVO();
            item.setName(String.valueOf(row.get("name")));
            item.setCount(toLong(row.get("metric")));
            result.add(item);
        }
        return result;
    }

    private List<CampusAnalyticsRecentEventRespVO> queryRecentEvents(Long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
        }
        List<CampusAnalyticsRecentEventRespVO> result = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT e.id, e.user_id,"
                + " COALESCE(NULLIF(u.nickname, ''), CONCAT('用户', e.user_id)) nickname,"
                + " e.event_name, e.page_path, e.create_time FROM campus_analytics_event e"
                + " LEFT JOIN campus_miniapp_user u ON u.id = e.user_id AND u.deleted = b'0'"
                + " WHERE e.deleted = b'0'" + tenantClause(tenantId, "e.tenant_id")
                + " ORDER BY e.id DESC LIMIT 20", params)) {
            CampusAnalyticsRecentEventRespVO item = new CampusAnalyticsRecentEventRespVO();
            item.setId(toLong(row.get("id")));
            item.setUserId(toLong(row.get("user_id")));
            item.setNickname(String.valueOf(row.get("nickname")));
            item.setEventName(String.valueOf(row.get("event_name")));
            item.setPagePath(String.valueOf(row.get("page_path")));
            item.setCreateTime(toLocalDateTime(row.get("create_time")));
            result.add(item);
        }
        return result;
    }

    private List<CampusAnalyticsCampusRespVO> queryCampuses() {
        List<CampusAnalyticsCampusRespVO> result = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT system_tenant_id, COALESCE(NULLIF(display_name, ''),"
                        + " CONCAT(school_name, IF(campus_name = '', '', CONCAT(' · ', campus_name)))) name"
                        + " FROM campus_tenant_profile WHERE deleted = b'0' ORDER BY system_tenant_id",
                new MapSqlParameterSource())) {
            CampusAnalyticsCampusRespVO item = new CampusAnalyticsCampusRespVO();
            item.setTenantId(toLong(row.get("system_tenant_id")));
            item.setName(String.valueOf(row.get("name")));
            result.add(item);
        }
        return result;
    }

    private MapSqlParameterSource periodParams(LocalDateTime start, LocalDateTime end, Long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", start)
                .addValue("end", end);
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
        }
        return params;
    }

    private String tenantClause(Long tenantId, String column) {
        return tenantId == null ? "" : " AND " + column + " = :tenantId";
    }

    private long queryLong(String sql, MapSqlParameterSource params) {
        Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
        return result == null ? 0L : result;
    }

    private BigDecimal queryDecimal(String sql, MapSqlParameterSource params) {
        BigDecimal result = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private String trim(String value) {
        return StrUtil.blankToDefault(StrUtil.trim(value), "");
    }

}
