package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 校园小程序用户信息 Response VO")
@Data
public class CampusUserRespVO {

    @Schema(description = "用户编号")
    private Long id;

    @Schema(description = "微信 openid")
    private String openid;

    @Schema(description = "微信 unionid")
    private String unionid;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "学校名称")
    private String schoolName;

    @Schema(description = "校区名称")
    private String campusName;

    @Schema(description = "用户角色，student、merchant、agent")
    private String roleType;

    @Schema(description = "是否已绑定手机号")
    private Boolean mobileBound;

    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

}
