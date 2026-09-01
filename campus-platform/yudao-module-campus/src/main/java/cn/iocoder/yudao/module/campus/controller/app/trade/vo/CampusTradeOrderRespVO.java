package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 校园二手订单 Response VO")
@Data
public class CampusTradeOrderRespVO {

    private Long id;
    private String orderNo;
    private Long postId;
    private Integer bizType;
    private Long buyerId;
    private Long sellerId;
    private String buyerName;
    private String sellerName;
    private String title;
    private String coverImage;
    private BigDecimal amount;
    private Integer status;
    private String statusText;
    private Integer fulfillmentStatus;
    private String fulfillmentStatusText;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptExpiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime submittedAt;
    private String completionNote;
    private List<String> completionImages;
    private LocalDateTime confirmExpiresAt;
    private Integer disputeStatus;
    private String disputeStatusText;
    private String disputeReason;
    private List<String> disputeImages;
    private LocalDateTime disputedAt;
    private LocalDateTime disputeResolvedAt;
    private String disputeResolution;
    private Boolean autoConfirmed;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;
    private String closeReason;
    private Integer refundStatus;
    private BigDecimal incomeAmount;
    private Boolean expired;
}
