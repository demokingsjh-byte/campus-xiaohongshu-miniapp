package cn.iocoder.yudao.module.campus.controller.app.follow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 校园关注用户 Response VO")
@Data
public class CampusFollowUserRespVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private String schoolName;
    private String campusName;
    private Boolean mutual;
    private LocalDateTime followedAt;
}
