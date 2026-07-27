package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import lombok.Data;

@Data
public class CampusTradeContactRespVO {
    private Long orderId;
    private Integer status;
    private boolean paid;
    private String sellerName;
    private String contact;
}
