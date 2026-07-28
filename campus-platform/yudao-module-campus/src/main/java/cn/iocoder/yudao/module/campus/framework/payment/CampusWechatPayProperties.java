package cn.iocoder.yudao.module.campus.framework.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.wechat-pay")
public class CampusWechatPayProperties {

    private boolean enabled;
    private String appId;
    private String mchId;
    private String apiV3Key;
    private String apiV3KeyPath;
    private String certSerialNo;
    private String privateKeyPath;
    private String publicKeyId;
    private String publicKeyPath;
    private String notifyUrl;
    private String refundNotifyUrl;
}
