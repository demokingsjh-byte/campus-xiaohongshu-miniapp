package cn.iocoder.yudao.module.campus.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.campus.controller.admin.trade.vo.CampusTradeOrderPageReqVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class CampusTradeOrderAdminServiceImpl implements CampusTradeOrderAdminService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileApi fileApi;

    public CampusTradeOrderAdminServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FileApi fileApi) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileApi = fileApi;
    }

    @Override
    public PageResult<Map<String, Object>> getOrderPage(CampusTradeOrderPageReqVO reqVO) {
        int pageNo = Math.max(reqVO.getPageNo() == null ? 1 : reqVO.getPageNo(), 1);
        int pageSize = Math.min(Math.max(reqVO.getPageSize() == null ? 20 : reqVO.getPageSize(), 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", (pageNo - 1) * pageSize)
                .addValue("pageSize", pageSize);
        String where = buildWhere(reqVO, params);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_trade_order o"
                        + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                        + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'"
                        + where, params, Long.class);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(selectSql() + where
                + " ORDER BY o.id DESC LIMIT :offset, :pageSize", params)
                .stream().map(this::normalizeRow).collect(Collectors.toList());
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    public Map<String, Object> getOrder(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.id = :orderId AND o.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("orderId", orderId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "订单不存在");
        }
        return normalizeRow(rows.get(0));
    }

    @Override
    public Map<String, Object> getSummary(Long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String tenantClause = "";
        if (tenantId != null) {
            tenantClause = " AND tenant_id = :tenantId";
            params.addValue("tenantId", tenantId);
        }
        return jdbcTemplate.queryForMap("SELECT"
                + " COUNT(*) AS totalCount,"
                + " SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS waitingCount,"
                + " SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END) AS paidCount,"
                + " SUM(CASE WHEN status = 4 OR refund_status = 2 THEN 1 ELSE 0 END) AS refundedCount,"
                + " COALESCE(SUM(CASE WHEN status IN (1, 2, 4) THEN amount ELSE 0 END), 0) AS paidAmount,"
                + " COALESCE(SUM(CASE WHEN refund_status = 2 THEN refund_amount ELSE 0 END), 0) AS refundedAmount"
                + " FROM campus_trade_order WHERE deleted = b'0'" + tenantClause, params);
    }

    private String buildWhere(CampusTradeOrderPageReqVO reqVO, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE o.deleted = b'0'");
        if (StrUtil.isNotBlank(reqVO.getOrderNo())) {
            where.append(" AND o.order_no LIKE :orderNo");
            params.addValue("orderNo", "%" + reqVO.getOrderNo().trim() + "%");
        }
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            where.append(" AND (o.item_title_snapshot LIKE :keyword OR b.nickname LIKE :keyword"
                    + " OR s.nickname LIKE :keyword OR b.mobile LIKE :keyword OR s.mobile LIKE :keyword)");
            params.addValue("keyword", "%" + reqVO.getKeyword().trim() + "%");
        }
        if (reqVO.getStatus() != null) {
            where.append(" AND o.status = :status");
            params.addValue("status", reqVO.getStatus());
        }
        if (reqVO.getRefundStatus() != null) {
            where.append(" AND o.refund_status = :refundStatus");
            params.addValue("refundStatus", reqVO.getRefundStatus());
        }
        if (reqVO.getTenantId() != null) {
            where.append(" AND o.tenant_id = :tenantId");
            params.addValue("tenantId", reqVO.getTenantId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            where.append(" AND o.create_time >= :createTimeStart");
            params.addValue("createTimeStart", reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            where.append(" AND o.create_time <= :createTimeEnd");
            params.addValue("createTimeEnd", reqVO.getCreateTimeEnd());
        }
        return where.toString();
    }

    private String selectSql() {
        return "SELECT o.id, o.order_no AS orderNo, o.product_id AS productId,"
                + " o.buyer_id AS buyerId, o.seller_id AS sellerId, o.tenant_id AS tenantId,"
                + " o.item_title_snapshot AS title, o.item_cover_snapshot AS coverImage,"
                + " p.images_json AS postImagesJson, o.amount, o.status,"
                + " o.expires_at AS expiresAt, o.paid_at AS paidAt, o.completed_at AS completedAt,"
                + " o.closed_at AS closedAt, o.close_reason AS closeReason,"
                + " o.wechat_trade_state AS wechatTradeState, o.wechat_query_at AS wechatQueryAt,"
                + " o.wechat_query_error AS wechatQueryError, o.wx_transaction_id AS wxTransactionId,"
                + " o.refund_no AS refundNo, o.wx_refund_id AS wxRefundId,"
                + " o.refund_status AS refundStatus, o.refund_amount AS refundAmount,"
                + " o.refund_reason AS refundReason, o.refund_requested_at AS refundRequestedAt,"
                + " o.refunded_at AS refundedAt, o.refund_error AS refundError,"
                + " o.refund_operator AS refundOperator, o.refund_notify_at AS refundNotifyAt,"
                + " o.create_time AS createTime, o.update_time AS updateTime,"
                + " b.nickname AS buyerName, b.avatar AS buyerAvatar, b.mobile AS buyerMobile,"
                + " s.nickname AS sellerName, s.avatar AS sellerAvatar, s.mobile AS sellerMobile,"
                + " p.location AS location, p.contact AS sellerContact,"
                + " p.school_name AS schoolName, p.campus_name AS campusName"
                + " FROM campus_trade_order o"
                + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'"
                + " LEFT JOIN campus_post p ON p.id = o.product_id AND p.deleted = b'0'";
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        String cover = value(row.get("coverImage"));
        if (StrUtil.isBlank(cover)) {
            cover = firstImage(value(row.get("postImagesJson")));
        }
        row.put("coverImage", refreshFileUrl(cover));
        row.put("statusText", statusText(number(row.get("status"))));
        row.put("refundStatusText", refundStatusText(number(row.get("refundStatus"))));
        row.remove("postImagesJson");
        return row;
    }

    private String firstImage(String imagesJson) {
        if (StrUtil.isBlank(imagesJson)) {
            return "";
        }
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

    private String statusText(int status) {
        switch (status) {
            case 0: return "待付款";
            case 1: return "已付款";
            case 2: return "已完成";
            case 3: return "已关闭";
            case 4: return "已退款";
            default: return "未知";
        }
    }

    private String refundStatusText(int status) {
        switch (status) {
            case 1: return "退款处理中";
            case 2: return "退款成功";
            case 3: return "退款失败";
            default: return "未退款";
        }
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
