package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "用户 App - 校园小程序微信静默登录 Request VO")
@Data
public class CampusWechatLoginReqVO {

    @Schema(description = "wx.login 返回的临时 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录 code 不能为空")
    private String code;

    @Schema(description = "入口 scene，用于记录二维码、邀请等来源")
    private String scene;

    @Schema(description = "邀请人用户编号")
    private Long inviterUserId;

    @Schema(description = "当前校区租户编号")
    private Long tenantId;

}
