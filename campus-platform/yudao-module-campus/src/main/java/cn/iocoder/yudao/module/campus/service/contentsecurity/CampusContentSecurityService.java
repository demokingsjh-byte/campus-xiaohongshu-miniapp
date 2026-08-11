package cn.iocoder.yudao.module.campus.service.contentsecurity;

public interface CampusContentSecurityService {

    int SCENE_COMMENT = 2;
    int SCENE_FORUM = 3;

    CampusContentCheckResult checkText(String openid, int scene, String content, String title, String nickname);

    CampusContentCheckResult checkImage(String openid, int scene, String imageUrl);

    boolean isCallbackSignatureValid(String signature, String timestamp, String nonce);

    CampusContentCheckResult parseMediaCallback(String body, String encryptType,
                                                 String msgSignature, String timestamp, String nonce);

    boolean isEnabled();

}
