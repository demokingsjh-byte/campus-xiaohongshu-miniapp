package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import lombok.Data;

@Data
public class CampusTradePayRespVO {
    private Long orderId;
    private String orderNo;
    private Integer status;
    private String timeStamp;
    private String nonceStr;
    private String packageValue;
    private String signType;
    private String paySign;
}
