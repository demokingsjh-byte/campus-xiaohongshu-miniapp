package cn.iocoder.yudao.module.campus.service.meta;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CampusResourceRegistry {

    private static final Map<String, CampusResourceMeta> RESOURCES = Arrays.asList(
            meta("region", "campus_region",
                    set("name", "province", "city", "district", "status", "sort", "tenant_id", "creator", "updater"),
                    set("status", "city", "district"),
                    set("name", "province", "city")),
            meta("school", "campus_school_catalog",
                    set("name", "province", "city", "district", "logo_url", "status", "tenant_id", "creator", "updater"),
                    set("status", "city", "district"),
                    set("name", "province", "city")),
            meta("tenant-profile", "campus_tenant_profile",
                    set("system_tenant_id", "region_id", "school_id", "school_name", "campus_name", "display_name",
                            "logo_url", "province", "city", "district", "address", "lat", "lng", "agent_user_id",
                            "commission_enabled", "status", "tenant_id", "creator", "updater"),
                    set("system_tenant_id", "region_id", "school_id", "agent_user_id", "status", "tenant_id"),
                    set("school_name", "campus_name", "display_name", "city")),
            meta("agent", "campus_agent",
                    set("system_tenant_id", "user_id", "agent_level", "status", "commission_rate", "invite_code",
                            "started_at", "ended_at", "tenant_id", "creator", "updater"),
                    set("system_tenant_id", "user_id", "agent_level", "status", "tenant_id"),
                    set("invite_code")),
            meta("product", "campus_product",
                    set("user_id", "category_id", "title", "description", "price", "images", "status",
                            "audit_reason", "location", "view_count", "like_count", "tenant_id", "creator", "updater"),
                    set("user_id", "category_id", "status", "tenant_id"),
                    set("title", "location")),
            meta("post", "campus_post",
                    set("title", "content", "price", "original_price", "location", "trade_mode", "visible_range",
                            "status", "tenant_id", "updater"),
                    set("user_id", "tenant_id", "type", "channel", "status", "school_name", "campus_name"),
                    set("title", "content", "location")),
            meta("miniapp-user", "campus_miniapp_user",
                    set("nickname", "avatar", "mobile", "phone_country_code", "school_name", "campus_name",
                            "grade", "gender", "role_type", "source_scene", "inviter_user_id", "tenant_id", "updater"),
                    set("tenant_id", "inviter_user_id", "role_type", "gender"),
                    set("openid", "unionid", "nickname", "mobile", "school_name", "campus_name", "grade"))
    ).stream().collect(Collectors.toMap(CampusResourceMeta::getResource, Function.identity()));

    private CampusResourceRegistry() {
    }

    public static CampusResourceMeta get(String resource) {
        CampusResourceMeta meta = RESOURCES.get(resource);
        if (meta == null) {
            throw new IllegalArgumentException("不支持的校园资源：" + resource);
        }
        return meta;
    }

    private static CampusResourceMeta meta(String resource, String tableName, Set<String> writableColumns,
                                           Set<String> exactQueryColumns, Set<String> likeQueryColumns) {
        return new CampusResourceMeta(resource, tableName, writableColumns, writableColumns, exactQueryColumns, likeQueryColumns);
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
