package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "用户 App - 创建校园内容 Request VO")
@Data
public class CampusPostCreateReqVO {

    @NotBlank
    @Schema(description = "类型：idle、help、confession、ride、shop、lost、club、job", requiredMode = Schema.RequiredMode.REQUIRED)
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

    @Min(value = 1, message = "库存至少为 1 件")
    @Max(value = 999, message = "库存不能超过 999 件")
    @Schema(description = "二手商品库存数量，未填写时默认为 1", example = "2")
    private Integer stockTotal;

    @Size(max = 160, message = "公开位置不能超过 160 个字符")
    @Schema(description = "公开展示的位置；商家团购填写大概位置", example = "北门商业街附近")
    private String location;

    @Size(max = 255, message = "商户实际地址不能超过 255 个字符")
    @Schema(description = "商家团购的实际地址，团购详情展示", example = "北门商业街 18 号 2 楼")
    private String merchantAddress;

    @Size(max = 120, message = "门店位置名称不能超过 120 个字符")
    @Schema(description = "地图选择的门店或地点名称", example = "北门茶铺")
    private String merchantLocationName;

    @DecimalMin(value = "-90.0000000", message = "门店纬度不合法")
    @DecimalMax(value = "90.0000000", message = "门店纬度不合法")
    @Schema(description = "地图选择的门店纬度，仅用于地图查看和导航")
    private BigDecimal merchantLatitude;

    @DecimalMin(value = "-180.0000000", message = "门店经度不合法")
    @DecimalMax(value = "180.0000000", message = "门店经度不合法")
    @Schema(description = "地图选择的门店经度，仅用于地图查看和导航")
    private BigDecimal merchantLongitude;

    private String tradeMode;
    private String visibleRange;

    @Size(max = 20, message = "联系电话不能超过 20 个字符")
    private String contact;
    private Boolean anonymous;

    @Size(max = 3)
    private List<String> tags;

    @Size(max = 9)
    private List<String> images;
}
