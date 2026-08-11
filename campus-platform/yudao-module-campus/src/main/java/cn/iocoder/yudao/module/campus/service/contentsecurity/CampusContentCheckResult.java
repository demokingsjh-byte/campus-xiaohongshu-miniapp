package cn.iocoder.yudao.module.campus.service.contentsecurity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A normalized result from WeChat content security.
 */
@Data
@AllArgsConstructor
public class CampusContentCheckResult {

    public static final String PASS = "PASS";
    public static final String PENDING = "PENDING";
    public static final String REVIEW = "REVIEW";
    public static final String RISKY = "RISKY";
    public static final String ERROR = "ERROR";

    private String suggest;
    private String label;
    private String traceId;
    private String rawResult;

    public static CampusContentCheckResult pass() {
        return new CampusContentCheckResult(PASS, "", "", "");
    }

    public boolean isPass() {
        return PASS.equals(suggest);
    }

    public boolean isRisky() {
        return RISKY.equals(suggest);
    }

}
