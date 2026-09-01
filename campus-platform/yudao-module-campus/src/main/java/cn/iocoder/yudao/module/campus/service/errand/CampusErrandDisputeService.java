package cn.iocoder.yudao.module.campus.service.errand;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.errand.vo.CampusErrandDisputePageReqVO;

import java.util.Map;

public interface CampusErrandDisputeService {

    PageResult<Map<String, Object>> getPage(CampusErrandDisputePageReqVO reqVO);

    Map<String, Object> get(Long orderId);
}
