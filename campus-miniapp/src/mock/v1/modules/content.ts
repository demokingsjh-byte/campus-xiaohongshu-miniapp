import type { CampusPost } from '@/mock/campus';
import { defineMock } from '@alova/mock';
import { ResultEnum } from '@/enums/httpEnum';
import { campusPosts } from '@/mock/campus';
import { createMock } from '@/mock/utils';

const POSTS_KEY = 'campus-mock-server-posts';
const LIKES_KEY = 'campus-mock-server-likes';
const FAVORITES_KEY = 'campus-mock-server-favorites';
const PROFILE_KEY = 'campus-mock-profile';

const channelMap: Record<string, string> = {
  idle: '二手',
  help: '互助',
  ride: '拼车',
  shop: '探店',
  lost: '失物',
  club: '社团',
};
const coverMap: Record<string, Pick<CampusPost, 'coverColor' | 'coverEmoji' | 'coverLabel'>> = {
  idle: { coverColor: '#E8F1FF', coverEmoji: '📦', coverLabel: '同校闲置' },
  help: { coverColor: '#EEF2FF', coverEmoji: '🙌', coverLabel: '同校互助' },
  ride: { coverColor: '#DCEEF3', coverEmoji: '🚕', coverLabel: '拼车招募中' },
  shop: { coverColor: '#FCE7DE', coverEmoji: '🥤', coverLabel: '真实探店' },
  lost: { coverColor: '#FFF0D9', coverEmoji: '🔎', coverLabel: '失物信息' },
  club: { coverColor: '#EEF2FF', coverEmoji: '🎉', coverLabel: '活动报名中' },
};

function getStoredPosts(): CampusPost[] {
  const value = uni.getStorageSync(POSTS_KEY);
  return Array.isArray(value) ? value : [];
}

function setStoredPosts(posts: CampusPost[]) {
  uni.setStorageSync(POSTS_KEY, posts);
}

function getIds(key: string): number[] {
  const value = uni.getStorageSync(key);
  return Array.isArray(value) ? value.map(Number) : [];
}

function setIds(key: string, ids: number[]) {
  uni.setStorageSync(key, ids);
}

function queryOf(params: any) {
  return params?.query || params?.params || {};
}

function allPosts() {
  const likes = getIds(LIKES_KEY);
  const favorites = getIds(FAVORITES_KEY);
  return [...getStoredPosts(), ...campusPosts].map(post => ({
    ...post,
    liked: likes.includes(post.id),
    collected: favorites.includes(post.id),
    owner: getStoredPosts().some(item => item.id === post.id),
    collects: post.collects || (favorites.includes(post.id) ? 1 : 0),
  }));
}

function page(list: CampusPost[]) {
  return createMock({ data: { list, total: list.length } });
}

function findPost(id: number) {
  return allPosts().find(item => item.id === id);
}

function setInteraction(id: number, active: boolean, key: string, countKey: 'likes' | 'collects') {
  const ids = getIds(key);
  const wasActive = ids.includes(id);
  const nextIds = active ? [...new Set([...ids, id])] : ids.filter(item => item !== id);
  setIds(key, nextIds);
  const stored = getStoredPosts();
  const index = stored.findIndex(item => item.id === id);
  if (index >= 0 && wasActive !== active) {
    const storedPost = stored[index]!;
    const current = storedPost[countKey] || 0;
    stored[index] = { ...storedPost, [countKey]: Math.max(0, current + (active ? 1 : -1)) };
    setStoredPosts(stored);
  }
  const post = findPost(id);
  if (!post)
    return createMock({ data: null, code: ResultEnum.FAIL, message: '内容不存在或已下架' });
  return createMock({ data: { ...post, [countKey === 'likes' ? 'liked' : 'collected']: active } });
}

export const contentMocks = defineMock({
  '[POST]/api/campus/post/create': (params) => {
    const data = params.data || {};
    const profile = uni.getStorageSync(PROFILE_KEY) || {};
    const tenantId = Number(profile.schoolName === '长沙学院' ? 202 : 201);
    const author = data.anonymous ? '同校同学' : (profile.nickname || '校园体验用户');
    const images = Array.isArray(data.images) ? data.images : [];
    const cover = coverMap[data.type] || coverMap.help!;
    const post: CampusPost = {
      id: Date.now(),
      tenantId,
      userId: 10001,
      type: data.type,
      channel: channelMap[data.type] || '互助',
      title: String(data.title || '').trim(),
      content: String(data.content || '').trim(),
      author,
      avatar: data.anonymous ? '' : (profile.avatar || ''),
      avatarText: author.slice(0, 1),
      school: profile.schoolName || (tenantId === 202 ? '长沙学院' : '吉首大学'),
      campusName: profile.campusName || '主校区',
      time: '刚刚',
      price: data.price || undefined,
      originalPrice: data.originalPrice || undefined,
      location: data.location || '',
      tradeMode: data.tradeMode || '',
      visibleRange: data.visibleRange || '仅本校可见',
      tags: Array.isArray(data.tags) && data.tags.length ? data.tags : ['校园新鲜事'],
      images,
      coverImage: images[0],
      likes: 0,
      collects: 0,
      comments: 0,
      views: 0,
      status: 1,
      liked: false,
      collected: false,
      owner: true,
      createTime: new Date().toISOString(),
      height: images.length > 1 ? 'tall' : (data.price ? 'medium' : 'short'),
      ...cover,
    };
    setStoredPosts([post, ...getStoredPosts()]);
    return createMock({ data: post });
  },
  '[GET]/api/campus/post/page': (params) => {
    const query = queryOf(params);
    const tenantId = Number(query.tenantId || 201);
    const channel = String(query.channel || '推荐');
    const keyword = String(query.keyword || '').trim().toLowerCase();
    const result = allPosts().filter((post) => {
      if (post.tenantId !== tenantId)
        return false;
      if (channel && channel !== '推荐' && post.channel !== channel)
        return false;
      return !keyword || [post.title, post.content, post.author, post.school, ...post.tags].join(' ').toLowerCase().includes(keyword);
    });
    return page(result);
  },
  '[GET]/api/campus/post/my-page': () => page(allPosts().filter(post => post.owner)),
  '[GET]/api/campus/post/favorite-page': () => page(allPosts().filter(post => post.collected)),
  '[GET]/api/campus/post/get': (params) => {
    const post = findPost(Number(queryOf(params).id));
    return post
      ? createMock({ data: post })
      : createMock({ data: null, code: ResultEnum.FAIL, message: '内容不存在或已下架' });
  },
  '[PUT]/api/campus/post/like': params => setInteraction(Number(queryOf(params).id), Boolean(params.data?.active), LIKES_KEY, 'likes'),
  '[PUT]/api/campus/post/collect': params => setInteraction(Number(queryOf(params).id), Boolean(params.data?.active), FAVORITES_KEY, 'collects'),
  '[DELETE]/api/campus/post/delete': (params) => {
    const id = Number(queryOf(params).id);
    const stored = getStoredPosts();
    if (!stored.some(item => item.id === id))
      return createMock({ data: false, code: ResultEnum.FAIL, message: '内容不存在或无权删除' });
    setStoredPosts(stored.filter(item => item.id !== id));
    return createMock({ data: true });
  },
  '[POST]/api/campus/post/report': () => createMock({ data: true }),
});
