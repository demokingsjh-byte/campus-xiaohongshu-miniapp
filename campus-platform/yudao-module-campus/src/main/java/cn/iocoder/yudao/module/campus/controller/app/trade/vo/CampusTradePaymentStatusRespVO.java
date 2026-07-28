package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CampusTradePaymentStatusRespVO {
    private Long orderId;
    private String orderNo;
    private Integer status;
    private boolean paid;
    private boolean retryable;
    private String wechatTradeState;
    private String wechatQueryError;
    private LocalDateTime wechatQueriedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
}
