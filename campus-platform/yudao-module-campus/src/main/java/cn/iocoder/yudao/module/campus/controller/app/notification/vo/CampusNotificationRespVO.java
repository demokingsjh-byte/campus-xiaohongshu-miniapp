package cn.iocoder.yudao.module.campus.controller.app.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 校园通知 Response VO")
@Data
public class CampusNotificationRespVO {

    private Long id;
    private String type;
    private String eventType;
    private String actorNickname;
    private String actorAvatar;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private String time;
    private Boolean read;
    private String targetType;
    private Long targetId;
}
