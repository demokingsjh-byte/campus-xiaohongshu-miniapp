<script lang="ts" setup>
import type { CampusHomeCategory, CampusHomeConfig } from '@/services/api/content';
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import PrototypeTabBar from '@/components/PrototypeTabBar/index.vue';
import { campusTenants, getDefaultTenant } from '@/mock/campus';
import { getCampusHomeConfig } from '@/services/api/content';
import { useCampusNotificationStore } from '@/stores/modules/notification';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const DEFAULT_HOME_CONFIG: CampusHomeConfig = {
  searchPlaceholder: '搜索',
  notice: '',
  categoryIconVisible: true,
  categoryTitleVisible: true,
  categories: [
    { key: 'recommend', title: '推荐', channel: '推荐', icon: '🚩' },
    { key: 'idle', title: '二手闲置', channel: '二手', icon: '🧺', publishType: 'idle' },
    { key: 'errand', title: '代拿代办', channel: '互助', icon: '🏃', publishType: 'help' },
    { key: 'fun', title: '校园趣事', channel: '社团', icon: '🎒', publishType: 'club' },
    { key: 'job', title: '兼职信息', channel: '兼职', icon: '🧰', publishType: 'job' },
    { key: 'confession', title: '表白墙', channel: '表白', icon: '💗', publishType: 'confession' },
    { key: 'groupbuy', title: '商家团购', channel: '探店', icon: '🏪', publishType: 'shop' },
  ],
};

const PROTOTYPE_CATEGORY_ICONS: Record<string, string> = {
  recommend: '/static/images/home-prototype/category-recommend.png',
  idle: '/static/images/home-prototype/category-idle.png',
  errand: '/static/images/home-prototype/category-errand.png',
  fun: '/static/images/home-prototype/category-fun.png',
  job: '/static/images/home-prototype/category-job.png',
};

const activeCategoryKey = ref('recommend');
const homeConfig = ref<CampusHomeConfig>(DEFAULT_HOME_CONFIG);

function categoryIconSource(category: CampusHomeCategory) {
  return PROTOTYPE_CATEGORY_ICONS[category.key] || category.icon;
}

function categoryIconVisible(category: CampusHomeCategory) {
  return homeConfig.value.categoryIconVisible !== false && category.iconVisible !== false;
}

function categoryTitleVisible(category: CampusHomeCategory) {
  return homeConfig.value.categoryTitleVisible !== false && category.titleVisible !== false;
}

const state = ref<'content' | 'loading' | 'empty' | 'error'>('loading');
const showCampusPicker = ref(false);
const campusSwitching = ref(false);
const categoryScrollLeft = ref(0);
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
const notificationStore = useCampusNotificationStore();
const userStore = useUserStore();
const statusBarHeight = ref(0);
const menuButtonRightInset = ref(104);

interface MenuButtonRect {
  left: number
  top: number
}

const navigationStyle = computed(() => ({
  '--status-bar-height': `${statusBarHeight.value}px`,
  '--menu-button-right-inset': `${menuButtonRightInset.value}px`,
}));
const categories = computed(() => homeConfig.value.categories.length
  ? homeConfig.value.categories
  : DEFAULT_HOME_CONFIG.categories);
const activeCategory = computed<CampusHomeCategory>(() => categories.value.find(item => item.key === activeCategoryKey.value)
  || categories.value[0]
  || DEFAULT_HOME_CONFIG.categories[0]);
const visiblePosts = computed(() => contentStore.allPosts);
const recommendationConfessions = computed(() => activeCategoryKey.value === 'recommend'
  ? visiblePosts.value.filter(post => post.channel === '表白')
  : []);
const gridPosts = computed(() => activeCategoryKey.value === 'recommend'
  ? visiblePosts.value.filter(post => post.channel !== '表白')
  : visiblePosts.value);
