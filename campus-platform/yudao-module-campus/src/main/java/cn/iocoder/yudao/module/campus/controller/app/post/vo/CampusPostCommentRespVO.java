package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 校园帖子评论 Response VO")
@Data
public class CampusPostCommentRespVO {

    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private Long replyToUserId;
    private String replyToAuthor;
    private String author;
    private String avatar;
    private String avatarText;
    private String content;
    private List<Long> mentionUserIds;
    private List<String> images;
    private String time;
    private Boolean owner;
    private Integer likeCount;
    private Integer replyCount;
    @Schema(description = "审核状态：0 审核中，1 已通过，2 未通过")
    private Integer status;
    private Boolean liked;
    private LocalDateTime createTime;
}
