package cn.iocoder.yudao.module.campus.service.auth;

import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusAuthLoginRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusPhoneBindReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusUserRespVO;
import cn.iocoder.yudao.module.campus.controller.app.auth.vo.CampusWechatLoginReqVO;

import javax.validation.Valid;

public interface CampusAppAuthService {

    CampusAuthLoginRespVO wechatLogin(@Valid CampusWechatLoginReqVO reqVO);

    CampusUserRespVO getLoginUser(Long userId);

    CampusUserRespVO updateProfile(Long userId, @Valid CampusUserProfileUpdateReqVO reqVO);

    CampusUserRespVO bindPhone(Long userId, @Valid CampusPhoneBindReqVO reqVO);

    void deleteAccount(Long userId);

}
