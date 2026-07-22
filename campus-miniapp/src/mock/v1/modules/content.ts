import type { CampusPost } from '@/mock/campus';
import { defineMock } from '@alova/mock';
import { ResultEnum } from '@/enums/httpEnum';
import { campusPosts } from '@/mock/campus';
import { createMock } from '@/mock/utils';

const POSTS_KEY = 'campus-mock-server-posts';
const LIKES_KEY = 'campus-mock-server-likes';
const FAVORITES_KEY = 'campus-mock-server-favorites';
const COMMENTS_KEY = 'campus-mock-server-comments';
const COMMENT_LIKES_KEY = 'campus-mock-server-comment-likes';
const COMMENT_REPORTS_KEY = 'campus-mock-server-comment-reports';
const PROFILE_KEY = 'campus-mock-profile';

interface MockComment {
  id: number
  postId: number
  userId: number
  parentId?: number
  author: string
  avatar: string
  avatarText: string
  content: string
  time: string
  owner: boolean
  likeCount: number
  replyCount: number
  liked: boolean
  createTime: string
}

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

function getKeys(key: string): string[] {
  const value = uni.getStorageSync(key);
  return Array.isArray(value) ? value.map(String) : [];
}

function setKeys(key: string, keys: string[]) {
  uni.setStorageSync(key, keys);
}

function getStoredComments(): Record<string, MockComment[]> {
  const value = uni.getStorageSync(COMMENTS_KEY);
  return value && typeof value === 'object' ? value : {};
}

function setStoredComments(comments: Record<string, MockComment[]>) {
  uni.setStorageSync(COMMENTS_KEY, comments);
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

function getMockComment(postId: number, commentId: number) {
  return (getStoredComments()[String(postId)] || []).find(item => item.id === commentId);
}

function decorateComments(postId: number, userId = 10001) {
  const likedKeys = getKeys(COMMENT_LIKES_KEY);
  const list = getStoredComments()[String(postId)] || [];
  return list.map((item) => {
    const likeCount = likedKeys.filter(key => key.startsWith(`${item.id}:`)).length;
    return {
      ...item,
      likeCount,
      replyCount: list.filter(reply => reply.parentId === item.id).length,
      liked: likedKeys.includes(`${item.id}:${userId}`),
    };
  });
}

function createMockComment(params: any) {
  const postId = Number(queryOf(params).postId);
  const parentId = params.data?.parentId == null ? undefined : Number(params.data.parentId);
  const content = String(params.data?.content || '').trim();
  if (!Number.isFinite(postId) || !findPost(postId))
    return createMock({ data: null, code: ResultEnum.FAIL, message: '帖子不存在' });
  if (parentId !== undefined && (!Number.isFinite(parentId) || !getMockComment(postId, parentId)))
    return createMock({ data: null, code: ResultEnum.FAIL, message: '回复的评论不存在' });
  if (!content)
    return createMock({ data: null, code: ResultEnum.FAIL, message: '评论内容不能为空' });
  const profile = uni.getStorageSync(PROFILE_KEY) || {};
  const author = profile.nickname || '校园体验用户';
  const created: MockComment = {
    id: Date.now(),
    postId,
    userId: 10001,
    parentId,
    author,
    avatar: profile.avatar || '',
    avatarText: author.slice(0, 1),
    content,
    time: '刚刚',
    owner: true,
    likeCount: 0,
    replyCount: 0,
    liked: false,
    createTime: new Date().toISOString(),
  };
  const comments = getStoredComments();
  comments[String(postId)] = [created, ...(comments[String(postId)] || [])];
  setStoredComments(comments);
  const storedPosts = getStoredPosts();
  const index = storedPosts.findIndex(item => item.id === postId);
  if (index >= 0) {
    storedPosts[index] = { ...storedPosts[index]!, comments: (storedPosts[index]!.comments || 0) + 1 };
    setStoredPosts(storedPosts);
  }
  return createMock({ data: created });
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
  '[GET]/api/campus/post/comment-page': (params) => {
    const query = queryOf(params);
    const postId = Number(query.postId);
    if (!Number.isFinite(postId))
      return createMock({ data: null, code: ResultEnum.FAIL, message: '帖子不存在' });
    const pageNo = Math.max(Number(query.pageNo || 1), 1);
    const pageSize = Math.max(Number(query.pageSize || 20), 1);
    if (!findPost(postId)) {
      return createMock({ data: null, code: ResultEnum.FAIL, message: '帖子不存在' });
    }
    const comments = decorateComments(postId);
    if (query.sort === 'likes') {
      comments.sort((a, b) => b.likeCount - a.likeCount || Date.parse(b.createTime) - Date.parse(a.createTime));
    } else if (query.sort === 'seller') {
      const ownerId = findPost(postId)?.userId;
      comments.sort((a, b) => Number(a.userId !== ownerId) - Number(b.userId !== ownerId) || Date.parse(b.createTime) - Date.parse(a.createTime));
    }
    const offset = (pageNo - 1) * pageSize;
    return createMock({ data: { list: comments.slice(offset, offset + pageSize), total: comments.length } });
  },
  '[POST]/api/campus/post/comment': createMockComment,
  '[POST]/api/campus/post/comment/reply': createMockComment,
  '[PUT]/api/campus/post/comment/like': (params) => {
    const commentId = Number(queryOf(params).id);
    const active = Boolean(params.data?.active);
    const postId = Object.keys(getStoredComments()).map(Number).find(id => Boolean(getMockComment(id, commentId)));
    if (!postId)
      return createMock({ data: null, code: ResultEnum.FAIL, message: '评论不存在' });
    const key = `${commentId}:10001`;
    const keys = getKeys(COMMENT_LIKES_KEY);
    const next = active ? [...new Set([...keys, key])] : keys.filter(item => item !== key);
    setKeys(COMMENT_LIKES_KEY, next);
    return createMock({ data: decorateComments(postId).find(item => item.id === commentId) });
  },
  '[DELETE]/api/campus/post/comment/delete': (params) => {
    const commentId = Number(queryOf(params).id);
    const comments = getStoredComments();
    const postId = Object.keys(comments).map(Number).find(id => Boolean(getMockComment(id, commentId)));
    if (!postId)
      return createMock({ data: false, code: ResultEnum.FAIL, message: '评论不存在' });
    const current = comments[String(postId)] || [];
    const removedIds = new Set([commentId, ...current.filter(item => item.parentId === commentId).map(item => item.id)]);
    comments[String(postId)] = current.filter(item => !removedIds.has(item.id));
    setStoredComments(comments);
    const storedPosts = getStoredPosts();
    const index = storedPosts.findIndex(item => item.id === postId);
    if (index >= 0) {
      storedPosts[index] = { ...storedPosts[index]!, comments: comments[String(postId)]!.length };
      setStoredPosts(storedPosts);
    }
    return createMock({ data: true });
  },
  '[POST]/api/campus/post/comment/report': (params) => {
    const commentId = Number(queryOf(params).id);
    const comments = getStoredComments();
    const postId = Object.keys(comments).map(Number).find(id => Boolean(getMockComment(id, commentId)));
    if (!postId)
      return createMock({ data: false, code: ResultEnum.FAIL, message: '评论不存在' });
    const reports = uni.getStorageSync(COMMENT_REPORTS_KEY) || {};
    reports[`${commentId}:10001`] = { postId, commentId, reason: params.data?.reason, detail: params.data?.detail || '', status: 0 };
    uni.setStorageSync(COMMENT_REPORTS_KEY, reports);
    return createMock({ data: true });
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
