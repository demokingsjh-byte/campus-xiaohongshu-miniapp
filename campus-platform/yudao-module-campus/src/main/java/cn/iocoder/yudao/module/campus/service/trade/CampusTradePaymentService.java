package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePaymentStatusRespVO;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeRefundRespVO;

import java.util.Map;

public interface CampusTradePaymentService {
    CampusTradePayRespVO createPayment(Long postId, Long buyerId, String userIp);

    CampusTradePayRespVO createPaymentByOrder(Long orderId, Long buyerId, String userIp);
    CampusTradePaymentStatusRespVO getPaymentStatus(Long orderId, Long buyerId);
    CampusTradeContactRespVO getContact(Long postId, Long buyerId);
    CampusTradeContactRespVO getContactByOrder(Long orderId, Long userId);
    void handleWechatNotify(String body, Map<String, String> headers);
    CampusTradeRefundRespVO refundOrder(Long orderId, String reason, String operator);
    CampusTradeRefundRespVO syncRefund(Long orderId);
    void handleWechatRefundNotify(String body, Map<String, String> headers);
}
