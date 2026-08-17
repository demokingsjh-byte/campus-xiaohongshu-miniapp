import type { CampusPost } from '@/mock/campus';
import type { CampusPostCreateParams, CampusPostPageParams } from '@/services/api/content';
import type { CampusTenant } from '@/utils/tenant';
import { defineStore } from 'pinia';
import {
  createCampusPost,
  deleteCampusPost,
  getCampusPost,
  getCampusPostPage,
  getFavoriteCampusPostPage,
  getMyCampusPostPage,
  setCampusPostCollect,
  setCampusPostLike,
} from '@/services/api/content';
import { clearCampusTenant, getCampusTenant, setCampusTenant } from '@/utils/tenant';
import { resolveCampusAvatar, resolveCampusMediaUrl } from '@/utils/avatar';

export type PublishPostInput = CampusPostCreateParams;

export const useTenantStore = defineStore('TenantStore', () => {
  const currentTenant = ref<CampusTenant | null>(getCampusTenant());

  const tenantId = computed(() => currentTenant.value?.id || null);
  const tenantName = computed(() => currentTenant.value?.name || '');

  function selectTenant(tenant: CampusTenant) {
    currentTenant.value = tenant;
    setCampusTenant(tenant);
  }

  function clearTenant() {
    currentTenant.value = null;
    clearCampusTenant();
  }

  return {
    currentTenant,
    tenantId,
    tenantName,
    selectTenant,
    clearTenant,
  };
});

export const useCampusContentStore = defineStore('CampusContentStore', () => {
  const posts = ref<CampusPost[]>([]);
  const publishedPosts = ref<CampusPost[]>([]);
  const favoritePosts = ref<CampusPost[]>([]);
  const currentPost = ref<CampusPost | null>(null);
  const loading = ref(false);
  const allPosts = computed(() => posts.value);
  let postsRequest: Promise<CampusPost[]> | null = null;
  let postsRequestKey = '';
  let latestPostsRequestId = 0;

  function normalizePostMedia(post: CampusPost): CampusPost {
    const images = (post.images || []).map(resolveCampusMediaUrl).filter(Boolean);
    return {
      ...post,
      avatar: resolveCampusAvatar(post.avatar),
      images,
      coverImage: resolveCampusMediaUrl(post.coverImage || images[0]),
    };
  }

  function replacePost(updated: CampusPost) {
    const replace = (list: CampusPost[]) => {
      const index = list.findIndex(item => item.id === updated.id);
      if (index >= 0)
        list[index] = updated;
    };
    replace(posts.value);
    replace(publishedPosts.value);
    replace(favoritePosts.value);
    if (currentPost.value?.id === updated.id)
      currentPost.value = updated;
  }

  function loadPosts(params: CampusPostPageParams = {}) {
    const requestParams = { pageNo: 1, pageSize: 100, ...params };
    const requestKey = JSON.stringify(requestParams);
    if (postsRequest && postsRequestKey === requestKey)
      return postsRequest;

    const requestId = ++latestPostsRequestId;
    loading.value = true;
    const request = (async () => {
      const page = await getCampusPostPage(requestParams);
      const nextPosts = (page.list || []).map(normalizePostMedia);
      if (requestId === latestPostsRequestId)
        posts.value = nextPosts;
      return nextPosts;
    })();
    postsRequest = request;
    postsRequestKey = requestKey;
    return request.finally(() => {
      if (postsRequest === request) {
        postsRequest = null;
        postsRequestKey = '';
        loading.value = false;
      }
    });
  }

  async function loadMyPosts() {
    const page = await getMyCampusPostPage({ pageNo: 1, pageSize: 100 });
    // “我的发布”接口返回的每一条内容都属于当前用户。即使详情接口暂时
    // 没有返回 owner，也保留这份可靠的本人标记供详情页判断。
    publishedPosts.value = (page.list || []).map(post => ({ ...normalizePostMedia(post), owner: true }));
    return publishedPosts.value;
  }

  async function loadFavorites() {
    const page = await getFavoriteCampusPostPage({ pageNo: 1, pageSize: 100 });
    favoritePosts.value = (page.list || []).map(normalizePostMedia);
    return favoritePosts.value;
  }

  async function publishPost(input: PublishPostInput) {
    // 发布接口成功返回的内容必然是当前用户创建的。
    const createdResult = await createCampusPost(input);
    const created = { ...normalizePostMedia(createdResult), owner: true };
    if (created.status === 1)
      posts.value = [created, ...posts.value.filter(item => item.id !== created.id)];
    publishedPosts.value = [created, ...publishedPosts.value.filter(item => item.id !== created.id)];
    currentPost.value = created;
    return created;
  }

  async function loadPost(id: number) {
    const post = normalizePostMedia(await getCampusPost(id));
    currentPost.value = post;
    replacePost(post);
    return post;
  }

  function getPost(id: number) {
    return currentPost.value?.id === id
      ? currentPost.value
      : [...posts.value, ...publishedPosts.value, ...favoritePosts.value].find(item => item.id === id);
  }

  async function setPostLike(id: number, active: boolean) {
    const updated = normalizePostMedia(await setCampusPostLike(id, active));
    replacePost(updated);
    return updated;
  }

  async function setPostCollect(id: number, active: boolean) {
    const updated = normalizePostMedia(await setCampusPostCollect(id, active));
    replacePost(updated);
    if (active && !favoritePosts.value.some(item => item.id === id))
      favoritePosts.value.unshift(updated);
    if (!active)
      favoritePosts.value = favoritePosts.value.filter(item => item.id !== id);
    return updated;
  }

  async function removePost(id: number) {
    await deleteCampusPost(id);
    posts.value = posts.value.filter(item => item.id !== id);
    publishedPosts.value = publishedPosts.value.filter(item => item.id !== id);
    favoritePosts.value = favoritePosts.value.filter(item => item.id !== id);
    if (currentPost.value?.id === id)
      currentPost.value = null;
  }

  function clearPersonalContent() {
    publishedPosts.value = [];
    favoritePosts.value = [];
    currentPost.value = null;
  }

  return {
    posts,
    publishedPosts,
    favoritePosts,
    currentPost,
    loading,
    allPosts,
    loadPosts,
    loadMyPosts,
    loadFavorites,
    publishPost,
    loadPost,
    getPost,
    setPostLike,
    setPostCollect,
    removePost,
    clearPersonalContent,
  };
});