const leftPosts = computed(() => gridPosts.value.filter((_, index) => index % 2 === 0));
const rightPosts = computed(() => gridPosts.value.filter((_, index) => index % 2 === 1));
const useWideFeed = computed(() => ['job', 'groupbuy'].includes(activeCategoryKey.value));

function updateNavigationLayout() {
  const systemInfo = uni.getSystemInfoSync();
  const runtime = uni as typeof uni & {
    getMenuButtonBoundingClientRect?: () => MenuButtonRect
  };
  const menuButton = runtime.getMenuButtonBoundingClientRect?.();
  statusBarHeight.value = Math.max(systemInfo.statusBarHeight || 0, menuButton?.top || 0);
  if (menuButton?.left && systemInfo.windowWidth)
    menuButtonRightInset.value = Math.max(92, systemInfo.windowWidth - menuButton.left + 8);
}

if (!tenantStore.currentTenant || !campusTenants.some(item => item.id === tenantStore.tenantId))
  tenantStore.selectTenant(getDefaultTenant());

async function loadConfig() {
  try {
    const response = await getCampusHomeConfig(tenantStore.tenantId || undefined);
    if (response?.categories?.length) {
      homeConfig.value = {
        searchPlaceholder: response.searchPlaceholder || DEFAULT_HOME_CONFIG.searchPlaceholder,
        notice: response.notice || '',
        categoryIconVisible: response.categoryIconVisible !== false,
        categoryTitleVisible: response.categoryTitleVisible !== false,
        categories: response.categories,
      };
    }
  } catch {
    homeConfig.value = DEFAULT_HOME_CONFIG;
  }
  if (!categories.value.some(item => item.key === activeCategoryKey.value))
    activeCategoryKey.value = categories.value[0]?.key || 'recommend';
}

async function loadFeed(showLoading = true) {
  if (showLoading)
    state.value = 'loading';
  try {
    await contentStore.loadPosts({
      tenantId: tenantStore.tenantId || undefined,
      channel: activeCategory.value.channel || '推荐',
    });
    state.value = visiblePosts.value.length ? 'content' : 'empty';
  } catch {
    state.value = visiblePosts.value.length ? 'content' : 'error';
  }
}

function centerCategory(category: CampusHomeCategory) {
  const index = categories.value.findIndex(item => item.key === category.key);
  if (index < 0)
    return;

  const { windowWidth = 375 } = uni.getSystemInfoSync();
  const rpxToPx = windowWidth / 750;
  const itemWidth = 150 * rpxToPx;
  const stripPadding = 15 * rpxToPx;
  const stripWidth = categories.value.length * itemWidth + stripPadding * 2;
  const centeredLeft = stripPadding + index * itemWidth + itemWidth / 2 - windowWidth / 2;
  const maxScrollLeft = Math.max(0, stripWidth - windowWidth);
  categoryScrollLeft.value = Math.round(Math.min(Math.max(centeredLeft, 0), maxScrollLeft));
}

async function chooseCategory(category: CampusHomeCategory) {
  centerCategory(category);
  if (activeCategoryKey.value === category.key)
    return;
  activeCategoryKey.value = category.key;
  await loadFeed();
}

function goSearch() {
  uni.navigateTo({ url: '/pages/search/index' });
}

function goMessages() {
  uni.navigateTo({ url: '/pages/messages/index' });
}

function openCampusPicker() {
  showCampusPicker.value = true;
}

