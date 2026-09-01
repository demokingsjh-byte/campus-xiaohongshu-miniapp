package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "用户 App - 代拿代办发起申诉请求")
@Data
public class CampusErrandDisputeReqVO {

    @NotBlank(message = "请填写申诉原因")
    @Size(max = 500, message = "申诉原因不能超过 500 个字")
    private String reason;

    @Size(max = 3, message = "申诉凭证最多上传 3 张")
    private List<String> images;
}
