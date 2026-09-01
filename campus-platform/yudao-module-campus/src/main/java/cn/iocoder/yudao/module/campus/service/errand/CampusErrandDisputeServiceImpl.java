package cn.iocoder.yudao.module.campus.service.errand;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.errand.vo.CampusErrandDisputePageReqVO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class CampusErrandDisputeServiceImpl implements CampusErrandDisputeService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CampusErrandDisputeServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<Map<String, Object>> getPage(CampusErrandDisputePageReqVO reqVO) {
        int pageNo = Math.max(reqVO.getPageNo() == null ? 1 : reqVO.getPageNo(), 1);
        int pageSize = Math.min(Math.max(reqVO.getPageSize() == null ? 20 : reqVO.getPageSize(), 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", (pageNo - 1) * pageSize).addValue("pageSize", pageSize);
        String where = buildWhere(reqVO, params);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_trade_order o"
                + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'" + where,
                params, Long.class);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(selectSql() + where
                + " ORDER BY CASE WHEN o.dispute_status = 1 THEN 0 ELSE 1 END, o.disputed_at ASC, o.id ASC"
                + " LIMIT :offset, :pageSize", params);
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    public Map<String, Object> get(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE o.id = :id AND o.biz_type = 4 AND o.dispute_status <> 0"
                        + " AND o.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", orderId));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "代办申诉不存在");
        }
        Map<String, Object> detail = rows.get(0);
        detail.put("messages", jdbcTemplate.queryForList("SELECT m.id, m.sender_id AS senderId,"
                        + " u.nickname AS senderName, m.content, m.create_time AS createTime"
                        + " FROM campus_trade_message m LEFT JOIN campus_miniapp_user u"
                        + " ON u.id = m.sender_id AND u.deleted = b'0'"
                        + " WHERE m.order_id = :orderId AND m.deleted = b'0'"
                        + " ORDER BY m.create_time ASC, m.id ASC",
                new MapSqlParameterSource("orderId", orderId)));
        return detail;
    }

    private String buildWhere(CampusErrandDisputePageReqVO reqVO, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE o.biz_type = 4 AND o.dispute_status <> 0"
                + " AND o.deleted = b'0'");
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            where.append(" AND (o.order_no LIKE :keyword OR o.item_title_snapshot LIKE :keyword"
                    + " OR b.nickname LIKE :keyword OR s.nickname LIKE :keyword)");
            params.addValue("keyword", "%" + reqVO.getKeyword().trim() + "%");
        }
        if (reqVO.getStatus() != null) {
            where.append(" AND o.dispute_status = :status");
            params.addValue("status", reqVO.getStatus());
        }
        if (reqVO.getTenantId() != null) {
            where.append(" AND o.tenant_id = :tenantId");
            params.addValue("tenantId", reqVO.getTenantId());
        }
        return where.toString();
    }

    private String selectSql() {
        return "SELECT o.id AS orderId, o.order_no AS orderNo, o.product_id AS postId,"
                + " o.item_title_snapshot AS title, o.item_cover_snapshot AS coverImage, o.amount,"
                + " o.buyer_id AS publisherId, b.nickname AS publisherName, b.mobile AS publisherMobile,"
                + " o.seller_id AS helperId, s.nickname AS helperName, s.mobile AS helperMobile,"
                + " o.tenant_id AS tenantId, o.status AS orderStatus, o.fulfillment_status AS fulfillmentStatus,"
                + " o.completion_note AS completionNote, o.completion_images_json AS completionImagesJson,"
                + " o.submitted_at AS submittedAt, o.confirm_expires_at AS confirmExpiresAt,"
                + " o.dispute_status AS disputeStatus, o.dispute_reason AS disputeReason,"
                + " o.dispute_images_json AS disputeImagesJson, o.disputed_at AS disputedAt,"
                + " o.dispute_resolution AS disputeResolution, o.dispute_resolved_at AS disputeResolvedAt,"
                + " o.dispute_resolver_id AS disputeResolverId, o.refund_status AS refundStatus,"
                + " o.create_time AS createTime, o.update_time AS updateTime"
                + " FROM campus_trade_order o"
                + " LEFT JOIN campus_miniapp_user b ON b.id = o.buyer_id AND b.deleted = b'0'"
                + " LEFT JOIN campus_miniapp_user s ON s.id = o.seller_id AND s.deleted = b'0'";
    }
}
