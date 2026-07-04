import { ResultEnum } from '@/enums/httpEnum';
import { createMock } from '@/mock/utils';
import { getRandomChsString } from '@/utils/character';
import { defineMock } from '@alova/mock';
import multiavatar from '@multiavatar/multiavatar';
import { join, random, sampleSize } from 'lodash-es';

function createRandomToken(len = 36 * 6) {
  const token = join(sampleSize('0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-', len), '');
  return `eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.${token}`;
}

export const authMocks = defineMock({
  // 登录
  '[POST]/api/login': (params) => {
    const { email, password } = params.data;
    if (email === 'uni-app@test.com' && (password === 'Vue3_Ts_Vite' || password === '123456')) {
      const token = createRandomToken();
      return createMock({ data: { token } });
    }
    return createMock({ data: [], code: ResultEnum.FAIL, message: '邮箱或密码错误' });
  },
  // 获取用户信息
  '[GET]/api/users': () => {
    const generateNicknames = getRandomChsString(random(2, 6));
    const svgCode = multiavatar(generateNicknames);
    const base64SVG = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(
      svgCode,
    )}`;

    return createMock({
      data: {
        id: 1,
        nickname: generateNicknames,
        avatar: base64SVG,
        email: 'uni-app@test.com',
      },
    });
  },
  '[POST]/api/campus/auth/wechat-login': (params) => {
    const { code, tenantId, scene, inviterUserId } = params.data || {};
    if (!code) {
      return createMock({ data: null, code: ResultEnum.FAIL, message: '微信 code 不能为空' });
    }
    const token = createRandomToken();
    return createMock({
      data: {
        token,
        refreshToken: createRandomToken(64),
        expiresTime: '2026-07-31 23:59:59',
        userInfo: {
          id: 10001,
          openid: 'mock-openid-10001',
          unionid: '',
          nickname: '校园体验用户',
          avatar: '',
          mobile: '',
          email: '',
          schoolName: tenantId ? `租户 ${tenantId}` : '未选择学校',
          campusName: scene || '默认校区',
          roleType: 'student',
          mobileBound: false,
          lastLoginTime: '2026-07-05 10:00:00',
          inviterUserId,
        },
      },
    });
  },
  '[GET]/api/campus/auth/me': () => {
    return createMock({
      data: {
        id: 10001,
        openid: 'mock-openid-10001',
        unionid: '',
        nickname: '校园体验用户',
        avatar: '',
        mobile: '',
        email: '',
        schoolName: '未选择学校',
        campusName: '默认校区',
        roleType: 'student',
        mobileBound: false,
        lastLoginTime: '2026-07-05 10:00:00',
      },
    });
  },
  '[PUT]/api/campus/auth/profile': (params) => {
    return createMock({
      data: {
        id: 10001,
        openid: 'mock-openid-10001',
        nickname: params.data?.nickname || '校园体验用户',
        avatar: params.data?.avatar || '',
        mobile: '',
        schoolName: params.data?.schoolName || '未选择学校',
        campusName: params.data?.campusName || '默认校区',
        roleType: params.data?.roleType || 'student',
        mobileBound: false,
        lastLoginTime: '2026-07-05 10:00:00',
      },
    });
  },
  '[POST]/api/campus/auth/phone': () => {
    return createMock({
      data: {
        id: 10001,
        openid: 'mock-openid-10001',
        nickname: '校园体验用户',
        avatar: '',
        mobile: '13800000000',
        schoolName: '未选择学校',
        campusName: '默认校区',
        roleType: 'student',
        mobileBound: true,
        lastLoginTime: '2026-07-05 10:00:00',
      },
    });
  },
});
