import type { UserInfoModel } from '@/services/model/userModel';
import { defineStore } from 'pinia';
import {
  bindCampusPhone,
  deleteCampusAccount,
  getCurrentCampusUser,
  login as loginApi,
  updateCampusProfile,
  wechatLogin,
} from '@/services/api/auth';
import { uploadCampusAvatar } from '@/services/api/file';
import { startCampusAnalytics, stopCampusAnalytics } from '@/utils/analytics';
import { getToken, isLogin, removeToken, setAuthSession } from '@/utils/auth';
import { isUseMock } from '@/utils/env';
import { clearCampusLocalData, hasCurrentPrivacyConsent, revokePrivacyConsent } from '@/utils/privacy';
import { getCampusTenantId } from '@/utils/tenant';

function uniLoginCode() {
  return new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) {
          resolve(res.code);
          return;
        }
        reject(new Error('微信登录未返回 code'));
      },
      fail: error => reject(new Error(error.errMsg || '无法获取微信登录凭证')),
    });
  });
}

function isTemporaryLocalAvatar(path: string) {
  return /^(?:wxfile|file|blob):|^https?:\/\/tmp\//i.test(path);
}

export const useUserStore = defineStore('UserStore', () => {
  const token = ref<string | null>(null);
  const userInfo = ref<UserInfoModel | null>(null);
  let initialization: Promise<void> | null = null;

  async function initUserInfo() {
    if (initialization)
      return initialization;
    initialization = (async () => {
      if (isLogin() && !hasCurrentPrivacyConsent()) {
        removeToken();
        token.value = null;
        userInfo.value = null;
      } else if (isLogin()) {
        token.value = getToken();
        try {
          await getUserInfo();
          token.value = getToken();
        } catch {
          removeToken();
          token.value = null;
          userInfo.value = null;
        }
      }
      // 没有本地登录凭证时保持游客状态。只有用户明确阅读并同意规则、点击登录后，
      // 才调用微信登录并在后端创建校园用户。
    })();
    try {
      await initialization;
    } finally {
      initialization = null;
    }
  }

  const loggedIn = computed(() => !!token.value);
  const profileCompleted = computed(() => {
    const user = userInfo.value;
    return Boolean(
      user?.nickname
      && user.nickname !== '校园体验用户'
      && user.schoolName
      && user.campusName
      && user.grade,
    );
  });

  const { send: sendLogin } = useRequest(loginApi, { immediate: false });
  async function login(params: LoginParams) {
    const res = await sendLogin(params);
    token.value = res.token;
    setAuthSession(res);
    await getUserInfo();
  }

  const { send: sendWechatLogin } = useRequest(wechatLogin, { immediate: false });
  async function silentLogin(options: Partial<WechatLoginParams> = {}) {
    let code = options.code;
    if (!code) {
      try {
        code = await uniLoginCode();
      } catch (error) {
        if (!isUseMock()) {
          throw error instanceof Error ? error : new Error('无法获取微信登录凭证');
        }
        code = 'mock-wechat-login-code';
      }
    }

    const res = await sendWechatLogin({
      ...options,
      code,
      tenantId: options.tenantId ?? getCampusTenantId() ?? undefined,
    });
    token.value = res.token;
    setAuthSession(res);
    try {
      await getUserInfo();
    } catch (error) {
      removeToken();
      token.value = null;
      userInfo.value = null;
      throw error;
    }
    startCampusAnalytics(options.scene);
    return true;
  }

  const { send: _getUserInfo } = useRequest(getCurrentCampusUser, { immediate: false });
  async function getUserInfo() {
    userInfo.value = (await _getUserInfo()) as UserInfoModel;
  }

  const { send: sendUpdateProfile } = useRequest(updateCampusProfile, { immediate: false });
  async function updateProfile(params: CampusProfileUpdateParams) {
    userInfo.value = (await sendUpdateProfile(params)) as UserInfoModel;
    return userInfo.value;
  }

  /**
   * 将微信 chooseAvatar 返回的临时文件上传为永久文件，并绑定到当前校园用户。
   * 绑定后重新从服务端读取资料，避免页面只保留本地临时路径。
   */
  async function bindWechatAvatar(temporaryFilePath: string) {
    if (!loggedIn.value || !userInfo.value)
      throw new Error('请先登录后再绑定头像');

    const uploadedAvatar = await uploadCampusAvatar(temporaryFilePath);
    if (!uploadedAvatar)
      throw new Error('头像上传未返回永久地址');
    if (!isUseMock() && isTemporaryLocalAvatar(uploadedAvatar))
      throw new Error('头像上传仍返回临时地址，无法持久化绑定');

    await sendUpdateProfile({ avatar: uploadedAvatar });
    const persistedUser = (await _getUserInfo()) as UserInfoModel;
    if (!persistedUser?.avatar)
      throw new Error('头像未能绑定到当前用户');

    userInfo.value = persistedUser;
    return persistedUser.avatar;
  }

  const { send: sendBindPhone } = useRequest(bindCampusPhone, { immediate: false });
  async function bindPhone(phoneCode: string) {
    userInfo.value = (await sendBindPhone({ phoneCode })) as UserInfoModel;
    return userInfo.value;
  }

  async function deleteAccount() {
    await deleteCampusAccount();
    await logout({ clearConsent: true });
  }

  async function logout(options: { clearConsent?: boolean } = {}) {
    stopCampusAnalytics();
    removeToken();
    uni.removeStorageSync('yd-demo-login');
    clearCampusLocalData({ keepConsent: !options.clearConsent });
    if (options.clearConsent)
      revokePrivacyConsent();
    userInfo.value = null;
    token.value = null;
  }

  return {
    userInfo,
    loggedIn,
    profileCompleted,
    login,
    logout,
    getUserInfo,
    initUserInfo,
    silentLogin,
    updateProfile,
    bindWechatAvatar,
    bindPhone,
    deleteAccount,
  };
});
