package cn.iocoder.yudao.module.campus.service.job;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditPageReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditReviewReqVO;
import cn.iocoder.yudao.module.campus.service.contentsecurity.CampusContentCheckResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class CampusJobAuditServiceImpl implements CampusJobAuditService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CampusJobAuditServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<Map<String, Object>> getPage(CampusJobAuditPageReqVO reqVO) {
        int pageNo = Math.max(reqVO.getPageNo() == null ? 1 : reqVO.getPageNo(), 1);
        int pageSize = Math.min(Math.max(reqVO.getPageSize() == null ? 20 : reqVO.getPageSize(), 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", (pageNo - 1) * pageSize)
                .addValue("pageSize", pageSize);
        String where = buildWhere(reqVO, params);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_post p"
                + " LEFT JOIN campus_miniapp_user u ON u.id = p.user_id AND u.deleted = b'0'"
                + where, params, Long.class);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(selectSql() + where
                + " ORDER BY CASE WHEN p.status = 0 THEN 0 ELSE 1 END, p.create_time ASC, p.id ASC"
                + " LIMIT :offset, :pageSize", params);
        return new PageResult<>(list, total == null ? 0L : total);
    }

    @Override
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql()
                        + " WHERE p.id = :id AND p.type = 'job' AND p.deleted = b'0' LIMIT 1",
                new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            throw exception0(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "兼职信息不存在");
        }
        return rows.get(0);
    }

    @Override
    public Map<String, Object> getSummary(Long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String tenantCondition = "";
        if (tenantId != null) {
            tenantCondition = " AND tenant_id = :tenantId";
            params.addValue("tenantId", tenantId);
        }
        return jdbcTemplate.queryForMap("SELECT COUNT(*) AS totalCount,"
                + " SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pendingCount,"
                + " SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS approvedCount,"
                + " SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS rejectedCount"
                + " FROM campus_post WHERE type = 'job' AND deleted = b'0'" + tenantCondition, params);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(CampusJobAuditReviewReqVO reqVO, Long auditorId) {
        Map<String, Object> job = get(reqVO.getId());
        int currentStatus = ((Number) job.get("status")).intValue();
        if (currentStatus != STATUS_PENDING) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "该兼职信息已完成审核，请刷新列表");
        }
        String reason = StrUtil.blankToDefault(reqVO.getReason(), "").trim();
        if (!Boolean.TRUE.equals(reqVO.getApproved()) && StrUtil.isBlank(reason)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "驳回时必须填写原因");
        }
        if (Boolean.TRUE.equals(reqVO.getApproved()) && hasRiskyContent(reqVO.getId())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "内容安全检测未通过，不能审核发布");
        }
        int targetStatus = Boolean.TRUE.equals(reqVO.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", reqVO.getId())
                .addValue("status", targetStatus)
                .addValue("reason", reason)
                .addValue("auditorId", auditorId)
                .addValue("operator", "admin:" + auditorId);
        int updated = jdbcTemplate.update("UPDATE campus_post SET status = :status, audit_reason = :reason,"
                + " audit_time = NOW(), auditor_id = :auditorId, updater = :operator, update_time = NOW()"
                + " WHERE id = :id AND type = 'job' AND status = 0 AND deleted = b'0'", params);
        if (updated == 0) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "审核状态已变化，请刷新后重试");
        }
    }

    private boolean hasRiskyContent(Long postId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campus_content_audit"
                        + " WHERE entity_type = 'POST' AND entity_id = :postId AND suggest = :risky"
                        + " AND deleted = b'0'",
                new MapSqlParameterSource("postId", postId)
                        .addValue("risky", CampusContentCheckResult.RISKY), Long.class);
        return count != null && count > 0;
    }

    private String buildWhere(CampusJobAuditPageReqVO reqVO, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE p.type = 'job' AND p.deleted = b'0'");
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            where.append(" AND (p.title LIKE :keyword OR p.content LIKE :keyword OR p.contact LIKE :keyword"
                    + " OR u.nickname LIKE :keyword OR u.mobile LIKE :keyword)");
            params.addValue("keyword", "%" + reqVO.getKeyword().trim() + "%");
        }
        if (reqVO.getStatus() != null) {
            where.append(" AND p.status = :status");
            params.addValue("status", reqVO.getStatus());
        }
        if (reqVO.getTenantId() != null) {
            where.append(" AND p.tenant_id = :tenantId");
            params.addValue("tenantId", reqVO.getTenantId());
        }
        if (StrUtil.isNotBlank(reqVO.getSchoolName())) {
            where.append(" AND p.school_name LIKE :schoolName");
            params.addValue("schoolName", "%" + reqVO.getSchoolName().trim() + "%");
        }
        if (StrUtil.isNotBlank(reqVO.getCampusName())) {
            where.append(" AND p.campus_name LIKE :campusName");
            params.addValue("campusName", "%" + reqVO.getCampusName().trim() + "%");
        }
        if (reqVO.getCreateTimeStart() != null) {
            where.append(" AND p.create_time >= :createTimeStart");
            params.addValue("createTimeStart", reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            where.append(" AND p.create_time <= :createTimeEnd");
            params.addValue("createTimeEnd", reqVO.getCreateTimeEnd());
        }
        return where.toString();
    }

    private String selectSql() {
        return "SELECT p.id, p.user_id AS userId, p.tenant_id AS tenantId, p.school_name AS schoolName,"
                + " p.campus_name AS campusName, p.title, p.content, p.price, p.location,"
                + " p.trade_mode AS tradeMode, p.contact, p.tags_json AS tagsJson, p.images_json AS imagesJson,"
                + " p.status, p.audit_reason AS auditReason, p.audit_time AS auditTime,"
                + " p.auditor_id AS auditorId, p.create_time AS createTime, p.update_time AS updateTime,"
                + " u.nickname AS publisherName, u.mobile AS publisherMobile"
                + " FROM campus_post p LEFT JOIN campus_miniapp_user u"
                + " ON u.id = p.user_id AND u.deleted = b'0'";
    }
}
