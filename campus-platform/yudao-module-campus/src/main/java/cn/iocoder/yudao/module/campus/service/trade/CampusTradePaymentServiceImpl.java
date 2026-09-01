package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeRefundRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeContactRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePayRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradePaymentStatusRespVO;
import cn.iocoder.yudao.module.campus.framework.payment.CampusWechatPayProperties;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result;
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(CampusTradePaymentServiceImpl.class);
    private static final int STATUS_WAITING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_REFUNDED = 4;
    private static final int REFUND_NONE = 0;
    private static final int REFUND_PROCESSING = 1;
    private static final int REFUND_SUCCESS = 2;
    private static final int REFUND_FAILED = 3;
    private enum ExistingWechatOrderState { NOT_FOUND, PAYING, PAID, CLOSED, UNKNOWN }
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
        String sellerPhone = StrUtil.blankToDefault(stringValue(post.get("contact")),
                stringValue(seller.get("mobile")));
        if (StrUtil.isBlank(sellerPhone)) {
            throw badRequest("发布者未预留联系方式，暂时无法购买");
        }

        Map<String, Object> order = findOrder(postId, buyerId);
        if (order == null) {
            order = insertOrder(post, buyerId, sellerId, amount, sellerPhone);
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
        } catch (WxPayException | IOException ex) {
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
            closeOrderAndReleaseStock(order, "TIMEOUT", "wechat-pay");
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
            if (existingState == ExistingWechatOrderState.UNKNOWN) {
                throw badRequest("WeChat payment status is temporarily unavailable; refresh the order and try again");
            }
            BigDecimal amount = decimalValue(order.get("amount"));
            int totalFen = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            String paymentScene = intValue(order.get("biz_type")) == 4 ? "校园代办-" : "校园二手-";
            WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request()
                    .setOutTradeNo(stringValue(order.get("order_no")))
                    .setDescription(crop(paymentScene + stringValue(order.get("item_title_snapshot")), 127))
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
        } catch (WxPayException | IOException ex) {
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
            WxPayOrderQueryV3Result result = queryWechatOrderByOutTradeNo(orderNo);
            log.info("WeChat payment state query before create completed, orderNo={}, tradeState={}, transactionId={}",
                    orderNo, result.getTradeState(), result.getTransactionId());
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
            log.error("WeChat payment state query failed before create, orderNo={}, errCode={}, message={}",
                    orderNo, ex.getErrCode(), ex.getMessage(), ex);
            auditWechatQuery(order, "QUERY_ERROR", summarizeWechatError(ex));
            return ExistingWechatOrderState.UNKNOWN;
        } catch (IOException ex) {
            log.error("WeChat payment notify client configuration failed", ex);
            log.error("WeChat payment client configuration failed before create, orderNo={}", orderNo, ex);
            auditWechatQuery(order, "QUERY_ERROR", summarizeWechatError(ex));
            return ExistingWechatOrderState.UNKNOWN;
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
                || details.contains("RESOURCE_NOT_EXISTS")
                || details.contains("REFUND_NOT_EXIST")
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
        String wechatQueryError = stringValue(order.get("wechat_query_error"));
        LocalDateTime wechatQueriedAt = toLocalDateTime(order.get("wechat_query_at"));
        // Also reconcile locally closed orders. WeChat may confirm a payment
        // after the local timeout/cancel action, and a verified SUCCESS must
        // still be allowed to recover the order to PAID.
        if (intValue(order.get("status")) == STATUS_WAITING || intValue(order.get("status")) == 3) {
            wechatTradeState = syncOrderFromWechat(order);
            order = findOrderForPayment(orderId, buyerId);
            wechatQueryError = order == null ? "" : stringValue(order.get("wechat_query_error"));
            wechatQueriedAt = order == null ? null : toLocalDateTime(order.get("wechat_query_at"));
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
        response.setWechatQueryError(nullIfBlank(wechatQueryError));
        response.setWechatQueriedAt(wechatQueriedAt);
        response.setRetryable("NOTPAY".equals(wechatTradeState)
                || "CLOSED".equals(wechatTradeState)
                || "REVOKED".equals(wechatTradeState)
                || response.getStatus() == 3);
        response.setExpiresAt(toLocalDateTime(order.get("expires_at")));
        response.setPaidAt(toLocalDateTime(order.get("paid_at")));
        return response;
    }

    /**
     * WeChat callbacks can be delayed or retried after a local timeout/cancel.
     * Reconcile recent unsettled orders server-side so payment confirmation
     * does not depend on the miniapp remaining open.
     */
    @Scheduled(initialDelay = 30_000L, fixedDelay = 30_000L)
    @Transactional(rollbackFor = Exception.class)
    public void reconcileRecentOrders() {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT * FROM campus_trade_order WHERE status IN (0, 3)"
                        + " AND wx_transaction_id IS NULL AND deleted = b'0'"
                        + " AND create_time >= DATE_SUB(NOW(), INTERVAL 2 HOUR)"
                        + " ORDER BY id DESC LIMIT 20", new MapSqlParameterSource());
        for (Map<String, Object> order : orders) {
            try {
                String tradeState = syncOrderFromWechat(order);
                if ("SUCCESS".equals(tradeState)) {
                    log.info("Reconciled WeChat payment for campus order {}", order.get("order_no"));
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to reconcile WeChat payment for campus order {}: {}",
                        order.get("order_no"), ex.getMessage());
            }
        }
    }

    /**
     * Refund callbacks can be delayed as well. Query the same merchant refund
     * number until WeChat reaches a terminal state.
     */
    @Scheduled(initialDelay = 45_000L, fixedDelay = 60_000L)
    public void reconcileProcessingRefunds() {
        List<Long> orderIds = jdbcTemplate.queryForList(
                "SELECT id FROM campus_trade_order WHERE refund_status = 1"
                        + " AND refund_no IS NOT NULL AND deleted = b'0'"
                        + " AND refund_requested_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
                        + " ORDER BY id DESC LIMIT 20",
                new MapSqlParameterSource(), Long.class);
        for (Long orderId : orderIds) {
            try {
                CampusTradeRefundRespVO result = syncRefund(orderId);
                log.info("Campus refund reconciliation completed, orderId={}, refundNo={}, status={}",
                        orderId, result.getRefundNo(), result.getRefundStatusText());
            } catch (RuntimeException ex) {
                log.warn("Campus refund reconciliation failed, orderId={}, message={}",
                        orderId, ex.getMessage());
            }
        }
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
            response.setParticipantName(stringValue(seller.get("nickname")));
            response.setContact(StrUtil.blankToDefault(stringValue(post.get("contact")),
                    stringValue(seller.get("mobile"))));
        }
        return response;
    }

    @Override
    public CampusTradeContactRespVO getContactByOrder(Long orderId, Long userId) {
        Map<String, Object> order = findParticipantOrder(orderId, userId);
        CampusTradeContactRespVO response = new CampusTradeContactRespVO();
        response.setOrderId(longValue(order.get("id")));
        response.setStatus(intValue(order.get("status")));
        response.setPaid(order.get("paid_at") != null);
        if (!response.isPaid()) {
            return response;
        }
        int bizType = intValue(order.get("biz_type"));
        int fulfillmentStatus = intValue(order.get("fulfillment_status"));
        if (bizType == 4 && fulfillmentStatus < 2) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "接单成功后才能查看对方联系方式");
        }
        boolean requesterIsBuyer = Objects.equals(longValue(order.get("buyer_id")), userId);
        Long participantId = requesterIsBuyer ? longValue(order.get("seller_id")) : longValue(order.get("buyer_id"));
        Map<String, Object> participant = getUser(participantId);
        Map<String, Object> post = getPostIncludingSold(longValue(order.get("product_id")));
        response.setParticipantName(stringValue(participant.get("nickname")));
        if (requesterIsBuyer) {
            response.setSellerName(stringValue(participant.get("nickname")));
        }
        String contact = requesterIsBuyer
                ? StrUtil.blankToDefault(stringValue(order.get("seller_phone_snapshot")),
                    StrUtil.blankToDefault(stringValue(participant.get("mobile")), stringValue(post.get("contact"))))
                : (bizType == 4
                    ? StrUtil.blankToDefault(stringValue(post.get("contact")), stringValue(participant.get("mobile")))
                    : stringValue(participant.get("mobile")));
        response.setContact(contact);
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
            String orderNo = result == null ? null : result.getOutTradeNo();
            String transactionId = result == null ? null : result.getTransactionId();
            String tradeState = result == null ? null : result.getTradeState();
            log.info("WeChat payment notify decrypted, orderNo={}, transactionId={}, tradeState={}, appId={}, mchId={}",
                    orderNo, transactionId, tradeState,
                    result == null ? null : result.getAppid(), result == null ? null : result.getMchid());
            if (!"SUCCESS".equals(tradeState)) {
                log.warn("WeChat payment notify is not successful, orderNo={}, transactionId={}, tradeState={}",
                        orderNo, transactionId, tradeState);
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
                log.error("WeChat payment notify order not found, orderNo={}, transactionId={}",
                        orderNo, transactionId);
                throw badRequest("支付订单不存在");
            }
            Map<String, Object> order = rows.get(0);
            if (intValue(order.get("status")) == STATUS_PAID) {
                markOrderPaid(order, result.getTransactionId(), result.getAmount() == null
                        ? null : result.getAmount().getTotal());
                log.info("WeChat payment notify was already processed, orderNo={}, transactionId={}",
                        orderNo, transactionId);
                return;
            }
            int expectedFen = decimalValue(order.get("amount")).movePointRight(2).intValueExact();
            if (result.getAmount() == null || !Objects.equals(result.getAmount().getTotal(), expectedFen)) {
                log.error("WeChat payment notify amount mismatch, orderNo={}, transactionId={}, expectedFen={}, actualFen={}",
                        orderNo, transactionId, expectedFen,
                        result.getAmount() == null ? null : result.getAmount().getTotal());
                throw badRequest("支付金额不匹配");
            }
            markOrderPaid(order, result.getTransactionId(), result.getAmount().getTotal());
            log.info("WeChat payment notify marked order paid, orderNo={}, transactionId={}, amountFen={}",
                    orderNo, transactionId, result.getAmount().getTotal());
        } catch (WxPayException ex) {
            log.error("WeChat payment notify verify/decrypt failed, errCode={}, message={}",
                    ex.getErrCode(), ex.getMessage(), ex);
            throw badRequest("微信支付回调验签失败");
        } catch (IOException ex) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "微信支付配置读取失败");
        }
    }

    @Override
    public CampusTradeRefundRespVO refundOrder(Long orderId, String reason, String operator) {
        requireEnabled();
        Map<String, Object> order = findAdminOrder(orderId);
        int orderStatus = intValue(order.get("status"));
        int refundStatus = intValue(order.get("refund_status"));
        if (orderStatus == STATUS_REFUNDED || refundStatus == REFUND_SUCCESS) {
            return refundResponse(order);
        }
        if (orderStatus != STATUS_PAID && orderStatus != STATUS_COMPLETED) {
            throw badRequest("只有已付款或已完成订单可以退款");
        }
        if (refundStatus == REFUND_PROCESSING) {
            CampusTradeRefundRespVO synced = syncRefund(orderId);
            if (synced.getRefundStatus() != REFUND_FAILED) {
                return synced;
            }
            // 旧版本可能在真正调用微信前已经把订单标记为处理中。
            // 当微信确认退款单不存在时，syncRefund 会将其改为失败，这里继续重新提交。
            order = findAdminOrder(orderId);
        }

        String refundNo = stringValue(order.get("refund_no"));
        if (StrUtil.isBlank(refundNo)) {
            refundNo = crop("RF" + stringValue(order.get("order_no")), 64);
        }
        String safeReason = crop(StrUtil.blankToDefault(reason, "后台协商退款").trim(), 80);
        String safeOperator = crop(StrUtil.blankToDefault(operator, "admin"), 64);
        int claimed = jdbcTemplate.update("UPDATE campus_trade_order SET refund_no = :refundNo,"
                        + " refund_status = :processing, refund_amount = amount, refund_reason = :reason,"
                        + " refund_requested_at = COALESCE(refund_requested_at, NOW()), refund_error = NULL,"
                        + " refund_operator = :operator, updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status IN (1, 2) AND refund_status IN (0, 3) AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId)
                        .addValue("refundNo", refundNo)
                        .addValue("processing", REFUND_PROCESSING)
                        .addValue("reason", safeReason)
                        .addValue("operator", safeOperator));
        if (claimed == 0) {
            Map<String, Object> current = findAdminOrder(orderId);
            return intValue(current.get("refund_status")) == REFUND_PROCESSING
                    ? syncRefund(orderId) : refundResponse(current);
        }
        order = findAdminOrder(orderId);
        try {
            int amountFen = decimalValue(order.get("amount")).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            WxPayRefundV3Request request = new WxPayRefundV3Request()
                    .setOutRefundNo(refundNo)
                    .setReason(safeReason)
                    .setNotifyUrl(refundNotifyUrl())
                    .setAmount(new WxPayRefundV3Request.Amount()
                            .setRefund(amountFen).setTotal(amountFen).setCurrency("CNY"));
            String transactionId = stringValue(order.get("wx_transaction_id"));
            if (StrUtil.isNotBlank(transactionId)) {
                request.setTransactionId(transactionId);
            } else {
                request.setOutTradeNo(stringValue(order.get("order_no")));
            }
            log.info("Submitting WeChat refund, orderNo={}, refundNo={}, transactionId={}, amountFen={}, operator={}",
                    order.get("order_no"), refundNo, transactionId, amountFen, safeOperator);
            WxPayRefundV3Result result = createClient().refundV3(request);
            applyRefundResult(order, result.getOutTradeNo(), result.getOutRefundNo(), result.getRefundId(),
                    result.getStatus(), result.getSuccessTime(),
                    result.getAmount() == null ? null : result.getAmount().getRefund(), false);
            log.info("WeChat refund accepted, orderNo={}, refundNo={}, wxRefundId={}, status={}",
                    order.get("order_no"), refundNo, result.getRefundId(), result.getStatus());
            return refundResponse(findAdminOrder(orderId));
        } catch (WxPayException | IOException | RuntimeException ex) {
            String error = summarizeWechatError(ex);
            markRefundFailed(orderId, error);
            log.error("WeChat refund submission failed, orderNo={}, refundNo={}, message={}",
                    order.get("order_no"), refundNo, error, ex);
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "微信退款申请失败：" + crop(error, 120));
        }
    }

    @Override
    public CampusTradeRefundRespVO syncRefund(Long orderId) {
        requireEnabled();
        Map<String, Object> order = findAdminOrder(orderId);
        String refundNo = stringValue(order.get("refund_no"));
        if (StrUtil.isBlank(refundNo)) {
            return refundResponse(order);
        }
        if (intValue(order.get("refund_status")) == REFUND_SUCCESS
                || intValue(order.get("status")) == STATUS_REFUNDED) {
            return refundResponse(order);
        }
        try {
            WxPayRefundQueryV3Result result = createClient().refundQueryV3(refundNo);
            applyRefundResult(order, result.getOutTradeNo(), result.getOutRefundNo(), result.getRefundId(),
                    result.getStatus(), result.getSuccessTime(),
                    result.getAmount() == null ? null : result.getAmount().getRefund(), false);
            log.info("WeChat refund query completed, orderNo={}, refundNo={}, status={}",
                    order.get("order_no"), refundNo, result.getStatus());
        } catch (WxPayException | IOException ex) {
            String error = summarizeWechatError(ex);
            if (ex instanceof WxPayException && isWechatOrderNotFound((WxPayException) ex)) {
                // 微信侧不存在该退款单，说明本地“处理中”并不代表已成功提交，恢复为可重试状态。
                markRefundFailed(orderId, error);
            } else {
                jdbcTemplate.update("UPDATE campus_trade_order SET refund_error = :error,"
                                + " updater = 'wechat-refund-query', update_time = NOW()"
                                + " WHERE id = :id AND refund_status <> :success AND deleted = b'0'",
                        new MapSqlParameterSource().addValue("id", orderId).addValue("error", error)
                                .addValue("success", REFUND_SUCCESS));
            }
            log.error("WeChat refund query failed, orderNo={}, refundNo={}, message={}",
                    order.get("order_no"), refundNo, error, ex);
        }
        return refundResponse(findAdminOrder(orderId));
    }

    @Override
    public void handleWechatRefundNotify(String body, Map<String, String> headers) {
        requireEnabled();
        try {
            SignatureHeader signature = SignatureHeader.builder()
                    .signature(header(headers, "wechatpay-signature"))
                    .nonce(header(headers, "wechatpay-nonce"))
                    .serial(header(headers, "wechatpay-serial"))
                    .timeStamp(header(headers, "wechatpay-timestamp"))
                    .build();
            WxPayRefundNotifyV3Result notify = createClient().parseRefundNotifyV3Result(body, signature);
            WxPayRefundNotifyV3Result.DecryptNotifyResult result = notify.getResult();
            if (result == null || StrUtil.isBlank(result.getOutRefundNo())) {
                throw badRequest("退款回调内容为空");
            }
            log.info("WeChat refund notify decrypted, orderNo={}, refundNo={}, wxRefundId={}, status={}",
                    result.getOutTradeNo(), result.getOutRefundNo(), result.getRefundId(),
                    result.getRefundStatus());
            if (!Objects.equals(properties.getMchId(), result.getMchid())) {
                throw badRequest("退款回调商户信息不匹配");
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM campus_trade_order WHERE refund_no = :refundNo"
                            + " AND deleted = b'0' LIMIT 1 FOR UPDATE",
                    new MapSqlParameterSource("refundNo", result.getOutRefundNo()));
            if (rows.isEmpty()) {
                throw badRequest("退款订单不存在");
            }
            Map<String, Object> order = rows.get(0);
            applyRefundResult(order, result.getOutTradeNo(), result.getOutRefundNo(), result.getRefundId(),
                    result.getRefundStatus(), result.getSuccessTime(),
                    result.getAmount() == null ? null : result.getAmount().getRefund(), true);
        } catch (WxPayException ex) {
            log.error("WeChat refund notify verify/decrypt failed, errCode={}, message={}",
                    ex.getErrCode(), ex.getMessage(), ex);
            throw badRequest("微信退款回调验签失败");
        } catch (IOException ex) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "微信支付配置读取失败");
        }
    }

    private void applyRefundResult(Map<String, Object> order, String outTradeNo, String outRefundNo,
                                   String wxRefundId, String wechatStatus, String successTime,
                                   Integer refundFen, boolean fromNotify) {
        String orderNo = stringValue(order.get("order_no"));
        String refundNo = stringValue(order.get("refund_no"));
        int expectedFen = decimalValue(order.get("amount")).movePointRight(2).intValueExact();
        if (StrUtil.isNotBlank(outTradeNo) && !Objects.equals(orderNo, outTradeNo)) {
            throw badRequest("微信退款对应的订单号不匹配");
        }
        if (!Objects.equals(refundNo, outRefundNo)) {
            throw badRequest("微信退款单号不匹配");
        }
        if (refundFen == null || !Objects.equals(expectedFen, refundFen)) {
            throw badRequest("微信退款金额不匹配");
        }

        int localRefundStatus = refundLocalStatus(wechatStatus);
        boolean success = localRefundStatus == REFUND_SUCCESS;
        String error = localRefundStatus == REFUND_FAILED
                ? crop("WECHAT_REFUND_" + StrUtil.blankToDefault(wechatStatus, "UNKNOWN"), 255) : null;
        jdbcTemplate.update("UPDATE campus_trade_order SET"
                        + " status = CASE WHEN :success = 1 THEN :refundedStatus ELSE status END,"
                        + " fulfillment_status = CASE WHEN :success = 1 AND biz_type = 4 THEN 5 ELSE fulfillment_status END,"
                        + " refund_status = :refundStatus, wx_refund_id = COALESCE(:wxRefundId, wx_refund_id),"
                        + " refund_amount = amount, refunded_at = CASE WHEN :success = 1"
                        + " THEN COALESCE(:refundedAt, NOW()) ELSE refunded_at END,"
                        + " refund_error = :error,"
                        + " refund_notify_at = CASE WHEN :fromNotify = 1 THEN NOW() ELSE refund_notify_at END,"
                        + " updater = :updater, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id"))
                        .addValue("success", success ? 1 : 0)
                        .addValue("refundedStatus", STATUS_REFUNDED)
                        .addValue("refundStatus", localRefundStatus)
                        .addValue("wxRefundId", nullIfBlank(wxRefundId))
                        .addValue("refundedAt", parseWechatTime(successTime))
                        .addValue("error", error)
                        .addValue("fromNotify", fromNotify ? 1 : 0)
                        .addValue("updater", fromNotify ? "wechat-refund-notify" : "wechat-refund-query"));
        if (success) {
            restoreSoldStockAfterRefund(order);
        }
    }

    private void markRefundFailed(Long orderId, String error) {
        jdbcTemplate.update("UPDATE campus_trade_order SET refund_status = :failed, refund_error = :error,"
                        + " updater = 'wechat-refund', update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND refund_status = :processing AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("failed", REFUND_FAILED)
                        .addValue("processing", REFUND_PROCESSING).addValue("error", crop(error, 255)));
    }

    private Map<String, Object> findAdminOrder(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM campus_trade_order WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", orderId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在");
        }
        return rows.get(0);
    }

    private CampusTradeRefundRespVO refundResponse(Map<String, Object> order) {
        CampusTradeRefundRespVO response = new CampusTradeRefundRespVO();
        response.setOrderId(longValue(order.get("id")));
        response.setOrderNo(stringValue(order.get("order_no")));
        response.setOrderStatus(intValue(order.get("status")));
        response.setRefundNo(stringValue(order.get("refund_no")));
        response.setWxRefundId(stringValue(order.get("wx_refund_id")));
        int refundStatus = intValue(order.get("refund_status"));
        response.setRefundStatus(refundStatus);
        response.setRefundStatusText(refundStatusText(refundStatus));
        response.setRefundAmount(decimalValue(order.get("refund_amount")));
        response.setRefundReason(stringValue(order.get("refund_reason")));
        response.setRefundError(stringValue(order.get("refund_error")));
        response.setRefundedAt(toLocalDateTime(order.get("refunded_at")));
        return response;
    }

    private int refundLocalStatus(String wechatStatus) {
        if ("SUCCESS".equalsIgnoreCase(wechatStatus)) {
            return REFUND_SUCCESS;
        }
        if ("CLOSED".equalsIgnoreCase(wechatStatus)
                || "ABNORMAL".equalsIgnoreCase(wechatStatus)) {
            return REFUND_FAILED;
        }
        return REFUND_PROCESSING;
    }

    private String refundStatusText(int status) {
        switch (status) {
            case REFUND_PROCESSING: return "退款处理中";
            case REFUND_SUCCESS: return "退款成功";
            case REFUND_FAILED: return "退款失败";
            default: return "未退款";
        }
    }

    private LocalDateTime parseWechatTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String refundNotifyUrl() {
        if (StrUtil.isNotBlank(properties.getRefundNotifyUrl())) {
            return properties.getRefundNotifyUrl().trim();
        }
        String paymentNotifyUrl = properties.getNotifyUrl();
        if (StrUtil.isBlank(paymentNotifyUrl)) {
            throw badRequest("微信支付退款回调地址未配置");
        }
        String suffix = "/wechat/notify";
        if (paymentNotifyUrl.endsWith(suffix)) {
            return paymentNotifyUrl.substring(0, paymentNotifyUrl.length() - suffix.length())
                    + "/wechat/refund-notify";
        }
        return paymentNotifyUrl + "/refund";
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
                                            BigDecimal amount, String sellerPhone) {
        reserveStock(longValue(post.get("id")), buyerId);
        String orderNo = "CS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderNo", orderNo).addValue("buyerId", buyerId).addValue("sellerId", sellerId)
                .addValue("postId", post.get("id")).addValue("amount", amount)
                .addValue("sellerPhone", sellerPhone)
                .addValue("tenantId", post.get("tenant_id")).addValue("operator", String.valueOf(buyerId));
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_trade_order (order_no, buyer_id, seller_id, product_id, amount,"
                        + " seller_phone_snapshot, status, inventory_state, expires_at, creator, updater, create_time, update_time, deleted, tenant_id)"
                        + " VALUES (:orderNo, :buyerId, :sellerId, :postId, :amount, :sellerPhone, 0, 1,"
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

    private Map<String, Object> findParticipantOrder(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "订单不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE id = :orderId AND (buyer_id = :userId OR seller_id = :userId)"
                        + " AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在或无权查看联系方式");
        }
        return rows.get(0);
    }

    private String syncOrderFromWechat(Map<String, Object> order) {
        String orderNo = stringValue(order.get("order_no"));
        try {
            WxPayOrderQueryV3Result result = queryWechatOrderByOutTradeNo(orderNo);
            log.info("WeChat payment state query completed, orderNo={}, tradeState={}, transactionId={}",
                    orderNo, result.getTradeState(), result.getTransactionId());
            if (!Objects.equals(properties.getAppId(), result.getAppid())
                    || !Objects.equals(properties.getMchId(), result.getMchid())) {
                throw badRequest("微信支付查询商户信息不匹配");
            }
            if ("SUCCESS".equals(result.getTradeState())) {
                markOrderPaid(order, result.getTransactionId(), result.getAmount() == null
                        ? null : result.getAmount().getTotal());
                auditWechatQuery(order, "SUCCESS", null);
            } else if ("CLOSED".equals(result.getTradeState()) || "REVOKED".equals(result.getTradeState())) {
                auditWechatQuery(order, result.getTradeState(), null);
                closeOrderAndReleaseStock(order, "WECHAT_CLOSED", "wechat-query");
            } else {
                auditWechatQuery(order, result.getTradeState(), null);
            }
            return result.getTradeState();
        } catch (WxPayException | IOException ex) {
            if (ex instanceof WxPayException && isWechatOrderNotFound((WxPayException) ex)) {
                auditWechatQuery(order, "NOTPAY", null);
                return "NOTPAY";
            }
            String error = summarizeWechatError(ex);
            auditWechatQuery(order, "QUERY_ERROR", error);
            log.error("WeChat payment state query failed, orderNo={}, message={}", orderNo, error, ex);
            return "QUERY_ERROR";
        }
    }

    /**
     * Query by the merchant order number explicitly.
     *
     * <p>The SDK's two-String overload is {@code queryOrderV3(transactionId, outTradeNo)}.
     * Passing {@code (orderNo, mchId)} makes the merchant id the out-trade-no and causes
     * every real order to be reported as missing. Building the request explicitly avoids
     * this easy-to-miss parameter-order ambiguity.</p>
     */
    private WxPayOrderQueryV3Result queryWechatOrderByOutTradeNo(String orderNo)
            throws IOException, WxPayException {
        WxPayOrderQueryV3Request request = new WxPayOrderQueryV3Request()
                .setOutTradeNo(orderNo)
                .setMchid(properties.getMchId());
        return createClient().queryOrderV3(request);
    }

    private void auditWechatQuery(Map<String, Object> order, String state, String error) {
        try {
            jdbcTemplate.update("UPDATE campus_trade_order SET wechat_trade_state = :state,"
                            + " wechat_query_at = NOW(), wechat_query_error = :error,"
                            + " updater = 'wechat-query', update_time = NOW()"
                            + " WHERE id = :id AND deleted = b'0'",
                    new MapSqlParameterSource().addValue("id", order.get("id"))
                            .addValue("state", nullIfBlank(state))
                            .addValue("error", nullIfBlank(error)));
        } catch (RuntimeException auditError) {
            log.warn("Unable to persist WeChat query audit, orderNo={}: {}", order.get("order_no"),
                    auditError.getMessage());
        }
    }

    private String summarizeWechatError(Exception ex) {
        String message = ex instanceof WxPayException
                ? ((WxPayException) ex).getErrCode() + ":" + ex.getMessage()
                : ex.getClass().getSimpleName() + ":" + ex.getMessage();
        if (message == null) {
            return "UNKNOWN";
        }
        message = message.replaceAll("[\\r\\n]+", " ");
        return message.substring(0, Math.min(message.length(), 255));
    }

    private String nullIfBlank(String value) {
        return StrUtil.isBlank(value) ? null : value;
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
            initializeErrandFulfillment(order.get("id"));
            return;
        }
        if (status != STATUS_WAITING && status != 3) {
            throw badRequest("订单状态不允许确认支付");
        }
        int inventoryState = intValue(order.get("inventory_state"));
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET status = 1, paid_at = COALESCE(paid_at, NOW()),"
                        + " fulfillment_status = CASE WHEN biz_type = 4 AND fulfillment_status = 0 THEN 1"
                        + " ELSE fulfillment_status END,"
                        + " accept_expires_at = CASE WHEN biz_type = 4 THEN COALESCE(accept_expires_at,"
                        + " DATE_ADD(NOW(), INTERVAL 24 HOUR)) ELSE accept_expires_at END,"
                        + " closed_at = NULL, close_reason = '', wx_transaction_id = :transactionId,"
                        + " inventory_state = CASE WHEN biz_type = 1 THEN 2 ELSE inventory_state END,"
                        + " updater = 'wechat-pay', update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status IN (0, 3) AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id"))
                        .addValue("transactionId", transactionId));
        if (updated > 0 && intValue(order.get("biz_type")) == 1) {
            completeStockSale(longValue(order.get("product_id")), inventoryState == 1);
        }
    }

    private void initializeErrandFulfillment(Object orderId) {
        jdbcTemplate.update("UPDATE campus_trade_order SET fulfillment_status = 1,"
                        + " accept_expires_at = COALESCE(accept_expires_at, DATE_ADD(NOW(), INTERVAL 24 HOUR)),"
                        + " updater = 'wechat-pay', update_time = NOW()"
                        + " WHERE id = :id AND biz_type = 4 AND status = 1 AND fulfillment_status = 0"
                        + " AND deleted = b'0'",
                new MapSqlParameterSource("id", orderId));
    }

    private void reserveStock(Long postId, Long buyerId) {
        int updated = jdbcTemplate.update("UPDATE campus_post SET stock_available = stock_available - 1,"
                        + " sale_status = CASE WHEN stock_available - 1 <= 0 THEN 2 ELSE 1 END,"
                        + " updater = :operator, update_time = NOW()"
                        + " WHERE id = :id AND type = 'idle' AND status = 1 AND sale_status = 1"
                        + " AND stock_available > 0 AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", postId)
                        .addValue("operator", String.valueOf(buyerId)));
        if (updated == 0) {
            throw badRequest("商品已售罄");
        }
    }

    private void closeOrderAndReleaseStock(Map<String, Object> order, String reason, String operator) {
        int inventoryState = intValue(order.get("inventory_state"));
        int bizType = intValue(order.get("biz_type"));
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET status = 3, closed_at = NOW(),"
                        + " close_reason = :reason,"
                        + " inventory_state = CASE WHEN biz_type = 1 AND inventory_state = 1 THEN 3"
                        + " ELSE inventory_state END, updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = 0 AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id")).addValue("reason", reason)
                        .addValue("operator", operator));
        if (updated > 0 && bizType == 1 && inventoryState == 1) {
            releaseReservedStock(longValue(order.get("product_id")), operator);
        }
    }

    private void releaseReservedStock(Long postId, String operator) {
        jdbcTemplate.update("UPDATE campus_post SET"
                        + " stock_available = LEAST(stock_total - sold_count, stock_available + 1),"
                        + " sale_status = CASE WHEN stock_total - sold_count > 0 THEN 1 ELSE 2 END,"
                        + " updater = :operator, update_time = NOW()"
                        + " WHERE id = :id AND type = 'idle' AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", postId).addValue("operator", operator));
    }

    private void completeStockSale(Long postId, boolean reserved) {
        if (reserved) {
            jdbcTemplate.update("UPDATE campus_post SET sold_count = LEAST(stock_total, sold_count + 1),"
                            + " sale_status = CASE WHEN stock_available <= 0 THEN 2 ELSE 1 END,"
                            + " updater = 'wechat-pay', update_time = NOW()"
                            + " WHERE id = :id AND type = 'idle' AND deleted = b'0'",
                    new MapSqlParameterSource("id", postId));
            return;
        }
        // 兼容迁移前订单或超时边界上的微信成功回调：支付事实优先，库存不会变成负数。
        jdbcTemplate.update("UPDATE campus_post SET"
                        + " sale_status = CASE WHEN stock_available <= 1 THEN 2 ELSE 1 END,"
                        + " stock_total = GREATEST(stock_total, sold_count + 1),"
                        + " stock_available = GREATEST(stock_available - 1, 0), sold_count = sold_count + 1,"
                        + " updater = 'wechat-pay', update_time = NOW()"
                        + " WHERE id = :id AND type = 'idle' AND deleted = b'0'",
                new MapSqlParameterSource("id", postId));
    }

    private void restoreSoldStockAfterRefund(Map<String, Object> order) {
        if (intValue(order.get("biz_type")) != 1) {
            return;
        }
        int claimed = jdbcTemplate.update("UPDATE campus_trade_order SET inventory_state = 3,"
                        + " updater = 'wechat-refund', update_time = NOW()"
                        + " WHERE id = :id AND inventory_state = 2 AND deleted = b'0'",
                new MapSqlParameterSource("id", order.get("id")));
        if (claimed == 0) {
            return;
        }
        jdbcTemplate.update("UPDATE campus_post SET sale_status = 1,"
                        + " stock_available = LEAST(stock_total - GREATEST(sold_count - 1, 0), stock_available + 1),"
                        + " sold_count = GREATEST(sold_count - 1, 0), updater = 'wechat-refund', update_time = NOW()"
                        + " WHERE id = :id AND type = 'idle' AND deleted = b'0'",
                new MapSqlParameterSource("id", order.get("product_id")));
    }

    private Map<String, Object> getPost(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_post"
                        + " WHERE id = :id AND status = 1 AND sale_status = 1"
                        + " AND stock_available > 0 AND deleted = b'0' LIMIT 1",
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
