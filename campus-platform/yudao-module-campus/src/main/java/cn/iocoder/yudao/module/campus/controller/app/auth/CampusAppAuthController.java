package cn.iocoder.yudao.module.campus.controller.app.auth;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园小程序认证")
@RestController
@RequestMapping("/campus/auth")
@Validated
public class CampusAppAuthController {

    @Resource
    private CampusAppAuthService campusAppAuthService;

    @PostMapping("/wechat-login")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "微信小程序静默登录")
    public CommonResult<CampusAuthLoginRespVO> wechatLogin(@Valid @RequestBody CampusWechatLoginReqVO reqVO) {
        return success(campusAppAuthService.wechatLogin(reqVO));
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

}
