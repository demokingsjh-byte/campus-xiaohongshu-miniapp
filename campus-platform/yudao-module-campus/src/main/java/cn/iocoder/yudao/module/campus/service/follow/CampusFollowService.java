package cn.iocoder.yudao.module.campus.service.follow;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.follow.vo.CampusFollowUserRespVO;

public interface CampusFollowService {

    boolean setFollow(Long userId, Long targetUserId, boolean active);

    boolean isFollowing(Long userId, Long targetUserId);

    long getFollowingCount(Long userId);

    PageResult<CampusFollowUserRespVO> getFollowingPage(Long userId, Integer pageNo, Integer pageSize);
}
