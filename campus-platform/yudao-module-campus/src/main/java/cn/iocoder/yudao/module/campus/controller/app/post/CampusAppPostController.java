package cn.iocoder.yudao.module.campus.controller.app.post;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentRespVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostInteractionReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostReportReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostRespVO;
import cn.iocoder.yudao.module.campus.service.post.CampusPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 校园社区内容")
@RestController
@RequestMapping("/campus/post")
@Validated
public class CampusAppPostController {

    @Resource
    private CampusPostService campusPostService;

    @PostMapping("/create")
    @Operation(summary = "发布校园内容")
    public CommonResult<CampusPostRespVO> createPost(@Valid @RequestBody CampusPostCreateReqVO reqVO) {
        return success(campusPostService.createPost(getLoginUserId(), reqVO));
    }

    @GetMapping("/get")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获取校园内容详情")
    public CommonResult<CampusPostRespVO> getPost(@RequestParam("id") Long id) {
        return success(campusPostService.getPost(id, getLoginUserId()));
    }

    @GetMapping("/page")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获取校园内容列表")
    public CommonResult<PageResult<CampusPostRespVO>> getPostPage(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return success(campusPostService.getPostPage(getLoginUserId(), tenantId, channel, keyword, pageNo, pageSize));
    }

    @GetMapping("/my-page")
    @Operation(summary = "获取我的发布")
    public CommonResult<PageResult<CampusPostRespVO>> getMyPostPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        return success(campusPostService.getMyPostPage(getLoginUserId(), pageNo, pageSize));
    }

    @GetMapping("/favorite-page")
    @Operation(summary = "获取我的收藏")
    public CommonResult<PageResult<CampusPostRespVO>> getFavoritePostPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        return success(campusPostService.getFavoritePostPage(getLoginUserId(), pageNo, pageSize));
    }

    @GetMapping("/comment-page")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获取帖子评论")
    public CommonResult<PageResult<CampusPostCommentRespVO>> getCommentPage(
            @RequestParam("postId") Long postId,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return success(campusPostService.getCommentPage(postId, getLoginUserId(), pageNo, pageSize));
    }

    @PostMapping("/comment")
    @Operation(summary = "发布帖子评论")
    public CommonResult<CampusPostCommentRespVO> createComment(
            @RequestParam("postId") Long postId,
            @Valid @RequestBody CampusPostCommentCreateReqVO reqVO) {
        return success(campusPostService.createComment(postId, getLoginUserId(), reqVO));
    }

    @PostMapping("/contact-request")
    @Operation(summary = "提交联系发布者申请")
    public CommonResult<Boolean> createContactRequest(@RequestParam("postId") Long postId) {
        campusPostService.createContactRequest(postId, getLoginUserId());
        return success(true);
    }

    @PutMapping("/like")
    @Operation(summary = "点赞或取消点赞")
    public CommonResult<CampusPostRespVO> setLike(@RequestParam("id") Long id,
                                                   @Valid @RequestBody CampusPostInteractionReqVO reqVO) {
        return success(campusPostService.setInteraction(id, getLoginUserId(), "LIKE", reqVO.getActive()));
    }

    @PutMapping("/collect")
    @Operation(summary = "收藏或取消收藏")
    public CommonResult<CampusPostRespVO> setCollect(@RequestParam("id") Long id,
                                                      @Valid @RequestBody CampusPostInteractionReqVO reqVO) {
        return success(campusPostService.setInteraction(id, getLoginUserId(), "COLLECT", reqVO.getActive()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除自己的发布")
    public CommonResult<Boolean> deletePost(@RequestParam("id") Long id) {
        campusPostService.deletePost(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/report")
    @Operation(summary = "举报校园内容")
    public CommonResult<Boolean> reportPost(@RequestParam("id") Long id,
                                             @Valid @RequestBody CampusPostReportReqVO reqVO) {
        campusPostService.reportPost(id, getLoginUserId(), reqVO);
        return success(true);
    }
}
