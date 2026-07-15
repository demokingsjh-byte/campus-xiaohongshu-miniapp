package cn.iocoder.yudao.module.campus.controller.admin.analytics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsOverviewRespVO;
import cn.iocoder.yudao.module.campus.service.analytics.CampusAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 校园小程序数据看板")
@RestController
@RequestMapping("/campus/analytics")
@Validated
public class CampusAnalyticsController {

    @Resource
    private CampusAnalyticsService campusAnalyticsService;

    @GetMapping("/overview")
    @Operation(summary = "获得小程序运营数据看板")
    @PreAuthorize("@ss.hasPermission('campus:analytics:query')")
    public CommonResult<CampusAnalyticsOverviewRespVO> getOverview(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "days", defaultValue = "7") @Min(7) @Max(30) Integer days) {
        return success(campusAnalyticsService.getOverview(tenantId, days));
    }

}
