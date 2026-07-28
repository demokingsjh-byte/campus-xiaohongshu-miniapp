package cn.iocoder.yudao.module.campus.controller.admin.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 校园订单退款结果")
@Data
public class CampusTradeRefundRespVO {

    private Long orderId;
    private String orderNo;
    private Integer orderStatus;
    private String refundNo;
    private String wxRefundId;
    private Integer refundStatus;
    private String refundStatusText;
    private BigDecimal refundAmount;
    private String refundReason;
    private String refundError;
    private LocalDateTime refundedAt;
}
