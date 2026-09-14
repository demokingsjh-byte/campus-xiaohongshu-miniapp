package cn.iocoder.yudao.module.campus.controller.admin.esp32;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;
import cn.iocoder.yudao.module.campus.service.esp32.CampusEsp32LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ESP32 助手链路日志")
@RestController
@RequestMapping("/campus/esp32/log")
@Validated
public class CampusEsp32LogController {

    @Resource
    private CampusEsp32LogService logService;

    @GetMapping("/page")
    @Operation(summary = "获得 ESP32 助手链路日志分页")
    @PreAuthorize("@ss.hasPermission('campus:esp32-log:query')")
    public CommonResult<PageResult<Map<String, Object>>> getPage(
            @Valid CampusEsp32LogPageReqVO reqVO) {
        return success(logService.getPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 ESP32 助手链路日志详情")
    @PreAuthorize("@ss.hasPermission('campus:esp32-log:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(logService.get(id));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得 ESP32 助手链路日志汇总")
    @PreAuthorize("@ss.hasPermission('campus:esp32-log:query')")
    public CommonResult<Map<String, Object>> getSummary(
            @Valid CampusEsp32LogPageReqVO reqVO) {
        return success(logService.getSummary(reqVO));
    }

    @GetMapping("/image")
    @Operation(summary = "获得 ESP32 助手日志图片")
    @PreAuthorize("@ss.hasPermission('campus:esp32-log:query')")
    public ResponseEntity<byte[]> getImage(@RequestParam("id") Long id) {
        byte[] image = logService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(image);
    }
}
