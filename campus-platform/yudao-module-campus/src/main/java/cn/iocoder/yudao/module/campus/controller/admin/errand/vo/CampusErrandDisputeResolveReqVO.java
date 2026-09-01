package cn.iocoder.yudao.module.campus.controller.admin.errand.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - 代拿代办申诉裁决请求")
@Data
public class CampusErrandDisputeResolveReqVO {

    @NotNull(message = "订单编号不能为空")
    private Long orderId;

    @NotNull(message = "裁决结果不能为空")
    @Schema(description = "2接单人胜诉并结算，3发布人胜诉并退款")
    private Integer result;

    @NotBlank(message = "请填写裁决说明")
    @Size(max = 500, message = "裁决说明不能超过 500 个字")
    private String resolution;
}
