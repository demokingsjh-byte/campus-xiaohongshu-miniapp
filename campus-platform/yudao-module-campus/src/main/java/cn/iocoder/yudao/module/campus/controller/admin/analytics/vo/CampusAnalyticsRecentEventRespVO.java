package cn.iocoder.yudao.module.campus.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 校园小程序最近事件 Response VO")
@Data
public class CampusAnalyticsRecentEventRespVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String eventName;
    private String pagePath;
    private LocalDateTime createTime;

}
