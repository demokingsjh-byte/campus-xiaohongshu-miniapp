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
      await userStore.updateProfile({
        nickname: userStore.userInfo?.nickname,
        avatar: userStore.userInfo?.avatar,
        schoolName: campus.name,
        campusName: campus.name === '吉首大学' ? '吉首校区' : '主校区',
        grade: userStore.userInfo?.grade,
        gender: userStore.userInfo?.gender,
        roleType: userStore.userInfo?.roleType || 'student',
      });
    }
    activeChannel.value = '推荐';
    tenantStore.selectTenant(campus);
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
      <view class="school-trigger" @click="openCampusPicker">
        <text>{{ tenantStore.tenantName || '选择学校' }}</text>
        <text class="school-arrow">
          ▾
        </text>
      </view>
      <view class="message-entry" @click="goMessages">
        <view class="bell-glyph" />
        <view class="message-dot" />
      </view>
    </view>

    <view class="search-entry" @click="goSearch">
      <view class="search-glyph search-small" /><text class="search-placeholder">
        搜二手、拼车、活动或同学
      </text>
    </view>

    <scroll-view class="channel-scroll" scroll-x :show-scrollbar="false">
      <view class="channels">
        <view v-for="channel in campusChannels" :key="channel" class="channel" :class="{ active: activeChannel === channel }" @click="chooseChannel(channel)">
          {{ channel }}
        </view>
      </view>
    </scroll-view>

    <view class="content-meta">
      <view class="content-context">
        <text>{{ activeChannel === '推荐' ? '最新动态' : activeChannel }}</text>
        <text>{{ tenantStore.tenantName }} · {{ visiblePosts.length }} 条</text>
      </view>
      <view class="refresh-entry" :class="{ refreshing }" @click="onRefresh">
        <text class="refresh-symbol">
          ↻
        </text>
        <text>{{ refreshing ? '刷新中' : '刷新' }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="feed-scroll" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view class="publish-inspire" @click="goPublish">
        <view class="inspire-avatar">
          ＋
        </view>
        <view class="inspire-copy">
          <text>分享一件校园新鲜事</text>
          <text>二手、互助、活动都可以发布</text>
        </view>
        <text class="inspire-arrow">
          ›
        </text>
      </view>
      <view v-if="state === 'loading'" class="feed-grid skeleton-wrap">
        <view v-for="n in 6" :key="n" class="skeleton-card">
          <view class="sk-cover" /><view class="sk-line wide" /><view class="sk-line" />
        </view>
      </view>
      <StatePanel
        v-else-if="state === 'empty'" title="这里还没有新内容"
        description="做第一个分享校园生活的人吧，真实内容会优先推荐给同校同学。" action="去发布"
        @action="uni.switchTab({ url: '/pages/publish/index' })"
      />
      <StatePanel v-else-if="state === 'error'" type="error" title="内容暂时加载失败" description="可能是网络开小差了，稍后重试就好。" action="重新加载" @action="retry" />
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
          <view class="picker-close" @click="showCampusPicker = false">
            ×
          </view>
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
  background: var(--yd-paper);
}
.search-glyph {
  position: relative;
  width: 24rpx;
  height: 24rpx;
  border: 3rpx solid #3a3a3c;
  border-radius: 50%;
}
.search-glyph::after {
  position: absolute;
  right: -9rpx;
  bottom: -6rpx;
  width: 11rpx;
  height: 3rpx;
  border-radius: 9rpx;
  background: #3a3a3c;
  content: '';
  transform: rotate(45deg);
}
.bell-glyph {
  position: relative;
  width: 26rpx;
  height: 28rpx;
  border: 3rpx solid #3a3a3c;
  border-top-left-radius: 16rpx;
  border-top-right-radius: 16rpx;
  border-bottom: 0;
}
.bell-glyph::before {
  position: absolute;
  left: -7rpx;
  bottom: -5rpx;
  width: 34rpx;
  height: 3rpx;
  border-radius: 9rpx;
  background: #3a3a3c;
  content: '';
}
.bell-glyph::after {
  position: absolute;
  left: 8rpx;
  bottom: -11rpx;
  width: 8rpx;
  height: 5rpx;
  border-radius: 0 0 8rpx 8rpx;
  background: #3a3a3c;
  content: '';
}
.search-entry {
  display: flex;
  align-items: center;
  height: 70rpx;
  margin: 0 24rpx;
  padding: 0 19rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 15rpx;
  color: #8b9591;
  background: var(--yd-card);
  box-shadow: 0 4rpx 0 rgba(75, 59, 44, 0.035);
  font-size: 24rpx;
}
.search-small {
  flex: 0 0 auto;
  width: 20rpx;
  height: 20rpx;
  margin-right: 15rpx;
  border-width: 3rpx;
  border-color: #71807b;
}
.search-small::after {
  background: #71807b;
}
.search-placeholder {
  flex: 1;
}
.channel-scroll {
  height: 72rpx;
  margin-top: 17rpx;
  white-space: nowrap;
}
.channels {
  display: flex;
  height: 72rpx;
  gap: 30rpx;
  padding: 0 24rpx 10rpx;
}
.channel {
  flex: 0 0 auto;
  position: relative;
  padding: 11rpx 0 15rpx;
  color: #5f6b67;
  background: transparent;
  font-size: 25rpx;
}
.channel.active {
  color: var(--yd-ink);
  background: transparent;
  font-weight: 700;
}
.channel.active::after {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 28rpx;
  height: 6rpx;
  border-radius: 9rpx;
  background: var(--yd-green);
  content: '';
  transform: translateX(-50%);
}
.refresh-entry {
  display: flex;
  align-items: center;
  gap: 5rpx;
  min-height: 44rpx;
  padding: 0 13rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.1);
  border-radius: 999rpx;
  color: var(--yd-green-dark);
  background: rgba(255, 255, 255, 0.66);
  font-size: 20rpx;
  font-weight: 700;
}
.refresh-symbol {
  font-size: 25rpx;
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
  height: calc(100vh - 432rpx - env(safe-area-inset-top));
}
.feed-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
  padding: 0 16rpx;
  align-items: start;
}
.publish-inspire {
  display: flex;
  align-items: center;
  gap: 13rpx;
  margin: 0 16rpx 16rpx;
  padding: 17rpx 18rpx;
  border: 1rpx solid #efc1b6;
  border-radius: 16rpx;
  background: var(--yd-coral-soft);
  box-shadow: 5rpx 6rpx 0 rgba(116, 55, 43, 0.055);
}
.inspire-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58rpx;
  height: 58rpx;
  border-radius: 14rpx 14rpx 14rpx 4rpx;
  color: #fff;
  background: var(--yd-coral);
  font-size: 34rpx;
  font-weight: 500;
}
.inspire-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.inspire-copy text:first-child {
  color: #24312d;
  font-size: 23rpx;
  font-weight: 800;
}
.inspire-copy text:last-child {
  margin-top: 4rpx;
  color: #71807a;
  font-size: 18rpx;
}
.feed-column {
  min-width: 0;
}
.feed-end {
  padding: 24rpx 0 44rpx;
  color: #a0a9a5;
  font-size: 22rpx;
  text-align: center;
}
.skeleton-wrap {
  opacity: 0.75;
}
.skeleton-card {
  overflow: hidden;
  margin-bottom: 18rpx;
  padding-bottom: 20rpx;
  border-radius: 22rpx;
  background: #fff;
}
.sk-cover {
  height: 240rpx;
  background: #ebe9e3;
  animation: pulse 1.2s infinite alternate;
}
.sk-line {
  width: 60%;
  height: 22rpx;
  margin: 14rpx 16rpx 0;
  border-radius: 8rpx;
  background: #ebe9e3;
}
.sk-line.wide {
  width: 82%;
}
@keyframes pulse {
  to {
    opacity: 0.45;
  }
}
.campus-picker-mask {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(19, 24, 31, 0.28);
  backdrop-filter: blur(8rpx);
  -webkit-backdrop-filter: blur(8rpx);
}
.campus-picker {
  width: 100%;
  padding: 12rpx 28rpx calc(34rpx + env(safe-area-inset-bottom));
  border: 1rpx solid rgba(255, 255, 255, 0.76);
  border-bottom: 0;
  border-radius: 36rpx 36rpx 0 0;
  background: rgba(248, 249, 252, 0.94);
  box-shadow: 0 -24rpx 70rpx rgba(24, 31, 43, 0.16);
  backdrop-filter: blur(38rpx) saturate(165%);
  -webkit-backdrop-filter: blur(38rpx) saturate(165%);
  box-sizing: border-box;
}
.picker-handle {
  width: 72rpx;
  height: 8rpx;
  margin: 0 auto 26rpx;
  border-radius: 99rpx;
  background: rgba(60, 60, 67, 0.18);
}
.picker-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 25rpx;
}
.picker-head > view:first-child {
  display: flex;
  flex-direction: column;
}
.picker-head text:first-child {
  color: #1d1d1f;
  font-size: 32rpx;
  font-weight: 800;
}
.picker-head text:last-child {
  margin-top: 8rpx;
  color: #7b7c82;
  font-size: 21rpx;
}
.picker-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  color: #63646a;
  background: rgba(118, 118, 128, 0.1);
  font-size: 34rpx;
  line-height: 1;
}
.campus-options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.campus-option {
  display: flex;
  align-items: center;
  min-height: 118rpx;
  padding: 18rpx 20rpx;
  border: 2rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 12rpx 30rpx rgba(28, 39, 61, 0.05);
  box-sizing: border-box;
}
.campus-option.selected {
  border-color: rgba(10, 132, 255, 0.42);
  background: rgba(236, 246, 255, 0.9);
  box-shadow: 0 14rpx 34rpx rgba(10, 132, 255, 0.1);
}
.campus-option.disabled {
  opacity: 0.64;
}
.campus-option-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 21rpx;
  color: #fff;
  background: linear-gradient(145deg, #70b7ff, #0a84ff);
  box-shadow: 0 10rpx 24rpx rgba(10, 132, 255, 0.2);
  font-size: 27rpx;
  font-weight: 800;
}
.campus-option:nth-child(2) .campus-option-mark {
  background: linear-gradient(145deg, #ffad8f, #ff6b5f);
  box-shadow: 0 10rpx 24rpx rgba(255, 107, 95, 0.18);
}
.campus-option-main {
  display: flex;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
  margin-left: 18rpx;
}
.campus-option-main text:first-child {
  color: #26272a;
  font-size: 27rpx;
  font-weight: 800;
}
.campus-option-main text:last-child {
  overflow: hidden;
  margin-top: 6rpx;
  color: #85868c;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.campus-check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 45rpx;
  height: 45rpx;
  margin-left: 12rpx;
  border-radius: 50%;
  color: #8e8e93;
  background: rgba(118, 118, 128, 0.08);
  font-size: 30rpx;
  font-weight: 700;
}
.campus-option.selected .campus-check {
  color: #fff;
  background: var(--yd-green);
  font-size: 22rpx;
}
.picker-tip {
  margin-top: 22rpx;
  color: #929398;
  font-size: 20rpx;
  text-align: center;
}

/* Home layout v2: school-first, compact and content-focused */
.home-page {
  background: #f6f7f9;
}
.status-space {
  height: env(safe-area-inset-top);
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 84rpx;
  gap: 18rpx;
  padding: 0 190rpx 0 24rpx;
}
.school-trigger {
  display: flex;
  overflow: hidden;
  align-items: center;
  min-width: 0;
  height: 72rpx;
  color: #1d1d1f;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1;
}
.school-trigger > text:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.school-arrow {
  flex: 0 0 auto;
  margin-left: 10rpx;
  color: #8e8e93;
  font-size: 22rpx;
  font-weight: 600;
}
.message-entry {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.1);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.88);
}
.message-entry .bell-glyph {
  width: 22rpx;
  height: 24rpx;
  border-width: 3rpx;
}
.message-entry .bell-glyph::before {
  left: -6rpx;
  width: 29rpx;
}
.message-entry .bell-glyph::after {
  left: 6rpx;
}
.message-dot {
  position: absolute;
  top: 9rpx;
  right: 8rpx;
  width: 12rpx;
  height: 12rpx;
  border: 3rpx solid #fff;
  border-radius: 50%;
  background: #ff5b57;
}
.search-entry {
  height: 84rpx;
  margin: 4rpx 24rpx 0;
  padding: 0 20rpx;
  border: 0;
  border-radius: 18rpx;
  color: #8e8e93;
  background: #e9ecf1;
  box-shadow: none;
  font-size: 24rpx;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.search-small {
  width: 19rpx;
  height: 19rpx;
  margin-right: 14rpx;
  border-color: #7d7e84;
}
.search-small::after {
  background: #7d7e84;
}
.channel-scroll {
  height: 76rpx;
  margin-top: 8rpx;
}
.channels {
  height: 76rpx;
  gap: 36rpx;
  padding: 0 24rpx;
}
.channel {
  padding: 18rpx 0 15rpx;
  color: #7b7c82;
  font-size: 25rpx;
}
.channel.active {
  color: #1d1d1f;
  font-weight: 800;
}
.channel.active::after {
  bottom: 5rpx;
  width: 24rpx;
  height: 5rpx;
  background: #0a84ff;
}
.content-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 66rpx;
  padding: 0 24rpx;
}
.content-context {
  display: flex;
  align-items: baseline;
  min-width: 0;
}
.content-context text:first-child {
  color: #2c2c2e;
  font-size: 25rpx;
  font-weight: 750;
}
.content-context text:last-child {
  overflow: hidden;
  margin-left: 12rpx;
  color: #9a9ba1;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.refresh-entry {
  min-height: 60rpx;
  padding: 0 10rpx 0 16rpx;
  border: 0;
  color: #6e6e73;
  background: transparent;
  font-size: 20rpx;
  font-weight: 600;
}
.refresh-symbol {
  color: #0a84ff;
  font-size: 25rpx;
}
.feed-scroll {
  height: calc(100vh - 318rpx - env(safe-area-inset-top));
}
.feed-grid {
  gap: 12rpx;
  padding: 0 20rpx;
}
.publish-inspire {
  gap: 14rpx;
  margin: 0 20rpx 16rpx;
  padding: 17rpx 18rpx;
  border: 1rpx solid rgba(10, 132, 255, 0.12);
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8rpx 24rpx rgba(31, 43, 65, 0.045);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.inspire-avatar {
  width: 54rpx;
  height: 54rpx;
  border-radius: 16rpx;
  color: #0a84ff;
  background: rgba(10, 132, 255, 0.1);
  font-size: 34rpx;
  font-weight: 500;
}
.inspire-copy text:first-child {
  color: #2c2c2e;
  font-size: 23rpx;
  font-weight: 750;
}
.inspire-copy text:last-child {
  margin-top: 5rpx;
  color: #929399;
  font-size: 19rpx;
}
.inspire-arrow {
  flex: 0 0 auto;
  color: #b0b1b7;
  font-size: 34rpx;
}
.campus-picker-mask {
  background: rgba(18, 20, 25, 0.24);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.campus-picker {
  padding: 12rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
  border: 0;
  border-radius: 30rpx 30rpx 0 0;
  background: #f5f6f8;
  box-shadow: 0 -18rpx 48rpx rgba(20, 24, 33, 0.12);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.picker-handle {
  width: 68rpx;
  height: 7rpx;
  margin-bottom: 22rpx;
}
.picker-head {
  align-items: center;
  margin-bottom: 20rpx;
  padding: 0 4rpx;
}
.picker-head text:first-child {
  font-size: 30rpx;
  font-weight: 800;
}
.picker-head text:last-child {
  margin-top: 6rpx;
  font-size: 20rpx;
}
.picker-close {
  width: 50rpx;
  height: 50rpx;
  font-size: 30rpx;
}
.campus-options {
  overflow: hidden;
  gap: 0;
  border: 1rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 22rpx;
  background: #fff;
}
.campus-option {
  min-height: 96rpx;
  padding: 14rpx 18rpx;
  border: 0;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 0;
  background: #fff;
  box-shadow: none;
}
.campus-option:last-child {
  border-bottom: 0;
}
.campus-option.selected {
  border-color: rgba(60, 60, 67, 0.08);
  background: rgba(10, 132, 255, 0.055);
  box-shadow: none;
}
.campus-option-mark,
.campus-option:nth-child(2) .campus-option-mark {
  width: 58rpx;
  height: 58rpx;
  border-radius: 17rpx;
  color: #0a84ff;
  background: rgba(10, 132, 255, 0.1);
  box-shadow: none;
  font-size: 23rpx;
}
.campus-option-main {
  margin-left: 16rpx;
}
.campus-option-main text:first-child {
  font-size: 26rpx;
  font-weight: 750;
}
.campus-option-main text:last-child {
  margin-top: 4rpx;
  color: #9a9ba1;
  font-size: 19rpx;
}
.campus-check {
  width: 40rpx;
  height: 40rpx;
  background: transparent;
  font-size: 28rpx;
}
.campus-option.selected .campus-check {
  color: #0a84ff;
  background: transparent;
  font-size: 24rpx;
}
.picker-tip {
  margin-top: 18rpx;
  font-size: 19rpx;
}
</style>
