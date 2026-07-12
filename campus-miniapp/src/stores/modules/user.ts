import type { UserInfoModel } from '@/services/model/userModel';
import { TOKEN_KEY } from '@/enums/cacheEnum';
import {
  bindCampusPhone,
  getCurrentCampusUser,
  login as loginApi,
  updateCampusProfile,
  wechatLogin,
} from '@/services/api/auth';
import { getToken, isLogin, removeToken, setToken } from '@/utils/auth';
import { removeCache } from '@/utils/cache';
import { isUseMock } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';
import { defineStore } from 'pinia';

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
      fail: reject,
    });
  });
}

export const useUserStore = defineStore('UserStore', () => {
  const token = ref<string | null>(null);
  const userInfo = ref<UserInfoModel | null>(null);

  async function initUserInfo() {
    if (isLogin()) {
      token.value = getToken();
      try {
        await getUserInfo();
        return;
      } catch {
        removeToken();
        token.value = null;
        userInfo.value = null;
      }
    }
    await silentLogin();
  }

  const loggedIn = computed(() => !!token.value);

  const { send: sendLogin } = useRequest(loginApi, { immediate: false });
  async function login(params: LoginParams) {
    const res = await sendLogin(params);
    token.value = res.token;
    setToken(res.token);
    await getUserInfo();
  }

  const { send: sendWechatLogin } = useRequest(wechatLogin, { immediate: false });
  async function silentLogin(options: Partial<WechatLoginParams> = {}) {
    let code = options.code;
    if (!code) {
      try {
        code = await uniLoginCode();
      } catch {
        if (!isUseMock()) {
          return false;
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
    setToken(res.token);
    userInfo.value = (res.userInfo || null) as UserInfoModel | null;
    if (!userInfo.value) {
      await getUserInfo();
    }
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

  const { send: sendBindPhone } = useRequest(bindCampusPhone, { immediate: false });
  async function bindPhone(phoneCode: string) {
    userInfo.value = (await sendBindPhone({ phoneCode })) as UserInfoModel;
    return userInfo.value;
  }

  async function logout() {
    removeCache(TOKEN_KEY);
    uni.removeStorageSync('yd-demo-login');
    userInfo.value = null;
    token.value = null;
  }

  return {
    userInfo,
    loggedIn,
    login,
    logout,
    getUserInfo,
    initUserInfo,
    silentLogin,
    updateProfile,
    bindPhone,
  };
});
