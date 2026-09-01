package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageRespVO;
import cn.iocoder.yudao.module.campus.controller.app.trade.vo.CampusTradeMessageSendReqVO;
import cn.iocoder.yudao.module.campus.framework.trade.CampusTradeChatProperties;
import cn.iocoder.yudao.module.campus.service.notification.CampusNotificationService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class CampusTradeChatServiceImpl implements CampusTradeChatService {

    private static final int STATUS_PAID = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int MAX_MESSAGES_PER_MINUTE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CampusTradeChatProperties properties;
    private final CampusNotificationService campusNotificationService;

    public CampusTradeChatServiceImpl(NamedParameterJdbcTemplate jdbcTemplate,
                                      CampusTradeChatProperties properties,
                                      CampusNotificationService campusNotificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.campusNotificationService = campusNotificationService;
    }

    @Override
    public List<CampusTradeMessageRespVO> getMessages(Long orderId, Long userId) {
        requireUserId(userId);
        Map<String, Object> order = getParticipantOrder(orderId, userId);
        if (order.get("paid_at") == null) {
            throw forbidden("支付完成后才能查看交易会话");
        }
        return jdbcTemplate.queryForList("SELECT m.id, m.order_id, m.sender_id, m.receiver_id, m.content,"
                        + " m.create_time, u.nickname AS sender_name FROM campus_trade_message m"
                        + " LEFT JOIN campus_miniapp_user u ON u.id = m.sender_id AND u.deleted = b'0'"
                        + " WHERE m.order_id = :orderId AND m.deleted = b'0' ORDER BY m.id ASC LIMIT 200",
                new MapSqlParameterSource("orderId", orderId)).stream()
                .map(this::toResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusTradeMessageRespVO sendMessage(Long userId, CampusTradeMessageSendReqVO reqVO) {
        requireUserId(userId);
        Map<String, Object> order = getParticipantOrder(reqVO.getOrderId(), userId);
        int orderStatus = intValue(order.get("status"));
        if (order.get("paid_at") == null || (orderStatus != STATUS_PAID && orderStatus != STATUS_COMPLETED)) {
            throw forbidden("仅已付款或已完成订单可以发送交易消息");
        }

        String content = reqVO.getContent().replaceAll("[\\r\\n]{3,}", "\n\n").trim();
        ensureSafeContent(content);
        Long recentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_trade_message"
                        + " WHERE order_id = :orderId AND sender_id = :senderId AND deleted = b'0'"
                        + " AND create_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)",
                new MapSqlParameterSource().addValue("orderId", reqVO.getOrderId())
                        .addValue("senderId", userId), Long.class);
        if (recentCount != null && recentCount >= MAX_MESSAGES_PER_MINUTE) {
            throw badRequest("消息发送过于频繁，请稍后再试");
        }

        Long buyerId = longValue(order.get("buyer_id"));
        Long sellerId = longValue(order.get("seller_id"));
        Long receiverId = userId.equals(buyerId) ? sellerId : buyerId;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", reqVO.getOrderId())
                .addValue("senderId", userId)
                .addValue("receiverId", receiverId)
                .addValue("tenantId", order.get("tenant_id"))
                .addValue("content", content)
                .addValue("operator", String.valueOf(userId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO campus_trade_message (order_id, sender_id, receiver_id, tenant_id,"
                        + " content, creator, updater, create_time, update_time, deleted) VALUES (:orderId,"
                        + " :senderId, :receiverId, :tenantId, :content, :operator, :operator, NOW(), NOW(), b'0')",
                params, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw exception0(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(), "消息发送失败，请稍后重试");
        }
        CampusTradeMessageRespVO response = getMessage(key.longValue(), reqVO.getOrderId());
        campusNotificationService.createInteraction(receiverId, longValue(order.get("tenant_id")), userId,
                response.getSenderName(), "ORDER_MESSAGE", "收到一条订单消息", crop(content, 80), "POST",
                longValue(order.get("product_id")));
        return response;
    }

    private Map<String, Object> getParticipantOrder(Long orderId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT o.id, o.product_id, o.buyer_id,"
                        + " o.seller_id, o.status, o.paid_at, o.tenant_id FROM campus_trade_order o"
                        + " INNER JOIN campus_post p ON p.id = o.product_id"
                        + " AND (p.type = 'idle' OR (p.type = 'help' AND o.biz_type = 4"
                        + " AND o.fulfillment_status IN (2, 3, 4)))"
                        + " WHERE o.id = :orderId AND (o.buyer_id = :userId OR o.seller_id = :userId)"
                        + " AND o.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", userId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在或无权查看会话");
        }
        return rows.get(0);
    }

    private CampusTradeMessageRespVO getMessage(Long id, Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT m.id, m.order_id, m.sender_id,"
                        + " m.receiver_id, m.content, m.create_time, u.nickname AS sender_name"
                        + " FROM campus_trade_message m LEFT JOIN campus_miniapp_user u"
                        + " ON u.id = m.sender_id AND u.deleted = b'0' WHERE m.id = :id"
                        + " AND m.order_id = :orderId AND m.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource().addValue("id", id).addValue("orderId", orderId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "消息不存在");
        }
        return toResp(rows.get(0));
    }

    private void ensureSafeContent(String content) {
        String normalizedContent = normalize(content);
        for (String word : properties.getSensitiveWords() == null
                ? Collections.<String>emptyList() : properties.getSensitiveWords()) {
            String normalizedWord = normalize(word);
            if (StrUtil.isNotBlank(normalizedWord) && normalizedContent.contains(normalizedWord)) {
                throw badRequest("消息包含不适合交易沟通的敏感内容，请修改后发送");
            }
        }
    }

    private String normalize(String value) {
        return StrUtil.blankToDefault(value, "").toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】《》]+", "");
    }

    private CampusTradeMessageRespVO toResp(Map<String, Object> row) {
        CampusTradeMessageRespVO vo = new CampusTradeMessageRespVO();
        vo.setId(longValue(row.get("id")));
        vo.setOrderId(longValue(row.get("order_id")));
        vo.setSenderId(longValue(row.get("sender_id")));
        vo.setSenderName(StrUtil.blankToDefault(stringValue(row.get("sender_name")), "校园同学"));
        vo.setReceiverId(longValue(row.get("receiver_id")));
        vo.setContent(stringValue(row.get("content")));
        vo.setCreateTime(toLocalDateTime(row.get("create_time")));
        return vo;
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "请先登录");
        }
    }

    private RuntimeException badRequest(String message) {
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    private RuntimeException forbidden(String message) {
        return exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), message);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return value instanceof LocalDateTime ? (LocalDateTime) value : null;
    }

    private static String crop(String value, int maxLength) {
        String text = StrUtil.blankToDefault(value, "");
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
