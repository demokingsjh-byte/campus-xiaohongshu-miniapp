package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "用户 App - 发送校园交易消息 Request VO")
@Data
public class CampusTradeMessageSendReqVO {

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容不能超过 500 个字")
    private String content;
}
