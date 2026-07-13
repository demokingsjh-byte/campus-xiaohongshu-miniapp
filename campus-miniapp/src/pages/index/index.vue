<script lang="ts" setup>
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusChannels, campusTenants, getDefaultTenant } from '@/mock/campus';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';

const activeChannel = ref('推荐');
const state = ref<'content' | 'loading' | 'empty' | 'error'>('loading');
const refreshing = ref(false);
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
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
function switchCampus() {
  uni.switchTab({ url: '/pages/demo/index' });
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
  await loadFeed();
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
      <view class="brand" @click="switchCampus">
        <view class="brand-mark">
          <text>云</text><i />
        </view>
        <view>
          <view class="brand-name">
            逛校园
          </view>
          <view class="campus-line">
            {{ tenantStore.tenantName || '全部校园' }} <text>⌄</text>
          </view>
        </view>
      </view>
    </view>

    <view class="search-entry" @click="goSearch">
      <view class="search-glyph search-small" /><text class="search-placeholder">
        搜二手、拼车、活动或同学
      </text><text class="hot-word" @click.stop="goMessages">
        消息 3
      </text>
    </view>

    <scroll-view class="channel-scroll" scroll-x :show-scrollbar="false">
      <view class="channels">
        <view v-for="channel in campusChannels" :key="channel" class="channel" :class="{ active: activeChannel === channel }" @click="chooseChannel(channel)">
          {{ channel }}
        </view>
      </view>
    </scroll-view>

    <view class="trend-card" @click="chooseChannel('二手')">
      <text class="trend-badge">
        校园热榜
      </text>
      <text class="trend-text">
        毕业季闲置交换周
      </text>
      <text class="trend-count">
        328 人在看
      </text>
      <text class="trend-arrow">
        ›
      </text>
    </view>

    <view class="campus-note">
      <view><text class="note-dot" />只看本校真实内容</view>
      <text class="note-side">
        当前校园 {{ visiblePosts.length }} 条
      </text>
    </view>

    <scroll-view scroll-y class="feed-scroll" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view class="publish-inspire" @click="goPublish">
        <view class="inspire-avatar">
          ＋
        </view>
        <view class="inspire-copy">
          <text>今天校园里有什么新鲜事？</text><text>一张实拍，也能帮到同校同学</text>
        </view>
        <button>去分享</button>
      </view>
      <view v-if="state === 'loading'" class="feed-grid skeleton-wrap">
        <view v-for="n in 6" :key="n" class="skeleton-card">
          <view class="sk-cover" /><view class="sk-line wide" /><view class="sk-line" />
        </view>
      </view>
      <StatePanel
        v-else-if="state === 'empty'" title="这个分区还很安静"
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
  </view>
</template>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  background: var(--yd-paper);
}
.status-space {
  height: calc(78rpx + env(safe-area-inset-top));
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3rpx 184rpx 15rpx 24rpx;
}
.brand {
  display: flex;
  align-items: center;
  gap: 13rpx;
}
.brand-mark {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  border-radius: 16rpx 16rpx 16rpx 5rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 5rpx 6rpx 0 #c7ded5;
  font-size: 29rpx;
  font-weight: 900;
}
.brand-mark i {
  position: absolute;
  right: -3rpx;
  bottom: -3rpx;
  width: 15rpx;
  height: 15rpx;
  border: 4rpx solid var(--yd-paper);
  border-radius: 50%;
  background: var(--yd-coral);
}
.brand-name {
  color: var(--yd-ink);
  font-size: 35rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
}
.campus-line {
  margin-top: 1rpx;
  color: var(--yd-muted);
  font-size: 20rpx;
}
.campus-line text {
  color: var(--yd-green);
}
.top-actions {
  display: none;
}
.icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  border: 1rpx solid #e8e5de;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(31, 56, 49, 0.04);
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
.dot {
  position: absolute;
  top: 7rpx;
  right: 7rpx;
  width: 14rpx;
  height: 14rpx;
  border: 3rpx solid #fff;
  border-radius: 50%;
  background: var(--yd-coral);
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
.hot-word {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  color: var(--yd-coral);
  background: var(--yd-coral-soft);
  font-size: 19rpx;
  font-weight: 600;
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
.trend-card {
  display: flex;
  align-items: center;
  height: 66rpx;
  margin: 2rpx 20rpx 0;
  padding: 0 16rpx;
  border: 1rpx dashed #d7b863;
  border-radius: 12rpx;
  background: #fff3c9;
  box-shadow: 5rpx 6rpx 0 rgba(116, 89, 27, 0.06);
}
.trend-badge {
  flex: 0 0 auto;
  padding: 6rpx 10rpx;
  border-radius: 6rpx;
  color: var(--yd-ink);
  background: var(--yd-yellow);
  font-size: 18rpx;
  font-weight: 700;
}
.trend-text {
  flex: 1;
  margin-left: 13rpx;
  color: var(--yd-ink);
  font-size: 23rpx;
  font-weight: 600;
}
.trend-count {
  color: #9aa39f;
  font-size: 18rpx;
}
.trend-arrow {
  margin-left: 8rpx;
  color: #89938f;
  font-size: 30rpx;
}
.campus-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 26rpx 13rpx;
  color: #66736e;
  font-size: 21rpx;
}
.note-dot {
  display: inline-block;
  width: 12rpx;
  height: 12rpx;
  margin-right: 8rpx;
  border-radius: 50%;
  background: var(--yd-green);
}
.note-side {
  color: #9aa39f;
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
.publish-inspire button {
  flex: 0 0 auto;
  min-width: 112rpx;
  height: var(--yd-control-compact);
  padding: 0 20rpx;
  border-radius: 999rpx;
  color: #fff;
  background: var(--yd-green-dark);
  font-size: 22rpx;
  font-weight: 800;
  line-height: 1;
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

/* Apple-inspired glass theme */
.brand-mark {
  background: var(--yd-green);
  box-shadow: 0 12rpx 28rpx rgba(10, 132, 255, 0.2);
}
.icon-btn,
.search-entry,
.trend-card,
.campus-note,
.publish-inspire {
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 16rpx 38rpx rgba(33, 50, 86, 0.08);
  backdrop-filter: blur(28rpx) saturate(155%);
  -webkit-backdrop-filter: blur(28rpx) saturate(155%);
}
.search-glyph,
.bell-glyph {
  border-color: #3a3a3c;
}
.trend-card,
.campus-note,
.publish-inspire {
  border-radius: 24rpx;
}
.publish-inspire button {
  background: var(--yd-green);
  box-shadow: 0 10rpx 24rpx rgba(10, 132, 255, 0.22);
}
.skeleton-card,
.sk-cover,
.sk-line {
  background: rgba(118, 118, 128, 0.1);
}
</style>
