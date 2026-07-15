package cn.iocoder.yudao.module.campus.controller.app.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "用户 App - 小程序埋点上报 Request VO")
@Data
public class CampusAnalyticsTrackReqVO {

    @Schema(description = "客户端会话编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "miniapp-1721020000-a8f3")
    @NotBlank(message = "会话编号不能为空")
    @Size(max = 64, message = "会话编号不能超过 64 个字符")
    private String sessionId;

    @Schema(description = "事件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "PAGE_VIEW")
    @NotBlank(message = "事件名称不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "事件名称格式不正确")
    private String eventName;

    @Schema(description = "页面路径", example = "pages/index/index")
    @Size(max = 255, message = "页面路径不能超过 255 个字符")
    private String pagePath;

    @Schema(description = "事件扩展属性")
    private Map<String, Object> properties;

    @Schema(description = "客户端发生时间")
    private LocalDateTime clientTime;

    @Schema(description = "小程序版本")
    @Size(max = 32, message = "客户端版本不能超过 32 个字符")
    private String clientVersion;

    @Schema(description = "微信入口场景值")
    @Size(max = 64, message = "入口场景值不能超过 64 个字符")
    private String sourceScene;

}
