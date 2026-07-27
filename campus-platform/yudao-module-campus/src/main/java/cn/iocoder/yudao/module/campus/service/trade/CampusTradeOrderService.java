package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderRespVO;

public interface CampusTradeOrderService {

    CampusTradeOrderRespVO createOrder(Long buyerId, CampusTradeOrderCreateReqVO reqVO);

    CampusTradeOrderRespVO getOrder(Long orderId, Long userId);

    PageResult<CampusTradeOrderRespVO> getOrderPage(Long userId, String role, Integer status,
                                                     Integer pageNo, Integer pageSize);

    void cancelOrder(Long orderId, Long userId);
}
