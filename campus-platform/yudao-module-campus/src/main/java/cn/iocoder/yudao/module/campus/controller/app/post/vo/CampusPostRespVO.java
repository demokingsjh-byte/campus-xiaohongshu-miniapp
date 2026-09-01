package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 校园内容 Response VO")
@Data
public class CampusPostRespVO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String type;
    private String channel;
    private String title;
    private String content;
    private String author;
    private String avatar;
    private String avatarText;
    private String school;
    private String campusName;
    private String time;
    private String price;
    private String originalPrice;
    private Integer stockTotal;
    private Integer stockAvailable;
    private Integer soldCount;
    private Integer saleStatus;
    private Boolean soldOut;
    private Boolean downlisted;
    private String location;
    @Schema(description = "商家团购实际地址，仅在详情接口返回")
    private String merchantAddress;
    @Schema(description = "地图位置名称，仅在详情接口返回")
    private String merchantLocationName;
    @Schema(description = "门店纬度，仅供客户端地图导航使用，不直接展示")
    private BigDecimal merchantLatitude;
    @Schema(description = "门店经度，仅供客户端地图导航使用，不直接展示")
    private BigDecimal merchantLongitude;
    private String tradeMode;
    private String visibleRange;
    private List<String> tags;
    private List<String> images;
    private String coverImage;
    private String coverColor;
    private String coverEmoji;
    private String coverLabel;
    private String height;
    private Integer likes;
    private Integer collects;
    private Integer comments;
    private Integer views;
    private Integer status;
    private String auditReason;
    private LocalDateTime auditTime;
    private Boolean liked;
    private Boolean collected;
    private Boolean owner;
    @Schema(description = "发布代拿代办时自动创建的待付款订单编号")
    private Long errandOrderId;
    private LocalDateTime createTime;
}
