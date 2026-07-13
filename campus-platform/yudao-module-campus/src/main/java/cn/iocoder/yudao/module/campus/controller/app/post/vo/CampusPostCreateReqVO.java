package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "用户 App - 创建校园内容 Request VO")
@Data
public class CampusPostCreateReqVO {

    @NotBlank
    @Schema(description = "类型：idle、help、ride、shop、lost、club", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank
    @Size(min = 4, max = 80)
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Size(min = 10, max = 2000)
    @Schema(description = "正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "原价不能小于 0")
    private BigDecimal originalPrice;
    private String location;
    private String tradeMode;
    private String visibleRange;
    private String contact;
    private Boolean anonymous;

    @Size(max = 3)
    private List<String> tags;

    @Size(max = 9)
    private List<String> images;
}
