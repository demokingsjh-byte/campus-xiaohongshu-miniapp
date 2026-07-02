package cn.iocoder.yudao.module.campus.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.service.CampusCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 校园基础模块")
@RestController
@RequestMapping("/campus/{resource}")
@Validated
public class CampusAdminController {

    @Resource
    private CampusCrudService campusCrudService;

    @PostMapping("/create")
    @Operation(summary = "创建校园基础数据")
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':create')")
    public CommonResult<Long> create(@PathVariable String resource,
                                     @Valid @RequestBody Map<String, Object> reqVO) {
        return success(campusCrudService.create(resource, reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改校园基础数据")
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':update')")
    public CommonResult<Boolean> update(@PathVariable String resource,
                                        @Valid @RequestBody Map<String, Object> reqVO) {
        campusCrudService.update(resource, reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除校园基础数据")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':delete')")
    public CommonResult<Boolean> delete(@PathVariable String resource,
                                        @RequestParam("id") Long id) {
        campusCrudService.delete(resource, id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除校园基础数据")
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':delete')")
    public CommonResult<Boolean> deleteList(@PathVariable String resource,
                                            @RequestParam("ids") @NotEmpty List<Long> ids) {
        campusCrudService.deleteList(resource, ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得校园基础数据")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':query')")
    public CommonResult<Map<String, Object>> get(@PathVariable String resource,
                                                 @RequestParam("id") Long id) {
        return success(campusCrudService.get(resource, id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得校园基础数据分页")
    @PreAuthorize("@ss.hasPermission('campus:' + #resource + ':query')")
    public CommonResult<PageResult<Map<String, Object>>> page(@PathVariable String resource,
                                                              @RequestParam Map<String, Object> params) {
        return success(campusCrudService.page(resource, params));
    }
}
