package cn.iocoder.yudao.module.campus.controller.app.analytics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.campus.controller.app.analytics.vo.CampusAnalyticsTrackReqVO;
import cn.iocoder.yudao.module.campus.service.analytics.CampusAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园小程序埋点")
@RestController
@RequestMapping("/campus/analytics")
@Validated
public class CampusAppAnalyticsController {

    @Resource
    private CampusAnalyticsService campusAnalyticsService;

    @PostMapping("/track")
    @Operation(summary = "上报小程序行为或在线心跳")
    public CommonResult<Boolean> track(@Valid @RequestBody CampusAnalyticsTrackReqVO reqVO) {
        campusAnalyticsService.track(getLoginUserId(), reqVO);
        return success(true);
    }

}
