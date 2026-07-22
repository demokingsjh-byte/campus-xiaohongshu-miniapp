package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "用户 App - 举报帖子评论 Request VO")
@Data
public class CampusPostCommentReportReqVO {

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 32, message = "举报原因不能超过 32 个字符")
    private String reason;

    @Size(max = 300, message = "补充说明不能超过 300 个字符")
    private String detail;
}
