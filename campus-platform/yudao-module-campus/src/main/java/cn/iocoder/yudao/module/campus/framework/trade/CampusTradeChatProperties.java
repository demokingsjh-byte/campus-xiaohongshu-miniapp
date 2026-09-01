package cn.iocoder.yudao.module.campus.framework.trade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "campus.trade-chat")
public class CampusTradeChatProperties {

    /**
     * 交易会话默认拦截的高风险词。部署环境可通过
     * campus.trade-chat.sensitive-words 覆盖或追加业务词库。
     */
    private List<String> sensitiveWords = new ArrayList<>(Arrays.asList(
            "裸聊", "代考", "枪支", "毒品", "赌博", "博彩", "洗钱",
            "刷单返利", "校园贷", "银行卡密码", "支付密码", "短信验证码"
    ));
}
