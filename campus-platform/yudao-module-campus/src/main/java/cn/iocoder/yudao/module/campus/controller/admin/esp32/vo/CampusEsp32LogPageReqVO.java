package cn.iocoder.yudao.module.campus.controller.admin.esp32.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ESP32 助手链路日志分页请求")
@Data
public class CampusEsp32LogPageReqVO {

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    @Schema(description = "设备编号")
    private String deviceId;

    @Schema(description = "请求编号")
    private String requestId;

    @Schema(description = "状态：CAPTURING、SUBMITTED、MODEL_DONE、SPEAKING、COMPLETED、IGNORED、INTERRUPTED、FAILED、DISCONNECTED")
    private String status;

    private LocalDateTime createTimeStart;
    private LocalDateTime createTimeEnd;
}
