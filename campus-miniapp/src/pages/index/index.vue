<script lang="ts" setup>
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusChannels, campusTenants, getDefaultTenant } from '@/mock/campus';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const activeChannel = ref('推荐');
const state = ref<'content' | 'loading' | 'empty' | 'error'>('loading');
const refreshing = ref(false);
const showCampusPicker = ref(false);
const campusSwitching = ref(false);
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
const userStore = useUserStore();
if (!tenantStore.currentTenant || !campusTenants.some(item => item.id === tenantStore.tenantId))
  tenantStore.selectTenant(getDefaultTenant());
const visiblePosts = computed(() => contentStore.allPosts.filter((item) => {
  const matchesTenant = !tenantStore.tenantId || item.tenantId === tenantStore.tenantId;
  const matchesChannel = activeChannel.value === '推荐' || item.channel === activeChannel.value;
  return matchesTenant && matchesChannel;
}));
const leftPosts = computed(() => visiblePosts.value.filter((_, index) => index % 2 === 0));
const rightPosts = computed(() => visiblePosts.value.filter((_, index) => index % 2 === 1));

function chooseChannel(channel: string) {
  activeChannel.value = channel;
  state.value = visiblePosts.value.length ? 'content' : 'empty';
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
      // Keep the tenant header in sync with the token before issuing any authenticated
      // request for the newly selected campus.
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
    activeChannel.value = '推荐';
    showCampusPicker.value = false;
    uni.showToast({ title: `已切换到${campus.name}`, icon: 'success' });
  } catch {
    uni.showToast({ title: '校园切换失败，请重试', icon: 'none' });
  } finally {
    campusSwitching.value = false;
  }
}
function goPublish() {
  uni.switchTab({ url: '/pages/publish/index' });
}
async function loadFeed(showLoading = true) {
  if (showLoading)
    state.value = 'loading';
  try {
    await contentStore.loadPosts({ tenantId: tenantStore.tenantId || undefined });
    state.value = visiblePosts.value.length ? 'content' : 'empty';
  } catch {
    state.value = contentStore.allPosts.length ? 'content' : 'error';
  }
}
function retry() {
  state.value = 'loading';
  loadFeed();
}
async function onRefresh() {
  if (refreshing.value)
    return;
  refreshing.value = true;
  try {
    await loadFeed(false);
  } finally {
    refreshing.value = false;
  }
}
onShow(async () => {
  const channel = uni.getStorageSync('campus-home-channel');
  if (campusChannels.includes(channel))
    chooseChannel(channel);
  uni.removeStorageSync('campus-home-channel');
  await loadFeed(!contentStore.allPosts.length);
});
watch(activeChannel, () => {
  state.value = visiblePosts.value.length ? 'content' : 'empty';
});
watch(() => tenantStore.tenantId, () => loadFeed());
</script>

<template>
  <view class="home-page">
    <view class="status-space" />
    <view class="topbar">
      <button class="school-trigger" aria-label="切换学校" @click="openCampusPicker">
        <text>{{ tenantStore.tenantName || '选择学校' }}</text>
        <text class="school-arrow">
          ▾
        </text>
      </button>
      <button class="message-entry" aria-label="消息通知" @click="goMessages">
        <image class="topbar-icon" src="/static/icons/ui/bell.svg" mode="aspectFit" />
        <view class="message-dot" />
      </button>
    </view>

    <button class="search-entry" aria-label="搜索校园内容" @click="goSearch">
      <image class="search-icon" src="/static/icons/ui/search.svg" mode="aspectFit" />
      <text class="search-placeholder">
        搜二手、拼车、活动或同学
      </text>
    </button>

    <scroll-view class="channel-scroll" scroll-x :show-scrollbar="false">
      <view class="channels">
        <view
          v-for="channel in campusChannels" :key="channel" class="channel"
          :class="{ active: activeChannel === channel }" @click="chooseChannel(channel)"
        >
          {{ channel }}
        </view>
      </view>
    </scroll-view>

    <view class="content-meta">
      <view class="content-context">
        <text>{{ activeChannel === '推荐' ? '校园新鲜事' : activeChannel }}</text>
        <text>{{ visiblePosts.length }} 条</text>
      </view>
      <button class="refresh-entry" :class="{ refreshing }" @click="onRefresh">
        <text class="refresh-symbol">
          ↻
        </text>
        <text>{{ refreshing ? '刷新中' : '换一换' }}</text>
      </button>
    </view>

    <scroll-view
      scroll-y class="feed-scroll" refresher-enabled :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <button class="publish-inspire" @click="goPublish">
        <view class="inspire-avatar">
          ＋
        </view>
        <view class="inspire-copy">
          <text>分享校园新鲜事</text>
          <text>记录真实、有用的校园生活</text>
        </view>
        <text class="inspire-arrow">
          ›
        </text>
      </button>

      <view v-if="state === 'loading'" class="feed-grid skeleton-wrap">
        <view v-for="n in 6" :key="n" class="skeleton-card">
          <view class="sk-cover" />
          <view class="sk-line wide" />
          <view class="sk-line" />
        </view>
      </view>
      <StatePanel
        v-else-if="state === 'empty'" title="这里还没有新内容"
        description="做第一个分享校园生活的人吧，真实内容会优先推荐给同校同学。" action="去发布"
        @action="uni.switchTab({ url: '/pages/publish/index' })"
      />
      <StatePanel
        v-else-if="state === 'error'" type="error" title="内容暂时加载失败"
        description="可能是网络开小差了，稍后重试就好。" action="重新加载" @action="retry"
      />
      <view v-else class="feed-grid">
        <view class="feed-column">
          <CampusPostCard v-for="post in leftPosts" :key="post.id" :post="post" />
        </view>
        <view class="feed-column">
          <CampusPostCard v-for="post in rightPosts" :key="post.id" :post="post" />
        </view>
      </view>
      <view class="feed-end">
        已经逛到底啦 · 去发布点新鲜事吧
      </view>
    </scroll-view>

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
  background: var(--color-page);
}

