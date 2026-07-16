package cn.iocoder.yudao.module.campus.controller.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusAuthLoginRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusPhoneBindReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusWechatLoginReqVO;
import cn.iocoder.yudao.module.campus.service.auth.CampusAppAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.error;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.obtainAuthorization;

@Tag(name = "用户 App - 校园小程序认证")
@RestController
@RequestMapping("/campus/auth")
@Validated
public class CampusAppAuthController {

    @Resource
    private CampusAppAuthService campusAppAuthService;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenCommonApi;

    @PostMapping("/wechat-login")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "微信小程序静默登录")
    public CommonResult<CampusAuthLoginRespVO> wechatLogin(@Valid @RequestBody CampusWechatLoginReqVO reqVO) {
        try {
            return success(campusAppAuthService.wechatLogin(reqVO));
        } catch (RuntimeException ex) {
            ServiceException serviceException = findServiceException(ex);
            if (serviceException != null) {
                return error(serviceException);
            }
            throw ex;
        }
    }

    @PostMapping("/refresh-token")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "刷新校园小程序访问令牌")
    public CommonResult<CampusAuthLoginRespVO> refreshToken(
            @RequestParam("refreshToken") String refreshToken) {
        return success(campusAppAuthService.refreshToken(refreshToken));
    }

    /**
     * 部分代理会把业务异常包裹多层；登录接口需要把原始业务码和提示稳定返回给小程序。
     */
    private static ServiceException findServiceException(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof ServiceException) {
                return (ServiceException) current;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前校园用户信息")
    public CommonResult<CampusUserRespVO> getLoginUser() {
        return success(campusAppAuthService.getLoginUser(getLoginUserId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "补全当前校园用户资料")
    public CommonResult<CampusUserRespVO> updateProfile(@Valid @RequestBody CampusUserProfileUpdateReqVO reqVO) {
        return success(campusAppAuthService.updateProfile(getLoginUserId(), reqVO));
    }

    @PostMapping("/phone")
    @Operation(summary = "绑定微信授权手机号")
    public CommonResult<CampusUserRespVO> bindPhone(@Valid @RequestBody CampusPhoneBindReqVO reqVO) {
        return success(campusAppAuthService.bindPhone(getLoginUserId(), reqVO));
    }

    @DeleteMapping("/account")
    @Operation(summary = "注销当前校园账号")
    public CommonResult<Boolean> deleteAccount(HttpServletRequest request) {
        Long userId = getLoginUserId();
        campusAppAuthService.deleteAccount(userId);
        String accessToken = obtainAuthorization(request, "Authorization", "access_token");
        if (StrUtil.isNotBlank(accessToken)) {
            oauth2TokenCommonApi.removeAccessToken(accessToken);
        }
        return success(true);
    }

}
