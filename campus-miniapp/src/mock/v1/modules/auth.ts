import { defineMock } from '@alova/mock';
import multiavatar from '@multiavatar/multiavatar';
import { join, random, sampleSize } from 'lodash-es';
import { ResultEnum } from '@/enums/httpEnum';
import { campusPosts } from '@/mock/campus';
import { createMock } from '@/mock/utils';
import { getRandomChsString } from '@/utils/character';

function createRandomToken(len = 36 * 6) {
  const token = join(sampleSize('0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-', len), '');
  return `eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.${token}`;
}

const MOCK_PROFILE_KEY = 'campus-mock-profile';
const MOCK_POSTS_KEY = 'campus-mock-server-posts';
const MOCK_FOLLOWING_KEY = 'campus-mock-following-user-ids';
const mockTypeByChannel: Record<string, string> = {
  二手: 'idle',
  互助: 'help',
  表白: 'confession',
  拼车: 'ride',
  探店: 'shop',
  失物: 'lost',
  社团: 'club',
  兼职: 'job',
};
function getMockProfile() {
  const cached = uni.getStorageSync(MOCK_PROFILE_KEY);
  return cached && typeof cached === 'object' ? cached : null;
}
function saveMockProfile(profile: Record<string, any>) {
  uni.setStorageSync(MOCK_PROFILE_KEY, profile);
  return profile;
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
    const tenantProfiles: Record<number, { schoolName: string, campusName: string }> = {
      201: { schoolName: '吉首大学', campusName: '吉首校区' },
      202: { schoolName: '长沙学院', campusName: '主校区' },
    };
    const tenantProfile = tenantProfiles[Number(tenantId)] || { schoolName: tenantId ? `租户 ${tenantId}` : '未选择学校', campusName: '默认校区' };
    const cachedProfile = getMockProfile();
    const token = createRandomToken();
    return createMock({
      data: {
        token,
        refreshToken: createRandomToken(64),
        expiresTime: '2026-07-31 23:59:59',
        userInfo: cachedProfile || {
          id: 10001,
          openid: 'mock-openid-10001',
          unionid: '',
          nickname: '校园体验用户',
          avatar: '',
          mobile: '',
          email: '',
          schoolName: tenantProfile.schoolName,
          campusName: scene || tenantProfile.campusName,
          grade: '2023级',
          gender: '不公开',
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
      data: getMockProfile() || {
        id: 10001,
        openid: 'mock-openid-10001',
        unionid: '',
        nickname: '校园体验用户',
        avatar: '',
        mobile: '',
        email: '',
        schoolName: '未选择学校',
        campusName: '默认校区',
        grade: '2023级',
        gender: '不公开',
        roleType: 'student',
        mobileBound: false,
        lastLoginTime: '2026-07-05 10:00:00',
      },
    });
  },
  '[GET]/api/campus/auth/public-profile': (params) => {
    const query = params?.query || params?.params || {};
    const userId = Number(query.userId);
    const currentProfile = getMockProfile() || {};
    const storedPosts = uni.getStorageSync(MOCK_POSTS_KEY);
    const posts = [...(Array.isArray(storedPosts) ? storedPosts : []), ...campusPosts]
      .map(post => ({
        ...post,
        userId: post.userId || 20000 + post.id,
        type: post.type || mockTypeByChannel[post.channel],
        anonymous: post.anonymous ?? post.author === '匿名用户',
      }));
    const targetPost = posts.find(post => Number(post.userId) === userId && !post.anonymous);
    const isSelf = userId === 10001;
    if (!isSelf && !targetPost)
      return createMock({ data: null, code: ResultEnum.FAIL, message: '用户不存在或已注销' });
    const ownPosts = posts.filter(post => Number(post.userId) === userId
      && !post.anonymous && !post.downlisted && (post.status === undefined || post.status === 1));
    const postCounts = ownPosts.reduce<Record<string, number>>((counts, post) => {
      const type = post.type || '';
      if (type)
        counts[type] = (counts[type] || 0) + 1;
      return counts;
    }, {});
    const followingIds = uni.getStorageSync(MOCK_FOLLOWING_KEY);
    const normalizedFollowingIds = Array.isArray(followingIds) ? followingIds.map(Number) : [];
    return createMock({ data: {
      userId,
      tenantId: Number(isSelf ? (ownPosts[0]?.tenantId || 201) : targetPost?.tenantId || 201),
      nickname: isSelf ? (currentProfile.nickname || '校园体验用户') : (targetPost?.author || '校园同学'),
      avatar: isSelf ? (currentProfile.avatar || '') : (targetPost?.avatar || ''),
      schoolName: isSelf ? (currentProfile.schoolName || '吉首大学') : (targetPost?.school || '吉首大学'),
      campusName: isSelf ? (currentProfile.campusName || '吉首校区') : (targetPost?.campusName || '吉首校区'),
      grade: isSelf ? (currentProfile.grade || '2023级') : '2023级',
      gender: isSelf ? (currentProfile.gender || '不公开') : (userId % 2 ? '女' : '男'),
      province: '湖南省',
      followerCount: ownPosts.reduce((total, post) => total + Number(post.likes || 0), 0),
      followingCount: isSelf ? normalizedFollowingIds.length : userId % 47,
      followed: normalizedFollowingIds.includes(userId),
      self: isSelf,
      postCounts,
    } });
  },
  '[PUT]/api/campus/auth/profile': (params) => {
    const currentProfile = getMockProfile() || {};
    const profile = saveMockProfile({
      ...currentProfile,
      id: 10001,
      openid: 'mock-openid-10001',
      nickname: params.data?.nickname ?? currentProfile.nickname ?? '校园体验用户',
      avatar: params.data?.avatar ?? currentProfile.avatar ?? '',
      mobile: currentProfile.mobile ?? '',
      schoolName: params.data?.schoolName ?? currentProfile.schoolName ?? '未选择学校',
      campusName: params.data?.campusName ?? currentProfile.campusName ?? '默认校区',
      grade: params.data?.grade ?? currentProfile.grade ?? '2023级',
      gender: params.data?.gender ?? currentProfile.gender ?? '不公开',
      roleType: params.data?.roleType ?? currentProfile.roleType ?? 'student',
      mobileBound: currentProfile.mobileBound ?? false,
      lastLoginTime: '2026-07-13 10:00:00',
    });
    return createMock({
      data: profile,
    });
  },
  '[POST]/api/campus/auth/phone': () => {
    const profile = saveMockProfile({
      ...(getMockProfile() || {}),
      id: 10001,
      openid: 'mock-openid-10001',
      mobile: '13800000000',
      mobileBound: true,
      lastLoginTime: '2026-07-13 10:00:00',
    });
    return createMock({
      data: profile,
    });
  },
  '[DELETE]/api/campus/auth/account': () => {
    uni.removeStorageSync(MOCK_PROFILE_KEY);
    return createMock({ data: true });
  },
});
