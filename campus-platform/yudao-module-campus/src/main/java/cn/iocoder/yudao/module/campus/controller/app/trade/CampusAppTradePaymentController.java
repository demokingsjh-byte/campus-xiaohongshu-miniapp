package cn.iocoder.yudao.module.campus.controller.app.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePaymentStatusRespVO;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/campus/trade")
public class CampusAppTradePaymentController {

    @Resource
    private CampusTradePaymentService paymentService;

    @PostMapping("/pay")
    public CommonResult<CampusTradePayRespVO> pay(@RequestParam("postId") Long postId,
                                                  HttpServletRequest request) {
        return success(paymentService.createPayment(postId, getLoginUserId(), ServletUtils.getClientIP(request)));
    }

    @PostMapping("/order/pay")
    public CommonResult<CampusTradePayRespVO> payOrder(@RequestParam("orderId") Long orderId,
                                                       HttpServletRequest request) {
        return success(paymentService.createPaymentByOrder(orderId, getLoginUserId(),
                ServletUtils.getClientIP(request)));
    }

    @GetMapping("/order/payment-status")
    public CommonResult<CampusTradePaymentStatusRespVO> paymentStatus(@RequestParam("orderId") Long orderId) {
        return success(paymentService.getPaymentStatus(orderId, getLoginUserId()));
    }

    @GetMapping("/contact")
    public CommonResult<CampusTradeContactRespVO> contact(@RequestParam("postId") Long postId) {
        return success(paymentService.getContact(postId, getLoginUserId()));
    }

    @PostMapping("/wechat/notify")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<Map<String, String>> notify(@RequestBody String body, HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        paymentService.handleWechatNotify(body, headers);
        return ResponseEntity.ok(Collections.singletonMap("code", "SUCCESS"));
    }
}
