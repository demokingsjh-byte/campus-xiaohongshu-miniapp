package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "用户 App - 校园小程序手机号绑定 Request VO")
@Data
public class CampusPhoneBindReqVO {

    @Schema(description = "微信手机号组件返回的 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号授权 code 不能为空")
    private String phoneCode;

}
