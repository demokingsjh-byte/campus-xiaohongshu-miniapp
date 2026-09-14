import { request } from '@/utils/http';

const LOGIN = '/login';
const LOGIN_OUT = '/logout';
const REFRESH_TOKEN = '/campus/auth/refresh-token';
const CAMPUS_WECHAT_LOGIN = '/campus/auth/wechat-login';
const CAMPUS_CURRENT_USER = '/campus/auth/me';
const CAMPUS_PROFILE = '/campus/auth/profile';
const CAMPUS_PHONE = '/campus/auth/phone';
const CAMPUS_ACCOUNT = '/campus/auth/account';
const CAMPUS_PUBLIC_PROFILE = '/campus/auth/public-profile';

export interface CampusPublicUserProfile {
  userId: number
  tenantId: number
  nickname: string
  avatar?: string
  schoolName?: string
  campusName?: string
  grade?: string
  gender?: string
  province?: string
  followerCount: number
  followingCount: number
  followed: boolean
  self: boolean
  postCounts: Record<string, number>
}

/**
 * 登录
 * @param params
 */
export function login(params: LoginParams) {
  return request.Post<LoginModel>(LOGIN, params, {
    meta: {
      ignoreAuth: true,
    },
  });
}

/**
 * 登出
 */
export function logout() {
  return request.Post(LOGIN_OUT, {});
}

/**
 * 刷新token
 */
export function refreshToken(refreshToken: string) {
  return request.Post<WechatLoginModel>(REFRESH_TOKEN, {}, {
    params: { refreshToken },
    meta: {
      ignoreAuth: true,
      silentError: true,
    },
  });
}

/**
 * 微信小程序静默登录
 */
export function wechatLogin(params: WechatLoginParams) {
  return request.Post<WechatLoginModel>(CAMPUS_WECHAT_LOGIN, params, {
    meta: {
      ignoreAuth: true,
    },
  });
}

/**
 * 获取当前校园小程序用户
 */
export function getCurrentCampusUser() {
  return request.Get<CampusUserInfoModel>(CAMPUS_CURRENT_USER, {
    meta: {
      silentError: true,
    },
  });
}

/** 获取不包含手机号、OpenID 等隐私字段的公开主页资料。 */
export function getCampusPublicUserProfile(userId: number) {
  return request.Get<CampusPublicUserProfile>(CAMPUS_PUBLIC_PROFILE, {
    params: { userId },
    cacheFor: 0,
    meta: { ignoreAuth: true, silentError: true },
  });
}

/**
 * 补全校园资料
 */
export function updateCampusProfile(params: CampusProfileUpdateParams) {
  return request.Put<CampusUserInfoModel>(CAMPUS_PROFILE, params);
}

/**
 * 绑定微信授权手机号
 */
export function bindCampusPhone(params: CampusPhoneBindParams) {
  return request.Post<CampusUserInfoModel>(CAMPUS_PHONE, params);
}

/**
 * 注销当前校园账号并删除或匿名化关联个人信息
 */
export function deleteCampusAccount() {
  return request.Delete<boolean>(CAMPUS_ACCOUNT);
}
