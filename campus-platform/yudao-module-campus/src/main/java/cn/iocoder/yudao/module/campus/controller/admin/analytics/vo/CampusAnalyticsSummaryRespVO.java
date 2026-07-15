package cn.iocoder.yudao.module.campus.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 校园小程序统计摘要 Response VO")
@Data
public class CampusAnalyticsSummaryRespVO {

    private Long totalUsers;
    private Long registeredUsers;
    private Long onlineUsers;
    private Long activeUsers;
    private Long postCount;
    private Long orderCount;
    private Long paidOrderCount;
    private BigDecimal revenueAmount;
    private Long eventCount;

}
