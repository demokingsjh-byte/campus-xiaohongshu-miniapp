package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandDisputeReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandSubmitReqVO;

public interface CampusTradeOrderService {

    CampusTradeOrderRespVO createOrder(Long buyerId, CampusTradeOrderCreateReqVO reqVO);

    CampusTradeOrderRespVO getOrder(Long orderId, Long userId);

    PageResult<CampusTradeOrderRespVO> getOrderPage(Long userId, String role, Integer status,
                                                     Integer pageNo, Integer pageSize);

    void cancelOrder(Long orderId, Long userId);

    CampusTradeOrderRespVO createErrandOrder(Long publisherId, Long postId);

    CampusTradeOrderRespVO getErrandOrderByPost(Long postId, Long userId);

    CampusTradeOrderRespVO acceptErrandOrder(Long orderId, Long helperId);

    CampusTradeOrderRespVO submitErrandOrder(Long orderId, Long helperId, CampusErrandSubmitReqVO reqVO);

    CampusTradeOrderRespVO confirmErrandOrder(Long orderId, Long publisherId);

    CampusTradeOrderRespVO disputeErrandOrder(Long orderId, Long publisherId, CampusErrandDisputeReqVO reqVO);

    void resolveErrandDispute(Long orderId, Integer result, String resolution, Long adminId);

    CampusTradeOrderRespVO cancelErrandOrder(Long orderId, Long publisherId);
}
