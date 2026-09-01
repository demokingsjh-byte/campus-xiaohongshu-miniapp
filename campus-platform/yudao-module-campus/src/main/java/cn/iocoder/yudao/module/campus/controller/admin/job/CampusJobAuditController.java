package cn.iocoder.yudao.module.campus.controller.admin.job;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditPageReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditReviewReqVO;
import cn.iocoder.yudao.module.campus.service.job.CampusJobAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 兼职信息审核")
@RestController
@RequestMapping("/campus/job-audit")
@Validated
public class CampusJobAuditController {

    @Resource
    private CampusJobAuditService jobAuditService;

    @GetMapping("/page")
    @Operation(summary = "获得兼职信息审核分页")
    @PreAuthorize("@ss.hasPermission('campus:job-audit:query')")
    public CommonResult<PageResult<Map<String, Object>>> getPage(@Valid CampusJobAuditPageReqVO reqVO) {
        return success(jobAuditService.getPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得兼职信息审核详情")
    @PreAuthorize("@ss.hasPermission('campus:job-audit:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(jobAuditService.get(id));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得兼职信息审核汇总")
    @PreAuthorize("@ss.hasPermission('campus:job-audit:query')")
    public CommonResult<Map<String, Object>> getSummary(
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        return success(jobAuditService.getSummary(tenantId));
    }

    @PostMapping("/review")
    @Operation(summary = "人工审核兼职信息")
    @PreAuthorize("@ss.hasPermission('campus:job-audit:review')")
    public CommonResult<Boolean> review(@Valid @RequestBody CampusJobAuditReviewReqVO reqVO) {
        jobAuditService.review(reqVO, getLoginUserId());
        return success(true);
    }
}
