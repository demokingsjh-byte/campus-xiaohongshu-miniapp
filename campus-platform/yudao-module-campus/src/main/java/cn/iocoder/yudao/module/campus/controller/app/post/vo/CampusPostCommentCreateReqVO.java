package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "用户 App - 发布校园帖子评论 Request VO")
@Data
public class CampusPostCommentCreateReqVO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 300, message = "评论内容不能超过 300 个字")
    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
