package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.List;

@Schema(description = "用户 App - 发布校园帖子评论 Request VO")
@Data
public class CampusPostCommentCreateReqVO {

    @Schema(description = "回复的评论 ID，为空表示发布一级评论")
    private Long parentId;

    @Schema(description = "被回复用户 ID")
    private Long replyToUserId;

    @Schema(description = "被艾特用户 ID")
    private List<Long> mentionUserIds = Collections.emptyList();

    @Schema(description = "评论图片地址，最多 3 张")
    @Size(max = 3, message = "评论图片最多 3 张")
    private List<String> images = Collections.emptyList();

    @Size(max = 300, message = "评论内容不能超过 300 个字")
    @Schema(description = "评论内容")
    private String content;
}
