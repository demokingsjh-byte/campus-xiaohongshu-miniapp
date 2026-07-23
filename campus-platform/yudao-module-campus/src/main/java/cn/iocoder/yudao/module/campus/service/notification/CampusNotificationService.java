package cn.iocoder.yudao.module.campus.service.notification;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.notification.vo.CampusNotificationRespVO;

public interface CampusNotificationService {

    PageResult<CampusNotificationRespVO> getPage(Long userId, String type, Integer pageNo, Integer pageSize);

    Long getUnreadCount(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);

    void createInteraction(Long recipientUserId, Long tenantId, Long actorUserId, String actorNickname,
                           String eventType, String title, String content, String targetType, Long targetId);
}
