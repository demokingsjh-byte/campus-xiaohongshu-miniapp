package cn.iocoder.yudao.module.campus.service.job;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditPageReqVO;
import cn.iocoder.yudao.module.campus.controller.admin.job.vo.CampusJobAuditReviewReqVO;

import java.util.Map;

public interface CampusJobAuditService {

    PageResult<Map<String, Object>> getPage(CampusJobAuditPageReqVO reqVO);

    Map<String, Object> get(Long id);

    Map<String, Object> getSummary(Long tenantId);

    void review(CampusJobAuditReviewReqVO reqVO, Long auditorId);
}
