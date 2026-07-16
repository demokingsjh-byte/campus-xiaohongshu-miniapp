import { REFRESH_TOKEN_KEY, TOKEN_EXPIRES_TIME_KEY, TOKEN_KEY } from '@/enums/cacheEnum';
import { getCache, removeCache, setCache } from '@/utils/cache';
import { getBaseUrl } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';

const authenticationScheme = 'Bearer';
const AUTH_SESSION_CACHE_TIME = 60 * 60 * 24 * 31;
const TOKEN_REFRESH_BUFFER = 60 * 1000;
const CAMPUS_REFRESH_TOKEN = '/campus/auth/refresh-token';

interface AuthSession {
  token: string
  refreshToken?: string
  expiresTime?: string | number
}

let refreshPromise: Promise<string> | null = null;

export function getToken() {
  return getCache<string>(TOKEN_KEY) || null;
}

export function getAuthorization() {
  const token = getToken();
  return token ? `${authenticationScheme} ${token}` : null;
}

export function setToken(token: string) {
  return setCache(TOKEN_KEY, token, AUTH_SESSION_CACHE_TIME);
}

export function getRefreshToken() {
  return getCache<string>(REFRESH_TOKEN_KEY) || null;
}

export function getTokenExpiresTime() {
  return getCache<number>(TOKEN_EXPIRES_TIME_KEY) || null;
}

export function setAuthSession(session: AuthSession) {
  setToken(session.token);
  if (session.refreshToken)
    setCache(REFRESH_TOKEN_KEY, session.refreshToken, AUTH_SESSION_CACHE_TIME);

  const expiresTimeValue = typeof session.expiresTime === 'string'
    ? session.expiresTime.replace(' ', 'T')
    : session.expiresTime;
  const expiresTime = expiresTimeValue ? new Date(expiresTimeValue).getTime() : Number.NaN;
  if (Number.isFinite(expiresTime))
    setCache(TOKEN_EXPIRES_TIME_KEY, expiresTime, AUTH_SESSION_CACHE_TIME);
}

export function removeToken() {
  removeCache(TOKEN_KEY);
  removeCache(REFRESH_TOKEN_KEY);
  removeCache(TOKEN_EXPIRES_TIME_KEY);
}

function requestTokenRefresh(refreshToken: string) {
  return new Promise<AuthSession>((resolve, reject) => {
    const tenantId = getCampusTenantId();
    uni.request({
      url: `${getBaseUrl()}${CAMPUS_REFRESH_TOKEN}?refreshToken=${encodeURIComponent(refreshToken)}`,
      method: 'POST',
      header: tenantId
        ? { 'X-Tenant-Id': String(tenantId), 'tenant-id': String(tenantId) }
        : {},
      success: (response) => {
        const body = response.data as API<AuthSession>;
        if (response.statusCode !== 200) {
          reject(new Error(`刷新登录状态失败[HTTP ${response.statusCode}]`));
          return;
        }
        if ((body.code !== 0 && body.code !== 10000) || !body.data?.token) {
          removeToken();
          reject(new Error(body.msg || body.message || '登录状态已失效，请重新登录'));
          return;
        }
        resolve(body.data);
      },
      fail: error => reject(new Error(error.errMsg || '刷新登录状态失败')),
    });
  });
}

export async function ensureValidToken(forceRefresh = false) {
  if (refreshPromise)
    return refreshPromise;

  const accessToken = getToken();
  const expiresTime = getTokenExpiresTime();
  const doesNotNeedRefresh = accessToken
    && !forceRefresh
    && (!expiresTime || expiresTime > Date.now() + TOKEN_REFRESH_BUFFER);
  if (doesNotNeedRefresh)
    return accessToken;

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    if (forceRefresh || !accessToken)
      removeToken();
    throw new Error('登录状态已失效，请重新登录');
  }

  refreshPromise = requestTokenRefresh(refreshToken)
    .then((session) => {
      setAuthSession(session);
      return session.token;
    })
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

export function isLogin() {
  return !!(getToken() || getRefreshToken());
}