async function selectCampus(campus: typeof campusTenants[number]) {
  if (campusSwitching.value)
    return;
  if (campus.id === tenantStore.tenantId) {
    showCampusPicker.value = false;
    return;
  }
  campusSwitching.value = true;
  try {
    if (userStore.loggedIn) {
      await userStore.silentLogin({ tenantId: campus.id });
      tenantStore.selectTenant(campus);
      await userStore.updateProfile({
        nickname: userStore.userInfo?.nickname,
        avatar: userStore.userInfo?.avatar,
        schoolName: campus.name,
        campusName: campus.name === '吉首大学' ? '吉首校区' : '主校区',
        grade: userStore.userInfo?.grade,
        gender: userStore.userInfo?.gender,
        roleType: userStore.userInfo?.roleType || 'student',
      });
    } else {
      tenantStore.selectTenant(campus);
    }
    activeCategoryKey.value = 'recommend';
    await loadConfig();
    await loadFeed();
    showCampusPicker.value = false;
    uni.showToast({ title: `已切换到${campus.name}`, icon: 'success' });
  } catch {
    uni.showToast({ title: '校园切换失败，请重试', icon: 'none' });
  } finally {
    campusSwitching.value = false;
  }
}

function goPublish(type?: string) {
  if (type)
    uni.setStorageSync('campus-publish-active-type', type);
  uni.reLaunch({ url: '/pages/publish/index' });
}

async function retry() {
  await loadFeed();
}

onLoad(() => updateNavigationLayout());
onShow(async () => {
  updateNavigationLayout();
  await loadConfig();
  const requestedChannel = uni.getStorageSync('campus-home-channel');
  if (requestedChannel) {
    const requestedCategory = categories.value.find(item => [item.key, item.channel, item.title].includes(requestedChannel));
    if (requestedCategory)
      activeCategoryKey.value = requestedCategory.key;
  }
  uni.removeStorageSync('campus-home-channel');
  await loadFeed(!contentStore.allPosts.length);
  if (userStore.loggedIn)
    await notificationStore.loadUnreadCount();
});
watch(() => userStore.loggedIn, loggedIn => loggedIn && notificationStore.loadUnreadCount());
</script>

