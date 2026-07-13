package cn.iocoder.yudao.module.campus.service.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusAuthLoginRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusPhoneBindReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusWechatLoginReqVO;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
@Slf4j
public class CampusAppAuthServiceImpl implements CampusAppAuthService {

    private static final String TABLE = "campus_miniapp_user";
    private static final Long DEFAULT_TENANT_ID = 0L;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenCommonApi;
    @Resource
    private SocialUserApi socialUserApi;
    @Resource
    private SocialClientApi socialClientApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusAuthLoginRespVO wechatLogin(CampusWechatLoginReqVO reqVO) {
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

        Long tenantId = reqVO.getTenantId() == null ? DEFAULT_TENANT_ID : reqVO.getTenantId();
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
                        + " avatar = COALESCE(NULLIF(:avatar, ''), avatar),"
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
        respVO.setAvatar(toStr(row.get("avatar")));
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

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

}
