package cn.iocoder.yudao.module.campus.service.contentsecurity;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaMediaAsyncCheckResult;
import cn.binarywang.wx.miniapp.bean.WxMaMessage;
import cn.binarywang.wx.miniapp.bean.security.WxMaMediaSecCheckCheckRequest;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckRequest;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckResponse;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CampusContentSecurityServiceImpl implements CampusContentSecurityService {

    private final WxMaService wxMaService;

    @Value("${campus.content-security.enabled:false}")
    private boolean enabled;

    public CampusContentSecurityServiceImpl(WxMaService wxMaService) {
        this.wxMaService = wxMaService;
    }

    @Override
    public CampusContentCheckResult checkText(String openid, int scene, String content,
                                               String title, String nickname) {
        if (!enabled || StrUtil.isBlank(content)) {
            return CampusContentCheckResult.pass();
        }
        if (StrUtil.isBlank(openid)) {
            return errorResult("missing_openid");
        }
        try {
            WxMaMsgSecCheckCheckRequest request = WxMaMsgSecCheckCheckRequest.builder()
                    .version("2")
                    .openid(openid)
                    .scene(scene)
                    .content(content)
                    .title(StrUtil.blankToDefault(title, ""))
                    .nickname(StrUtil.blankToDefault(nickname, ""))
                    .build();
            WxMaMsgSecCheckCheckResponse response = wxMaService.getSecurityService().checkMessage(request);
            String suggest = response.getResult() == null ? CampusContentCheckResult.ERROR
                    : normalizeSuggest(response.getResult().getSuggest());
            String label = response.getResult() == null ? "empty_result" : response.getResult().getLabel();
            return new CampusContentCheckResult(suggest, StrUtil.blankToDefault(label, ""),
                    StrUtil.blankToDefault(response.getTraceId(), ""), JsonUtils.toJsonString(response));
        } catch (WxErrorException | RuntimeException ex) {
            log.warn("WeChat text content check failed: {}", ex.getMessage());
            return errorResult("wechat_request_failed");
        }
    }

    @Override
    public CampusContentCheckResult checkImage(String openid, int scene, String imageUrl) {
        if (!enabled) {
            return CampusContentCheckResult.pass();
        }
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(imageUrl)) {
            return errorResult(StrUtil.isBlank(openid) ? "missing_openid" : "missing_image_url");
        }
        try {
            WxMaMediaSecCheckCheckRequest request = WxMaMediaSecCheckCheckRequest.builder()
                    .mediaUrl(imageUrl)
                    .mediaType(2)
                    .version(2)
                    .openid(openid)
                    .scene(scene)
                    .build();
            WxMaMediaAsyncCheckResult response = wxMaService.getSecurityService().mediaCheckAsync(request);
            String suggest = response.getResult() == null ? CampusContentCheckResult.PENDING
                    : normalizeSuggest(response.getResult().getSuggest());
            String label = response.getResult() == null ? "" : response.getResult().getLabel();
            String traceId = StrUtil.blankToDefault(response.getTraceId(), "");
            if (CampusContentCheckResult.PENDING.equals(suggest) && StrUtil.isBlank(traceId)) {
                suggest = CampusContentCheckResult.ERROR;
                label = "missing_trace_id";
            }
            return new CampusContentCheckResult(suggest, StrUtil.blankToDefault(label, ""), traceId,
                    JsonUtils.toJsonString(response));
        } catch (WxErrorException | RuntimeException ex) {
            log.warn("WeChat image content check failed: {}", ex.getMessage());
            return errorResult("wechat_request_failed");
        }
    }

    @Override
    public boolean isCallbackSignatureValid(String signature, String timestamp, String nonce) {
        return StrUtil.isAllNotBlank(signature, timestamp, nonce)
                && wxMaService.checkSignature(timestamp, nonce, signature);
    }

    @Override
    public CampusContentCheckResult parseMediaCallback(String body, String encryptType,
                                                        String msgSignature, String timestamp, String nonce) {
        if (StrUtil.isBlank(body)) {
            return errorResult("empty_callback");
        }
        try {
            boolean xml = body.trim().startsWith("<");
            WxMaMessage message;
            if ("aes".equalsIgnoreCase(encryptType)) {
                message = xml
                        ? WxMaMessage.fromEncryptedXml(body, wxMaService.getWxMaConfig(), timestamp, nonce, msgSignature)
                        : WxMaMessage.fromEncryptedJson(body, wxMaService.getWxMaConfig());
            } else {
                message = xml ? WxMaMessage.fromXml(body) : WxMaMessage.fromJson(body);
            }
            if (message == null || message.getResult() == null || StrUtil.isBlank(message.getTraceId())) {
                return errorResult("invalid_callback");
            }
            return new CampusContentCheckResult(normalizeSuggest(message.getResult().getSuggest()),
                    StrUtil.blankToDefault(message.getResult().getLabel(), ""), message.getTraceId(), body);
        } catch (RuntimeException ex) {
            log.warn("Unable to parse WeChat media check callback: {}", ex.getMessage());
            return errorResult("invalid_callback");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private static CampusContentCheckResult errorResult(String label) {
        return new CampusContentCheckResult(CampusContentCheckResult.ERROR, label, "", "");
    }

    private static String normalizeSuggest(String suggest) {
        if (StrUtil.isBlank(suggest)) {
            return CampusContentCheckResult.ERROR;
        }
        switch (suggest.toLowerCase()) {
            case "pass":
                return CampusContentCheckResult.PASS;
            case "review":
                return CampusContentCheckResult.REVIEW;
            case "risky":
                return CampusContentCheckResult.RISKY;
            default:
                return CampusContentCheckResult.ERROR;
        }
    }

}
