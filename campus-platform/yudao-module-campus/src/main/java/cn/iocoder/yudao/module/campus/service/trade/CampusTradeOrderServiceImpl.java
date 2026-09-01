package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandDisputeReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusErrandSubmitReqVO;
import cn.iocoder.yudao.module.campus.service.notification.CampusNotificationService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusTradeOrderServiceImpl implements CampusTradeOrderService {

    private static final Logger log = LoggerFactory.getLogger(CampusTradeOrderServiceImpl.class);

    public static final int STATUS_WAIT_PAY = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_CLOSED = 3;
    public static final int STATUS_REFUNDED = 4;

    public static final int BIZ_TYPE_IDLE = 1;
    public static final int BIZ_TYPE_ERRAND = 4;
    public static final int FULFILLMENT_WAIT_PAY = 0;
    public static final int FULFILLMENT_WAIT_ACCEPT = 1;
    public static final int FULFILLMENT_ACCEPTED = 2;
    public static final int FULFILLMENT_WAIT_CONFIRM = 3;
    public static final int FULFILLMENT_COMPLETED = 4;
    public static final int FULFILLMENT_CANCELED = 5;

    public static final int DISPUTE_NONE = 0;
    public static final int DISPUTE_PENDING = 1;
    public static final int DISPUTE_HELPER_WIN = 2;
    public static final int DISPUTE_PUBLISHER_WIN = 3;

    private static final int PAYMENT_TIMEOUT_MINUTES = 15;
    private static final int ERRAND_CONFIRM_TIMEOUT_HOURS = 24;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileApi fileApi;
    private final CampusTradePaymentService paymentService;
    private final CampusNotificationService campusNotificationService;

    public CampusTradeOrderServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi,
                                       CampusTradePaymentService paymentService,
                                       CampusNotificationService campusNotificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
        this.paymentService = paymentService;
        this.campusNotificationService = campusNotificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO createOrder(Long buyerId, CampusTradeOrderCreateReqVO reqVO) {
        requireUserId(buyerId);
        if (reqVO == null || reqVO.getPostId() == null) {
            throw badRequest("商品不能为空");
        }

        Map<String, Object> post = lockPost(reqVO.getPostId());
        if (!"idle".equals(value(post, "type"))) {
            throw badRequest("只有二手商品可以创建交易订单");
        }
        Long sellerId = toLong(post.get("user_id"));
        if (Objects.equals(sellerId, buyerId)) {
            throw badRequest("不能购买自己发布的商品");
        }
        BigDecimal amount = decimal(post.get("price"));
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw badRequest("商品价格无效");
        }

        Map<String, Object> buyer = getUser(buyerId);
        Map<String, Object> seller = getUser(sellerId);
        if (!Objects.equals(toLong(post.get("tenant_id")), toLong(buyer.get("tenant_id")))) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能购买本校商品");
        }
        String sellerPhone = StrUtil.blankToDefault(value(post, "contact"), value(seller, "mobile"));
        if (StrUtil.isBlank(sellerPhone)) {
            throw badRequest("发布者未预留联系电话，暂时无法购买");
        }

        Map<String, Object> activeOrder = findPendingBuyerOrder(reqVO.getPostId(), buyerId);
        if (activeOrder != null) {
            int status = toInt(activeOrder.get("status"));
            LocalDateTime expiresAt = toLocalDateTime(activeOrder.get("expires_at"));
            if (status == STATUS_WAIT_PAY && expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
                closeOrder(activeOrder, "TIMEOUT");
            } else {
                return toResp(activeOrder);
            }
        }

        reserveStock(reqVO.getPostId(), buyerId);

        String orderNo = createOrderNo();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderNo", orderNo)
                .addValue("buyerId", buyerId)
                .addValue("sellerId", sellerId)
                .addValue("postId", reqVO.getPostId())
                .addValue("amount", amount)
                .addValue("title", crop(value(post, "title"), 255))
                .addValue("coverImage", firstImage(value(post, "images_json")))
                .addValue("sellerPhone", sellerPhone)
                .addValue("tenantId", post.get("tenant_id"))
                .addValue("operator", String.valueOf(buyerId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_trade_order (order_no, buyer_id, seller_id, product_id, biz_type, amount,"
                        + " item_title_snapshot, item_cover_snapshot, seller_phone_snapshot, status, inventory_state, expires_at, creator, updater,"
                        + " create_time, update_time, deleted, tenant_id) VALUES (:orderNo, :buyerId, :sellerId,"
                        + " :postId, 1, :amount, :title, :coverImage, :sellerPhone, 0, 1, DATE_ADD(NOW(), INTERVAL "
                        + PAYMENT_TIMEOUT_MINUTES + " MINUTE), :operator, :operator, NOW(), NOW(), b'0', :tenantId)",
                params, keyHolder);
        return getOrderByOrderNo(orderNo, buyerId);
    }

    @Override
    public CampusTradeOrderRespVO getOrder(Long orderId, Long userId) {
        requireUserId(userId);
        Map<String, Object> order = findOrder(orderId, userId, false);
        if (order == null) {
            throw notFound("订单不存在或无权查看");
        }
        return toResp(order);
    }

    @Override
    public PageResult<CampusTradeOrderRespVO> getOrderPage(Long userId, String role, Integer status,
                                                            Integer pageNo, Integer pageSize) {
        requireUserId(userId);
        String safeRole = "seller".equalsIgnoreCase(role) ? "seller" : "buyer";
        int safePageNo = Math.max(pageNo == null ? 1 : pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        String userCondition = "seller".equals(safeRole) ? "o.seller_id = :userId" : "o.buyer_id = :userId";
        String statusCondition = status == null ? "" : " AND o.status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("offset", (safePageNo - 1) * safePageSize)
                .addValue("pageSize", safePageSize);
        if (status != null) {
            params.addValue("status", status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_trade_order o"
                + " WHERE " + userCondition + statusCondition + " AND o.deleted = b'0'", params, Long.class);
        List<CampusTradeOrderRespVO> list = jdbcTemplate.queryForList(selectSql() + " WHERE "
                        + userCondition + statusCondition + " AND o.deleted = b'0'"
                        + " ORDER BY o.id DESC LIMIT :offset, :pageSize", params)
                .stream().map(this::toResp).collect(java.util.stream.Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        requireUserId(userId);
        Map<String, Object> order = findOrder(orderId, userId, true);
        if (order == null) {
            throw notFound("订单不存在或无权操作");
        }
        int status = toInt(order.get("status"));
        if (status == STATUS_CLOSED) {
            return;
        }
        if (status != STATUS_WAIT_PAY) {
            throw badRequest("当前订单状态不能取消");
        }
        closeOrder(order, "USER_CANCEL");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO createErrandOrder(Long publisherId, Long postId) {
        requireUserId(publisherId);
        if (postId == null) {
            throw badRequest("代办任务不能为空");
        }
        Map<String, Object> post = lockErrandPost(postId, publisherId);
        BigDecimal amount = decimal(post.get("price"));
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw badRequest("请先设置有效的任务赏金");
        }
        Map<String, Object> publisher = getUser(publisherId);
        if (!Objects.equals(toLong(post.get("tenant_id")), toLong(publisher.get("tenant_id")))) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能发布本校代办任务");
        }
        Map<String, Object> active = findActiveErrandOrder(postId, publisherId);
        if (active != null) {
            return toResp(active);
        }

        String orderNo = createOrderNo();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderNo", orderNo).addValue("publisherId", publisherId).addValue("postId", postId)
                .addValue("amount", amount).addValue("title", crop(value(post, "title"), 255))
                .addValue("coverImage", firstImage(value(post, "images_json")))
                .addValue("tenantId", post.get("tenant_id")).addValue("operator", String.valueOf(publisherId));
        jdbcTemplate.update("INSERT INTO campus_trade_order (order_no, buyer_id, seller_id, product_id, biz_type,"
                        + " amount, item_title_snapshot, item_cover_snapshot, seller_phone_snapshot, status,"
                        + " fulfillment_status, expires_at, creator, updater, create_time, update_time, deleted, tenant_id)"
                        + " VALUES (:orderNo, :publisherId, 0, :postId, 4, :amount, :title, :coverImage, '', 0, 0,"
                        + " DATE_ADD(NOW(), INTERVAL " + PAYMENT_TIMEOUT_MINUTES
                        + " MINUTE), :operator, :operator, NOW(), NOW(), b'0', :tenantId)", params);
        return getErrandOrderByPost(postId, publisherId);
    }

    @Override
    public CampusTradeOrderRespVO getErrandOrderByPost(Long postId, Long userId) {
        requireUserId(userId);
        Map<String, Object> user = getUser(userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.product_id = :postId AND o.biz_type = 4 AND o.tenant_id = :tenantId"
                        + " AND o.deleted = b'0' ORDER BY o.id DESC LIMIT 1",
                new MapSqlParameterSource().addValue("postId", postId).addValue("tenantId", user.get("tenant_id")));
        if (rows.isEmpty()) {
            throw notFound("该任务尚未创建付款订单");
        }
        Map<String, Object> order = rows.get(0);
        int status = toInt(order.get("status"));
        if (!Objects.equals(toLong(order.get("buyer_id")), userId)
                && (status == STATUS_WAIT_PAY || status == STATUS_CLOSED || status == STATUS_REFUNDED)) {
            throw notFound("该任务当前不可接单");
        }
        return toResp(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO acceptErrandOrder(Long orderId, Long helperId) {
        requireUserId(helperId);
        Map<String, Object> order = lockErrandOrder(orderId);
        if (Objects.equals(toLong(order.get("buyer_id")), helperId)) {
            throw badRequest("不能接取自己发布的任务");
        }
        Map<String, Object> helper = getUser(helperId);
        if (!Objects.equals(toLong(order.get("tenant_id")), toLong(helper.get("tenant_id")))) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能接取本校任务");
        }
        if (toInt(order.get("status")) != STATUS_PAID
                || toInt(order.get("fulfillment_status")) != FULFILLMENT_WAIT_ACCEPT) {
            throw badRequest("任务已被接取或当前不可接单");
        }
        LocalDateTime acceptExpiresAt = toLocalDateTime(order.get("accept_expires_at"));
        if (acceptExpiresAt != null && !acceptExpiresAt.isAfter(LocalDateTime.now())) {
            throw badRequest("任务接单时间已截止，赏金将退回发布人");
        }
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET seller_id = :helperId,"
                        + " seller_phone_snapshot = :mobile, fulfillment_status = :accepted, accepted_at = NOW(),"
                        + " updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :waiting"
                        + " AND seller_id = 0 AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("helperId", helperId)
                        .addValue("mobile", crop(value(helper, "mobile"), 20)).addValue("accepted", FULFILLMENT_ACCEPTED)
                        .addValue("paid", STATUS_PAID).addValue("waiting", FULFILLMENT_WAIT_ACCEPT)
                        .addValue("operator", String.valueOf(helperId)));
        if (updated == 0) {
            throw badRequest("任务刚刚已被其他同学接取");
        }
        campusNotificationService.createInteraction(toLong(order.get("buyer_id")), toLong(order.get("tenant_id")),
                helperId, value(helper, "nickname"), "ERRAND_ACCEPTED", "代办任务已被接单",
                value(helper, "nickname") + " 已接单，可进入任务详情沟通办理安排", "POST",
                toLong(order.get("product_id")));
        return getOrder(orderId, helperId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO submitErrandOrder(Long orderId, Long helperId, CampusErrandSubmitReqVO reqVO) {
        requireUserId(helperId);
        Map<String, Object> order = lockErrandOrder(orderId);
        if (!Objects.equals(toLong(order.get("seller_id")), helperId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只有接单人可以提交完成");
        }
        if (toInt(order.get("status")) != STATUS_PAID
                || toInt(order.get("fulfillment_status")) != FULFILLMENT_ACCEPTED) {
            throw badRequest("当前任务状态不能提交完成");
        }
        String note = reqVO == null ? "" : StrUtil.blankToDefault(reqVO.getNote(), "").trim();
        List<String> images = cleanImages(reqVO == null ? null : reqVO.getImages(), 3);
        if (StrUtil.isBlank(note) && images.isEmpty()) {
            throw badRequest("请填写完成说明或上传至少一张完成凭证");
        }
        jdbcTemplate.update("UPDATE campus_trade_order SET fulfillment_status = :waitingConfirm,"
                        + " submitted_at = NOW(), completion_note = :note, completion_images_json = :imagesJson,"
                        + " confirm_expires_at = DATE_ADD(NOW(), INTERVAL " + ERRAND_CONFIRM_TIMEOUT_HOURS + " HOUR),"
                        + " confirm_reminder_stage = 0, dispute_status = 0, updater = :operator,"
                        + " update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :accepted AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("waitingConfirm", FULFILLMENT_WAIT_CONFIRM)
                        .addValue("paid", STATUS_PAID).addValue("accepted", FULFILLMENT_ACCEPTED)
                        .addValue("note", crop(note, 500)).addValue("imagesJson", JsonUtils.toJsonString(images))
                        .addValue("operator", String.valueOf(helperId)));
        Map<String, Object> helper = getUser(helperId);
        campusNotificationService.createInteraction(toLong(order.get("buyer_id")), toLong(order.get("tenant_id")),
                helperId, value(helper, "nickname"), "ERRAND_SUBMITTED", "接单人已提交完成",
                "请在 24 小时内确认完成或发起申诉，逾期系统将自动把赏金结算给接单人", "POST",
                toLong(order.get("product_id")));
        return getOrder(orderId, helperId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO confirmErrandOrder(Long orderId, Long publisherId) {
        requireUserId(publisherId);
        Map<String, Object> order = lockErrandOrder(orderId);
        if (!Objects.equals(toLong(order.get("buyer_id")), publisherId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只有任务发布人可以确认完成");
        }
        if (toInt(order.get("status")) == STATUS_COMPLETED
                && toInt(order.get("fulfillment_status")) == FULFILLMENT_COMPLETED) {
            return getOrder(orderId, publisherId);
        }
        if (toInt(order.get("status")) != STATUS_PAID
                || toInt(order.get("fulfillment_status")) != FULFILLMENT_WAIT_CONFIRM) {
            throw badRequest("请等待接单人提交完成后再确认");
        }
        if (toInt(order.get("dispute_status")) != DISPUTE_NONE) {
            throw badRequest("该订单已发起申诉，赏金已冻结，请等待平台处理");
        }
        settleErrand(order, publisherId, false);
        return getOrder(orderId, publisherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeOrderRespVO disputeErrandOrder(Long orderId, Long publisherId,
                                                      CampusErrandDisputeReqVO reqVO) {
        requireUserId(publisherId);
        Map<String, Object> order = lockErrandOrder(orderId);
        if (!Objects.equals(toLong(order.get("buyer_id")), publisherId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只有任务发布人可以发起申诉");
        }
        if (toInt(order.get("status")) != STATUS_PAID
                || toInt(order.get("fulfillment_status")) != FULFILLMENT_WAIT_CONFIRM) {
            throw badRequest("当前订单不能发起申诉");
        }
        if (toInt(order.get("dispute_status")) != DISPUTE_NONE) {
            throw badRequest("该订单已经发起申诉，请等待平台处理");
        }
        LocalDateTime confirmExpiresAt = toLocalDateTime(order.get("confirm_expires_at"));
        if (confirmExpiresAt != null && !confirmExpiresAt.isAfter(LocalDateTime.now())) {
            throw badRequest("确认期限已结束，订单将自动完成结算");
        }
        String reason = reqVO == null ? "" : StrUtil.blankToDefault(reqVO.getReason(), "").trim();
        if (StrUtil.isBlank(reason)) {
            throw badRequest("请填写申诉原因");
        }
        List<String> images = cleanImages(reqVO.getImages(), 3);
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET dispute_status = :pending,"
                        + " dispute_reason = :reason, dispute_images_json = :imagesJson, disputed_at = NOW(),"
                        + " updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :waitingConfirm"
                        + " AND dispute_status = 0 AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("pending", DISPUTE_PENDING)
                        .addValue("reason", crop(reason, 500)).addValue("imagesJson", JsonUtils.toJsonString(images))
                        .addValue("paid", STATUS_PAID).addValue("waitingConfirm", FULFILLMENT_WAIT_CONFIRM)
                        .addValue("operator", String.valueOf(publisherId)));
        if (updated == 0) {
            throw badRequest("订单状态已变化，请刷新后重试");
        }
        Map<String, Object> publisher = getUser(publisherId);
        campusNotificationService.createInteraction(toLong(order.get("seller_id")), toLong(order.get("tenant_id")),
                publisherId, value(publisher, "nickname"), "ERRAND_DISPUTED", "代办订单进入申诉处理",
                "发布人对完成结果提出异议，赏金已冻结，平台处理前不会结算或退款", "POST",
                toLong(order.get("product_id")));
        return getOrder(orderId, publisherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveErrandDispute(Long orderId, Integer result, String resolution, Long adminId) {
        requireUserId(adminId);
        if (!Objects.equals(result, DISPUTE_HELPER_WIN) && !Objects.equals(result, DISPUTE_PUBLISHER_WIN)) {
            throw badRequest("裁决结果无效");
        }
        String safeResolution = StrUtil.blankToDefault(resolution, "").trim();
        if (StrUtil.isBlank(safeResolution)) {
            throw badRequest("请填写裁决说明");
        }
        Map<String, Object> order = lockErrandOrder(orderId);
        if (toInt(order.get("dispute_status")) != DISPUTE_PENDING
                || toInt(order.get("status")) != STATUS_PAID
                || toInt(order.get("fulfillment_status")) != FULFILLMENT_WAIT_CONFIRM) {
            throw badRequest("该申诉已处理或订单状态已变化");
        }
        String operator = "admin:" + adminId;
        if (Objects.equals(result, DISPUTE_HELPER_WIN)) {
            int updated = jdbcTemplate.update("UPDATE campus_trade_order SET dispute_status = :result,"
                            + " dispute_resolution = :resolution, dispute_resolved_at = NOW(),"
                            + " dispute_resolver_id = :adminId, status = :completed,"
                            + " fulfillment_status = :fulfilled, completed_at = NOW(), auto_confirmed = b'0',"
                            + " updater = :operator, update_time = NOW(), version = version + 1"
                            + " WHERE id = :id AND status = :paid AND fulfillment_status = :waitingConfirm"
                            + " AND dispute_status = :pending AND deleted = b'0'",
                    new MapSqlParameterSource().addValue("id", orderId).addValue("result", DISPUTE_HELPER_WIN)
                            .addValue("resolution", crop(safeResolution, 500)).addValue("adminId", adminId)
                            .addValue("completed", STATUS_COMPLETED).addValue("fulfilled", FULFILLMENT_COMPLETED)
                            .addValue("paid", STATUS_PAID).addValue("waitingConfirm", FULFILLMENT_WAIT_CONFIRM)
                            .addValue("pending", DISPUTE_PENDING).addValue("operator", operator));
            if (updated == 0) {
                throw badRequest("申诉状态已变化，请刷新后重试");
            }
            insertErrandIncome(order, operator);
            notifyDisputeResolved(order, true, safeResolution);
            return;
        }

        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET dispute_status = :result,"
                        + " dispute_resolution = :resolution, dispute_resolved_at = NOW(),"
                        + " dispute_resolver_id = :adminId, fulfillment_status = :canceled,"
                        + " close_reason = 'DISPUTE_PUBLISHER_WIN', closed_at = NOW(),"
                        + " updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :waitingConfirm"
                        + " AND dispute_status = :pending AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("result", DISPUTE_PUBLISHER_WIN)
                        .addValue("resolution", crop(safeResolution, 500)).addValue("adminId", adminId)
                        .addValue("canceled", FULFILLMENT_CANCELED).addValue("paid", STATUS_PAID)
                        .addValue("waitingConfirm", FULFILLMENT_WAIT_CONFIRM).addValue("pending", DISPUTE_PENDING)
                        .addValue("operator", operator));
        if (updated == 0) {
            throw badRequest("申诉状态已变化，请刷新后重试");
        }
        paymentService.refundOrder(orderId, "代办申诉裁决退款", operator);
        notifyDisputeResolved(order, false, safeResolution);
    }

    @Override
    public CampusTradeOrderRespVO cancelErrandOrder(Long orderId, Long publisherId) {
        requireUserId(publisherId);
        Map<String, Object> order = lockErrandOrder(orderId);
        if (!Objects.equals(toLong(order.get("buyer_id")), publisherId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只有任务发布人可以取消");
        }
        int status = toInt(order.get("status"));
        int fulfillment = toInt(order.get("fulfillment_status"));
        if (status == STATUS_REFUNDED) {
            return getOrder(orderId, publisherId);
        }
        if (fulfillment == FULFILLMENT_CANCELED) {
            paymentService.refundOrder(orderId, "代办任务无人接单，发布人取消", "errand-user-" + publisherId);
            return getOrder(orderId, publisherId);
        }
        if (status == STATUS_WAIT_PAY) {
            closeOrder(order, "USER_CANCEL");
            return getOrder(orderId, publisherId);
        }
        if (status != STATUS_PAID || fulfillment != FULFILLMENT_WAIT_ACCEPT) {
            throw badRequest("已有同学接单后不能直接取消，请先协商处理");
        }
        int canceled = jdbcTemplate.update("UPDATE campus_trade_order SET fulfillment_status = :canceled, close_reason = 'USER_CANCEL',"
                        + " closed_at = NOW(), updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :waiting AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("canceled", FULFILLMENT_CANCELED)
                        .addValue("paid", STATUS_PAID).addValue("waiting", FULFILLMENT_WAIT_ACCEPT)
                        .addValue("operator", String.valueOf(publisherId)));
        if (canceled == 0) {
            throw badRequest("任务刚刚已被同学接取，不能再直接取消退款");
        }
        paymentService.refundOrder(orderId, "代办任务无人接单，发布人取消", "errand-user-" + publisherId);
        return getOrder(orderId, publisherId);
    }

    @Scheduled(initialDelay = 60, fixedDelay = 60_000L)
    @Transactional(rollbackFor = Exception.class)
    public void closeExpiredOrders() {
        List<Map<String, Object>> expiredOrders = jdbcTemplate.queryForList(
                "SELECT * FROM campus_trade_order WHERE status = :waitStatus"
                        + " AND expires_at IS NOT NULL AND expires_at <= NOW() AND deleted = b'0' FOR UPDATE",
                new MapSqlParameterSource("waitStatus", STATUS_WAIT_PAY));
        expiredOrders.forEach(order -> closeOrder(order, "TIMEOUT"));
    }

    @Scheduled(initialDelay = 120_000L, fixedDelay = 60_000L)
    public void refundExpiredUnacceptedErrands() {
        List<Long> orderIds = jdbcTemplate.queryForList("SELECT id FROM campus_trade_order"
                        + " WHERE biz_type = 4 AND status = 1"
                        + " AND ((fulfillment_status = 1 AND accept_expires_at IS NOT NULL"
                        + " AND accept_expires_at <= NOW()) OR (fulfillment_status = 5"
                        + " AND close_reason IN ('NO_ACCEPTOR', 'USER_CANCEL')))"
                        + " AND refund_status IN (0, 3) AND deleted = b'0' ORDER BY id LIMIT 20",
                Collections.emptyMap(), Long.class);
        for (Long orderId : orderIds) {
            try {
                int claimed = jdbcTemplate.update("UPDATE campus_trade_order SET fulfillment_status = 5,"
                                + " close_reason = CASE WHEN fulfillment_status = 1 THEN 'NO_ACCEPTOR' ELSE close_reason END,"
                                + " closed_at = COALESCE(closed_at, NOW()), updater = 'errand-timeout', update_time = NOW()"
                                + " WHERE id = :id AND status = 1 AND fulfillment_status IN (1, 5) AND deleted = b'0'",
                        new MapSqlParameterSource("id", orderId));
                if (claimed == 0) {
                    continue;
                }
                paymentService.refundOrder(orderId, "代办任务无人接单取消或超时", "errand-timeout");
            } catch (RuntimeException ex) {
                log.warn("Failed to refund expired errand order, orderId={}: {}", orderId, ex.getMessage());
            }
        }
    }

    @Scheduled(initialDelay = 150_000L, fixedDelay = 60_000L)
    @Transactional(rollbackFor = Exception.class)
    public void autoConfirmExpiredErrands() {
        List<Long> orderIds = jdbcTemplate.queryForList("SELECT id FROM campus_trade_order"
                        + " WHERE biz_type = 4 AND status = 1 AND fulfillment_status = 3"
                        + " AND dispute_status = 0 AND confirm_expires_at IS NOT NULL"
                        + " AND confirm_expires_at <= NOW() AND deleted = b'0' ORDER BY id LIMIT 20",
                Collections.emptyMap(), Long.class);
        for (Long orderId : orderIds) {
            Map<String, Object> order = lockErrandOrder(orderId);
            if (toInt(order.get("status")) == STATUS_PAID
                    && toInt(order.get("fulfillment_status")) == FULFILLMENT_WAIT_CONFIRM
                    && toInt(order.get("dispute_status")) == DISPUTE_NONE) {
                LocalDateTime expiresAt = toLocalDateTime(order.get("confirm_expires_at"));
                if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
                    settleErrand(order, toLong(order.get("buyer_id")), true);
                }
            }
        }
    }

    @Scheduled(initialDelay = 180_000L, fixedDelay = 60_000L)
    public void remindErrandConfirmation() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, buyer_id, seller_id, tenant_id,"
                        + " product_id, confirm_expires_at, confirm_reminder_stage FROM campus_trade_order"
                        + " WHERE biz_type = 4 AND status = 1 AND fulfillment_status = 3 AND dispute_status = 0"
                        + " AND confirm_expires_at > NOW() AND confirm_expires_at <= DATE_ADD(NOW(), INTERVAL 12 HOUR)"
                        + " AND confirm_reminder_stage < 2 AND deleted = b'0' ORDER BY id LIMIT 50",
                Collections.emptyMap());
        for (Map<String, Object> row : rows) {
            LocalDateTime expiresAt = toLocalDateTime(row.get("confirm_expires_at"));
            if (expiresAt == null) {
                continue;
            }
            int stage = expiresAt.isAfter(LocalDateTime.now().plusHours(2)) ? 1 : 2;
            int updated = jdbcTemplate.update("UPDATE campus_trade_order SET confirm_reminder_stage = :stage,"
                            + " updater = 'errand-reminder', update_time = NOW()"
                            + " WHERE id = :id AND confirm_reminder_stage < :stage AND dispute_status = 0"
                            + " AND status = 1 AND fulfillment_status = 3 AND deleted = b'0'",
                    new MapSqlParameterSource().addValue("id", row.get("id")).addValue("stage", stage));
            if (updated == 0) {
                continue;
            }
            Long helperId = toLong(row.get("seller_id"));
            Map<String, Object> helper = getUser(helperId);
            campusNotificationService.createInteraction(toLong(row.get("buyer_id")), toLong(row.get("tenant_id")),
                    helperId, value(helper, "nickname"), "ERRAND_CONFIRM_REMINDER", "请及时确认代办完成情况",
                    stage == 2 ? "确认期限不足 2 小时；如有异议请立即申诉，否则到期将自动结算"
                            : "确认期限不足 12 小时；确认完成后赏金将结算给接单人",
                    "POST", toLong(row.get("product_id")));
        }
    }

    private void settleErrand(Map<String, Object> order, Long publisherId, boolean automatic) {
        Long orderId = toLong(order.get("id"));
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET status = :completed,"
                        + " fulfillment_status = :fulfilled, completed_at = NOW(), auto_confirmed = :automatic,"
                        + " updater = :operator, update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :paid AND fulfillment_status = :waitingConfirm"
                        + " AND dispute_status = 0 AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", orderId).addValue("completed", STATUS_COMPLETED)
                        .addValue("fulfilled", FULFILLMENT_COMPLETED).addValue("automatic", automatic)
                        .addValue("paid", STATUS_PAID).addValue("waitingConfirm", FULFILLMENT_WAIT_CONFIRM)
                        .addValue("operator", automatic ? "errand-auto-confirm" : String.valueOf(publisherId)));
        if (updated == 0) {
            throw badRequest("订单状态已变化，请刷新后重试");
        }
        insertErrandIncome(order, automatic ? "errand-auto-confirm" : String.valueOf(publisherId));
        Long helperId = toLong(order.get("seller_id"));
        BigDecimal amount = decimal(order.get("amount"));
        Map<String, Object> publisher = getUser(publisherId);
        campusNotificationService.createInteraction(helperId, toLong(order.get("tenant_id")), publisherId,
                automatic ? "系统自动确认" : value(publisher, "nickname"),
                automatic ? "ERRAND_AUTO_COMPLETED" : "ERRAND_COMPLETED", "代办赏金已结算",
                (automatic ? "发布人超过 24 小时未操作，系统已自动确认完成；¥" : "发布人已确认完成，¥")
                        + amount.setScale(2) + " 已计入你的可提现收益", "POST", toLong(order.get("product_id")));
        if (automatic) {
            campusNotificationService.createInteraction(publisherId, toLong(order.get("tenant_id")), helperId,
                    "系统自动确认", "ERRAND_AUTO_COMPLETED", "代办订单已自动完成",
                    "你在 24 小时确认期内未操作，系统已将赏金结算给接单人", "POST",
                    toLong(order.get("product_id")));
        }
    }

    private void insertErrandIncome(Map<String, Object> order, String operator) {
        Long orderId = toLong(order.get("id"));
        Long helperId = toLong(order.get("seller_id"));
        BigDecimal amount = decimal(order.get("amount"));
        Long incomeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_commission_record"
                        + " WHERE order_id = :orderId AND receiver_user_id = :helperId AND receiver_type = 6"
                        + " AND deleted = b'0'",
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("helperId", helperId), Long.class);
        if (incomeCount != null && incomeCount > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO campus_commission_record (order_id, order_no, biz_type, receiver_user_id,"
                        + " receiver_type, base_amount, rate, amount, status, settled_at, creator, updater,"
                        + " create_time, update_time, deleted, tenant_id) VALUES (:orderId, :orderNo, 4, :helperId,"
                        + " 6, :amount, 100.00, :amount, 1, NOW(), :operator, :operator, NOW(), NOW(), b'0', :tenantId)",
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("orderNo", order.get("order_no"))
                        .addValue("helperId", helperId).addValue("amount", amount).addValue("operator", operator)
                        .addValue("tenantId", order.get("tenant_id")));
    }

    private void notifyDisputeResolved(Map<String, Object> order, boolean helperWins, String resolution) {
        Long publisherId = toLong(order.get("buyer_id"));
        Long helperId = toLong(order.get("seller_id"));
        Long tenantId = toLong(order.get("tenant_id"));
        Long postId = toLong(order.get("product_id"));
        campusNotificationService.createInteraction(helperId, tenantId, publisherId, "平台裁决",
                "ERRAND_DISPUTE_RESOLVED", helperWins ? "申诉已处理，赏金已结算" : "申诉已处理，赏金将退款",
                resolution, "POST", postId);
        campusNotificationService.createInteraction(publisherId, tenantId, helperId, "平台裁决",
                "ERRAND_DISPUTE_RESOLVED", helperWins ? "申诉已处理，赏金已结算" : "申诉已处理，赏金将原路退款",
                resolution, "POST", postId);
    }

    private Map<String, Object> lockErrandPost(Long postId, Long publisherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_post WHERE id = :id"
                        + " AND user_id = :publisherId AND type = 'help' AND status = 1 AND deleted = b'0'"
                        + " LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource().addValue("id", postId).addValue("publisherId", publisherId));
        if (rows.isEmpty()) {
            throw notFound("代办任务不存在、已下架或无权操作");
        }
        return rows.get(0);
    }

    private Map<String, Object> findActiveErrandOrder(Long postId, Long publisherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.product_id = :postId AND o.buyer_id = :publisherId AND o.biz_type = 4"
                        + " AND o.status IN (0, 1, 2) AND o.deleted = b'0' ORDER BY o.id DESC LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource().addValue("postId", postId).addValue("publisherId", publisherId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> lockErrandOrder(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE id = :id AND biz_type = 4 AND deleted = b'0' LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource("id", orderId));
        if (rows.isEmpty()) {
            throw notFound("代办订单不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> lockPost(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_post"
                        + " WHERE id = :id AND status = 1 AND deleted = b'0' LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource("id", postId));
        if (rows.isEmpty()) {
            throw notFound("商品不存在或已下架");
        }
        return rows.get(0);
    }

    private Map<String, Object> findPendingBuyerOrder(Long postId, Long buyerId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE product_id = :postId AND buyer_id = :buyerId AND status = 0 AND deleted = b'0'"
                        + " ORDER BY id DESC LIMIT 1 FOR UPDATE",
                new MapSqlParameterSource().addValue("postId", postId).addValue("buyerId", buyerId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findOrder(Long orderId, Long userId, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.id = :orderId AND (o.buyer_id = :userId OR o.seller_id = :userId)"
                        + " AND o.deleted = b'0' LIMIT 1" + lock,
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", userId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private CampusTradeOrderRespVO getOrderByOrderNo(String orderNo, Long userId) {
        Map<String, Object> order = findByOrderNo(orderNo, userId);
        if (order == null) {
            throw notFound("订单创建失败");
        }
        return toResp(order);
    }

    private Map<String, Object> findByOrderNo(String orderNo, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.order_no = :orderNo AND o.buyer_id = :userId AND o.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("orderNo", orderNo).addValue("userId", userId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void closeOrder(Map<String, Object> order, String reason) {
        int inventoryState = toInt(order.get("inventory_state"));
        int updated = jdbcTemplate.update("UPDATE campus_trade_order SET status = :status, closed_at = NOW(),"
                        + " close_reason = :reason, updater = 'trade-order', update_time = NOW(), version = version + 1"
                        + ", inventory_state = CASE WHEN inventory_state = 1 THEN 3 ELSE inventory_state END"
                        + " WHERE id = :id AND status = :waitStatus AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id"))
                        .addValue("status", STATUS_CLOSED).addValue("waitStatus", STATUS_WAIT_PAY)
                        .addValue("reason", reason));
        if (updated > 0 && inventoryState == 1) {
            releaseReservedStock(toLong(order.get("product_id")));
        }
    }

    private String selectSql() {
        return "SELECT o.id, o.order_no, o.product_id, o.biz_type, o.buyer_id, o.seller_id, o.inventory_state,"
                + " b.nickname AS buyer_name, s.nickname AS seller_name, o.amount, o.status,"
                + " o.fulfillment_status, o.expires_at, o.accept_expires_at, o.accepted_at, o.submitted_at,"
                + " o.completion_note, o.completion_images_json, o.confirm_expires_at, o.dispute_status,"
                + " o.dispute_reason, o.dispute_images_json, o.disputed_at, o.dispute_resolved_at,"
                + " o.dispute_resolution, o.auto_confirmed,"
                + " o.paid_at, o.completed_at, o.closed_at, o.close_reason, o.refund_status,"
                + " (SELECT COALESCE(SUM(cr.amount), 0) FROM campus_commission_record cr"
                + " WHERE cr.order_id = o.id AND cr.receiver_type = 6 AND cr.status IN (1, 2)"
                + " AND cr.deleted = b'0') AS income_amount,"
                + " o.item_title_snapshot, o.item_cover_snapshot, p.images_json AS post_images_json"
                + " FROM campus_trade_order o"
                + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'"
                // 订单本身保存标题和封面快照；旧订单快照缺失时，软删除帖子仍可作为私有历史兜底。
                + " LEFT JOIN campus_post p ON p.id = o.product_id";
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

    private void releaseReservedStock(Long postId) {
        if (postId == null) {
            return;
        }
        jdbcTemplate.update("UPDATE campus_post SET"
                        + " stock_available = LEAST(stock_total - sold_count, stock_available + 1),"
                        + " sale_status = CASE WHEN stock_total - sold_count > 0 THEN 1 ELSE 2 END,"
                        + " updater = 'trade-order', update_time = NOW()"
                        + " WHERE id = :id AND type = 'idle' AND deleted = b'0'",
                new MapSqlParameterSource("id", postId));
    }

    private CampusTradeOrderRespVO toResp(Map<String, Object> row) {
        CampusTradeOrderRespVO vo = new CampusTradeOrderRespVO();
        vo.setId(toLong(row.get("id")));
        vo.setOrderNo(value(row, "order_no"));
        vo.setPostId(toLong(row.get("product_id")));
        vo.setBizType(toInt(row.get("biz_type")));
        vo.setBuyerId(toLong(row.get("buyer_id")));
        vo.setSellerId(toLong(row.get("seller_id")));
        vo.setBuyerName(value(row, "buyer_name"));
        vo.setSellerName(value(row, "seller_name"));
        vo.setTitle(value(row, "item_title_snapshot"));
        String coverImage = value(row, "item_cover_snapshot");
        String currentPostCover = firstImage(value(row, "post_images_json"));
        if (StrUtil.isBlank(coverImage) || (isTemporarySignedUrl(coverImage) && StrUtil.isNotBlank(currentPostCover))) {
            coverImage = currentPostCover;
        }
        vo.setCoverImage(refreshFileUrl(coverImage));
        vo.setAmount(decimal(row.get("amount")));
        int status = toInt(row.get("status"));
        vo.setStatus(status);
        vo.setStatusText(statusText(status));
        int fulfillmentStatus = toInt(row.get("fulfillment_status"));
        vo.setFulfillmentStatus(fulfillmentStatus);
        int disputeStatus = toInt(row.get("dispute_status"));
        vo.setFulfillmentStatusText(disputeStatus == DISPUTE_PENDING
                ? "申诉处理中 · 赏金已冻结" : fulfillmentStatusText(fulfillmentStatus));
        vo.setExpiresAt(toLocalDateTime(row.get("expires_at")));
        vo.setAcceptExpiresAt(toLocalDateTime(row.get("accept_expires_at")));
        vo.setAcceptedAt(toLocalDateTime(row.get("accepted_at")));
        vo.setSubmittedAt(toLocalDateTime(row.get("submitted_at")));
        vo.setCompletionNote(value(row, "completion_note"));
        vo.setCompletionImages(refreshFileUrls(parseImages(value(row, "completion_images_json"))));
        vo.setConfirmExpiresAt(toLocalDateTime(row.get("confirm_expires_at")));
        vo.setDisputeStatus(disputeStatus);
        vo.setDisputeStatusText(disputeStatusText(disputeStatus));
        vo.setDisputeReason(value(row, "dispute_reason"));
        vo.setDisputeImages(refreshFileUrls(parseImages(value(row, "dispute_images_json"))));
        vo.setDisputedAt(toLocalDateTime(row.get("disputed_at")));
        vo.setDisputeResolvedAt(toLocalDateTime(row.get("dispute_resolved_at")));
        vo.setDisputeResolution(value(row, "dispute_resolution"));
        vo.setAutoConfirmed(toBoolean(row.get("auto_confirmed")));
        vo.setPaidAt(toLocalDateTime(row.get("paid_at")));
        vo.setCompletedAt(toLocalDateTime(row.get("completed_at")));
        vo.setClosedAt(toLocalDateTime(row.get("closed_at")));
        vo.setCloseReason(value(row, "close_reason"));
        vo.setRefundStatus(toInt(row.get("refund_status")));
        vo.setIncomeAmount(decimal(row.get("income_amount")));
        vo.setExpired(status == STATUS_WAIT_PAY && vo.getExpiresAt() != null
                && !vo.getExpiresAt().isAfter(LocalDateTime.now()));
        return vo;
    }

    private Map<String, Object> getUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, tenant_id, nickname, mobile FROM campus_miniapp_user"
                        + " WHERE id = :id AND deleted = b'0' LIMIT 1", new MapSqlParameterSource("id", userId));
        if (rows.isEmpty()) {
            throw notFound("用户不存在");
        }
        return rows.get(0);
    }

    private String statusText(int status) {
        switch (status) {
            case STATUS_WAIT_PAY: return "待付款";
            case STATUS_PAID: return "已付款";
            case STATUS_COMPLETED: return "已完成";
            case STATUS_CLOSED: return "已关闭";
            case STATUS_REFUNDED: return "已退款";
            default: return "未知状态";
        }
    }

    private String firstImage(String imagesJson) {
        if (StrUtil.isBlank(imagesJson)) return "";
        List<String> images = parseImages(imagesJson);
        return images == null || images.isEmpty() ? "" : StrUtil.blankToDefault(images.get(0), "");
    }

    private List<String> parseImages(String imagesJson) {
        if (StrUtil.isBlank(imagesJson)) {
            return Collections.emptyList();
        }
        List<String> images = JsonUtils.parseObjectQuietly(imagesJson, new TypeReference<List<String>>() { });
        return images == null ? Collections.emptyList() : images;
    }

    private List<String> cleanImages(List<String> images, int maxCount) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        for (String image : images) {
            String safe = StrUtil.blankToDefault(image, "").trim();
            if (StrUtil.isNotBlank(safe) && !cleaned.contains(safe)) {
                cleaned.add(crop(safe, 1000));
            }
            if (cleaned.size() >= maxCount) {
                break;
            }
        }
        return cleaned;
    }

    private List<String> refreshFileUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        return urls.stream().map(this::refreshFileUrl).collect(java.util.stream.Collectors.toList());
    }

    private String refreshFileUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        try {
            return fileApi.presignGetUrl(url, null);
        } catch (RuntimeException ex) {
            return url;
        }
    }

    private String fulfillmentStatusText(int status) {
        switch (status) {
            case FULFILLMENT_WAIT_PAY: return "待支付赏金";
            case FULFILLMENT_WAIT_ACCEPT: return "待接单";
            case FULFILLMENT_ACCEPTED: return "进行中";
            case FULFILLMENT_WAIT_CONFIRM: return "待发布人确认";
            case FULFILLMENT_COMPLETED: return "已完成并结算";
            case FULFILLMENT_CANCELED: return "已取消退款";
            default: return "未知履约状态";
        }
    }

    private String disputeStatusText(int status) {
        switch (status) {
            case DISPUTE_PENDING: return "申诉处理中";
            case DISPUTE_HELPER_WIN: return "已裁决：接单人胜诉";
            case DISPUTE_PUBLISHER_WIN: return "已裁决：发布人胜诉";
            default: return "无申诉";
        }
    }

    private boolean isTemporarySignedUrl(String url) {
        return StrUtil.containsIgnoreCase(url, "X-Amz-Signature=")
                || StrUtil.containsIgnoreCase(url, "X-Amz-Expires=")
                || StrUtil.containsIgnoreCase(url, "OSSAccessKeyId=")
                || StrUtil.containsIgnoreCase(url, "Expires=");
    }

    private String createOrderNo() {
        return "CS" + System.currentTimeMillis() + Long.toHexString(Double.doubleToLongBits(Math.random()))
                .substring(0, 8);
    }

    private static void requireUserId(Long userId) {
        if (userId == null) throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "请先登录");
    }

    private RuntimeException badRequest(String message) {
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    private RuntimeException notFound(String message) {
        return exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), message);
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }

    private static String crop(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