.status-space {
  height: env(safe-area-inset-top);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 184rpx 0 var(--page-gutter);
}

/* #ifdef H5 */
.topbar {
  padding-right: var(--page-gutter);
}
/* #endif */

.school-trigger {
  display: flex;
  overflow: hidden;
  align-items: center;
  justify-content: flex-start;
  height: var(--touch-regular);
  color: var(--color-text);
  font-size: var(--font-size-title);
  font-weight: 800;
}

.school-trigger > text:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.school-arrow {
  flex: 0 0 auto;
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-meta);
}

.message-entry {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: var(--touch-regular);
  height: var(--touch-regular);
  border: 1rpx solid var(--color-border);
  border-radius: 50%;
  background: var(--color-glass-strong);
  box-shadow: 0 8rpx 24rpx rgba(45, 83, 126, 0.08);
  backdrop-filter: blur(20rpx) saturate(150%);
  -webkit-backdrop-filter: blur(20rpx) saturate(150%);
}

.topbar-icon {
  width: 36rpx;
  height: 36rpx;
}

.message-dot {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 12rpx;
  height: 12rpx;
  border: 3rpx solid var(--color-surface);
  border-radius: 50%;
  background: var(--color-accent);
}

.search-entry {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: auto;
  height: var(--touch-regular);
  margin: var(--space-1) var(--page-gutter) 0;
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  color: var(--color-text-tertiary);
  border: 1rpx solid rgba(16, 167, 121, 0.1);
  background: rgba(235, 244, 253, 0.76);
  backdrop-filter: blur(18rpx) saturate(145%);
  -webkit-backdrop-filter: blur(18rpx) saturate(145%);
  font-size: var(--font-size-label);
}

.search-icon {
  flex: 0 0 auto;
  width: 32rpx;
  height: 32rpx;
  margin-right: var(--space-3);
  opacity: 0.7;
}

.search-placeholder {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.channel-scroll {
  height: 76rpx;
  margin-top: var(--space-1);
  white-space: nowrap;
}

.channels {
  display: flex;
  height: 76rpx;
  gap: 36rpx;
  padding: 0 var(--page-gutter);
}

.channel {
  position: relative;
  flex: 0 0 auto;
  padding: 18rpx 0 14rpx;
  color: var(--color-text-secondary);
  font-size: var(--font-size-label);
}

.channel.active {
  color: var(--color-text);
  font-weight: 800;
}

.channel.active::after {
  position: absolute;
  left: 50%;
  bottom: 4rpx;
  width: 28rpx;
  height: 6rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  content: '';
  transform: translateX(-50%);
}

.content-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60rpx;
  padding: 0 var(--page-gutter);
}

