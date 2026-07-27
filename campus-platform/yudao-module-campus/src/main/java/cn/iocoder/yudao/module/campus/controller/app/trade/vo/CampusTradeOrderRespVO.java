package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 校园二手订单 Response VO")
@Data
public class CampusTradeOrderRespVO {

    private Long id;
    private String orderNo;
    private Long postId;
    private Long buyerId;
    private Long sellerId;
    private String buyerName;
    private String sellerName;
    private String title;
    private String coverImage;
    private BigDecimal amount;
    private Integer status;
    private String statusText;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;
    private String closeReason;
    private Boolean expired;
}
