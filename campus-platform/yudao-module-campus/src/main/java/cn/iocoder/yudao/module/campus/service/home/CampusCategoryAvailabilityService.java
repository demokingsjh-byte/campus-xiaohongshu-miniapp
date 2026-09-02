package cn.iocoder.yudao.module.campus.service.home;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读取后台“分类管理”的功能开关，供内容和交易接口共享同一套启停规则。
 */
@Service
public class CampusCategoryAvailabilityService {

    private static final Set<String> MANAGED_PUBLISH_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("idle", "help", "club", "job", "confession", "shop")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CampusCategoryAvailabilityService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 未执行分类表升级时保持旧版本兼容，默认允许已有发布类型。
     */
    public boolean isPublishTypeEnabled(Long tenantId, String publishType) {
        Map<String, Boolean> availability = getAvailability(tenantId);
        if (availability == null) {
            return true;
        }
        Boolean enabled = availability.get(publishType);
        if (enabled != null) {
            return enabled;
        }
        return !MANAGED_PUBLISH_TYPES.contains(publishType);
    }

    public Set<String> getDisabledPublishTypes(Long tenantId) {
        Map<String, Boolean> availability = getAvailability(tenantId);
        if (availability == null) {
            return Collections.emptySet();
        }
        Set<String> disabled = new HashSet<>();
        for (String publishType : MANAGED_PUBLISH_TYPES) {
            if (!Boolean.TRUE.equals(availability.get(publishType))) {
                disabled.add(publishType);
            }
        }
        availability.forEach((publishType, enabled) -> {
            if (!Boolean.TRUE.equals(enabled)) {
                disabled.add(publishType);
            }
        });
        return disabled;
    }

    /**
     * 校区存在独立配置时以校区为准；否则回退 tenant_id=0 的全局配置。
     */
    private Map<String, Boolean> getAvailability(Long tenantId) {
        try {
            long resolvedTenantId = tenantId == null ? 0L : tenantId;
            List<Map<String, Object>> rows = queryRows(resolvedTenantId);
            if (rows.isEmpty() && resolvedTenantId != 0L) {
                rows = queryRows(0L);
            }
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Boolean> availability = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String publishType = value(row.get("publish_type"));
                if (!publishType.isEmpty()) {
                    availability.put(publishType, toBoolean(row.get("enabled")));
                }
            }
            return availability;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> queryRows(long tenantId) {
        return jdbcTemplate.queryForList("SELECT publish_type, enabled FROM campus_home_category"
                        + " WHERE tenant_id = :tenantId AND deleted = b'0'",
                new MapSqlParameterSource("tenantId", tenantId));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            return bytes.length > 0 && bytes[0] != 0;
        }
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
