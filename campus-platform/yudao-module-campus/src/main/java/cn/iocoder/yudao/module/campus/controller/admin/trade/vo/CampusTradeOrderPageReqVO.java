package cn.iocoder.yudao.module.campus.controller.admin.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 校园订单分页请求")
@Data
public class CampusTradeOrderPageReqVO {

    @Schema(description = "页码")
    @Min(1)
    private Integer pageNo = 1;

    @Schema(description = "每页数量")
    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "商品、买家、卖家或手机号关键词")
    private String keyword;

    @Schema(description = "订单状态：0待付款 1已付款 2已完成 3已关闭 4已退款")
    private Integer status;

    @Schema(description = "退款状态：0未退款 1处理中 2成功 3失败")
    private Integer refundStatus;

    @Schema(description = "校园租户编号")
    private Long tenantId;

    @Schema(description = "创建时间起")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间止")
    private LocalDateTime createTimeEnd;
}
