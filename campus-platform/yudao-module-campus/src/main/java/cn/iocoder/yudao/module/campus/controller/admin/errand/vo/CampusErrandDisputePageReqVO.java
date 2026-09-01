package cn.iocoder.yudao.module.campus.controller.admin.errand.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Schema(description = "管理后台 - 代拿代办申诉分页请求")
@Data
public class CampusErrandDisputePageReqVO {

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    @Schema(description = "订单号、任务标题、双方昵称关键词")
    private String keyword;

    @Schema(description = "申诉状态：1待处理 2接单人胜诉 3发布人胜诉")
    private Integer status;

    private Long tenantId;
}
