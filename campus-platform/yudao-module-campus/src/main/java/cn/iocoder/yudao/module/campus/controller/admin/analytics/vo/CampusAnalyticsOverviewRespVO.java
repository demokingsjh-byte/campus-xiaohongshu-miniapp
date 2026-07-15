package cn.iocoder.yudao.module.campus.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 校园小程序数据看板 Response VO")
@Data
public class CampusAnalyticsOverviewRespVO {

    private CampusAnalyticsSummaryRespVO today;
    private CampusAnalyticsSummaryRespVO yesterday;
    private List<CampusAnalyticsTrendRespVO> trend;
    private List<CampusAnalyticsRankRespVO> topEvents;
    private List<CampusAnalyticsRankRespVO> topPages;
    private List<CampusAnalyticsRecentEventRespVO> recentEvents;
    private List<CampusAnalyticsCampusRespVO> campuses;
    private Integer onlineWindowMinutes;
    private LocalDateTime serverTime;

}
