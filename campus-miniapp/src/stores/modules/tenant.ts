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

  async function loadPosts(params: CampusPostPageParams = {}) {
    loading.value = true;
    try {
      const page = await getCampusPostPage({ pageNo: 1, pageSize: 100, ...params });
      posts.value = page.list || [];
      return posts.value;
    } finally {
      loading.value = false;
    }
  }

  async function loadMyPosts() {
    const page = await getMyCampusPostPage({ pageNo: 1, pageSize: 100 });
    publishedPosts.value = page.list || [];
    return publishedPosts.value;
  }

  async function loadFavorites() {
    const page = await getFavoriteCampusPostPage({ pageNo: 1, pageSize: 100 });
    favoritePosts.value = page.list || [];
    return favoritePosts.value;
  }

  async function publishPost(input: PublishPostInput) {
    const created = await createCampusPost(input);
    posts.value = [created, ...posts.value.filter(item => item.id !== created.id)];
    publishedPosts.value = [created, ...publishedPosts.value.filter(item => item.id !== created.id)];
    currentPost.value = created;
    return created;
  }

  async function loadPost(id: number) {
    const post = await getCampusPost(id);
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
    const updated = await setCampusPostLike(id, active);
    replacePost(updated);
    return updated;
  }

  async function setPostCollect(id: number, active: boolean) {
    const updated = await setCampusPostCollect(id, active);
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
