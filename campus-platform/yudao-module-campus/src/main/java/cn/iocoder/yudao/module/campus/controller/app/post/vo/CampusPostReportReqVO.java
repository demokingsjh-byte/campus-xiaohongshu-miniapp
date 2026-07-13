package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "用户 App - 举报校园内容 Request VO")
@Data
public class CampusPostReportReqVO {

    @NotBlank(message = "请选择举报原因")
    @Size(max = 32, message = "举报原因不能超过 32 个字符")
    @Schema(description = "举报原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "广告诈骗")
    private String reason;

    @Size(max = 300, message = "补充说明不能超过 300 个字符")
    @Schema(description = "补充说明", example = "对方要求跳转陌生平台转账")
    private String detail;
}
