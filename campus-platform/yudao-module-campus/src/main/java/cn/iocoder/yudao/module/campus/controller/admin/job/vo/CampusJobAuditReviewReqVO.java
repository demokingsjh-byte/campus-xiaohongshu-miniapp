package cn.iocoder.yudao.module.campus.controller.admin.job.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - 兼职信息人工审核请求")
@Data
public class CampusJobAuditReviewReqVO {

    @NotNull(message = "兼职信息编号不能为空")
    private Long id;

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Size(max = 200, message = "审核意见不能超过 200 个字")
    private String reason;
}