<template>
  <view class="home-page" :style="navigationStyle">
    <view class="hero-shell">
      <view class="status-space" />
      <view class="topbar">
        <button class="school-trigger" aria-label="切换学校" @click="openCampusPicker">
          <text>{{ tenantStore.tenantName || '选择学校' }}</text>
          <view class="school-arrow" aria-hidden="true" />
        </button>

        <button class="search-entry" aria-label="搜索校园内容" @click="goSearch">
          <image class="search-icon" src="/static/images/home-prototype/search.svg" mode="aspectFit" />
          <text class="search-label">
            搜索
          </text>
        </button>

        <button class="message-entry" aria-label="消息通知" @click="goMessages">
          <image src="/static/images/home-prototype/bell.svg" mode="aspectFit" />
        </button>
      </view>

      <scroll-view
        class="category-scroll" scroll-x scroll-with-animation
        :scroll-left="categoryScrollLeft" :show-scrollbar="false"
      >
        <view class="category-strip">
          <button
            v-for="category in categories" :key="category.key" class="category-item"
            :class="{ active: activeCategoryKey === category.key }" @click="chooseCategory(category)"
          >
            <view v-if="categoryIconVisible(category)" class="category-icon-wrap">
              <image
                v-if="categoryIconSource(category).startsWith('/') || categoryIconSource(category).startsWith('http')"
                class="category-image" :src="categoryIconSource(category)" mode="aspectFit"
              />
              <text v-else class="category-emoji">
                {{ category.icon }}
              </text>
            </view>
            <text v-if="categoryTitleVisible(category)" class="category-title">
              {{ category.title }}
            </text>
          </button>
        </view>
      </scroll-view>
    </view>

    <view v-if="homeConfig.notice" class="notice-bar">
      <text class="notice-mark">
        ●
      </text>
      <text>{{ homeConfig.notice }}</text>
    </view>

    <view class="feed-scroll">
      <view
        v-if="state === 'content' && activeCategoryKey === 'recommend'"
        class="share-fresh-card" role="button" aria-label="去分享新鲜事" @click="goPublish()"
      >
        <view class="share-copy">
          <view class="share-title">
            <text class="share-hash">
              #
            </text>
            <text>分享新鲜事</text>
          </view>
          <text class="share-subtitle">
            {{ homeConfig.notice || '记录真实、有用的校园生活' }}
          </text>
        </view>
        <view class="share-action">
          <image src="/static/images/home-prototype/share-button.png" mode="aspectFit" />
          <text>去分享</text>
        </view>
      </view>

      <view v-if="state === 'loading'" class="feed-grid skeleton-grid">
        <view v-for="n in 6" :key="n" class="skeleton-card">
          <view class="skeleton-cover" />
          <view class="skeleton-line wide" />
          <view class="skeleton-line" />
        </view>
      </view>

      <view v-else-if="state === 'empty'" class="empty-state">
        <view class="empty-visual">
          <text>🔎</text>
        </view>
        <text class="empty-title">
          这里还没有新内容
        </text>
        <text class="empty-copy">
          做第一个分享校园生活的人吧，真实内容会优先推荐给同校同学
        </text>
        <button class="empty-action" @click="goPublish(activeCategory.publishType)">
          去发布
        </button>
      </view>

      <view v-else-if="state === 'error'" class="empty-state error-state">
        <view class="empty-visual">
          <text>📡</text>
        </view>
        <text class="empty-title">
          内容暂时加载失败
        </text>
        <text class="empty-copy">
          网络开了个小差，稍后再试试
        </text>
        <button class="empty-action" @click="retry">
          重新加载
        </button>
      </view>

      <template v-else-if="useWideFeed">
        <view class="feed-list">
          <CampusPostCard
            v-for="post in visiblePosts" :key="post.id" :post="post"
            :variant="activeCategoryKey"
          />
        </view>
        <view class="feed-end">
          没有更多了～
        </view>
        <view class="bottom-safe-space" />
      </template>

      <template v-else>
        <scroll-view
          v-if="recommendationConfessions.length" class="confession-scroll"
          scroll-x :show-scrollbar="false" enhanced
        >
          <view class="confession-track">
            <CampusPostCard
              v-for="post in recommendationConfessions" :key="post.id"
              :post="post" variant="confession"
            />
          </view>
        </scroll-view>

        <view class="feed-grid" :class="{ 'confession-grid': activeCategoryKey === 'confession' }">
          <view class="feed-column">
            <CampusPostCard
              v-for="post in leftPosts" :key="post.id" :post="post"
              :variant="activeCategoryKey"
            />
          </view>
          <view class="feed-column">
            <CampusPostCard
              v-for="post in rightPosts" :key="post.id" :post="post"
              :variant="activeCategoryKey"
            />
          </view>
        </view>
        <view class="feed-end">
          没有更多了～
        </view>
      </template>
      <view class="bottom-safe-space" />
    </view>

    <PrototypeTabBar active="home" />

    <view v-if="showCampusPicker" class="campus-picker-mask" @click="showCampusPicker = false">
      <view class="campus-picker" @click.stop>
        <view class="picker-handle" />
        <view class="picker-head">
          <view>
            <text>切换学校</text>
            <text>选择后将更新首页内容</text>
          </view>
          <button class="picker-close" aria-label="关闭" @click="showCampusPicker = false">
            ×
          </button>
        </view>
        <view class="campus-options">
          <view
            v-for="campus in campusTenants" :key="campus.id" class="campus-option"
            :class="{ selected: campus.id === tenantStore.tenantId, disabled: campusSwitching }"
            @click="selectCampus(campus)"
          >
            <view class="campus-option-mark">
              {{ campus.name.slice(0, 1) }}
            </view>
            <view class="campus-option-main">
              <text>{{ campus.name }}</text>
              <text>{{ campus.id === tenantStore.tenantId ? '当前学校' : '点击切换' }}</text>
            </view>
            <view class="campus-check">
              {{ campus.id === tenantStore.tenantId ? '✓' : '›' }}
            </view>
          </view>
        </view>
        <view class="picker-tip">
          切换校园不会影响你的历史发布和收藏
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  color: #1c1f1d;
  background: #f4f4f4;
}

