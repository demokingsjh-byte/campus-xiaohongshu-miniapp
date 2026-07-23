package cn.iocoder.yudao.module.campus.controller.app.notification;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.notification.vo.CampusNotificationRespVO;
import cn.iocoder.yudao.module.campus.service.notification.CampusNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园通知")
@RestController
@RequestMapping("/campus/notification")
public class CampusAppNotificationController {

    @Resource
    private CampusNotificationService campusNotificationService;

    @GetMapping("/page")
    @Operation(summary = "获得校园通知分页")
    public CommonResult<PageResult<CampusNotificationRespVO>> getPage(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        return success(campusNotificationService.getPage(getLoginUserId(), type, pageNo, pageSize));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获得校园通知未读数")
    public CommonResult<Long> getUnreadCount() {
        return success(campusNotificationService.getUnreadCount(getLoginUserId()));
    }

    @PutMapping("/read")
    @Operation(summary = "标记校园通知已读")
    public CommonResult<Boolean> markRead(@RequestParam("id") Long id) {
        campusNotificationService.markRead(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记全部校园通知已读")
    public CommonResult<Boolean> markAllRead() {
        campusNotificationService.markAllRead(getLoginUserId());
        return success(true);
    }
}
