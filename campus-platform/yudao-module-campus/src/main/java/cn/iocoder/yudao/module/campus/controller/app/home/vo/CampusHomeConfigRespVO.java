package cn.iocoder.yudao.module.campus.controller.app.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "用户 App - 校园首页配置 Response VO")
@Data
public class CampusHomeConfigRespVO {

    @Schema(description = "搜索框提示文案", example = "搜索校园新鲜事")
    private String searchPlaceholder;

    @Schema(description = "首页公告；空字符串表示不展示")
    private String notice;

    @Schema(description = "是否显示分类图标", example = "true")
    private Boolean categoryIconVisible;

    @Schema(description = "是否显示分类文字", example = "true")
    private Boolean categoryTitleVisible;

    @Schema(description = "首页分类导航")
    private List<Category> categories;

    @Schema(description = "用户 App - 校园首页分类")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {

        @Schema(description = "稳定分类标识", example = "idle")
        private String key;

        @Schema(description = "显示名称", example = "二手闲置")
        private String title;

        @Schema(description = "内容频道；推荐分类使用推荐", example = "二手")
        private String channel;

        @Schema(description = "图标；支持小程序本地资源路径、Emoji 或完整图片地址",
                example = "/static/images/home-prototype/category-idle.png")
        private String icon;

        @Schema(description = "点击发布时使用的内容类型", example = "idle")
        private String publishType;

        @Schema(description = "该分类是否显示图标；为空时默认显示", example = "true")
        private Boolean iconVisible;

        @Schema(description = "该分类是否显示文字；为空时默认显示", example = "true")
        private Boolean titleVisible;

    }

}
