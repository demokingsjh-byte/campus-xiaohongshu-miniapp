package cn.iocoder.yudao.module.campus.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "用户 App - 代拿代办提交完成请求")
@Data
public class CampusErrandSubmitReqVO {

    @Size(max = 500, message = "完成说明不能超过 500 个字")
    private String note;

    @Size(max = 3, message = "完成凭证最多上传 3 张")
    private List<String> images;
}
