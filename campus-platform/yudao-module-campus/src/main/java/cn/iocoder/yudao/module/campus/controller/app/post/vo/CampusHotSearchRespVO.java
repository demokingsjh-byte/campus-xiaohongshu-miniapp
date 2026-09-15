package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 校园热搜 Response VO")
@Data
public class CampusHotSearchRespVO {

    @Schema(description = "可直接用于搜索的标签关键词", example = "校园卡")
    private String keyword;

    @Schema(description = "综合热度值（帖子互动热度、标签频次和时间衰减）", example = "126")
    private Long heat;

    @Schema(description = "统计周期内使用该标签的有效帖子数", example = "8")
    private Integer postCount;
}