.content-context {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.content-context text:first-child {
  color: var(--color-text);
  font-size: var(--font-size-label);
  font-weight: 750;
}

.content-context text:last-child {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

.refresh-entry {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-compact);
  color: var(--color-text-secondary);
  font-size: var(--font-size-caption);
}

.refresh-symbol {
  color: var(--color-primary);
  font-size: 26rpx;
  line-height: 1;
}

.refresh-entry.refreshing .refresh-symbol {
  animation: feed-refresh-spin 0.8s linear infinite;
}

@keyframes feed-refresh-spin {
  to {
    transform: rotate(360deg);
  }
}

.feed-scroll {
  height: calc(100vh - 312rpx - env(safe-area-inset-top));
}

.publish-inspire {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: auto;
  height: 104rpx;
  margin: 0 20rpx var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1rpx solid rgba(16, 167, 121, 0.16);
  border-radius: var(--radius-md);
  background: var(--color-glass-strong);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(22rpx) saturate(150%);
  -webkit-backdrop-filter: blur(22rpx) saturate(150%);
  line-height: 1.2;
}

.inspire-avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: var(--radius-sm);
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  font-size: 32rpx;
}

.inspire-copy {
  display: flex;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
  margin-left: var(--space-3);
  text-align: left;
}

.inspire-copy text:first-child {
  color: var(--color-text);
  font-size: var(--font-size-label);
  font-weight: 750;
}

.inspire-copy text:last-child {
  overflow: hidden;
  margin-top: 4rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspire-arrow {
  flex: 0 0 auto;
  color: var(--color-text-tertiary);
  font-size: 34rpx;
}

.feed-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  padding: 0 20rpx;
  align-items: start;
}

.feed-column {
  min-width: 0;
}

.feed-end {
  padding: var(--space-5) 0 48rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-meta);
  text-align: center;
}

.skeleton-wrap {
  opacity: 0.78;
}

.skeleton-card {
  overflow: hidden;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border: 1rpx solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.sk-cover {
  height: 232rpx;
  background: var(--color-page-deep);
  animation: pulse 1.2s infinite alternate;
}

.sk-line {
  width: 60%;
  height: 20rpx;
  margin: var(--space-2) var(--space-3) 0;
  border-radius: var(--radius-sm);
  background: var(--color-page-deep);
}

.sk-line.wide {
  width: 82%;
}

@keyframes pulse {
  to {
    opacity: 0.46;
  }
}

.campus-picker-mask {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: var(--color-mask);
}

.campus-picker {
  width: 100%;
  padding: var(--space-2) var(--page-gutter) calc(var(--space-5) + env(safe-area-inset-bottom));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-bottom: 0;
  background: rgba(242, 247, 252, 0.88);
  box-shadow: var(--shadow-floating);
  backdrop-filter: blur(34rpx) saturate(155%);
  -webkit-backdrop-filter: blur(34rpx) saturate(155%);
}

.picker-handle {
  width: 68rpx;
  height: 7rpx;
  margin: 0 auto var(--space-4);
  border-radius: var(--radius-pill);
  background: var(--color-border);
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
  padding: 0 4rpx;
}

.picker-head > view:first-child {
  display: flex;
  flex-direction: column;
}

.picker-head text:first-child {
  font-size: var(--font-size-title);
  font-weight: 800;
}

.picker-head text:last-child {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-meta);
}

.picker-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-regular);
  height: var(--touch-regular);
  border-radius: 50%;
  color: var(--color-text-secondary);
  background: var(--color-surface-subtle);
  font-size: 32rpx;
}

.campus-options {
  overflow: hidden;
  border: 1rpx solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.campus-option {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.campus-option:last-child {
  border-bottom: 0;
}

.campus-option.selected {
  background: var(--color-primary-soft);
}
.campus-option.disabled {
  opacity: 0.6;
}

.campus-option-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60rpx;
  height: 60rpx;
  border-radius: var(--radius-md);
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  font-size: var(--font-size-label);
  font-weight: 800;
}

.campus-option:nth-child(2) .campus-option-mark {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.campus-option-main {
  display: flex;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
  margin-left: var(--space-3);
}

.campus-option-main text:first-child {
  font-size: var(--font-size-body);
  font-weight: 750;
}

.campus-option-main text:last-child {
  margin-top: 4rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

.campus-check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  color: var(--color-text-tertiary);
  font-size: 30rpx;
}

.campus-option.selected .campus-check {
  color: var(--color-primary-strong);
  font-size: var(--font-size-label);
  font-weight: 800;
}

.picker-tip {
  margin-top: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
  text-align: center;
}
</style>
