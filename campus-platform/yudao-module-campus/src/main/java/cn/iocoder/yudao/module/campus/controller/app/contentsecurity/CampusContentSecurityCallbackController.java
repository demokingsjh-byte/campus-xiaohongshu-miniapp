package cn.iocoder.yudao.module.campus.controller.app.contentsecurity;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.service.contentsecurity.CampusContentCheckResult;
import cn.iocoder.yudao.module.campus.service.contentsecurity.CampusContentSecurityService;
import cn.iocoder.yudao.module.campus.service.post.CampusPostService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;

@Hidden
@RestController
@RequestMapping("/campus/content-security/wechat/callback")
@TenantIgnore
@PermitAll
public class CampusContentSecurityCallbackController {

    private final CampusContentSecurityService contentSecurityService;
    private final CampusPostService campusPostService;

    public CampusContentSecurityCallbackController(CampusContentSecurityService contentSecurityService,
                                                    CampusPostService campusPostService) {
        this.contentSecurityService = contentSecurityService;
        this.campusPostService = campusPostService;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("signature") String signature,
                                         @RequestParam("timestamp") String timestamp,
                                         @RequestParam("nonce") String nonce,
                                         @RequestParam("echostr") String echoStr) {
        if (!contentSecurityService.isCallbackSignatureValid(signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
        }
        return ResponseEntity.ok(echoStr);
    }

    @PostMapping
    public ResponseEntity<String> callback(
            @RequestBody String body,
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "msg_signature", required = false) String msgSignature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "encrypt_type", required = false) String encryptType) {
        boolean encrypted = "aes".equalsIgnoreCase(encryptType);
        if (!encrypted && !contentSecurityService.isCallbackSignatureValid(signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
        }
        CampusContentCheckResult result = contentSecurityService.parseMediaCallback(
                body, encryptType, msgSignature, timestamp, nonce);
        if (StrUtil.isBlank(result.getTraceId()) || CampusContentCheckResult.ERROR.equals(result.getSuggest())) {
            return ResponseEntity.badRequest().body("invalid callback");
        }
        campusPostService.handleMediaAuditCallback(result.getTraceId(), result.getSuggest(),
                result.getLabel(), result.getRawResult());
        return ResponseEntity.ok("success");
    }

}
