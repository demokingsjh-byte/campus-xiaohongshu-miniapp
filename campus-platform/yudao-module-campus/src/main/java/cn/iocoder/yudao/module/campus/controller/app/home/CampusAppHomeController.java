package cn.iocoder.yudao.module.campus.controller.app.home;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.controller.app.home.vo.CampusHomeConfigRespVO;
import cn.iocoder.yudao.module.campus.controller.app.home.vo.CampusHomeConfigRespVO.Category;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 校园首页")
@RestController
@RequestMapping("/campus/home")
@Validated
public class CampusAppHomeController {

    private static final String CONFIG_PREFIX = "campus.home.";
    private static final String DEFAULT_SEARCH_PLACEHOLDER = "搜索校园新鲜事";

    private static final List<Category> DEFAULT_CATEGORIES = Arrays.asList(
            new Category("recommend", "推荐", "推荐", "/static/images/home-prototype/category-recommend.png", null, true, true, true, 10),
            new Category("idle", "二手闲置", "二手", "/static/images/home-prototype/category-idle.png", "idle", true, true, true, 20),
            new Category("errand", "代拿代办", "互助", "/static/images/home-prototype/category-errand.png", "help", true, true, true, 30),
            new Category("fun", "校园趣事", "社团", "/static/images/home-prototype/category-fun.png", "club", true, true, true, 40),
            new Category("job", "兼职信息", "兼职", "/static/images/home-prototype/category-job.png", "job", true, true, true, 50),
            new Category("confession", "表白墙", "表白", "/static/images/home-prototype/category-confession.png", "confession", true, true, true, 60),
            new Category("groupbuy", "商家团购", "探店", "/static/images/home-prototype/category-groupbuy.png", "shop", true, true, true, 70)
    );

    @Resource
    private ConfigApi configApi;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @GetMapping("/config")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获取校园首页配置")
    public CommonResult<CampusHomeConfigRespVO> getHomeConfig(
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        CampusHomeConfigRespVO result = new CampusHomeConfigRespVO();
        result.setSearchPlaceholder(getConfigValue(tenantId, "search-placeholder", DEFAULT_SEARCH_PLACEHOLDER));
        result.setNotice(getNoticeValue(tenantId));
        result.setCategoryIconVisible(getBooleanConfigValue(tenantId, "category-icon-visible", true));
        result.setCategoryTitleVisible(getBooleanConfigValue(tenantId, "category-title-visible", true));
        result.setCategories(getCategories(tenantId));
        return success(result);
    }

    private List<Category> getCategories(Long tenantId) {
        List<Category> databaseCategories = getDatabaseCategories(tenantId);
        if (databaseCategories != null) {
            return databaseCategories;
        }
        String value = getConfigValue(tenantId, "categories", null);
        if (!hasText(value)) {
            return DEFAULT_CATEGORIES;
        }
        List<Category> configured = JsonUtils.parseObjectQuietly(value, new TypeReference<List<Category>>() { });
        if (configured == null || configured.isEmpty()) {
            return DEFAULT_CATEGORIES;
        }

        List<Category> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Category item : configured) {
            if (item == null || !hasText(item.getKey()) || !hasText(item.getTitle())
                    || !hasText(item.getChannel()) || !keys.add(item.getKey())) {
                continue;
            }
            if (item.getIconVisible() == null) {
                item.setIconVisible(true);
            }
            if (item.getTitleVisible() == null) {
                item.setTitleVisible(true);
            }
            if (item.getEnabled() == null) {
                item.setEnabled(true);
            }
            if (Boolean.TRUE.equals(item.getIconVisible()) && !hasText(item.getIcon())) {
                continue;
            }
            if (!hasText(item.getPublishType())) {
                item.setPublishType(getDefaultPublishType(item.getKey()));
            }
            normalized.add(item);
        }
        return normalized.isEmpty() ? DEFAULT_CATEGORIES : normalized;
    }

    /**
     * 优先读取后台分类表。返回 null 表示尚未执行升级脚本，此时继续兼容旧 JSON 配置。
     * 关闭的分类仍返回 enabled=false，供小程序同时隐藏入口和对应频道内容。
     */
    private List<Category> getDatabaseCategories(Long tenantId) {
        try {
            List<Map<String, Object>> rows = queryDatabaseCategories(tenantId == null ? 0L : tenantId);
            if (rows.isEmpty() && tenantId != null && tenantId != 0L) {
                rows = queryDatabaseCategories(0L);
            }
            if (rows.isEmpty()) {
                return null;
            }
            List<Category> result = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (Map<String, Object> row : rows) {
                String key = value(row, "category_key");
                String title = value(row, "title");
                String channel = value(row, "channel");
                String icon = value(row, "icon_url");
                if (!hasText(key) || !hasText(title) || !hasText(channel) || !keys.add(key)) {
                    continue;
                }
                boolean iconVisible = toBoolean(row.get("icon_visible"));
                if (iconVisible && !hasText(icon)) {
                    continue;
                }
                result.add(new Category(key, title, channel, icon, value(row, "publish_type"),
                        iconVisible, toBoolean(row.get("title_visible")), toBoolean(row.get("enabled")),
                        toInteger(row.get("sort"))));
            }
            return result;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> queryDatabaseCategories(Long tenantId) {
        return namedParameterJdbcTemplate.queryForList("SELECT category_key, title, channel, icon_url, publish_type,"
                        + " enabled, icon_visible, title_visible, sort FROM campus_home_category"
                        + " WHERE tenant_id = :tenantId AND deleted = b'0' ORDER BY sort ASC, id ASC",
                new MapSqlParameterSource("tenantId", tenantId));
    }

    private String getDefaultPublishType(String key) {
        for (Category item : DEFAULT_CATEGORIES) {
            if (item.getKey().equals(key)) {
                return item.getPublishType();
            }
        }
        return null;
    }

    private String getConfigValue(Long tenantId, String suffix, String defaultValue) {
        if (tenantId != null) {
            String tenantValue = configApi.getConfigValueByKey(CONFIG_PREFIX + tenantId + "." + suffix);
            if (hasText(tenantValue)) {
                return tenantValue.trim();
            }
        }
        String globalValue = configApi.getConfigValueByKey(CONFIG_PREFIX + suffix);
        return hasText(globalValue) ? globalValue.trim() : defaultValue;
    }

    private String getNoticeValue(Long tenantId) {
        if (tenantId != null) {
            String tenantValue = configApi.getConfigValueByKey(CONFIG_PREFIX + tenantId + ".notice");
            if (tenantValue != null) {
                return tenantValue.trim();
            }
        }
        String globalValue = configApi.getConfigValueByKey(CONFIG_PREFIX + "notice");
        return globalValue == null ? "" : globalValue.trim();
    }

    private boolean getBooleanConfigValue(Long tenantId, String suffix, boolean defaultValue) {
        String value = getConfigValue(tenantId, suffix, null);
        if (!hasText(value)) {
            return defaultValue;
        }
        switch (value.trim().toLowerCase()) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
                return false;
            default:
                return defaultValue;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
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

    private static int toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

}
