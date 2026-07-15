import { request } from '@/utils/http';

const LOGIN = '/login';
const LOGIN_OUT = '/logout';
const REFRESH_TOKEN = '/refresh/token';
const CAMPUS_WECHAT_LOGIN = '/campus/auth/wechat-login';
const CAMPUS_CURRENT_USER = '/campus/auth/me';
const CAMPUS_PROFILE = '/campus/auth/profile';
const CAMPUS_PHONE = '/campus/auth/phone';
const CAMPUS_ACCOUNT = '/campus/auth/account';

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
export function refreshToken() {
  return request.Post<LoginModel>(REFRESH_TOKEN, {});
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
