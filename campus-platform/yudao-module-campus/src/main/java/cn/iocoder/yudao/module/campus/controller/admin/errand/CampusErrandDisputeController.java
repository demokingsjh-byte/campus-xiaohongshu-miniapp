package cn.iocoder.yudao.module.campus.controller.admin.errand;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.errand.vo.CampusErrandDisputePageReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.errand.vo.CampusErrandDisputeResolveReqVO;
import cn.iocoder.yudao.module.campus.service.errand.CampusErrandDisputeService;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 代拿代办申诉")
@RestController
@RequestMapping("/campus/errand-dispute")
@Validated
public class CampusErrandDisputeController {

    @Resource
    private CampusErrandDisputeService disputeService;
    @Resource
    private CampusTradeOrderService orderService;

    @GetMapping("/page")
    @Operation(summary = "获得代拿代办申诉分页")
    @PreAuthorize("@ss.hasPermission('campus:errand-dispute:query')")
    public CommonResult<PageResult<Map<String, Object>>> page(@Valid CampusErrandDisputePageReqVO reqVO) {
        return success(disputeService.getPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得代拿代办申诉详情")
    @PreAuthorize("@ss.hasPermission('campus:errand-dispute:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam("orderId") Long orderId) {
        return success(disputeService.get(orderId));
    }

    @PostMapping("/resolve")
    @Operation(summary = "裁决代拿代办申诉")
    @PreAuthorize("@ss.hasPermission('campus:errand-dispute:resolve')")
    public CommonResult<Boolean> resolve(@Valid @RequestBody CampusErrandDisputeResolveReqVO reqVO) {
        orderService.resolveErrandDispute(reqVO.getOrderId(), reqVO.getResult(), reqVO.getResolution(),
                getLoginUserId());
        return success(true);
    }
}
