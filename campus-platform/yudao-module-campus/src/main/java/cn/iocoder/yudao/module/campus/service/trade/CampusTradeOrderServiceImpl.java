package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeOrderRespVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
@Validated
public class CampusTradeOrderServiceImpl implements CampusTradeOrderService {

    public static final int STATUS_WAIT_PAY = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_CLOSED = 3;
    public static final int STATUS_REFUNDED = 4;

    private static final int PAYMENT_TIMEOUT_MINUTES = 15;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileApi fileApi;

    public CampusTradeOrderServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
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
        if (!Objects.equals(toLong(post.get("tenant_id")), toLong(buyer.get("tenant_id")))) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只能购买本校商品");
        }

        Map<String, Object> activeOrder = findActiveProductOrder(reqVO.getPostId());
        if (activeOrder != null) {
            int status = toInt(activeOrder.get("status"));
            LocalDateTime expiresAt = toLocalDateTime(activeOrder.get("expires_at"));
            if (status == STATUS_WAIT_PAY && expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
                closeOrder(activeOrder, "TIMEOUT");
            } else if (Objects.equals(toLong(activeOrder.get("buyer_id")), buyerId)) {
                return toResp(activeOrder);
            } else {
                throw badRequest(status == STATUS_PAID ? "商品已售出" : "商品已被其他订单锁定");
            }
        }

        String orderNo = createOrderNo();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderNo", orderNo)
                .addValue("buyerId", buyerId)
                .addValue("sellerId", sellerId)
                .addValue("postId", reqVO.getPostId())
                .addValue("amount", amount)
                .addValue("title", crop(value(post, "title"), 255))
                .addValue("coverImage", firstImage(value(post, "images_json")))
                .addValue("tenantId", post.get("tenant_id"))
                .addValue("operator", String.valueOf(buyerId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_trade_order (order_no, buyer_id, seller_id, product_id, amount,"
                        + " item_title_snapshot, item_cover_snapshot, status, expires_at, creator, updater,"
                        + " create_time, update_time, deleted, tenant_id) VALUES (:orderNo, :buyerId, :sellerId,"
                        + " :postId, :amount, :title, :coverImage, 0, DATE_ADD(NOW(), INTERVAL "
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

    @Scheduled(initialDelay = 60, fixedDelay = 60_000L)
    @Transactional(rollbackFor = Exception.class)
    public void closeExpiredOrders() {
        jdbcTemplate.update("UPDATE campus_trade_order SET status = :closedStatus, closed_at = NOW(),"
                        + " close_reason = 'TIMEOUT', updater = 'trade-order-job', update_time = NOW()"
                        + " WHERE status = :waitStatus AND expires_at IS NOT NULL AND expires_at <= NOW()"
                        + " AND deleted = b'0'",
                new MapSqlParameterSource().addValue("closedStatus", STATUS_CLOSED)
                        .addValue("waitStatus", STATUS_WAIT_PAY));
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

    private Map<String, Object> findActiveProductOrder(Long postId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM campus_trade_order"
                        + " WHERE product_id = :postId AND status IN (0, 1) AND deleted = b'0'"
                        + " ORDER BY id DESC LIMIT 1 FOR UPDATE", new MapSqlParameterSource("postId", postId));
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
        jdbcTemplate.update("UPDATE campus_trade_order SET status = :status, closed_at = NOW(),"
                        + " close_reason = :reason, updater = 'trade-order', update_time = NOW(), version = version + 1"
                        + " WHERE id = :id AND status = :waitStatus AND deleted = b'0'",
                new MapSqlParameterSource().addValue("id", order.get("id"))
                        .addValue("status", STATUS_CLOSED).addValue("waitStatus", STATUS_WAIT_PAY)
                        .addValue("reason", reason));
    }

    private String selectSql() {
        return "SELECT o.id, o.order_no, o.product_id, o.buyer_id, o.seller_id,"
                + " b.nickname AS buyer_name, s.nickname AS seller_name, o.amount, o.status,"
                + " o.expires_at, o.paid_at, o.completed_at, o.closed_at, o.close_reason,"
                + " o.item_title_snapshot, o.item_cover_snapshot, p.images_json AS post_images_json"
                + " FROM campus_trade_order o"
                + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'"
                + " LEFT JOIN campus_post p ON p.id = o.product_id AND p.deleted = b'0'";
    }

    private CampusTradeOrderRespVO toResp(Map<String, Object> row) {
        CampusTradeOrderRespVO vo = new CampusTradeOrderRespVO();
        vo.setId(toLong(row.get("id")));
        vo.setOrderNo(value(row, "order_no"));
        vo.setPostId(toLong(row.get("product_id")));
        vo.setBuyerId(toLong(row.get("buyer_id")));
        vo.setSellerId(toLong(row.get("seller_id")));
        vo.setBuyerName(value(row, "buyer_name"));
        vo.setSellerName(value(row, "seller_name"));
        vo.setTitle(value(row, "item_title_snapshot"));
        String coverImage = value(row, "item_cover_snapshot");
        if (StrUtil.isBlank(coverImage)) {
            coverImage = firstImage(value(row, "post_images_json"));
        }
        vo.setCoverImage(refreshFileUrl(coverImage));
        vo.setAmount(decimal(row.get("amount")));
        int status = toInt(row.get("status"));
        vo.setStatus(status);
        vo.setStatusText(statusText(status));
        vo.setExpiresAt(toLocalDateTime(row.get("expires_at")));
        vo.setPaidAt(toLocalDateTime(row.get("paid_at")));
        vo.setCompletedAt(toLocalDateTime(row.get("completed_at")));
        vo.setClosedAt(toLocalDateTime(row.get("closed_at")));
        vo.setCloseReason(value(row, "close_reason"));
        vo.setExpired(status == STATUS_WAIT_PAY && vo.getExpiresAt() != null
                && !vo.getExpiresAt().isAfter(LocalDateTime.now()));
        return vo;
    }

    private Map<String, Object> getUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, tenant_id FROM campus_miniapp_user"
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
        List<String> images = JsonUtils.parseObjectQuietly(imagesJson, new TypeReference<List<String>>() { });
        return images == null || images.isEmpty() ? "" : StrUtil.blankToDefault(images.get(0), "");
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
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
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
