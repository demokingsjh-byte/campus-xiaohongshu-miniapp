package cn.iocoder.yudao.module.campus.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 校园小程序每日趋势 Response VO")
@Data
public class CampusAnalyticsTrendRespVO {

    private String date;
    private Long registeredUsers = 0L;
    private Long activeUsers = 0L;
    private Long postCount = 0L;
    private Long orderCount = 0L;
    private Long paidOrderCount = 0L;
    private BigDecimal revenueAmount = BigDecimal.ZERO;
    private Long eventCount = 0L;

}
