package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageSendReqVO;

import java.util.List;

public interface CampusTradeChatService {
    List<CampusTradeMessageRespVO> getMessages(Long orderId, Long userId);

    CampusTradeMessageRespVO sendMessage(Long userId, CampusTradeMessageSendReqVO reqVO);
}
