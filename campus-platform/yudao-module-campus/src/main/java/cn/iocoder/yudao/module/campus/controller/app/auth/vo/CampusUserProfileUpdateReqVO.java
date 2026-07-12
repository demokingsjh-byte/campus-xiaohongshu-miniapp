package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 校园小程序用户资料更新 Request VO")
@Data
public class CampusUserProfileUpdateReqVO {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "学校名称")
    private String schoolName;

    @Schema(description = "校区名称")
    private String campusName;

    @Schema(description = "年级", example = "2023级")
    private String grade;

    @Schema(description = "性别：不公开、男、女")
    private String gender;

    @Schema(description = "用户角色，student、merchant、agent")
    private String roleType;

}