.hero-shell {
  position: relative;
  z-index: 2;
  overflow: hidden;
  background:
    linear-gradient(45deg, rgba(255, 255, 255, 0.3) 25%, transparent 25%, transparent 75%, rgba(255, 255, 255, 0.3) 75%)
      0 0 / 56rpx 56rpx,
    linear-gradient(45deg, rgba(255, 255, 255, 0.3) 25%, transparent 25%, transparent 75%, rgba(255, 255, 255, 0.3) 75%)
      28rpx 28rpx / 56rpx 56rpx,
    linear-gradient(180deg, #bfffd1 0%, #d9fae5 62%, #eef6f1 100%);
}

.hero-shell::after {
  display: none;
}

.status-space {
  height: var(--status-bar-height, env(safe-area-inset-top));
}

.topbar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: 112rpx;
  padding: 0 var(--menu-button-right-inset, 104px) 0 30rpx;
  gap: 28rpx;
}

.school-trigger {
  display: flex;
  overflow: hidden;
  flex: 0 0 auto;
  align-items: center;
  max-width: 154rpx;
  height: 72rpx;
  padding: 0;
  color: #202421;
  font-size: 30rpx;
  font-weight: 500;
}

.school-trigger > text:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.school-arrow {
  width: 0;
  height: 0;
  margin-left: 8rpx;
  border-top: 9rpx solid #303531;
  border-right: 7rpx solid transparent;
  border-left: 7rpx solid transparent;
  transform: translateY(2rpx);
}

.search-entry {
  display: flex;
  overflow: hidden;
  flex: 1;
  align-items: center;
  justify-content: flex-start;
  min-width: 0;
  height: 72rpx;
  padding: 0 24rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.58);
  border-radius: 36rpx;
  color: #8b9190;
  background: rgba(242, 248, 247, 0.52);
  font-size: 24rpx;
  backdrop-filter: blur(12rpx);
}

.search-icon {
  flex: 0 0 auto;
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
}

.search-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-entry {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 72rpx;
  padding: 0;
}

.message-entry > image {
  width: 40rpx;
  height: 48rpx;
}

.category-scroll {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 178rpx;
  white-space: nowrap;
}

.category-strip {
  display: inline-flex;
  height: 178rpx;
  padding: 2rpx 15rpx 12rpx;
  align-items: flex-start;
}

.category-item {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-start;
  width: 150rpx;
  height: 168rpx;
  padding: 0;
  flex-direction: column;
}

.category-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 102rpx;
  height: 104rpx;
  border: 1rpx solid transparent;
  border-radius: 34rpx;
  transition:
    background 0.18s ease,
    transform 0.18s ease;
}

.category-item.active .category-icon-wrap {
  border-color: rgba(255, 255, 255, 0.82);
  background: linear-gradient(145deg, rgba(211, 255, 195, 0.9), rgba(239, 255, 231, 0.55));
  box-shadow: 0 10rpx 24rpx rgba(94, 223, 87, 0.14);
  transform: translateY(-2rpx);
}

.category-emoji {
  font-family: 'Segoe UI Emoji', 'Apple Color Emoji', sans-serif;
  font-size: 64rpx;
  line-height: 1;
  filter: drop-shadow(0 7rpx 8rpx rgba(48, 81, 59, 0.11));
}

.category-image {
  width: 102rpx;
  height: 102rpx;
}

.category-title {
  margin-top: 1rpx;
  color: #303331;
  font-size: 28rpx;
  line-height: 40rpx;
  white-space: nowrap;
}

.category-item.active .category-title {
  color: #171a18;
  font-weight: 650;
}

.notice-bar {
  display: flex;
  align-items: center;
  height: 58rpx;
  padding: 0 32rpx;
  color: #6c736f;
  background: #f5f5f5;
  font-size: 21rpx;
}

