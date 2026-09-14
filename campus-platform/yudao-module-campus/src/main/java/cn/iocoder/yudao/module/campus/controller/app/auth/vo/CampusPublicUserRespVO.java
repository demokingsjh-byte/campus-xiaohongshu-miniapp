package cn.iocoder.yudao.module.campus.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "用户 App - 校园用户公开主页 Response VO")
@Data
public class CampusPublicUserRespVO {

    @Schema(description = "用户编号")
    private Long userId;

    @Schema(description = "所属校园租户编号")
    private Long tenantId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "学校名称")
    private String schoolName;

    @Schema(description = "校区名称")
    private String campusName;

    @Schema(description = "年级")
    private String grade;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "公开展示省份")
    private String province;

    @Schema(description = "粉丝数量")
    private Long followerCount;

    @Schema(description = "关注数量")
    private Long followingCount;

    @Schema(description = "当前登录用户是否已关注")
    private Boolean followed;

    @Schema(description = "是否为当前登录用户本人")
    private Boolean self;

    @Schema(description = "各发布类型的公开内容数量")
    private Map<String, Long> postCounts;

}
