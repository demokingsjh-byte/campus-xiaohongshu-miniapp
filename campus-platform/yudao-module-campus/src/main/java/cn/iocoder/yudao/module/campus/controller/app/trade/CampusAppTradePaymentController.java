package cn.iocoder.yudao.module.campus.controller.app.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePaymentStatusRespVO;
import cn.iocoder.yudao.module.campus.service.trade.CampusTradePaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CampusAppTradePaymentController.class);

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
    public CommonResult<CampusTradeContactRespVO> contact(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "postId", required = false) Long postId) {
        if (orderId != null) {
            return success(paymentService.getContactByOrder(orderId, getLoginUserId()));
        }
        return success(paymentService.getContact(postId, getLoginUserId()));
    }

    @PostMapping("/wechat/notify")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<Map<String, String>> notify(@RequestBody String body, HttpServletRequest request) {
        Map<String, String> headers = readHeaders(request);
        String requestId = request.getHeader("Wechatpay-Request-Id");
        log.info("WeChat payment notify received, requestId={}, remoteIp={}, bodyLength={},"
                        + " signaturePresent={}, serialPresent={}, noncePresent={}, timestampPresent={}",
                requestId, ServletUtils.getClientIP(request), body == null ? 0 : body.length(),
                hasText(headers, "Wechatpay-Signature"), hasText(headers, "Wechatpay-Serial"),
                hasText(headers, "Wechatpay-Nonce"), hasText(headers, "Wechatpay-Timestamp"));
        try {
            paymentService.handleWechatNotify(body, headers);
            log.info("WeChat payment notify processed successfully, requestId={}", requestId);
            return ResponseEntity.ok(Collections.singletonMap("code", "SUCCESS"));
        } catch (RuntimeException ex) {
            log.error("WeChat payment notify processing failed, requestId={}, remoteIp={}",
                    requestId, ServletUtils.getClientIP(request), ex);
            throw ex;
        }
    }

    @PostMapping("/wechat/refund-notify")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<Map<String, String>> refundNotify(@RequestBody String body,
                                                            HttpServletRequest request) {
        Map<String, String> headers = readHeaders(request);
        String requestId = request.getHeader("Wechatpay-Request-Id");
        log.info("WeChat refund notify received, requestId={}, remoteIp={}, bodyLength={}",
                requestId, ServletUtils.getClientIP(request), body == null ? 0 : body.length());
        try {
            paymentService.handleWechatRefundNotify(body, headers);
            log.info("WeChat refund notify processed successfully, requestId={}", requestId);
            return ResponseEntity.ok(Collections.singletonMap("code", "SUCCESS"));
        } catch (RuntimeException ex) {
            log.error("WeChat refund notify processing failed, requestId={}, remoteIp={}",
                    requestId, ServletUtils.getClientIP(request), ex);
            throw ex;
        }
    }

    private Map<String, String> readHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private boolean hasText(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() != null && !entry.getValue().trim().isEmpty();
            }
        }
        return false;
    }
}
