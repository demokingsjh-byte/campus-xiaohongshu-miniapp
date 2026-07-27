package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePaymentStatusRespVO;
import cn.iocoder.yudao.module.campus.framework.payment.CampusWechatPayProperties;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class CampusTradePaymentServiceImpl implements CampusTradePaymentService {

    private static final int STATUS_WAITING = 0;
    private static final int STATUS_PAID = 1;
    private enum ExistingWechatOrderState { NOT_FOUND, PAYING, PAID, CLOSED }
    private static final DateTimeFormatter WECHAT_RFC3339_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CampusWechatPayProperties properties;

    public CampusTradePaymentServiceImpl(NamedParameterJdbcTemplate jdbcTemplate,
                                         CampusWechatPayProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradePayRespVO createPayment(Long postId, Long buyerId, String userIp) {
        requireEnabled();
        Map<String, Object> post = getPost(postId);
        if (!"idle".equals(stringValue(post.get("type")))) {
            throw badRequest("只有二手帖子可以购买");
        }
        Long sellerId = longValue(post.get("user_id"));
        if (Objects.equals(sellerId, buyerId)) {
            throw badRequest("不能购买自己发布的商品");
        }
        BigDecimal amount = decimalValue(post.get("price"));
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw badRequest("商品价格无效");
        }
        Map<String, Object> buyer = getUser(buyerId);
        Map<String, Object> seller = getUser(sellerId);
        if (!Objects.equals(longValue(post.get("tenant_id")), longValue(buyer.get("tenant_id")))) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能购买本校商品");
        }
        if (StrUtil.isBlank(stringValue(post.get("contact")))
                && StrUtil.isBlank(stringValue(seller.get("mobile")))) {
            throw badRequest("发布者未预留联系方式，暂时无法购买");
        }

        Map<String, Object> order = findOrder(postId, buyerId);
        if (order == null) {
            order = insertOrder(post, buyerId, sellerId, amount);
        }
        CampusTradePayRespVO response = baseResponse(order);
        if (intValue(order.get("status")) == STATUS_PAID) {
            return response;
        }

        try {
            int totalFen = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request()
                    .setOutTradeNo(stringValue(order.get("order_no")))
                    .setDescription(crop("校园二手-" + stringValue(post.get("title")), 127))
                    .setNotifyUrl(properties.getNotifyUrl())
                    .setTimeExpire(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusMinutes(30)
                            .format(WECHAT_RFC3339_SECONDS))
                    .setAmount(new WxPayUnifiedOrderV3Request.Amount().setTotal(totalFen))
                    .setPayer(new WxPayUnifiedOrderV3Request.Payer().setOpenid(stringValue(buyer.get("openid"))))
                    .setSceneInfo(new WxPayUnifiedOrderV3Request.SceneInfo()
                            .setPayerClientIp(StrUtil.blankToDefault(userIp, "127.0.0.1")));
            WxPayUnifiedOrderV3Result.JsapiResult result = createClient().createOrderV3(TradeTypeEnum.JSAPI, request);
            response.setTimeStamp(result.getTimeStamp());
            response.setNonceStr(result.getNonceStr());
            response.setPackageValue(result.getPackageValue());
            response.setSignType(result.getSignType());
            response.setPaySign(result.getPaySign());
            return response;
        } catch (Exception ex) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "微信支付下单失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradePayRespVO createPaymentByOrder(Long orderId, Long buyerId, String userIp) {
        requireEnabled();
        Map<String, Object> order = findOrderForPayment(orderId, buyerId);
        if (order == null) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在或无权支付");
        }
        CampusTradePayRespVO response = baseResponse(order);
        int status = intValue(order.get("status"));
        if (status == STATUS_PAID) {
            return response;
        }
        if (status != STATUS_WAITING) {
            throw badRequest("当前订单状态不能支付");
        }
        LocalDateTime expiresAt = toLocalDateTime(order.get("expires_at"));
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            jdbcTemplate.update("UPDATE campus_trade_order SET status = 3, closed_at = NOW(),"
                            + " close_reason = 'TIMEOUT', updater = 'wechat-pay', update_time = NOW()"
                            + " WHERE id = :id AND status = 0 AND deleted = b'0'",
                    new MapSqlParameterSource("id", order.get("id")));
            throw badRequest("订单已过期，请重新下单");
        }

        Map<String, Object> buyer = getUser(buyerId);
        if (StrUtil.isBlank(stringValue(buyer.get("openid")))) {
            throw badRequest("当前用户未完成微信登录，暂时无法支付");
        }
        try {
            ExistingWechatOrderState existingState = reconcileExistingWechatOrder(order);
            if (existingState == ExistingWechatOrderState.PAID) {
                return baseResponse(findOrderForPayment(orderId, buyerId));
            }
            if (existingState == ExistingWechatOrderState.PAYING) {
                // Do not submit the same out_trade_no again while WeChat is processing it.
                return baseResponse(order);
            }
            BigDecimal amount = decimalValue(order.get("amount"));
            int totalFen = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request()
                    .setOutTradeNo(stringValue(order.get("order_no")))
                    .setDescription(crop("校园二手-" + stringValue(order.get("item_title_snapshot")), 127))
                    .setNotifyUrl(properties.getNotifyUrl())
                    .setTimeExpire(formatWechatExpiry(expiresAt))
                    .setAmount(new WxPayUnifiedOrderV3Request.Amount().setTotal(totalFen))
                    .setPayer(new WxPayUnifiedOrderV3Request.Payer().setOpenid(stringValue(buyer.get("openid"))))
                    .setSceneInfo(new WxPayUnifiedOrderV3Request.SceneInfo()
                            .setPayerClientIp(StrUtil.blankToDefault(userIp, "127.0.0.1")));
            WxPayUnifiedOrderV3Result.JsapiResult result = createClient().createOrderV3(TradeTypeEnum.JSAPI, request);
            response.setTimeStamp(result.getTimeStamp());
            response.setNonceStr(result.getNonceStr());
            response.setPackageValue(result.getPackageValue());
            response.setSignType(result.getSignType());
            response.setPaySign(result.getPaySign());
            return response;
        } catch (Exception ex) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "微信支付下单失败，请稍后重试");
        }
    }

    private ExistingWechatOrderState reconcileExistingWechatOrder(Map<String, Object> order)
            throws IOException, WxPayException {
        String orderNo = stringValue(order.get("order_no"));
        if (StrUtil.isBlank(orderNo)) {
            return ExistingWechatOrderState.NOT_FOUND;
        }
        try {
            WxPayOrderQueryV3Result result = createClient().queryOrderV3(orderNo, properties.getMchId());
            if ("SUCCESS".equals(result.getTradeState())) {
                markOrderPaid(order, result.getTransactionId(), result.getAmount() == null
                        ? null : result.getAmount().getTotal());
                return ExistingWechatOrderState.PAID;
            }
            if ("USERPAYING".equals(result.getTradeState())) {
                return ExistingWechatOrderState.PAYING;
            }
            if (!"CLOSED".equals(result.getTradeState()) && !"REVOKED".equals(result.getTradeState())) {
                createClient().closeOrderV3(orderNo);
            }
            rotateOrderNumber(order);
            return ExistingWechatOrderState.CLOSED;
        } catch (WxPayException ex) {
            if (isWechatOrderNotFound(ex)) {
                return ExistingWechatOrderState.NOT_FOUND;
            }
            throw ex;
        }
    }

    private void rotateOrderNumber(Map<String, Object> order) {
        String orderNo = "CS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("UPDATE campus_trade_order SET order_no = :orderNo,"
                        + " updater = 'wechat-pay', update_time = NOW()"
                        + " WHERE id = :id AND status = :status AND deleted = b'0'",
                new MapSqlParameterSource().addValue("orderNo", orderNo)
                        .addValue("id", order.get("id")).addValue("status", STATUS_WAITING));
        order.put("order_no", orderNo);
    }

    private boolean isWechatOrderNotFound(WxPayException ex) {
        String code = ex.getErrCode();
        String message = ex.getMessage();
        String details = (String.valueOf(code) + " " + String.valueOf(message)).toUpperCase();
        return details.contains("RESOURCE_NOT_FOUND")
                || details.contains("ORDER_NOT_EXIST")
                || details.contains("ORDERNOTEXIST")
                || details.contains("NOT FOUND");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradePaymentStatusRespVO getPaymentStatus(Long orderId, Long buyerId) {
        requireEnabled();
        Map<String, Object> order = findOrderForPayment(orderId, buyerId);
        if (order == null) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在或无权查看");
        }
        String wechatTradeState = null;
        if (intValue(order.get("status")) == STATUS_WAITING) {
            wechatTradeState = syncOrderFromWechat(order);
            order = findOrderForPayment(orderId, buyerId);
            if (order == null) {
                throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在或无权查看");
            }
        }
        CampusTradePaymentStatusRespVO response = new CampusTradePaymentStatusRespVO();
        response.setOrderId(longValue(order.get("id")));
        response.setOrderNo(stringValue(order.get("order_no")));
        response.setStatus(intValue(order.get("status")));
        response.setPaid(response.getStatus() == STATUS_PAID);
        response.setWechatTradeState(wechatTradeState);
        response.setRetryable("NOTPAY".equals(wechatTradeState)
                || "CLOSED".equals(wechatTradeState)
                || "REVOKED".equals(wechatTradeState)
                || response.getStatus() == 3);
        response.setExpiresAt(toLocalDateTime(order.get("expires_at")));
        response.setPaidAt(toLocalDateTime(order.get("paid_at")));
        return response;
    }

    @Override
    public CampusTradeContactRespVO getContact(Long postId, Long buyerId) {
        Map<String, Object> order = findOrder(postId, buyerId);
        CampusTradeContactRespVO response = new CampusTradeContactRespVO();
        if (order == null) {
            response.setStatus(STATUS_WAITING);
            return response;
        }
        response.setOrderId(longValue(order.get("id")));
        response.setStatus(intValue(order.get("status")));
        response.setPaid(response.getStatus() == STATUS_PAID);
        if (response.isPaid()) {
            Map<String, Object> post = getPostIncludingSold(postId);
            Map<String, Object> seller = getUser(longValue(post.get("user_id")));
            response.setSellerName(stringValue(seller.get("nickname")));
            response.setContact(StrUtil.blankToDefault(stringValue(post.get("contact")),
                    stringValue(seller.get("mobile"))));
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatNotify(String body, Map<String, String> headers) {
        requireEnabled();
        try {
            SignatureHeader signature = SignatureHeader.builder()
                    .signature(header(headers, "wechatpay-signature"))
                    .nonce(header(headers, "wechatpay-nonce"))
                    .serial(header(headers, "wechatpay-serial"))
                    .timeStamp(header(headers, "wechatpay-timestamp"))
                    .build();
            WxPayNotifyV3Result notify = createClient().parseOrderNotifyV3Result(body, signature);
            WxPayNotifyV3Result.DecryptNotifyResult result = notify.getResult();
            if (!"SUCCESS".equals(result.getTradeState())) {
                return;
            }
            if (!Objects.equals(properties.getAppId(), result.getAppid())
                    || !Objects.equals(properties.getMchId(), result.getMchid())) {
                throw badRequest("支付回调商户信息不匹配");
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM campus_trade_order WHERE order_no = :orderNo AND deleted = b'0' LIMIT 1 FOR UPDATE",
                    new MapSqlParameterSource("orderNo", result.getOutTradeNo()));
            if (rows.isEmpty()) {
                throw badRequest("支付订单不存在");
            }
            Map<String, Object> order = rows.get(0);
            if (intValue(order.get("status")) == STATUS_PAID) {
                markOrderPaid(order, result.getTransactionId(), result.getAmount() == null
                        ? null : result.getAmount().getTotal());
                return;
            }
            if (intValue(order.get("status")) == STATUS_PAID) {
                return;
            }
            int expectedFen = decimalValue(order.get("amount")).movePointRight(2).intValueExact();
            if (result.getAmount() == null || !Objects.equals(result.getAmount().getTotal(), expectedFen)) {
                throw badRequest("支付金额不匹配");
            }
            markOrderPaid(order, result.getTransactionId(), result.getAmount().getTotal());
        } catch (WxPayException ex) {
            throw badRequest("微信支付回调验签失败");
        } catch (IOException ex) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "微信支付配置读取失败");
        }
    }

    private WxPayService createClient() throws IOException {
        WxPayConfig config = new WxPayConfig();
        config.setAppId(properties.getAppId());
        config.setMchId(properties.getMchId());
        config.setApiV3Key(readSecret(properties.getApiV3Key(), properties.getApiV3KeyPath()));
        config.setCertSerialNo(properties.getCertSerialNo());
        config.setPrivateKeyPath(properties.getPrivateKeyPath());
        config.setPublicKeyId(properties.getPublicKeyId());
        config.setPublicKeyPath(properties.getPublicKeyPath());
        config.setStrictlyNeedWechatPaySerial(true);
        config.setFullPublicKeyModel(true);
        WxPayService client = new WxPayServiceImpl();
        client.setConfig(config);
        return client;
    }

    private Map<String, Object> insertOrder(Map<String, Object> post, Long buyerId, Long sellerId,
                                            BigDecimal amount) {
        String orderNo = "CS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderNo", orderNo).addValue("buyerId", buyerId).addValue("sellerId", sellerId)
                .addValue("postId", post.get("id")).addValue("amount", amount)
                .addValue("tenantId", post.get("tenant_id")).addValue("operator", String.valueOf(buyerId));
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_trade_order (order_no, buyer_id, seller_id, product_id, amount,"
                        + " status, expires_at, creator, updater, create_time, update_time, deleted, tenant_id)"
                        + " VALUES (:orderNo, :buyerId, :sellerId, :postId, :amount, 0,"
                        + " DATE_ADD(NOW(), INTERVAL 15 MINUTE), :operator, :operator, NOW(), NOW(), b'0', :tenantId)",
                params, key);
        return findOrder(post.get("id") instanceof Number ? ((Number) post.get("id")).longValue() : null, buyerId);
    }

    private Map<String, Object> findOrder(Long postId, Long buyerId) {
        if (postId == null || buyerId == null) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE product_id = :postId AND buyer_id = :buyerId AND status IN (0, 1)"
                        + " AND deleted = b'0' ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource().addValue("postId", postId).addValue("buyerId", buyerId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findOrderForPayment(Long orderId, Long buyerId) {
        if (orderId == null || buyerId == null) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE id = :orderId AND buyer_id = :buyerId AND deleted = b'0' LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("buyerId", buyerId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String syncOrderFromWechat(Map<String, Object> order) {
        try {
            WxPayOrderQueryV3Result result = createClient().queryOrderV3(
                    stringValue(order.get("order_no")), properties.getMchId());
            if (!Objects.equals(properties.getAppId(), result.getAppid())
                    || !Objects.equals(properties.getMchId(), result.getMchid())) {
                throw badRequest("微信支付查询商户信息不匹配");
            }
            if ("SUCCESS".equals(result.getTradeState())) {
                markOrderPaid(order, result.getTransactionId(), result.getAmount() == null
                        ? null : result.getAmount().getTotal());
            } else if ("CLOSED".equals(result.getTradeState()) || "REVOKED".equals(result.getTradeState())) {
                jdbcTemplate.update("UPDATE campus_trade_order SET status = 3, closed_at = NOW(),"
                                + " close_reason = 'WECHAT_CLOSED', updater = 'wechat-query', update_time = NOW()"
                                + " WHERE id = :id AND status = 0 AND deleted = b'0'",
                        new MapSqlParameterSource("id", order.get("id")));
            }
            return result.getTradeState();
        } catch (WxPayException | IOException ex) {
            // A temporary WeChat query failure keeps the local order pending.
            if (ex instanceof WxPayException && isWechatOrderNotFound((WxPayException) ex)) {
                return "NOTPAY";
            }
            return null;
        }
    }

    private void markOrderPaid(Map<String, Object> order, String transactionId, Integer actualTotalFen) {
        if (StrUtil.isBlank(transactionId)) {
            throw badRequest("微信支付交易号为空");
        }
        BigDecimal amount = decimalValue(order.get("amount"));
        int expectedFen = amount.movePointRight(2).intValueExact();
        if (actualTotalFen == null || !Objects.equals(actualTotalFen, expectedFen)) {
            throw badRequest("支付金额不匹配");
        }
        List<Map<String, Object>> duplicateRows = jdbcTemplate.queryForList(
                "SELECT id FROM campus_trade_order WHERE wx_transaction_id = :transactionId"
                        + " AND id <> :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("transactionId", transactionId)
                        .addValue("id", order.get("id")));
        if (!duplicateRows.isEmpty()) {
            throw badRequest("微信交易号已绑定其他订单");
        }
        int status = intValue(order.get("status"));
        String existingTransactionId = stringValue(order.get("wx_transaction_id"));
        if (status == STATUS_PAID) {
            if (StrUtil.isNotBlank(existingTransactionId)
                    && !Objects.equals(existingTransactionId, transactionId)) {
                throw badRequest("订单已绑定其他微信交易");
            }
            if (StrUtil.isBlank(existingTransactionId)) {
                jdbcTemplate.update("UPDATE campus_trade_order SET wx_transaction_id = :transactionId,"
                                + " updater = 'wechat-pay', update_time = NOW() WHERE id = :id AND deleted = b'0'",
                        new MapSqlParameterSource().addValue("id", order.get("id"))
                                .addValue("transactionId", transactionId));
            }
            return;
        }
        if (status != STATUS_WAITING && status != 3) {
            throw badRequest("订单状态不允许确认支付");
        }
        jdbcTemplate.update("UPDATE campus_trade_order SET status = 1, paid_at = COALESCE(paid_at, NOW()),"
                        + " closed_at = NULL, close_reason = '', wx_transaction_id = :transactionId,"
                        + " updater = 'wechat-pay', update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status IN (0, 3) AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id"))
                        .addValue("transactionId", transactionId));
    }

    private Map<String, Object> getPost(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_post"
                        + " WHERE id = :id AND status = 1 AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", postId));
        if (rows.isEmpty()) throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "商品不存在或已下架");
        return rows.get(0);
    }

    private Map<String, Object> getPostIncludingSold(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_post"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1", new MapSqlParameterSource("id", postId));
        if (rows.isEmpty()) throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "商品不存在");
        return rows.get(0);
    }

    private Map<String, Object> getUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1", new MapSqlParameterSource("id", userId));
        if (rows.isEmpty()) throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "用户不存在");
        return rows.get(0);
    }

    private CampusTradePayRespVO baseResponse(Map<String, Object> order) {
        CampusTradePayRespVO response = new CampusTradePayRespVO();
        response.setOrderId(longValue(order.get("id")));
        response.setOrderNo(stringValue(order.get("order_no")));
        response.setStatus(intValue(order.get("status")));
        return response;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw badRequest("微信支付尚未启用");
    }

    private String readSecret(String directValue, String path) throws IOException {
        if (StrUtil.isNotBlank(directValue)) return directValue.trim();
        if (StrUtil.isBlank(path)) throw new IOException("API V3 key is missing");
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
    }

    private String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private RuntimeException badRequest(String message) {
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private Long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value)); }
    private BigDecimal decimalValue(Object value) { return value == null ? null : new BigDecimal(String.valueOf(value)); }
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }
    private String formatWechatExpiry(LocalDateTime expiresAt) {
        LocalDateTime target = expiresAt == null ? LocalDateTime.now().plusMinutes(15) : expiresAt;
        return target.atOffset(ZoneOffset.ofHours(8)).format(WECHAT_RFC3339_SECONDS);
    }
    private String crop(String value, int length) { return value.length() <= length ? value : value.substring(0, length); }
}
