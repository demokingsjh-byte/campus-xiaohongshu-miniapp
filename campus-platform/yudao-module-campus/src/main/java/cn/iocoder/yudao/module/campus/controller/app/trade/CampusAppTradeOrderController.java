package cn.iocoder.yudao.module.campus.controller.app.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandDisputeReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandSubmitReqVO;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园订单")
@RestController
@RequestMapping("/campus/trade/order")
@Validated
public class CampusAppTradeOrderController {

    @Resource
    private CampusTradeOrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "创建校园二手订单")
    public CommonResult<CampusTradeOrderRespVO> create(@Valid @RequestBody CampusTradeOrderCreateReqVO reqVO) {
        return success(orderService.createOrder(getLoginUserId(), reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取校园二手订单详情")
    public CommonResult<CampusTradeOrderRespVO> get(@RequestParam("id") Long id) {
        return success(orderService.getOrder(id, getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获取校园二手订单列表")
    public CommonResult<PageResult<CampusTradeOrderRespVO>> page(
            @RequestParam(value = "role", defaultValue = "buyer") String role,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return success(orderService.getOrderPage(getLoginUserId(), role, status, pageNo, pageSize));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消待付款校园二手订单")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        orderService.cancelOrder(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/errand/create")
    @Operation(summary = "为本人发布的代拿代办创建付款订单")
    public CommonResult<CampusTradeOrderRespVO> createErrand(@RequestParam("postId") Long postId) {
        return success(orderService.createErrandOrder(getLoginUserId(), postId));
    }

    @GetMapping("/errand/get-by-post")
    @Operation(summary = "获取代拿代办订单状态")
    public CommonResult<CampusTradeOrderRespVO> getErrandByPost(@RequestParam("postId") Long postId) {
        return success(orderService.getErrandOrderByPost(postId, getLoginUserId()));
    }

    @PostMapping("/errand/accept")
    @Operation(summary = "接取代拿代办任务")
    public CommonResult<CampusTradeOrderRespVO> acceptErrand(@RequestParam("id") Long id) {
        return success(orderService.acceptErrandOrder(id, getLoginUserId()));
    }

    @PostMapping("/errand/submit")
    @Operation(summary = "接单人提交代拿代办完成")
    public CommonResult<CampusTradeOrderRespVO> submitErrand(
            @RequestParam("id") Long id,
            @Valid @RequestBody(required = false) CampusErrandSubmitReqVO reqVO) {
        return success(orderService.submitErrandOrder(id, getLoginUserId(), reqVO));
    }

    @PostMapping("/errand/confirm")
    @Operation(summary = "发布人确认代拿代办完成并结算收益")
    public CommonResult<CampusTradeOrderRespVO> confirmErrand(@RequestParam("id") Long id) {
        return success(orderService.confirmErrandOrder(id, getLoginUserId()));
    }

    @PostMapping("/errand/dispute")
    @Operation(summary = "发布人在确认期限内对代拿代办结果发起申诉")
    public CommonResult<CampusTradeOrderRespVO> disputeErrand(
            @RequestParam("id") Long id,
            @Valid @RequestBody CampusErrandDisputeReqVO reqVO) {
        return success(orderService.disputeErrandOrder(id, getLoginUserId(), reqVO));
    }

    @PostMapping("/errand/cancel")
    @Operation(summary = "无人接单时取消任务并原路退款")
    public CommonResult<CampusTradeOrderRespVO> cancelErrand(@RequestParam("id") Long id) {
        return success(orderService.cancelErrandOrder(id, getLoginUserId()));
    }
}
