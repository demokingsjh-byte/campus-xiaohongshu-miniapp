import type { CampusPost } from '@/mock/campus';
import type { CampusTenant } from '@/utils/tenant';
import { defineStore } from 'pinia';
import { campusPosts } from '@/mock/campus';
import { clearCampusTenant, getCampusTenant, setCampusTenant } from '@/utils/tenant';

const CONTENT_STORAGE_KEY = 'campus-published-posts';

export interface PublishPostInput {
  tenantId: number
  school: string
  type: string
  title: string
  content: string
  price?: string
  tags: string[]
  images: string[]
  author: string
  anonymous?: boolean
}

const channelMap: Record<string, string> = {
  idle: '二手',
  help: '互助',
  ride: '拼车',
  shop: '探店',
  lost: '失物',
  club: '社团',
};

const coverMap: Record<string, { color: string, emoji: string, label: string }> = {
  idle: { color: '#DDEFE8', emoji: '📦', label: '刚刚发布｜实拍' },
  help: { color: '#E6EEE0', emoji: '🙌', label: '同校互助' },
  ride: { color: '#DCEEF3', emoji: '🚕', label: '拼车招募中' },
  shop: { color: '#FCE7DE', emoji: '🥤', label: '真实探店' },
  lost: { color: '#FFF0D9', emoji: '🔎', label: '失物信息' },
  club: { color: '#E7EFE8', emoji: '🎉', label: '活动报名中' },
};

function readPublishedPosts(): CampusPost[] {
  const cached = uni.getStorageSync(CONTENT_STORAGE_KEY);
  return Array.isArray(cached) ? cached : [];
}

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
  const publishedPosts = ref<CampusPost[]>(readPublishedPosts());
  const allPosts = computed(() => [...publishedPosts.value, ...campusPosts]);

  function publishPost(input: PublishPostInput) {
    const cover = coverMap[input.type] || coverMap.help;
    const author = input.anonymous ? '同校同学' : (input.author || '同校同学');
    const post: CampusPost = {
      id: Date.now(),
      tenantId: input.tenantId,
      channel: channelMap[input.type] || '互助',
      title: input.title.trim(),
      content: input.content.trim(),
      author,
      avatarText: author.slice(0, 1),
      school: input.school,
      time: '刚刚',
      price: input.price?.trim() || undefined,
      tags: input.tags.length ? [...input.tags] : ['校园新鲜事'],
      likes: 0,
      comments: 0,
      coverColor: cover.color,
      coverEmoji: cover.emoji,
      coverLabel: cover.label,
      height: input.images.length > 1 ? 'tall' : (input.price ? 'medium' : 'short'),
      coverImage: input.images[0],
    };
    publishedPosts.value.unshift(post);
    publishedPosts.value = publishedPosts.value.slice(0, 30);
    uni.setStorageSync(CONTENT_STORAGE_KEY, publishedPosts.value);
    return post;
  }

  function getPost(id: number) {
    return allPosts.value.find(item => item.id === id);
  }

  return { publishedPosts, allPosts, publishPost, getPost };
});
