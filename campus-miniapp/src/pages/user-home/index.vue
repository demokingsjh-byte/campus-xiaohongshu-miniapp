<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import type { CampusPublicUserProfile } from '@/services/api/auth';
import type { CampusHomeConfig } from '@/services/api/content';
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { getCampusPublicUserProfile } from '@/services/api/auth';
import { getCampusHomeConfig, getCampusUserPostPage, setCampusUserFollow } from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar, resolveCampusMediaUrl } from '@/utils/avatar';

interface ProfileCategory {
  type: string
  title: string
  count: number
}

const PAGE_SIZE = 20;
const categoryDefaults = [
  { type: 'idle', title: '二手闲置' },
  { type: 'help', title: '代拿代办' },
  { type: 'club', title: '校园趣事' },
  { type: 'job', title: '兼职信息' },
  { type: 'confession', title: '表白墙' },
  { type: 'shop', title: '商家团购' },
  { type: 'ride', title: '拼车出行' },
  { type: 'lost', title: '失物招领' },
] as const;

const userStore = useUserStore();
const targetUserId = ref(0);
const requestedType = ref('');
const profile = ref<CampusPublicUserProfile>();
const categories = ref<ProfileCategory[]>([]);
const activeType = ref('');
const posts = ref<CampusPost[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageState = ref<'loading' | 'content' | 'error'>('loading');
const loadingMore = ref(false);
const followBusy = ref(false);
const statusBarHeight = ref(0);
let requestToken = 0;

const navigationStyle = computed(() => ({ '--status-bar-height': `${statusBarHeight.value}px` }));
const hasMore = computed(() => posts.value.length < total.value);
const leftPosts = computed(() => posts.value.filter((_, index) => index % 2 === 0));
const rightPosts = computed(() => posts.value.filter((_, index) => index % 2 === 1));
const profileDescription = computed(() => {
  const parts = [profile.value?.schoolName, profile.value?.campusName, profile.value?.grade].filter(Boolean);
  return parts.length ? parts.join(' · ') : '这个人很神秘，还没有填写个人介绍';
});
const locationLabel = computed(() => profile.value?.province ? `IP ${profile.value.province}` : (profile.value?.schoolName || '校园用户'));
const genderLabel = computed(() => {
  if (profile.value?.gender === '男')
    return '♂ 男生';
  if (profile.value?.gender === '女')
    return '♀ 女生';
  return '';
});

onLoad((query) => {
  const userId = Number(query?.userId || 0);
  targetUserId.value = Number.isFinite(userId) && userId > 0 ? userId : 0;
  requestedType.value = String(query?.type || '').trim();
  updateNavigationLayout();
});

onShow(() => {
  if (targetUserId.value)
    void loadProfilePage();
  else
    pageState.value = 'error';
});

onReachBottom(() => {
  if (hasMore.value && !loadingMore.value)
    void loadPosts(false);
});

onPullDownRefresh(async () => {
  await loadProfilePage();
  uni.stopPullDownRefresh();
});

onShareAppMessage(() => ({
  title: `${profile.value?.nickname || '校园同学'}的个人主页`,
  path: `/pages/user-home/index?userId=${targetUserId.value}`,
  imageUrl: profile.value?.avatar || undefined,
}));

function updateNavigationLayout() {
  const info = uni.getWindowInfo();
  statusBarHeight.value = info.statusBarHeight || 0;
}

function normalizePost(post: CampusPost): CampusPost {
  const images = (post.images || []).map(resolveCampusMediaUrl).filter(Boolean);
  return {
    ...post,
    avatar: resolveCampusAvatar(post.avatar),
    images,
    coverImage: resolveCampusMediaUrl(post.coverImage || images[0]),
  };
}

function buildCategories(homeConfig?: CampusHomeConfig) {
  const configured = new Map((homeConfig?.categories || [])
    .filter(item => item.publishType)
    .map(item => [item.publishType!, item]));
  const counts = profile.value?.postCounts || {};
  categories.value = categoryDefaults
    .filter((item) => {
      const config = configured.get(item.type);
      return !config || config.enabled !== false;
    })
    .map((item) => {
      const config = configured.get(item.type);
      return { type: item.type, title: config?.title || item.title, count: Number(counts[item.type] || 0) };
    });

  const requestedAvailable = categories.value.some(item => item.type === requestedType.value);
  const currentAvailable = categories.value.some(item => item.type === activeType.value);
  if (requestedAvailable)
    activeType.value = requestedType.value;
  else if (!currentAvailable)
    activeType.value = categories.value.find(item => item.count > 0)?.type || categories.value[0]?.type || '';
}

async function loadProfilePage() {
  const token = ++requestToken;
  pageState.value = 'loading';
  try {
    await userStore.initUserInfo();
    const loadedProfile = await getCampusPublicUserProfile(targetUserId.value);
    if (token !== requestToken)
      return;
    profile.value = loadedProfile;
    let homeConfig: CampusHomeConfig | undefined;
    try {
      homeConfig = await getCampusHomeConfig(loadedProfile.tenantId);
    } catch {
      // 旧服务端没有配置接口时仍按默认分类展示，公开帖子接口继续负责最终过滤。
    }
    if (token !== requestToken)
      return;
    buildCategories(homeConfig);
    await loadPosts(true, token);
    if (token === requestToken)
      pageState.value = 'content';
  } catch {
    if (token === requestToken)
      pageState.value = 'error';
  }
}

async function loadPosts(reset: boolean, parentToken = requestToken) {
  if (!activeType.value)
    return;
  const targetPage = reset ? 1 : pageNo.value + 1;
  if (!reset)
    loadingMore.value = true;
  try {
    const result = await getCampusUserPostPage({
      userId: targetUserId.value,
      type: activeType.value,
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    });
    if (parentToken !== requestToken)
      return;
    const nextPosts = (result.list || []).map(normalizePost);
    posts.value = reset ? nextPosts : [...posts.value, ...nextPosts];
    total.value = Number(result.total || 0);
    pageNo.value = targetPage;
  } catch {
    if (reset)
      throw new Error('公开发布加载失败');
    uni.showToast({ title: '更多内容加载失败，请重试', icon: 'none' });
  } finally {
    loadingMore.value = false;
  }
}

async function selectCategory(type: string) {
  if (type === activeType.value)
    return;
  activeType.value = type;
  requestedType.value = type;
  posts.value = [];
  total.value = 0;
  pageNo.value = 1;
  pageState.value = 'loading';
  const token = ++requestToken;
  try {
    await loadPosts(true, token);
    if (token === requestToken)
      pageState.value = 'content';
  } catch {
    if (token === requestToken)
      pageState.value = 'error';
  }
}

function goBack() {
  if (getCurrentPages().length > 1)
    uni.navigateBack();
  else
    uni.switchTab({ url: '/pages/index/index' });
}

function goEditProfile() {
  uni.navigateTo({ url: '/pages/login/index?mode=edit' });
}

async function toggleFollow() {
  if (!profile.value || followBusy.value)
    return;
  if (profile.value.self) {
    goEditProfile();
    return;
  }
  if (!userStore.loggedIn) {
    uni.showModal({
      title: '登录后关注',
      content: '登录后可以关注该用户，并在“我的关注”中再次找到TA。',
      confirmText: '去登录',
      success: result => result.confirm && uni.navigateTo({ url: '/pages/login/index' }),
    });
    return;
  }
  followBusy.value = true;
  const active = !profile.value.followed;
  try {
    profile.value.followed = await setCampusUserFollow(profile.value.userId, active);
    profile.value.followerCount = Math.max(0, Number(profile.value.followerCount || 0) + (active ? 1 : -1));
    uni.showToast({ title: active ? '已关注' : '已取消关注', icon: 'none' });
  } catch {
    uni.showToast({ title: '关注操作失败，请重试', icon: 'none' });
  } finally {
    followBusy.value = false;
  }
}
</script>

<template>
  <view class="user-home" :style="navigationStyle">
    <view class="profile-hero">
      <view class="status-space" />
      <view class="custom-nav">
        <view class="back-button" aria-label="返回" @click="goBack">
          <image src="/static/icons/ui/back.svg" mode="aspectFit" />
        </view>
        <view class="capsule-space" />
      </view>

      <template v-if="profile">
        <view class="profile-main">
          <image class="profile-avatar" :src="resolveCampusAvatar(profile.avatar)" mode="aspectFill" />
          <view class="profile-copy">
            <text class="profile-name">
              {{ profile.nickname || '校园同学' }}
            </text>
            <view class="profile-stats">
              <text>{{ profile.followerCount || 0 }} 粉丝</text>
              <text>{{ profile.followingCount || 0 }} 关注</text>
            </view>
          </view>
          <button
            class="follow-button" :class="{ followed: profile.followed && !profile.self }"
            :disabled="followBusy" @click="toggleFollow"
          >
            {{ profile.self ? '编辑资料' : (profile.followed ? '已关注' : '关注') }}
          </button>
        </view>
        <view class="identity-tags">
          <text class="location-tag">
            {{ locationLabel }}
          </text>
          <text v-if="genderLabel" class="gender-tag" :class="profile.gender === '女' ? 'female' : 'male'">
            {{ genderLabel }}
          </text>
        </view>
        <view class="profile-description">
          {{ profileDescription }}
        </view>
      </template>
    </view>

    <view class="profile-content">
      <scroll-view v-if="profile && categories.length" class="category-scroll" scroll-x :show-scrollbar="false">
        <view class="category-tabs">
          <view
            v-for="category in categories" :key="category.type" class="category-tab"
            :class="{ active: activeType === category.type }" @click="selectCategory(category.type)"
          >
            <text>{{ category.title }}</text><text v-if="category.count" class="category-count">
              {{ category.count }}
            </text>
          </view>
        </view>
      </scroll-view>

      <StatePanel v-if="pageState === 'loading'" title="正在加载个人主页" description="请稍候…" />
      <StatePanel
        v-else-if="pageState === 'error'" type="offline" title="个人主页加载失败"
        description="用户可能已注销，或当前网络不可用" action="重新加载" @action="loadProfilePage"
      />
      <view v-else-if="posts.length" class="post-waterfall">
        <view class="post-column">
          <CampusPostCard
            v-for="item in leftPosts" :key="item.id" :post="item"
            collection-context
          />
        </view>
        <view class="post-column">
          <CampusPostCard
            v-for="item in rightPosts" :key="item.id" :post="item"
            collection-context
          />
        </view>
      </view>
      <StatePanel
        v-else title="这个分类还没有发布"
        :description="profile?.self ? '发布内容后会展示在你的公开主页' : 'TA暂时没有公开发布这类内容'"
      />
      <view v-if="loadingMore" class="loading-more">
        正在加载更多…
      </view>
      <view v-else-if="posts.length && !hasMore" class="loading-more">
        已经到底啦
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.user-home {
  box-sizing: border-box;
  min-height: 100vh;
  color: #1f1f1f;
  background: #fff;
  font-family: 'PingFang SC', sans-serif;
}

.profile-hero {
  box-sizing: border-box;
  min-height: 514rpx;
  padding: 0 32rpx 38rpx;
  background:
    radial-gradient(circle at 82% 8%, rgba(102, 240, 194, 0.25), transparent 34%),
    linear-gradient(180deg, #bff9ca 0%, #e9f8ee 62%, #f7f7f7 100%);
}

.status-space {
  height: var(--status-bar-height);
}

.custom-nav {
  position: relative;
  display: flex;
  align-items: center;
  height: 94rpx;
}

.back-button {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 76rpx;
  height: 76rpx;
}

.back-button image {
  width: 30rpx;
  height: 30rpx;
}
.capsule-space {
  position: absolute;
  right: 0;
  width: 186rpx;
  height: 64rpx;
}

.profile-main {
  display: flex;
  align-items: center;
  height: 144rpx;
  margin-top: 26rpx;
}

.profile-avatar {
  flex: 0 0 auto;
  width: 128rpx;
  height: 128rpx;
  border: 6rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 50%;
  background: #fff;
}

.profile-copy {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
  padding-right: 16rpx;
}

.profile-name {
  display: -webkit-box;
  overflow: hidden;
  max-height: 80rpx;
  color: #111;
  font-size: 32rpx;
  font-weight: 650;
  line-height: 40rpx;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.profile-stats {
  display: flex;
  align-items: center;
  gap: 30rpx;
  margin-top: 8rpx;
  color: #858585;
  font-size: 27rpx;
  line-height: 40rpx;
}

.follow-button {
  display: flex;
  flex: 0 0 124rpx;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 124rpx;
  min-width: 124rpx;
  max-width: 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 8rpx;
  border: 0;
  border-radius: 22rpx;
  color: #171717;
  background: #8cf408;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 58rpx;
  white-space: nowrap;
}

.follow-button::after {
  border: 0;
}
.follow-button.followed {
  color: #6e746f;
  background: rgba(255, 255, 255, 0.78);
}
.follow-button[disabled] {
  opacity: 0.68;
}

.identity-tags {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 17rpx;
}

.identity-tags text {
  display: flex;
  align-items: center;
  height: 45rpx;
  padding: 0 13rpx;
  border-radius: 11rpx;
  color: #969696;
  background: rgba(255, 255, 255, 0.74);
  font-size: 23rpx;
  line-height: 45rpx;
}

.identity-tags .gender-tag.male {
  color: #2494ca;
  background: rgba(218, 241, 253, 0.82);
}
.identity-tags .gender-tag.female {
  color: #e65787;
  background: rgba(255, 228, 238, 0.82);
}

.profile-description {
  overflow: hidden;
  margin-top: 24rpx;
  color: #909090;
  font-size: 27rpx;
  line-height: 40rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-content {
  position: relative;
  z-index: 2;
  min-height: calc(100vh - 490rpx);
  margin-top: -22rpx;
  padding-top: 12rpx;
  border-radius: 34rpx 34rpx 0 0;
  background: #fff;
}

.category-scroll {
  width: 100%;
  height: 90rpx;
}

.category-tabs {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: 90rpx;
  padding: 0 29rpx;
  gap: 45rpx;
  white-space: nowrap;
  width: max-content;
}

.category-tab {
  position: relative;
  display: flex;
  align-items: baseline;
  height: 70rpx;
  color: #8d8d8d;
  font-size: 30rpx;
  line-height: 70rpx;
  flex: 0 0 auto;
}

.category-tab.active {
  color: #202020;
  font-weight: 650;
}
.category-tab.active::after {
  position: absolute;
  z-index: -1;
  bottom: 9rpx;
  left: 0;
  width: 100%;
  height: 9rpx;
  border-radius: 5rpx;
  background: #82ef0b;
  content: '';
}

.category-count {
  margin-left: 6rpx;
  color: #9b9b9b;
  font-size: 21rpx;
  font-weight: 400;
}

.post-waterfall {
  display: flex;
  align-items: flex-start;
  box-sizing: border-box;
  width: 100%;
  padding: 0 30rpx 28rpx;
  gap: 24rpx;
}

.post-column {
  flex: 1;
  min-width: 0;
}

.post-column :deep(.post-card) {
  width: 100%;
}
.post-column :deep(.post-card.structured-meta-card) {
  width: 100%;
}
.post-column :deep(.post-card.structured-meta-card .cover) {
  width: 100%;
}
.post-column :deep(.post-card.structured-meta-card .card-body) {
  width: 100%;
}
.post-column :deep(.post-card.structured-meta-card .post-title),
.post-column :deep(.post-card.structured-meta-card .trade-line),
.post-column :deep(.post-card.structured-meta-card .author-row) {
  width: 100%;
}

.post-column :deep(.post-card.structured-meta-card .post-title) {
  display: -webkit-box;
  flex-basis: 80rpx;
  height: 80rpx;
  max-height: 80rpx;
  line-height: 40rpx;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.loading-more {
  padding: 8rpx 0 42rpx;
  color: #aaa;
  font-size: 23rpx;
  text-align: center;
}
</style>
