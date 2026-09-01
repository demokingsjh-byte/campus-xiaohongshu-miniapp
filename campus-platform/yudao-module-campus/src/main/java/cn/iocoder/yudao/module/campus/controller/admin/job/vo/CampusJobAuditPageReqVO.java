package cn.iocoder.yudao.module.campus.controller.admin.job.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 兼职信息审核分页请求")
@Data
public class CampusJobAuditPageReqVO {

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    @Schema(description = "标题、正文、发布者或联系方式关键词")
    private String keyword;

    @Schema(description = "状态：0待审核 1已通过 2已驳回/下架")
    private Integer status;

    private Long tenantId;
    private String schoolName;
    private String campusName;
    private LocalDateTime createTimeStart;
    private LocalDateTime createTimeEnd;
}
