package cn.iocoder.yudao.module.campus.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 校园小程序校区选项 Response VO")
@Data
public class CampusAnalyticsCampusRespVO {

    private Long tenantId;
    private String name;

}
