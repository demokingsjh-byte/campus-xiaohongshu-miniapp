package cn.iocoder.yudao.module.infra.controller.app.file;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.AppFileUploadReqVO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
@Slf4j
public class AppFileController {

    private static final String CAMPUS_OSS_HOST = "dylsjh.oss-cn-shenzhen.aliyuncs.com";

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    @PermitAll
    public CommonResult<String> uploadFile(@Valid AppFileUploadReqVO uploadReqVO) throws Exception {
        MultipartFile file = uploadReqVO.getFile();
        byte[] content = IoUtil.readBytes(file.getInputStream());
        // 微信开发者工具可能把 http://tmp/xxx.jpg 作为 multipart 原始文件名。
        // 只保留最后一段安全文件名，避免临时路径被编码后成为 OSS 对象名。
        String originalFilename = file.getOriginalFilename();
        String safeFilename = originalFilename == null ? null
                : FileUtil.getName(originalFilename.replace('\\', '/'));
        return success(fileService.createFile(content, safeFilename,
                uploadReqVO.getDirectory(), file.getContentType()));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "获取文件预签名地址（上传）", description = "模式二：前端上传文件：用于前端直接上传七牛、阿里云 OSS 等文件存储器")
    @Parameters({
            @Parameter(name = "name", description = "文件名称", required = true),
            @Parameter(name = "directory", description = "文件目录")
    })
    public CommonResult<FilePresignedUrlRespVO> getFilePresignedUrl(
            @RequestParam("name") String name,
            @RequestParam(value = "directory", required = false) String directory) {
        return success(fileService.presignPutUrl(name, directory));
    }

    @PostMapping("/create")
    @Operation(summary = "创建文件", description = "模式二：前端上传文件：配合 presigned-url 接口，记录上传了上传的文件")
    @PermitAll
    public CommonResult<Long> createFile(@Valid @RequestBody FileCreateReqVO createReqVO) {
        return success(fileService.createFile(createReqVO));
    }

    @GetMapping("/proxy")
    @PermitAll
    // Mini-program image requests cannot attach tenant headers, so this public media bridge must bypass tenant validation.
    @TenantIgnore
    @Operation(summary = "代理读取校园媒体文件", description = "让微信体验版通过已备案的业务域名读取私有 OSS 文件")
    public void proxyCampusMedia(@RequestParam("url") String sourceUrl, HttpServletResponse response) throws Exception {
        URI source = URI.create(sourceUrl);
        if (!"https".equalsIgnoreCase(source.getScheme())
                || !CAMPUS_OSS_HOST.equalsIgnoreCase(source.getHost())
                || source.getRawPath() == null || !source.getRawPath().startsWith("/campus/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "不支持的媒体地址");
            return;
        }

        // 数据库只需要保存稳定对象地址；每次读取时由服务端生成短期有效签名。
        String stableUrl = source.getScheme() + "://" + source.getHost() + source.getRawPath();
        URL signedUrl = new URL(fileService.presignGetUrl(stableUrl, 300));
        HttpURLConnection connection = (HttpURLConnection) signedUrl.openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod("GET");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体文件读取失败");
                return;
            }
            String contentType = connection.getContentType();
            response.setContentType(StrUtil.blankToDefault(contentType, "application/octet-stream"));
            if (connection.getContentLengthLong() >= 0) {
                response.setContentLengthLong(connection.getContentLengthLong());
            }
            response.setHeader("Cache-Control", "public, max-age=3600");
            IoUtil.copy(connection.getInputStream(), response.getOutputStream());
        } finally {
            connection.disconnect();
        }
    }

}
