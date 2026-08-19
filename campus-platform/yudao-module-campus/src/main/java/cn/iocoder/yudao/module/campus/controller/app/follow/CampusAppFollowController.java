package cn.iocoder.yudao.module.campus.controller.app.follow;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.follow.vo.CampusFollowUserRespVO;
import cn.iocoder.yudao.module.campus.service.follow.CampusFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园用户关注")
@RestController
@RequestMapping("/campus/follow")
@Validated
public class CampusAppFollowController {

    @Resource
    private CampusFollowService campusFollowService;

    @PutMapping("/set")
    @Operation(summary = "关注或取消关注用户")
    public CommonResult<Boolean> setFollow(@RequestParam("targetUserId") Long targetUserId,
                                           @RequestParam("active") Boolean active) {
        return success(campusFollowService.setFollow(getLoginUserId(), targetUserId, Boolean.TRUE.equals(active)));
    }

    @GetMapping("/status")
    @Operation(summary = "查询是否已关注用户")
    public CommonResult<Boolean> getFollowStatus(@RequestParam("targetUserId") Long targetUserId) {
        return success(campusFollowService.isFollowing(getLoginUserId(), targetUserId));
    }

    @GetMapping("/count")
    @Operation(summary = "查询我的关注数量")
    public CommonResult<Long> getFollowingCount() {
        return success(campusFollowService.getFollowingCount(getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "查询我的关注列表")
    public CommonResult<PageResult<CampusFollowUserRespVO>> getFollowingPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        return success(campusFollowService.getFollowingPage(getLoginUserId(), pageNo, pageSize));
    }
}
