package cn.iocoder.yudao.module.campus.service.post;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentCreateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostCommentRespVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostReportReqVO;
import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusPostRespVO;

public interface CampusPostService {

    CampusPostRespVO createPost(Long userId, CampusPostCreateReqVO reqVO);

    CampusPostRespVO getPost(Long id, Long loginUserId);

    PageResult<CampusPostRespVO> getPostPage(Long loginUserId, Long tenantId, String channel,
                                             String keyword, Integer pageNo, Integer pageSize);

    PageResult<CampusPostRespVO> getMyPostPage(Long userId, Integer pageNo, Integer pageSize);

    PageResult<CampusPostRespVO> getFavoritePostPage(Long userId, Integer pageNo, Integer pageSize);

    PageResult<CampusPostCommentRespVO> getCommentPage(Long postId, Long loginUserId,
                                                       Integer pageNo, Integer pageSize);

    CampusPostCommentRespVO createComment(Long postId, Long userId, CampusPostCommentCreateReqVO reqVO);

    CampusPostRespVO setInteraction(Long postId, Long userId, String type, boolean active);

    void deletePost(Long postId, Long userId);

    void reportPost(Long postId, Long userId, CampusPostReportReqVO reqVO);
}
