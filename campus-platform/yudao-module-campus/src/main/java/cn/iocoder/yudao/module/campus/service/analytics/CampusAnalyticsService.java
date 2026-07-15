package cn.iocoder.yudao.module.campus.service.analytics;

import cn.iocoder.yudao.module.campus.controller.admin.analytics.vo.CampusAnalyticsOverviewRespVO;
import cn.iocoder.yudao.module.campus.controller.app.analytics.vo.CampusAnalyticsTrackReqVO;

public interface CampusAnalyticsService {

    void track(Long userId, CampusAnalyticsTrackReqVO reqVO);

    CampusAnalyticsOverviewRespVO getOverview(Long tenantId, Integer days);

}
