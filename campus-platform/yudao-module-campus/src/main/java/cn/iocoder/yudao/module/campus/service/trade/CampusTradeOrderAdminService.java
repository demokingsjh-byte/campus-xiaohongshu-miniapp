package cn.iocoder.yudao.module.campus.service.trade;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeOrderPageReqVO;

import java.util.Map;

public interface CampusTradeOrderAdminService {

    PageResult<Map<String, Object>> getOrderPage(CampusTradeOrderPageReqVO reqVO);

    Map<String, Object> getOrder(Long orderId);

    Map<String, Object> getSummary(Long tenantId);
}
