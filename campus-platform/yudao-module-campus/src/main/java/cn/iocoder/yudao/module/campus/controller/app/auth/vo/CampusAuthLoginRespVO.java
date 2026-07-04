package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 校园小程序登录 Response VO")
@Data
public class CampusAuthLoginRespVO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "令牌过期时间")
    private LocalDateTime expiresTime;

    @Schema(description = "校园用户信息")
    private CampusUserRespVO userInfo;

}
