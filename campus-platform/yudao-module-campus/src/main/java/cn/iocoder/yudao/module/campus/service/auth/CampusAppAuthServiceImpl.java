package cn.iocoder.yudao.module.campus.service.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusAuthLoginRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusPhoneBindReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusPublicUserRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusWechatLoginReqVO;
import cn.iocoder.yudao.module.campus.service.home.CampusCategoryAvailabilityService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
@Slf4j
public class CampusAppAuthServiceImpl implements CampusAppAuthService {

    private static final String TABLE = "campus_miniapp_user";
    private static final Long DEFAULT_TENANT_ID = 201L;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenCommonApi;
    @Resource
    private SocialUserApi socialUserApi;
    @Resource
    private SocialClientApi socialClientApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private CampusCategoryAvailabilityService categoryAvailabilityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusAuthLoginRespVO wechatLogin(CampusWechatLoginReqVO reqVO) {
        Long tenantId = reqVO.getTenantId() == null ? DEFAULT_TENANT_ID : reqVO.getTenantId();
        return TenantUtils.execute(tenantId, () -> doWechatLogin(reqVO, tenantId));
    }

    private CampusAuthLoginRespVO doWechatLogin(CampusWechatLoginReqVO reqVO, Long tenantId) {
        SocialUserRespDTO socialUser;
        try {
            socialUser = socialUserApi.getSocialUserByCode(
                    UserTypeEnum.MEMBER.getValue(), SocialTypeEnum.WECHAT_MINI_PROGRAM.getType(), reqVO.getCode(), null);
        } catch (Exception ex) {
            log.warn("[wechatLogin][获取微信用户失败 tenantId({})]", reqVO.getTenantId(), ex);
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                    "微信登录凭证无效，请重新进入小程序后重试");
        }
        if (socialUser == null || StrUtil.isBlank(socialUser.getOpenid())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "微信登录失败，未获取到 openid");
        }

        CampusUserRespVO user = getByOpenid(socialUser.getOpenid());
        if (user == null) {
            Long userId = createUser(socialUser, reqVO, tenantId);
            user = getLoginUser(userId);
        } else {
            updateLoginSnapshot(user.getId(), socialUser, reqVO, tenantId);
            user = getLoginUser(user.getId());
        }

        OAuth2AccessTokenCreateReqDTO tokenReq = new OAuth2AccessTokenCreateReqDTO();
        tokenReq.setUserId(user.getId());
        tokenReq.setUserType(UserTypeEnum.MEMBER.getValue());
        tokenReq.setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT);
        tokenReq.setScopes(Collections.singletonList("campus-miniapp"));
        OAuth2AccessTokenRespDTO token = oauth2TokenCommonApi.createAccessToken(tokenReq);

        return buildLoginResp(token, user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusAuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenRespDTO token = oauth2TokenCommonApi.refreshAccessToken(
                refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT);
        if (!Objects.equals(token.getUserType(), UserTypeEnum.MEMBER.getValue())) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌不属于校园用户");
        }
        return buildLoginResp(token, getLoginUser(token.getUserId()));
    }

    private CampusAuthLoginRespVO buildLoginResp(OAuth2AccessTokenRespDTO token, CampusUserRespVO user) {
        CampusAuthLoginRespVO respVO = new CampusAuthLoginRespVO();
        respVO.setToken(token.getAccessToken());
        respVO.setRefreshToken(token.getRefreshToken());
        respVO.setExpiresTime(token.getExpiresTime());
        respVO.setUserInfo(user);
        return respVO;
    }

    @Override
    public CampusUserRespVO getLoginUser(Long userId) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "SELECT * FROM " + TABLE + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "校园用户不存在");
        }
        return toUserResp(rows.get(0));
    }

    @Override
    public CampusPublicUserRespVO getPublicUser(Long targetUserId, Long loginUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "用户编号不正确");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetUserId", targetUserId)
                .addValue("loginUserId", loginUserId);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "SELECT u.id, u.tenant_id, u.nickname, u.avatar, u.school_name, u.campus_name, u.grade, u.gender,"
                        + " (SELECT COUNT(*) FROM campus_user_follow follower"
                        + " WHERE follower.follow_user_id = u.id AND follower.deleted = b'0') AS follower_count,"
                        + " (SELECT COUNT(*) FROM campus_user_follow following"
                        + " WHERE following.user_id = u.id AND following.deleted = b'0') AS following_count,"
                        + " CASE WHEN :loginUserId IS NULL THEN b'0' ELSE EXISTS(SELECT 1 FROM campus_user_follow mine"
                        + " WHERE mine.user_id = :loginUserId AND mine.follow_user_id = u.id AND mine.deleted = b'0') END AS followed"
                        + " FROM campus_miniapp_user u"
                        + " WHERE u.id = :targetUserId AND u.deleted = b'0' LIMIT 1", params);
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "用户不存在或已注销");
        }

        Map<String, Object> row = rows.get(0);
        Long tenantId = toLong(row.get("tenant_id"));
        CampusPublicUserRespVO respVO = new CampusPublicUserRespVO();
        respVO.setUserId(toLong(row.get("id")));
        respVO.setTenantId(tenantId);
        respVO.setNickname(StrUtil.blankToDefault(toStr(row.get("nickname")), "校园同学"));
        respVO.setAvatar(refreshAvatarUrl(toStr(row.get("avatar"))));
        respVO.setSchoolName(toStr(row.get("school_name")));
        respVO.setCampusName(toStr(row.get("campus_name")));
        respVO.setGrade(toStr(row.get("grade")));
        respVO.setGender(toStr(row.get("gender")));
        respVO.setProvince(findProvince(tenantId));
        respVO.setFollowerCount(toLong(row.get("follower_count")));
        respVO.setFollowingCount(toLong(row.get("following_count")));
        respVO.setFollowed(toBoolean(row.get("followed")));
        respVO.setSelf(loginUserId != null && loginUserId.equals(targetUserId));
        respVO.setPostCounts(getPublicPostCounts(targetUserId, tenantId));
        return respVO;
    }

    private String findProvince(Long tenantId) {
        try {
            List<String> rows = namedParameterJdbcTemplate.queryForList(
                    "SELECT province FROM campus_tenant_profile"
                            + " WHERE system_tenant_id = :tenantId AND deleted = b'0' LIMIT 1",
                    new MapSqlParameterSource("tenantId", tenantId), String.class);
            return rows.isEmpty() ? "" : StrUtil.blankToDefault(rows.get(0), "");
        } catch (DataAccessException ex) {
            // 兼容尚未建立校区资料表的旧环境，省份标签缺失不应阻塞整个公开主页。
            return "";
        }
    }

    private Map<String, Long> getPublicPostCounts(Long targetUserId, Long tenantId) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "SELECT p.type, COUNT(*) AS post_count FROM campus_post p"
                        + " WHERE p.user_id = :targetUserId AND p.status = 1 AND p.deleted = b'0'"
                        + " AND p.anonymous = b'0'"
                        + " AND (p.type <> 'help' OR EXISTS (SELECT 1 FROM campus_trade_order eo"
                        + " WHERE eo.product_id = p.id AND eo.biz_type = 4 AND eo.status IN (1, 2)"
                        + " AND eo.fulfillment_status IN (1, 2, 3, 4) AND eo.deleted = b'0'))"
                        + " GROUP BY p.type", new MapSqlParameterSource("targetUserId", targetUserId));
        Set<String> disabledTypes = categoryAvailabilityService.getDisabledPublishTypes(tenantId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> countRow : rows) {
            String type = toStr(countRow.get("type"));
            if (StrUtil.isNotBlank(type) && !disabledTypes.contains(type)) {
                counts.put(type, toLong(countRow.get("post_count")));
            }
        }
        return counts;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusUserRespVO updateProfile(Long userId, CampusUserProfileUpdateReqVO reqVO) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("nickname", trim(reqVO.getNickname()))
                .addValue("avatar", trim(reqVO.getAvatar()))
                .addValue("schoolName", trim(reqVO.getSchoolName()))
                .addValue("campusName", trim(reqVO.getCampusName()))
                .addValue("grade", trim(reqVO.getGrade()))
                .addValue("gender", trim(reqVO.getGender()))
                .addValue("roleType", trim(reqVO.getRoleType()));
        namedParameterJdbcTemplate.update("UPDATE " + TABLE
                + " SET nickname = COALESCE(:nickname, nickname), avatar = COALESCE(:avatar, avatar),"
                + " school_name = COALESCE(:schoolName, school_name), campus_name = COALESCE(:campusName, campus_name),"
                + " grade = COALESCE(:grade, grade), gender = COALESCE(:gender, gender),"
                + " role_type = COALESCE(:roleType, role_type), update_time = NOW()"
                + " WHERE id = :id AND deleted = b'0'", params);
        return getLoginUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusUserRespVO bindPhone(Long userId, CampusPhoneBindReqVO reqVO) {
        SocialWxPhoneNumberInfoRespDTO phoneInfo = socialClientApi.getWxMaPhoneNumberInfo(
                UserTypeEnum.MEMBER.getValue(), reqVO.getPhoneCode());
        if (phoneInfo == null || StrUtil.isBlank(phoneInfo.getPurePhoneNumber())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "手机号授权失败");
        }
        namedParameterJdbcTemplate.update("UPDATE " + TABLE
                        + " SET mobile = :mobile, phone_country_code = :countryCode, update_time = NOW()"
                        + " WHERE id = :id AND deleted = b'0'",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("mobile", phoneInfo.getPurePhoneNumber())
                        .addValue("countryCode", phoneInfo.getCountryCode()));
        return getLoginUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        int userCount = namedParameterJdbcTemplate.update("UPDATE " + TABLE
                + " SET openid = CONCAT('deleted_', id, '_', UNIX_TIMESTAMP()), unionid = '',"
                + " nickname = '已注销用户', avatar = '', mobile = '', phone_country_code = '',"
                + " school_name = '', campus_name = '', grade = '', gender = '不公开',"
                + " source_scene = '', inviter_user_id = NULL, updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE id = :userId AND deleted = b'0'", params);
        if (userCount == 0) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "校园用户不存在或已注销");
        }

        namedParameterJdbcTemplate.update("UPDATE campus_post"
                + " SET title = '内容已由用户删除', content = '', contact = '', location = '', merchant_address = '',"
                + " merchant_location_name = '', merchant_latitude = NULL, merchant_longitude = NULL,"
                + " images_json = '[]', tags_json = '[]', status = 2, updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE user_id = :userId AND deleted = b'0'", params);
        namedParameterJdbcTemplate.update("UPDATE campus_post_interaction"
                + " SET updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE user_id = :userId AND deleted = b'0'", params);
        namedParameterJdbcTemplate.update("UPDATE campus_user_follow"
                + " SET updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE (user_id = :userId OR follow_user_id = :userId) AND deleted = b'0'", params);
        namedParameterJdbcTemplate.update("UPDATE campus_post_comment"
                + " SET content = '', updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE user_id = :userId AND deleted = b'0'", params);
        namedParameterJdbcTemplate.update("UPDATE campus_post_report"
                + " SET detail = '', result_note = '', updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE reporter_user_id = :userId OR post_id IN"
                + " (SELECT id FROM campus_post WHERE user_id = :userId)", params);
        namedParameterJdbcTemplate.update("UPDATE campus_contact_request"
                + " SET requester_nickname = '', requester_mobile = '', target_nickname = '', target_mobile = '',"
                + " message = '', result_note = '', updater = '', update_time = NOW(), deleted = b'1'"
                + " WHERE requester_user_id = :userId OR target_user_id = :userId", params);
    }

    private CampusUserRespVO getByOpenid(String openid) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "SELECT * FROM " + TABLE + " WHERE openid = :openid AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("openid", openid));
        return rows.isEmpty() ? null : toUserResp(rows.get(0));
    }

    private Long createUser(SocialUserRespDTO socialUser, CampusWechatLoginReqVO reqVO, Long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("openid", socialUser.getOpenid())
                .addValue("nickname", StrUtil.blankToDefault(socialUser.getNickname(), "校园体验用户"))
                .addValue("avatar", StrUtil.blankToDefault(socialUser.getAvatar(), ""))
                .addValue("scene", StrUtil.blankToDefault(reqVO.getScene(), ""))
                .addValue("inviterUserId", reqVO.getInviterUserId())
                .addValue("tenantId", tenantId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update("INSERT INTO " + TABLE
                + " (openid, nickname, avatar, source_scene, inviter_user_id, tenant_id, creator, updater,"
                + " first_login_time, last_login_time, create_time, update_time, deleted)"
                + " VALUES (:openid, :nickname, :avatar, :scene, :inviterUserId, :tenantId, '', '',"
                + " NOW(), NOW(), NOW(), NOW(), b'0')", params, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private void updateLoginSnapshot(Long userId, SocialUserRespDTO socialUser, CampusWechatLoginReqVO reqVO, Long tenantId) {
        namedParameterJdbcTemplate.update("UPDATE " + TABLE
                        + " SET nickname = COALESCE(NULLIF(:nickname, ''), nickname),"
                        // 用户主动授权并上传的头像已经持久化在资料表中，后续静默登录不能用社交快照覆盖。
                        + " avatar = CASE WHEN COALESCE(avatar, '') = ''"
                        + " THEN COALESCE(NULLIF(:avatar, ''), avatar) ELSE avatar END,"
                        + " source_scene = COALESCE(NULLIF(:scene, ''), source_scene),"
                        + " inviter_user_id = COALESCE(:inviterUserId, inviter_user_id),"
                        + " tenant_id = :tenantId, last_login_time = NOW(), update_time = NOW()"
                        + " WHERE id = :id AND deleted = b'0'",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("nickname", StrUtil.blankToDefault(socialUser.getNickname(), ""))
                        .addValue("avatar", StrUtil.blankToDefault(socialUser.getAvatar(), ""))
                        .addValue("scene", StrUtil.blankToDefault(reqVO.getScene(), ""))
                        .addValue("inviterUserId", reqVO.getInviterUserId())
                        .addValue("tenantId", tenantId));
    }

    private CampusUserRespVO toUserResp(Map<String, Object> row) {
        CampusUserRespVO respVO = new CampusUserRespVO();
        respVO.setId(toLong(row.get("id")));
        respVO.setOpenid(toStr(row.get("openid")));
        respVO.setUnionid(toStr(row.get("unionid")));
        respVO.setNickname(toStr(row.get("nickname")));
        respVO.setAvatar(refreshAvatarUrl(toStr(row.get("avatar"))));
        respVO.setMobile(toStr(row.get("mobile")));
        respVO.setSchoolName(toStr(row.get("school_name")));
        respVO.setCampusName(toStr(row.get("campus_name")));
        respVO.setGrade(toStr(row.get("grade")));
        respVO.setGender(toStr(row.get("gender")));
        respVO.setRoleType(toStr(row.get("role_type")));
        respVO.setMobileBound(StrUtil.isNotBlank(respVO.getMobile()));
        Object lastLoginTime = row.get("last_login_time");
        if (lastLoginTime instanceof Timestamp) {
            respVO.setLastLoginTime(((Timestamp) lastLoginTime).toLocalDateTime());
        } else if (lastLoginTime instanceof LocalDateTime) {
            respVO.setLastLoginTime((LocalDateTime) lastLoginTime);
        }
        return respVO;
    }

    private static String trim(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String refreshAvatarUrl(String avatar) {
        if (StrUtil.isBlank(avatar)) {
            return "";
        }
        try {
            return fileApi.presignGetUrl(avatar, null);
        } catch (RuntimeException ex) {
            // Keep the profile response usable for external or legacy avatar URLs.
            return avatar;
        }
    }

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
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
