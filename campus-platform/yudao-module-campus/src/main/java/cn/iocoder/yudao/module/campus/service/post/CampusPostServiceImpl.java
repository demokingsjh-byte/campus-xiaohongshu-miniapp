package cn.iocoder.yudao.module.campus.service.post;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostRespVO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusPostServiceImpl implements CampusPostService {

    private static final long DEFAULT_TENANT_ID = 201L;
    private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("idle", "help", "ride", "shop", "lost", "club")));
    private static final Map<String, String> CHANNEL_MAP = new HashMap<>();

    static {
        CHANNEL_MAP.put("idle", "二手");
        CHANNEL_MAP.put("help", "互助");
        CHANNEL_MAP.put("ride", "拼车");
        CHANNEL_MAP.put("shop", "探店");
        CHANNEL_MAP.put("lost", "失物");
        CHANNEL_MAP.put("club", "社团");
    }

    @Resource
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusPostRespVO createPost(Long userId, CampusPostCreateReqVO reqVO) {
        if (userId == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "请先登录后再发布");
        }
        if (!SUPPORTED_TYPES.contains(reqVO.getType())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不支持的发布类型");
        }
        Map<String, Object> user = getUser(userId);
        String schoolName = value(user, "school_name");
        String campusName = value(user, "campus_name");
        if (StrUtil.isBlank(schoolName) || StrUtil.isBlank(campusName)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "请先完善学校和校区资料后再发布");
        }
        long tenantId = toLong(user.get("tenant_id"), DEFAULT_TENANT_ID);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tenantId", tenantId)
                .addValue("schoolName", schoolName)
                .addValue("campusName", campusName)
                .addValue("type", reqVO.getType())
                .addValue("channel", CHANNEL_MAP.get(reqVO.getType()))
                .addValue("title", reqVO.getTitle().trim())
                .addValue("content", reqVO.getContent().trim())
                .addValue("price", reqVO.getPrice())
                .addValue("originalPrice", reqVO.getOriginalPrice())
                .addValue("location", trimToEmpty(reqVO.getLocation()))
                .addValue("tradeMode", trimToEmpty(reqVO.getTradeMode()))
                .addValue("visibleRange", StrUtil.blankToDefault(reqVO.getVisibleRange(), "仅本校可见"))
                .addValue("contact", trimToEmpty(reqVO.getContact()))
                .addValue("anonymous", Boolean.TRUE.equals(reqVO.getAnonymous()))
                .addValue("tagsJson", JsonUtils.toJsonString(defaultList(reqVO.getTags())))
                .addValue("imagesJson", JsonUtils.toJsonString(defaultList(reqVO.getImages())))
                .addValue("operator", String.valueOf(userId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_post (user_id, tenant_id, school_name, campus_name, type, channel,"
                        + " title, content, price, original_price, location, trade_mode, visible_range, contact, anonymous,"
                        + " tags_json, images_json, status, like_count, collect_count, comment_count, view_count,"
                        + " creator, updater, create_time, update_time, deleted) VALUES (:userId, :tenantId, :schoolName,"
                        + " :campusName, :type, :channel, :title, :content, :price, :originalPrice, :location, :tradeMode,"
                        + " :visibleRange, :contact, :anonymous, :tagsJson, :imagesJson, 1, 0, 0, 0, 0, :operator,"
                        + " :operator, NOW(), NOW(), b'0')",
                params, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "发布失败，请稍后重试");
        }
        return getPost(key.longValue(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusPostRespVO getPost(Long id, Long loginUserId) {
        return getPostInternal(id, loginUserId, true);
    }

    private CampusPostRespVO getPostInternal(Long id, Long loginUserId, boolean increaseView) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                selectSql() + " WHERE p.id = :id AND p.deleted = b'0'"
                        + " AND (p.status = 1 OR p.user_id = :loginUserId) LIMIT 1",
                new MapSqlParameterSource().addValue("id", id).addValue("loginUserId", loginUserId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "内容不存在或已下架");
        }
        if (increaseView && (loginUserId == null || !loginUserId.equals(toLongObject(rows.get(0).get("user_id"))))) {
            jdbcTemplate.update("UPDATE campus_post SET view_count = view_count + 1 WHERE id = :id",
                    new MapSqlParameterSource("id", id));
            rows.get(0).put("view_count", toInt(rows.get(0).get("view_count")) + 1);
        }
        return toResp(rows.get(0), loginUserId);
    }

    @Override
    public PageResult<CampusPostRespVO> getPostPage(Long loginUserId, Long tenantId, String channel,
                                                    String keyword, Integer pageNo, Integer pageSize) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId == null ? DEFAULT_TENANT_ID : tenantId)
                .addValue("channel", channel)
                .addValue("keyword", StrUtil.isBlank(keyword) ? null : "%" + keyword.trim() + "%")
                .addValue("loginUserId", loginUserId);
        String where = " WHERE p.deleted = b'0' AND p.status = 1 AND p.tenant_id = :tenantId"
                + " AND (:channel IS NULL OR :channel = '' OR :channel = '推荐' OR p.channel = :channel)"
                + " AND (:keyword IS NULL OR p.title LIKE :keyword OR p.content LIKE :keyword OR p.tags_json LIKE :keyword)";
        return page(where, params, loginUserId, pageNo, pageSize, "p.create_time DESC");
    }

    @Override
    public PageResult<CampusPostRespVO> getMyPostPage(Long userId, Integer pageNo, Integer pageSize) {
        requireUserId(userId);
        MapSqlParameterSource params = new MapSqlParameterSource("loginUserId", userId);
        String where = " WHERE p.deleted = b'0' AND p.user_id = :loginUserId";
        return page(where, params, userId, pageNo, pageSize, "p.create_time DESC");
    }

    @Override
    public PageResult<CampusPostRespVO> getFavoritePostPage(Long userId, Integer pageNo, Integer pageSize) {
        requireUserId(userId);
        MapSqlParameterSource params = new MapSqlParameterSource("loginUserId", userId);
        String where = " WHERE p.deleted = b'0' AND p.status = 1 AND EXISTS (SELECT 1 FROM campus_post_interaction f"
                + " WHERE f.post_id = p.id AND f.user_id = :loginUserId AND f.type = 'COLLECT' AND f.deleted = b'0')";
        return page(where, params, userId, pageNo, pageSize, "p.create_time DESC");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusPostRespVO setInteraction(Long postId, Long userId, String type, boolean active) {
        requireUserId(userId);
        if (!"LIKE".equals(type) && !"COLLECT".equals(type)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不支持的互动类型");
        }
        Map<String, Object> post = getPostRow(postId);
        boolean current = isInteractionActive(postId, userId, type);
        if (current == active) {
            return getPostInternal(postId, userId, false);
        }
        long tenantId = toLong(post.get("tenant_id"), DEFAULT_TENANT_ID);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("userId", userId)
                .addValue("type", type)
                .addValue("tenantId", tenantId)
                .addValue("operator", String.valueOf(userId));
        if (active) {
            jdbcTemplate.update("INSERT INTO campus_post_interaction (post_id, user_id, type, tenant_id, creator, updater,"
                            + " create_time, update_time, deleted) VALUES (:postId, :userId, :type, :tenantId, :operator,"
                            + " :operator, NOW(), NOW(), b'0') ON DUPLICATE KEY UPDATE deleted = b'0', updater = :operator,"
                            + " update_time = NOW()",
                    params);
        } else {
            jdbcTemplate.update("UPDATE campus_post_interaction SET deleted = b'1', updater = :operator, update_time = NOW()"
                    + " WHERE post_id = :postId AND user_id = :userId AND type = :type AND deleted = b'0'", params);
        }
        String countColumn = "LIKE".equals(type) ? "like_count" : "collect_count";
        jdbcTemplate.update("UPDATE campus_post SET " + countColumn + " = (SELECT COUNT(*)"
                        + " FROM campus_post_interaction i WHERE i.post_id = :postId AND i.type = :type"
                        + " AND i.deleted = b'0'), update_time = NOW() WHERE id = :postId AND deleted = b'0'",
                new MapSqlParameterSource().addValue("postId", postId).addValue("type", type));
        return getPostInternal(postId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long postId, Long userId) {
        requireUserId(userId);
        int updated = jdbcTemplate.update("UPDATE campus_post SET deleted = b'1', updater = :operator, update_time = NOW()"
                        + " WHERE id = :postId AND user_id = :userId AND deleted = b'0'",
                new MapSqlParameterSource().addValue("postId", postId).addValue("userId", userId)
                        .addValue("operator", String.valueOf(userId)));
        if (updated == 0) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "内容不存在或无权删除");
        }
    }

    private PageResult<CampusPostRespVO> page(String where, MapSqlParameterSource params, Long loginUserId,
                                               Integer pageNo, Integer pageSize, String orderBy) {
        int safePageNo = Math.max(pageNo == null ? 1 : pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_post p" + where, params, Long.class);
        params.addValue("offset", (safePageNo - 1) * safePageSize).addValue("pageSize", safePageSize);
        List<CampusPostRespVO> list = jdbcTemplate.queryForList(
                        selectSql() + where + " ORDER BY " + orderBy + " LIMIT :offset, :pageSize", params)
                .stream().map(row -> toResp(row, loginUserId)).collect(Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    private String selectSql() {
        return "SELECT p.*, u.nickname AS user_nickname, u.avatar AS user_avatar,"
                + " EXISTS (SELECT 1 FROM campus_post_interaction li WHERE li.post_id = p.id"
                + " AND li.user_id = :loginUserId AND li.type = 'LIKE' AND li.deleted = b'0') AS login_liked,"
                + " EXISTS (SELECT 1 FROM campus_post_interaction ci WHERE ci.post_id = p.id"
                + " AND ci.user_id = :loginUserId AND ci.type = 'COLLECT' AND ci.deleted = b'0') AS login_collected"
                + " FROM campus_post p LEFT JOIN campus_miniapp_user u ON u.id = p.user_id AND u.deleted = b'0'";
    }

    private CampusPostRespVO toResp(Map<String, Object> row, Long loginUserId) {
        CampusPostRespVO vo = new CampusPostRespVO();
        long id = toLong(row.get("id"), 0L);
        String type = value(row, "type");
        boolean anonymous = toBoolean(row.get("anonymous"));
        String nickname = StrUtil.blankToDefault(value(row, "user_nickname"), "校园同学");
        String author = anonymous ? "同校同学" : nickname;
        List<String> images = parseStringList(value(row, "images_json"));
        vo.setId(id);
        vo.setTenantId(toLongObject(row.get("tenant_id")));
        vo.setUserId(toLongObject(row.get("user_id")));
        vo.setType(type);
        vo.setChannel(value(row, "channel"));
        vo.setTitle(value(row, "title"));
        vo.setContent(value(row, "content"));
        vo.setAuthor(author);
        vo.setAvatar(anonymous ? "" : value(row, "user_avatar"));
        vo.setAvatarText(StrUtil.isBlank(author) ? "校" : author.substring(0, 1));
        vo.setSchool(value(row, "school_name"));
        vo.setCampusName(value(row, "campus_name"));
        vo.setTime(relativeTime(toLocalDateTime(row.get("create_time"))));
        vo.setPrice(formatPrice(row.get("price")));
        vo.setOriginalPrice(formatPrice(row.get("original_price")));
        vo.setLocation(value(row, "location"));
        vo.setTradeMode(value(row, "trade_mode"));
        vo.setVisibleRange(value(row, "visible_range"));
        vo.setTags(parseStringList(value(row, "tags_json")));
        vo.setImages(images);
        vo.setCoverImage(images.isEmpty() ? null : images.get(0));
        vo.setCoverColor(coverColor(type));
        vo.setCoverEmoji(coverEmoji(type));
        vo.setCoverLabel(coverLabel(type));
        vo.setHeight(images.size() > 1 ? "tall" : (row.get("price") == null ? "short" : "medium"));
        vo.setLikes(toInt(row.get("like_count")));
        vo.setCollects(toInt(row.get("collect_count")));
        vo.setComments(toInt(row.get("comment_count")));
        vo.setViews(toInt(row.get("view_count")));
        vo.setStatus(toInt(row.get("status")));
        vo.setLiked(toBoolean(row.get("login_liked")));
        vo.setCollected(toBoolean(row.get("login_collected")));
        vo.setOwner(loginUserId != null && loginUserId.equals(vo.getUserId()));
        vo.setCreateTime(toLocalDateTime(row.get("create_time")));
        return vo;
    }

    private Map<String, Object> getUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, school_name, campus_name FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "校园用户不存在，请重新登录");
        }
        return rows.get(0);
    }

    private Map<String, Object> getPostRow(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tenant_id FROM campus_post WHERE id = :id AND status = 1 AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", postId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "内容不存在或已下架");
        }
        return rows.get(0);
    }

    private boolean isInteractionActive(Long postId, Long userId, String type) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_post_interaction WHERE post_id = :postId"
                        + " AND user_id = :userId AND type = :type AND deleted = b'0'",
                new MapSqlParameterSource().addValue("postId", postId).addValue("userId", userId).addValue("type", type),
                Long.class);
        return count != null && count > 0;
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "请先登录");
        }
    }

    private static List<String> defaultList(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static List<String> parseStringList(String value) {
        if (StrUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        List<String> list = JsonUtils.parseObjectQuietly(value, new TypeReference<List<String>>() { });
        return list == null ? Collections.emptyList() : list;
    }

    private static String trimToEmpty(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Long toLongObject(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static long toLong(Object value, long defaultValue) {
        Long parsed = toLongObject(value);
        return parsed == null ? defaultValue : parsed;
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
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
        return "1".equals(String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }

    private static String formatPrice(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal price = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
        return price.stripTrailingZeros().toPlainString();
    }

    private static String relativeTime(LocalDateTime createTime) {
        if (createTime == null) {
            return "刚刚";
        }
        long minutes = Math.max(Duration.between(createTime, LocalDateTime.now()).toMinutes(), 0);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        return days < 7 ? days + "天前" : createTime.toLocalDate().toString();
    }

    private static String coverColor(String type) {
        if ("idle".equals(type)) return "#E8F1FF";
        if ("ride".equals(type)) return "#DCEEF3";
        if ("shop".equals(type)) return "#FCE7DE";
        if ("lost".equals(type)) return "#FFF0D9";
        return "#EEF2FF";
    }

    private static String coverEmoji(String type) {
        if ("idle".equals(type)) return "📦";
        if ("ride".equals(type)) return "🚕";
        if ("shop".equals(type)) return "🥤";
        if ("lost".equals(type)) return "🔎";
        if ("club".equals(type)) return "🎉";
        return "🙌";
    }

    private static String coverLabel(String type) {
        if ("idle".equals(type)) return "同校闲置";
        if ("ride".equals(type)) return "拼车招募中";
        if ("shop".equals(type)) return "真实探店";
        if ("lost".equals(type)) return "失物信息";
        if ("club".equals(type)) return "活动报名中";
        return "同校互助";
    }
}
