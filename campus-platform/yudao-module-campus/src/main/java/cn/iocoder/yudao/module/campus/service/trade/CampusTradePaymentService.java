package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;

import java.util.Map;

public interface CampusTradePaymentService {
    CampusTradePayRespVO createPayment(Long postId, Long buyerId, String userIp);
    CampusTradeContactRespVO getContact(Long postId, Long buyerId);
    void handleWechatNotify(String body, Map<String, String> headers);
}
