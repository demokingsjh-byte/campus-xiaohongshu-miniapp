package cn.iocoder.yudao.module.campus.controller.app.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
    private String location;
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
    private Boolean liked;
    private Boolean collected;
    private Boolean owner;
    private LocalDateTime createTime;
}
