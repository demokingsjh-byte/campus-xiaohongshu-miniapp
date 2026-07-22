package cn.iocoder.yudao.module.campus.service.post;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentReportReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentRespVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostReportReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostRespVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

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

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileApi fileApi;

    public CampusPostServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
    }

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
                .addValue("imagesJson", JsonUtils.toJsonString(defaultList(reqVO.getImages()).stream()
                        .filter(StrUtil::isNotBlank)
                        .map(HttpUtils::removeUrlQuery)
                        .collect(Collectors.toList())))
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
    public PageResult<CampusPostCommentRespVO> getCommentPage(Long postId, Long loginUserId,
                                                              Integer pageNo, Integer pageSize, String sort) {
        getPostRow(postId);
        int safePageNo = Math.max(pageNo == null ? 1 : pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 50);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("loginUserId", loginUserId)
                .addValue("offset", (safePageNo - 1) * safePageSize)
                .addValue("pageSize", safePageSize);
        String orderBy = commentOrderBy(sort);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_post_comment c"
                + " WHERE c.post_id = :postId AND c.status = 1 AND c.deleted = b'0'", params, Long.class);
        List<CampusPostCommentRespVO> list = jdbcTemplate.queryForList(commentSelectSql()
                        + " WHERE c.post_id = :postId AND c.status = 1 AND c.deleted = b'0'"
                        + " ORDER BY " + orderBy + " LIMIT :offset, :pageSize", params)
                .stream().map(row -> toCommentResp(row, loginUserId)).collect(Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusPostCommentRespVO createComment(Long postId, Long userId,
                                                  CampusPostCommentCreateReqVO reqVO) {
        requireUserId(userId);
        Map<String, Object> post = getPostRow(postId);
        Map<String, Object> user = getUser(userId);
        long postTenantId = toLong(post.get("tenant_id"), DEFAULT_TENANT_ID);
        long userTenantId = toLong(user.get("tenant_id"), DEFAULT_TENANT_ID);
        if (postTenantId != userTenantId) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能评论本校内容");
        }
        Long parentId = reqVO.getParentId();
        Long replyToUserId = reqVO.getReplyToUserId();
        if (parentId != null) {
            List<Map<String, Object>> parentRows = jdbcTemplate.queryForList("SELECT post_id, parent_id, user_id FROM campus_post_comment"
                    + " WHERE id = :parentId AND status = 1 AND deleted = b'0' LIMIT 1",
                    new MapSqlParameterSource("parentId", parentId));
            Long parentPostId = parentRows.isEmpty() ? null : toLongObject(parentRows.get(0).get("post_id"));
            if (parentPostId == null || !postId.equals(parentPostId)) {
                throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "回复的评论不存在或不属于当前帖子");
            }
            Map<String, Object> parent = parentRows.get(0);
            if (parent.get("parent_id") != null) {
                parentId = toLongObject(parent.get("parent_id"));
            }
            if (replyToUserId == null) {
                replyToUserId = toLongObject(parent.get("user_id"));
            }
        }
        String content = trimToEmpty(reqVO.getContent());
        List<String> images = defaultList(reqVO.getImages()).stream()
                .filter(StrUtil::isNotBlank).limit(3).map(HttpUtils::removeUrlQuery).collect(Collectors.toList());
        if (StrUtil.isBlank(content) && images.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "请输入评论内容或上传图片");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("userId", userId)
                .addValue("parentId", parentId)
                .addValue("replyToUserId", replyToUserId)
                .addValue("tenantId", postTenantId)
                .addValue("content", content)
                .addValue("mentionUserIdsJson", JsonUtils.toJsonString((reqVO.getMentionUserIds() == null
                        ? Collections.<Long>emptyList() : reqVO.getMentionUserIds()).stream()
                        .filter(java.util.Objects::nonNull).distinct().limit(20).collect(Collectors.toList())))
                .addValue("imagesJson", JsonUtils.toJsonString(images))
                .addValue("operator", String.valueOf(userId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_post_comment (post_id, user_id, parent_id, reply_to_user_id, tenant_id, content,"
                        + " mention_user_ids_json, images_json, status, like_count, creator, updater, create_time, update_time, deleted)"
                        + " VALUES (:postId, :userId, :parentId, :replyToUserId, :tenantId, :content, :mentionUserIdsJson,"
                        + " :imagesJson, 1, 0, :operator, :operator, NOW(), NOW(), b'0')", params, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "评论发布失败，请稍后重试");
        }
        jdbcTemplate.update("UPDATE campus_post SET comment_count = (SELECT COUNT(*) FROM campus_post_comment c"
                        + " WHERE c.post_id = :postId AND c.status = 1 AND c.deleted = b'0'), update_time = NOW()"
                        + " WHERE id = :postId AND deleted = b'0'", new MapSqlParameterSource("postId", postId));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(commentSelectSql()
                + " WHERE c.id = :id AND c.post_id = :postId AND c.status = 1 AND c.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("id", key.longValue()).addValue("postId", postId)
                        .addValue("loginUserId", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "璇勮淇℃伅鍥炶澶辫触锛岃绋嶅悗閲嶈瘯");
        }
        return toCommentResp(rows.get(0), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusPostCommentRespVO setCommentLike(Long commentId, Long userId, boolean active) {
        requireUserId(userId);
        Map<String, Object> comment = getCommentRow(commentId);
        Map<String, Object> user = getUser(userId);
        if (toLong(comment.get("tenant_id"), DEFAULT_TENANT_ID)
                != toLong(user.get("tenant_id"), DEFAULT_TENANT_ID)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能操作本校内容");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("commentId", commentId)
                .addValue("userId", userId)
                .addValue("tenantId", toLong(comment.get("tenant_id"), DEFAULT_TENANT_ID))
                .addValue("operator", String.valueOf(userId));
        Long current = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_post_comment_like"
                        + " WHERE comment_id = :commentId AND user_id = :userId AND deleted = b'0'", params, Long.class);
        boolean alreadyLiked = current != null && current > 0;
        if (alreadyLiked != active) {
            if (active) {
                jdbcTemplate.update("INSERT INTO campus_post_comment_like (comment_id, user_id, tenant_id, creator,"
                                + " updater, create_time, update_time, deleted) VALUES (:commentId, :userId, :tenantId,"
                                + " :operator, :operator, NOW(), NOW(), b'0') ON DUPLICATE KEY UPDATE deleted = b'0',"
                                + " updater = :operator, update_time = NOW()", params);
            } else {
                jdbcTemplate.update("UPDATE campus_post_comment_like SET deleted = b'1', updater = :operator,"
                                + " update_time = NOW() WHERE comment_id = :commentId AND user_id = :userId"
                                + " AND deleted = b'0'", params);
            }
            jdbcTemplate.update("UPDATE campus_post_comment SET like_count = (SELECT COUNT(*)"
                            + " FROM campus_post_comment_like WHERE comment_id = :commentId AND deleted = b'0'),"
                            + " update_time = NOW() WHERE id = :commentId AND deleted = b'0'", params);
        }
        return toCommentResp(getCommentRow(commentId, userId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        requireUserId(userId);
        Map<String, Object> comment = getCommentRow(commentId);
        Long ownerId = toLongObject(comment.get("user_id"));
        if (!userId.equals(ownerId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能删除自己发布的评论");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("commentId", commentId)
                .addValue("postId", comment.get("post_id"))
                .addValue("userId", userId)
                .addValue("operator", String.valueOf(userId));
        jdbcTemplate.update("UPDATE campus_post_comment SET deleted = b'1', status = 2, updater = :operator,"
                        + " update_time = NOW() WHERE id = :commentId AND user_id = :userId AND deleted = b'0'", params);
        jdbcTemplate.update("UPDATE campus_post_comment SET deleted = b'1', status = 2, updater = :operator,"
                        + " update_time = NOW() WHERE parent_id = :commentId AND deleted = b'0'", params);
        updateCommentCount(toLong(comment.get("post_id"), 0L));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportComment(Long commentId, Long userId, CampusPostCommentReportReqVO reqVO) {
        requireUserId(userId);
        Map<String, Object> comment = getCommentRow(commentId);
        if (userId.equals(toLongObject(comment.get("user_id")))) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不能举报自己的评论");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", comment.get("post_id"))
                .addValue("commentId", commentId)
                .addValue("userId", userId)
                .addValue("tenantId", comment.get("tenant_id"))
                .addValue("reason", reqVO.getReason().trim())
                .addValue("detail", trimToEmpty(reqVO.getDetail()))
                .addValue("operator", String.valueOf(userId));
        jdbcTemplate.update("INSERT INTO campus_post_comment_report (post_id, comment_id, reporter_user_id,"
                        + " tenant_id, reason, detail, status, creator, updater, create_time, update_time, deleted)"
                        + " VALUES (:postId, :commentId, :userId, :tenantId, :reason, :detail, 0, :operator,"
                        + " :operator, NOW(), NOW(), b'0') ON DUPLICATE KEY UPDATE reason = :reason, detail = :detail,"
                        + " status = 0, result_note = '', deleted = b'0', updater = :operator, update_time = NOW()",
                params);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createContactRequest(Long postId, Long userId) {
        requireUserId(userId);
        Map<String, Object> post = getPostRow(postId);
        Long targetUserId = toLongObject(post.get("user_id"));
        if (userId.equals(targetUserId)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不能联系自己发布的内容");
        }

        Map<String, Object> requester = getUser(userId);
        Map<String, Object> target = getUser(targetUserId);
        long postTenantId = toLong(post.get("tenant_id"), DEFAULT_TENANT_ID);
        long requesterTenantId = toLong(requester.get("tenant_id"), DEFAULT_TENANT_ID);
        if (postTenantId != requesterTenantId) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能联系本校内容发布者");
        }
        String requesterMobile = value(requester, "mobile");
        if (StrUtil.isBlank(requesterMobile)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "请先绑定手机号后再联系发布者");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("requesterUserId", userId)
                .addValue("targetUserId", targetUserId)
                .addValue("tenantId", postTenantId)
                .addValue("postTitle", value(post, "title"))
                .addValue("requesterNickname", value(requester, "nickname"))
                .addValue("requesterMobile", requesterMobile)
                .addValue("targetNickname", value(target, "nickname"))
                .addValue("targetMobile", value(target, "mobile"))
                .addValue("message", "希望联系该内容发布者")
                .addValue("operator", String.valueOf(userId));
        jdbcTemplate.update("INSERT INTO campus_contact_request (post_id, requester_user_id, target_user_id, tenant_id,"
                        + " post_title, requester_nickname, requester_mobile, target_nickname, target_mobile, message,"
                        + " status, result_note, creator, updater, create_time, update_time, deleted) VALUES (:postId,"
                        + " :requesterUserId, :targetUserId, :tenantId, :postTitle, :requesterNickname,"
                        + " :requesterMobile, :targetNickname, :targetMobile, :message, 0, '', :operator, :operator,"
                        + " NOW(), NOW(), b'0') ON DUPLICATE KEY UPDATE post_title = :postTitle,"
                        + " requester_nickname = :requesterNickname, requester_mobile = :requesterMobile,"
                        + " target_nickname = :targetNickname, target_mobile = :targetMobile, message = :message,"
                        + " status = 0, result_note = '', updater = :operator, update_time = NOW(), deleted = b'0'",
                params);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportPost(Long postId, Long userId, CampusPostReportReqVO reqVO) {
        requireUserId(userId);
        Map<String, Object> post = getPostRow(postId);
        Long ownerUserId = toLongObject(post.get("user_id"));
        if (userId.equals(ownerUserId)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不能举报自己的内容");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("userId", userId)
                .addValue("tenantId", toLong(post.get("tenant_id"), DEFAULT_TENANT_ID))
                .addValue("reason", reqVO.getReason().trim())
                .addValue("detail", trimToEmpty(reqVO.getDetail()))
                .addValue("operator", String.valueOf(userId));
        jdbcTemplate.update("INSERT INTO campus_post_report (post_id, reporter_user_id, tenant_id, reason, detail,"
                        + " status, creator, updater, create_time, update_time, deleted) VALUES (:postId, :userId,"
                        + " :tenantId, :reason, :detail, 0, :operator, :operator, NOW(), NOW(), b'0')"
                        + " ON DUPLICATE KEY UPDATE reason = :reason, detail = :detail, status = 0, deleted = b'0',"
                        + " updater = :operator, update_time = NOW()",
                params);
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

    private String commentSelectSql() {
        return "SELECT c.*, u.nickname AS user_nickname, u.avatar AS user_avatar, reply_user.nickname AS reply_to_author,"
                + " p.user_id AS post_owner_user_id, root.create_time AS root_create_time, root.like_count AS root_like_count,"
                + " (SELECT COUNT(*) FROM campus_post_comment r WHERE r.parent_id = c.id"
                + " AND r.status = 1 AND r.deleted = b'0') AS reply_count,"
                + " EXISTS (SELECT 1 FROM campus_post_comment_like cl WHERE cl.comment_id = c.id"
                + " AND cl.user_id = :loginUserId AND cl.deleted = b'0') AS login_liked"
                + " FROM campus_post_comment c LEFT JOIN campus_post_comment root"
                + " ON root.id = COALESCE(c.parent_id, c.id) AND root.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user u ON u.id = c.user_id AND u.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user reply_user ON reply_user.id = c.reply_to_user_id"
                + " AND reply_user.deleted = b'0' LEFT JOIN campus_post p ON p.id = c.post_id";
    }

    private String commentOrderBy(String sort) {
        if ("likes".equalsIgnoreCase(sort)) {
            return "root.like_count DESC, root.create_time DESC, root.id DESC,"
                    + " CASE WHEN c.id = root.id THEN 0 ELSE 1 END ASC, c.create_time ASC, c.id ASC";
        }
        if ("seller".equalsIgnoreCase(sort)) {
            return "CASE WHEN root.user_id = p.user_id THEN 0 ELSE 1 END ASC, root.create_time DESC, root.id DESC,"
                    + " CASE WHEN c.id = root.id THEN 0 ELSE 1 END ASC, c.create_time ASC, c.id ASC";
        }
        return "root.create_time DESC, root.id DESC, CASE WHEN c.id = root.id THEN 0 ELSE 1 END ASC,"
                + " c.create_time ASC, c.id ASC";
    }

    private Map<String, Object> getCommentRow(Long commentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, post_id, user_id, tenant_id FROM campus_post_comment"
                        + " WHERE id = :id AND status = 1 AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", commentId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "评论不存在或已被删除");
        }
        return rows.get(0);
    }

    private Map<String, Object> getCommentRow(Long commentId, Long loginUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(commentSelectSql()
                        + " WHERE c.id = :id AND c.status = 1 AND c.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("id", commentId).addValue("loginUserId", loginUserId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "评论不存在或已被删除");
        }
        return rows.get(0);
    }

    private void updateCommentCount(Long postId) {
        jdbcTemplate.update("UPDATE campus_post SET comment_count = (SELECT COUNT(*)"
                        + " FROM campus_post_comment c WHERE c.post_id = :postId AND c.status = 1"
                        + " AND c.deleted = b'0'), update_time = NOW() WHERE id = :postId AND deleted = b'0'",
                new MapSqlParameterSource("postId", postId));
    }

    private CampusPostCommentRespVO toCommentResp(Map<String, Object> row, Long loginUserId) {
        CampusPostCommentRespVO vo = new CampusPostCommentRespVO();
        String author = StrUtil.blankToDefault(value(row, "user_nickname"), "校园同学");
        vo.setId(toLongObject(row.get("id")));
        vo.setPostId(toLongObject(row.get("post_id")));
        vo.setUserId(toLongObject(row.get("user_id")));
        vo.setParentId(toLongObject(row.get("parent_id")));
        vo.setReplyToUserId(toLongObject(row.get("reply_to_user_id")));
        vo.setReplyToAuthor(value(row, "reply_to_author"));
        vo.setAuthor(author);
        vo.setAvatar(refreshFileUrl(value(row, "user_avatar")));
        vo.setAvatarText(StrUtil.isBlank(author) ? "校" : author.substring(0, 1));
        vo.setContent(value(row, "content"));
        vo.setMentionUserIds(parseLongList(value(row, "mention_user_ids_json")));
        vo.setImages(parseStringList(value(row, "images_json")).stream()
                .map(this::refreshFileUrl).collect(Collectors.toList()));
        vo.setCreateTime(toLocalDateTime(row.get("create_time")));
        vo.setTime(relativeTime(vo.getCreateTime()));
        vo.setOwner(loginUserId != null && loginUserId.equals(vo.getUserId()));
        vo.setLikeCount(toInt(row.get("like_count")));
        vo.setReplyCount(toInt(row.get("reply_count")));
        vo.setLiked(toBoolean(row.get("login_liked")));
        return vo;
    }

    private CampusPostRespVO toResp(Map<String, Object> row, Long loginUserId) {
        CampusPostRespVO vo = new CampusPostRespVO();
        long id = toLong(row.get("id"), 0L);
        String type = value(row, "type");
        boolean anonymous = toBoolean(row.get("anonymous"));
        String nickname = StrUtil.blankToDefault(value(row, "user_nickname"), "校园同学");
        String author = anonymous ? "同校同学" : nickname;
        List<String> images = parseStringList(value(row, "images_json")).stream()
                .map(this::refreshFileUrl)
                .collect(Collectors.toList());
        vo.setId(id);
        vo.setTenantId(toLongObject(row.get("tenant_id")));
        vo.setUserId(toLongObject(row.get("user_id")));
        vo.setType(type);
        vo.setChannel(value(row, "channel"));
        vo.setTitle(value(row, "title"));
        vo.setContent(value(row, "content"));
        vo.setAuthor(author);
        vo.setAvatar(anonymous ? "" : refreshFileUrl(value(row, "user_avatar")));
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

    /**
     * Refreshes private-storage URLs on every API response. Historical rows may contain an expired
     * query signature; the file client strips it and signs the underlying object again.
     */
    private String refreshFileUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        try {
            return fileApi.presignGetUrl(url, null);
        } catch (RuntimeException ex) {
            // Keep the feed available when a legacy or external image cannot be signed.
            return url;
        }
    }

    private Map<String, Object> getUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, nickname, mobile, school_name, campus_name FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "校园用户不存在，请重新登录");
        }
        return rows.get(0);
    }

    private Map<String, Object> getPostRow(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, user_id, title FROM campus_post"
                        + " WHERE id = :id AND status = 1 AND deleted = b'0' LIMIT 1",
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

    private static List<Long> parseLongList(String value) {
        if (StrUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        List<Long> list = JsonUtils.parseObjectQuietly(value, new TypeReference<List<Long>>() { });
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