.notice-mark {
  margin-right: 12rpx;
  color: #8bf11f;
  font-size: 16rpx;
}

.feed-scroll {
  min-height: calc(100vh - var(--status-bar-height, 0px) - 290rpx);
  background: #f4f4f4;
}

.share-fresh-card {
  position: relative;
  display: flex;
  overflow: hidden;
  align-items: center;
  justify-content: space-between;
  height: 128rpx;
  margin: 16rpx 32rpx 32rpx;
  padding: 0 24rpx 0 28rpx;
  border-radius: 32rpx;
  background:
    linear-gradient(45deg, rgba(15, 211, 242, 0.24) 25%, transparent 25%, transparent 75%, rgba(15, 211, 242, 0.24) 75%)
      0 0 / 58rpx 58rpx,
    linear-gradient(45deg, rgba(15, 211, 242, 0.24) 25%, transparent 25%, transparent 75%, rgba(15, 211, 242, 0.24) 75%)
      29rpx 29rpx / 58rpx 58rpx,
    linear-gradient(100deg, #71edf5 0%, #62ecf2 55%, #70eef3 100%);
  box-sizing: border-box;
}

.share-fresh-card::before,
.share-fresh-card::after {
  position: absolute;
  left: -20rpx;
  width: 270rpx;
  height: 4rpx;
  border-radius: 4rpx;
  background: #dfff37;
  content: '';
  opacity: 0.9;
  transform: rotate(-20deg);
}

.share-fresh-card::before {
  top: 59rpx;
}

.share-fresh-card::after {
  top: 82rpx;
  left: 18rpx;
  width: 180rpx;
}

.share-copy {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.share-title {
  display: flex;
  align-items: center;
  color: #182021;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 38rpx;
}

.share-hash {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38rpx;
  height: 38rpx;
  margin-right: 5rpx;
  border-radius: 50%;
  color: #fff;
  background: #202324;
  font-size: 24rpx;
  font-weight: 800;
}

.share-subtitle {
  overflow: hidden;
  margin-top: 9rpx;
  color: #1aa6b3;
  font-size: 25rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-action {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  width: 161rpx;
  height: 64rpx;
  margin-left: 12rpx;
}

.share-action image {
  display: block;
  width: 161rpx;
  height: 64rpx;
}

.share-action text {
  position: absolute;
  top: 0;
  right: 10rpx;
  width: 117rpx;
  color: #fff;
  font-size: 28rpx;
  line-height: 64rpx;
  text-align: center;
}

.confession-scroll {
  width: 100%;
  height: 336rpx;
  margin-bottom: 32rpx;
  white-space: nowrap;
}

.confession-track {
  display: inline-flex;
  height: 336rpx;
  padding: 0 32rpx;
  gap: 38rpx;
}

.confession-grid :deep(.variant-confession) {
  width: 100%;
  height: 336rpx;
  margin-bottom: 24rpx;
}

.confession-grid :deep(.confession-card) {
  padding-left: 40rpx;
}

.feed-grid {
  display: grid;
  padding: 0 32rpx;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24rpx;
  align-items: start;
}

.feed-list {
  padding: 4rpx 30rpx 0;
}

.feed-column {
  min-width: 0;
}

.feed-end {
  padding: 12rpx 0 18rpx;
  color: #aaaead;
  font-size: 23rpx;
  text-align: center;
}

.bottom-safe-space {
  height: calc(164rpx + env(safe-area-inset-bottom));
}

.skeleton-grid {
  opacity: 0.78;
}

.skeleton-card {
  overflow: hidden;
  height: 640rpx;
  margin-bottom: 18rpx;
  border-radius: 15rpx;
  background: #fff;
}

.skeleton-cover,
.skeleton-line {
  background: linear-gradient(100deg, #edf0ee 20%, #f7f8f7 40%, #edf0ee 60%);
  background-size: 200% 100%;
  animation: skeleton-move 1.15s infinite linear;
}

.skeleton-cover {
  height: 536rpx;
}

.skeleton-line {
  width: 56%;
  height: 19rpx;
  margin: 15rpx 16rpx 0;
  border-radius: 10rpx;
}

.skeleton-line.wide {
  width: 80%;
  margin-top: 20rpx;
}

@keyframes skeleton-move {
  to {
    background-position: -200% 0;
  }
}

.empty-state {
  display: flex;
  align-items: center;
  min-height: 560rpx;
  padding: 60rpx 72rpx 180rpx;
  flex-direction: column;
  text-align: center;
}

.empty-visual {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 210rpx;
  height: 170rpx;
}

.empty-visual::after {
  position: absolute;
  left: 34rpx;
  bottom: 20rpx;
  width: 140rpx;
  height: 28rpx;
  border-radius: 50%;
  background: rgba(105, 115, 110, 0.08);
  content: '';
  filter: blur(7rpx);
}

.empty-visual text {
  position: relative;
  z-index: 1;
  font-size: 112rpx;
  transform: rotate(-10deg);
}

.empty-title {
  color: #252927;
  font-size: 31rpx;
  font-weight: 650;
}

.empty-copy {
  max-width: 500rpx;
  margin-top: 16rpx;
  color: #a0a4a2;
  font-size: 23rpx;
  line-height: 1.55;
}

.empty-action {
  height: 61rpx;
  margin-top: 22rpx;
  padding: 0 42rpx;
  border-radius: 17rpx;
  color: #1e3511;
  background: #8ff51c;
  box-shadow: 0 9rpx 18rpx rgba(112, 217, 24, 0.14);
  font-size: 25rpx;
  font-weight: 650;
  line-height: 61rpx;
}

.campus-picker-mask {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(17, 25, 20, 0.42);
}

.campus-picker {
  width: 100%;
  padding: 16rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  border-radius: 34rpx 34rpx 0 0;
  background: #f7f8f7;
  box-shadow: 0 -18rpx 46rpx rgba(23, 34, 27, 0.12);
}

.picker-handle {
  width: 68rpx;
  height: 7rpx;
  margin: 0 auto 26rpx;
  border-radius: 8rpx;
  background: #d5d9d6;
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26rpx;
}

.picker-head > view:first-child {
  display: flex;
  flex-direction: column;
}

.picker-head text:first-child {
  font-size: 32rpx;
  font-weight: 750;
}

.picker-head text:last-child {
  margin-top: 7rpx;
  color: #969c98;
  font-size: 22rpx;
}

.picker-close {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  color: #777e7a;
  background: #ecefed;
  font-size: 31rpx;
  line-height: 64rpx;
}

.campus-options {
  overflow: hidden;
  border-radius: 24rpx;
  background: #fff;
}

.campus-option {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  padding: 18rpx 24rpx;
  border-bottom: 1rpx solid #eef0ee;
}

.campus-option:last-child {
  border-bottom: 0;
}
.campus-option.selected {
  background: #efffe7;
}
.campus-option.disabled {
  opacity: 0.58;
}

.campus-option-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  border-radius: 18rpx;
  color: #27321f;
  background: #93f421;
  font-size: 26rpx;
  font-weight: 700;
}

.campus-option-main {
  display: flex;
  flex: 1;
  margin-left: 20rpx;
  flex-direction: column;
}

.campus-option-main text:first-child {
  font-size: 27rpx;
  font-weight: 650;
}
.campus-option-main text:last-child {
  margin-top: 6rpx;
  color: #9a9f9c;
  font-size: 20rpx;
}
.campus-check {
  color: #65dc06;
  font-size: 30rpx;
}
.picker-tip {
  padding-top: 22rpx;
  color: #a3a8a5;
  font-size: 21rpx;
  text-align: center;
}
</style>
