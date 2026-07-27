package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "用户 App - 创建校园二手订单 Request VO")
@Data
public class CampusTradeOrderCreateReqVO {

    @Schema(description = "商品帖子编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "商品不能为空")
    private Long postId;
}
