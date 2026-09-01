package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CampusTradeMessageRespVO {
    private Long id;
    private Long orderId;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String content;
    private LocalDateTime createTime;
}
