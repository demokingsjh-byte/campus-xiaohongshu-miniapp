package cn.iocoder.yudao.module.campus.controller.admin.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeOrderPageReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeOrderRefundReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeRefundRespVO;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradeOrderAdminService;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 校园订单中心")
@RestController
@RequestMapping("/campus/trade-order")
@Validated
public class CampusTradeOrderAdminController {

    @Resource
    private CampusTradeOrderAdminService orderAdminService;

    @Resource
    private CampusTradePaymentService paymentService;

    @GetMapping("/page")
    @Operation(summary = "获得校园订单分页")
    @PreAuthorize("@ss.hasPermission('campus:trade-order:query')")
    public CommonResult<PageResult<Map<String, Object>>> getPage(
            @Valid CampusTradeOrderPageReqVO reqVO) {
        return success(orderAdminService.getOrderPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得校园订单详情")
    @PreAuthorize("@ss.hasPermission('campus:trade-order:query')")
    public CommonResult<Map<String, Object>> getOrder(@RequestParam("id") Long id) {
        return success(orderAdminService.getOrder(id));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得校园订单汇总")
    @PreAuthorize("@ss.hasPermission('campus:trade-order:query')")
    public CommonResult<Map<String, Object>> getSummary(
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        return success(orderAdminService.getSummary(tenantId));
    }

    @PostMapping("/refund")
    @Operation(summary = "发起微信全额退款")
    @PreAuthorize("@ss.hasPermission('campus:trade-order:refund')")
    public CommonResult<CampusTradeRefundRespVO> refund(
            @Valid @RequestBody CampusTradeOrderRefundReqVO reqVO) {
        return success(paymentService.refundOrder(reqVO.getOrderId(), reqVO.getReason(),
                "admin:" + getLoginUserId()));
    }

    @PostMapping("/refund-sync")
    @Operation(summary = "同步微信退款状态")
    @PreAuthorize("@ss.hasPermission('campus:trade-order:refund')")
    public CommonResult<CampusTradeRefundRespVO> syncRefund(@RequestParam("id") Long id) {
        return success(paymentService.syncRefund(id));
    }
}
