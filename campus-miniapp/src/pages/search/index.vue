<script lang="ts" setup>
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';

const keyword = ref('');
const searched = ref(false);
const onlyMine = ref(false);
const favoritesMode = ref(false);
const modeTitle = computed(() => onlyMine.value ? '我发布的' : (favoritesMode.value ? '我的收藏' : ''));
const activeTab = ref('全部');
const activeFilter = ref('综合');
const cachedRecent = uni.getStorageSync('campus-search-recent');
const recent = ref<string[]>(Array.isArray(cachedRecent) ? cachedRecent : ['折叠桌', '高铁站拼车', '计算器']);
const hot = ['毕业闲置', '校园卡', '周末活动', '校门口美食', '四六级', '找搭子'];
const tabs = ['全部', '二手', '互助', '活动', '用户'];
const tabChannels: Record<string, string[]> = { 二手: ['二手'], 互助: ['互助'], 活动: ['社团'] };
const contentStore = useCampusContentStore();
const tenantStore = useTenantStore();
const results = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  const source = onlyMine.value
    ? contentStore.publishedPosts
    : (favoritesMode.value ? contentStore.favoritePosts : contentStore.allPosts);
  if (!query)
    return onlyMine.value || favoritesMode.value ? source : [];

  const matched = source.filter((item) => {
    const content = [item.title, item.content, item.author, item.school, item.channel, ...item.tags].join(' ').toLowerCase();
    if (!content.includes(query))
      return false;
    if (activeTab.value === '用户')
      return `${item.author} ${item.school}`.toLowerCase().includes(query);
    const channels = tabChannels[activeTab.value];
    return !channels || channels.includes(item.channel);
  });

  if (activeFilter.value === '价格')
    return [...matched].sort((a, b) => Number.parseFloat(a.price || '0') - Number.parseFloat(b.price || '0'));
  if (activeFilter.value === '最新')
    return [...matched].sort((a, b) => b.id - a.id);
  return matched;
});
async function search(value?: string) {
  if (value)
    keyword.value = value;
  keyword.value = keyword.value.trim();
  if (!keyword.value) {
    uni.showToast({ title: '请输入搜索关键词', icon: 'none' });
    return;
  }
  searched.value = true;
  if (!onlyMine.value && !favoritesMode.value) {
    try {
      await contentStore.loadPosts({
        tenantId: tenantStore.tenantId || undefined,
        keyword: keyword.value,
      });
    } catch {
      uni.showToast({ title: '搜索失败，请检查网络', icon: 'none' });
    }
  }
  if (!recent.value.includes(keyword.value)) {
    recent.value.unshift(keyword.value);
    recent.value = recent.value.slice(0, 8);
    uni.setStorageSync('campus-search-recent', recent.value);
  }
}
function clear() {
  keyword.value = '';
  searched.value = false;
}
function clearRecent() {
  recent.value = [];
  uni.removeStorageSync('campus-search-recent');
}
onLoad(async (query) => {
  onlyMine.value = query?.mine === '1';
  favoritesMode.value = query?.favorites === '1';
  if (onlyMine.value) {
    searched.value = true;
    try {
      await contentStore.loadMyPosts();
    } catch {
      uni.showToast({ title: '我的发布加载失败', icon: 'none' });
    }
  } else if (favoritesMode.value) {
    searched.value = true;
    try {
      await contentStore.loadFavorites();
    } catch {
      uni.showToast({ title: '收藏加载失败', icon: 'none' });
    }
  }
  if (query?.keyword) {
    keyword.value = decodeURIComponent(query.keyword);
    searched.value = true;
    if (!onlyMine.value && !favoritesMode.value)
      await search();
  }
});
</script>

<template>
  <view class="search-page">
    <view class="search-status" />
    <view v-if="modeTitle" class="prototype-subpage-nav">
      <view class="back" @click="uni.navigateBack()">
        <image src="/static/icons/ui/back.svg" mode="aspectFit" />
      </view>
      <text>{{ modeTitle }}</text>
    </view>
    <view v-if="!onlyMine" class="search-top" :class="{ 'mode-search-top': modeTitle }">
      <view v-if="!modeTitle" class="back" @click="uni.navigateBack()">
        <image src="/static/icons/ui/back.svg" mode="aspectFit" />
      </view><view class="search-input">
        <image class="search-icon" src="/static/icons/ui/search.svg" mode="aspectFit" />
        <input v-model="keyword" :autofocus="!modeTitle" :placeholder="modeTitle ? `搜索${modeTitle}内容` : '搜校园内容和同学'" confirm-type="search" @confirm="search()">
        <view v-if="keyword" class="clear" @click="clear">
          <image src="/static/icons/ui/close.svg" mode="aspectFit" />
        </view>
      </view><text v-if="!modeTitle" class="search-text" @click="search()">
        搜索
      </text>
    </view>

    <view v-if="!searched" class="discover">
      <view class="discover-section">
        <view class="discover-head">
          <b>最近搜索</b><text @click="clearRecent">
            清空
          </text>
        </view><view v-if="recent.length" class="chip-list">
          <text v-for="item in recent" :key="item" @click="search(item)">
            {{ item }}
          </text>
        </view><view v-else class="recent-empty">
          暂无搜索记录
        </view>
      </view>
      <view class="discover-section">
        <view class="discover-head">
          <b>校园热搜</b><text>实时更新</text>
        </view><view class="hot-list">
          <view v-for="(item, index) in hot" :key="item" @click="search(item)">
            <text class="rank" :class="{ top: index < 3 }">
              {{ index + 1 }}
            </text><span>{{ item }}</span><i v-if="index < 2">热</i>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="results">
      <template v-if="onlyMine">
        <view class="prototype-manage-tabs">
          <text class="active">
            在卖 {{ results.length }}
          </text>
          <text>草稿(0)</text>
          <text>已下架(0)</text>
        </view>
        <StatePanel
          v-if="!results.length" title="还没有发布内容"
          description="完成第一次分享后，可以在这里管理自己发布的内容。"
          action="去发布" @action="uni.reLaunch({ url: '/pages/publish/index' })"
        />
        <view v-else class="prototype-manage-list">
          <view
            v-for="post in results" :key="post.id" class="prototype-manage-card"
            @click="uni.navigateTo({ url: `/pages/detail/index?id=${post.id}` })"
          >
            <view class="prototype-manage-main">
              <image v-if="post.coverImage || post.images?.[0]" :src="post.coverImage || post.images?.[0]" mode="aspectFill" />
              <view v-else class="prototype-manage-placeholder">
                {{ post.coverEmoji || '📦' }}
              </view>
              <view class="prototype-manage-copy">
                <text>{{ post.title }}</text>
                <text>浏览 {{ post.views || 0 }}</text>
                <view><text>{{ post.time }}</text><text><i>¥</i>{{ post.price || '面议' }}</text></view>
              </view>
            </view>
            <view class="prototype-manage-footer">
              <text>更多</text><text>编辑</text>
            </view>
          </view>
        </view>
      </template>
      <template v-else>
        <scroll-view scroll-x class="tab-scroll">
          <view class="tabs">
            <text v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">
              {{ tab }}
            </text>
          </view>
        </scroll-view>
        <view class="filters">
          <text v-for="filter in ['综合', '最新', '附近', '价格']" :key="filter" :class="{ active: activeFilter === filter }" @click="activeFilter = filter">
            {{ filter }}<i v-if="filter === '价格'">↕</i>
          </text>
        </view>
        <StatePanel
          v-if="!results.length" :title="onlyMine ? '还没有发布内容' : (favoritesMode ? '还没有收藏内容' : '没有找到相关内容')"
          :description="onlyMine ? '完成第一次分享后，可以在这里管理自己发布的内容。' : (favoritesMode ? '在内容详情点击收藏后，会同步保存在这里。' : `换个关键词试试，或者去发布「${keyword}」相关内容。`)"
          action="去发布" @action="uni.reLaunch({ url: '/pages/publish/index' })"
        />
        <template v-else>
          <view class="result-count">
            {{ onlyMine ? `我的发布共 ${results.length} 条` : (favoritesMode ? `我的收藏共 ${results.length} 条` : `找到 ${results.length} 条与“${keyword}”相关的内容`) }}
          </view><view class="result-grid">
            <view class="column">
              <CampusPostCard v-for="post in results.filter((_, i) => i % 2 === 0)" :key="post.id" :post="post" />
            </view><view class="column">
              <CampusPostCard v-for="post in results.filter((_, i) => i % 2 === 1)" :key="post.id" :post="post" />
            </view>
          </view>
        </template>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.search-page {
  min-height: 100vh;
  background: var(--yd-paper);
}
.search-status {
  height: calc(28rpx + env(safe-area-inset-top));
}
.search-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 10rpx 20rpx 18rpx;
}
.back {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 72rpx;
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
}
.back:active,
.search-text:active {
  background: rgba(118, 118, 128, 0.08);
}
.back image {
  width: 40rpx;
  height: 40rpx;
}
.search-input {
  display: flex;
  flex: 1;
  align-items: center;
  height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 15rpx;
  background: var(--yd-card);
  color: var(--yd-green);
}
.search-input input {
  flex: 1;
  margin-left: 14rpx;
  font-size: 24rpx;
}
.search-icon {
  flex: 0 0 auto;
  width: 32rpx;
  height: 32rpx;
}
.clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  margin-left: 10rpx;
  border-radius: 50%;
}
.clear image {
  width: 34rpx;
  height: 34rpx;
}
.search-text {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 72rpx;
  height: 72rpx;
  color: var(--yd-green-dark);
  font-size: 24rpx;
  font-weight: 700;
}
.discover {
  padding: 10rpx 24rpx;
}
.discover-section {
  margin-top: 28rpx;
  padding: 26rpx 24rpx;
}
.discover-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.discover-head b {
  font-size: 30rpx;
}
.discover-head text {
  color: var(--color-text-tertiary);
  font-size: 21rpx;
}
.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 18rpx;
}
.chip-list text {
  min-height: 64rpx;
  padding: 15rpx 24rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 10rpx;
  color: #65706c;
  background: var(--yd-card);
  font-size: 22rpx;
}
.recent-empty {
  margin-top: 18rpx;
  color: #a0a8a4;
  font-size: 22rpx;
}
.hot-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4rpx 28rpx;
  margin-top: 14rpx;
}
.hot-list > view {
  display: flex;
  align-items: center;
  min-height: 80rpx;
  font-size: 24rpx;
}
.rank {
  width: 36rpx;
  color: #9ba39f;
  font-weight: 800;
}
.rank.top {
  color: var(--yd-coral);
}
.hot-list span {
  flex: 1;
}
.hot-list i {
  padding: 3rpx 7rpx;
  border-radius: 7rpx;
  color: var(--yd-coral);
  background: var(--yd-coral-soft);
  font-size: 17rpx;
  font-style: normal;
}
.tab-scroll {
  border-bottom: 1rpx solid var(--yd-line);
  white-space: nowrap;
}
.tabs {
  display: flex;
  gap: 44rpx;
  padding: 0 24rpx;
}
.tabs text {
  position: relative;
  padding: 18rpx 0;
  color: #707b77;
  font-size: 25rpx;
}
.tabs .active {
  color: var(--yd-ink);
  font-weight: 800;
}
.tabs .active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 34rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: var(--yd-green);
  content: '';
  transform: translateX(-50%);
}
.filters {
  display: flex;
  gap: 36rpx;
  padding: 20rpx 24rpx;
  color: #7d8883;
  font-size: 22rpx;
}
.filters .active {
  color: var(--yd-green-dark);
  font-weight: 700;
}
.filters i {
  font-style: normal;
}
.result-count {
  padding: 0 24rpx 16rpx;
  color: var(--color-text-tertiary);
  font-size: 20rpx;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
  padding: 0 20rpx 30rpx;
}

/* Emerald glass theme */
.search-input,
.discover-section,
.filters {
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 16rpx 42rpx rgba(20, 91, 70, 0.1);
  backdrop-filter: blur(28rpx) saturate(155%);
  -webkit-backdrop-filter: blur(28rpx) saturate(155%);
}
.chip-list text,
.filters > view {
  border-color: rgba(60, 60, 67, 0.1);
  background: rgba(118, 118, 128, 0.08);
}
.tabs .active::after {
  background: var(--yd-green);
}

/* 蓝湖原型：搜索、我的发布与收藏 */
.search-page {
  min-height: 100vh;
  color: #202321;
  background: #f4f4f4;
}

.search-status {
  height: calc(28rpx + env(safe-area-inset-top));
  background: #edfbf0;
}

.prototype-subpage-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 112rpx;
  background: #edfbf0;
}

.prototype-subpage-nav .back {
  position: absolute;
  left: 26rpx;
  top: 20rpx;
}

.prototype-subpage-nav > text {
  font-size: 34rpx;
  font-weight: 600;
}

.search-top {
  gap: 8rpx;
  padding: 12rpx 24rpx 20rpx;
  background: #edfbf0;
}

.search-top.mode-search-top {
  padding: 14rpx 32rpx 28rpx;
}

.back {
  flex-basis: 64rpx;
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
}

.back image {
  width: 34rpx;
  height: 34rpx;
}

.search-input {
  height: 72rpx;
  padding: 0 22rpx;
  border: 0;
  border-radius: 36rpx;
  color: #929693;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: none;
  backdrop-filter: none;
}

.mode-search-top .search-input {
  height: 72rpx;
  background: #fff;
}

.search-input input {
  margin-left: 10rpx;
  color: #343735;
  font-size: 25rpx;
}

.search-icon {
  width: 38rpx;
  height: 38rpx;
  opacity: 0.62;
}

.search-text {
  min-width: 68rpx;
  height: 72rpx;
  color: #1e211f;
  font-size: 25rpx;
  font-weight: 550;
}

.discover {
  padding: 8rpx 24rpx 40rpx;
}

.discover-section {
  margin-top: 26rpx;
  padding: 28rpx 24rpx;
  border: 0;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
}

.discover-head b {
  color: #202321;
  font-size: 30rpx;
  font-weight: 600;
}

.chip-list text {
  min-height: 58rpx;
  padding: 12rpx 22rpx;
  border: 0;
  border-radius: 29rpx;
  color: #777c79;
  background: #f4f4f4;
}

.rank.top {
  color: #ff4d55;
}

.prototype-manage-tabs {
  display: flex;
  height: 92rpx;
  padding: 14rpx 32rpx 20rpx;
  gap: 32rpx;
  background: #edfbf0;
  box-sizing: border-box;
}

.prototype-manage-tabs text {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 20rpx;
  border-radius: 15rpx;
  color: #999d9a;
  background: #fff;
  font-size: 25rpx;
}

.prototype-manage-tabs text.active {
  color: #17200c;
  background: #96f51f;
  font-weight: 600;
}

.prototype-manage-list {
  padding: 28rpx 32rpx;
}

.prototype-manage-card {
  margin-bottom: 26rpx;
  padding: 24rpx;
  border-radius: 32rpx;
  background: #fff;
}

.prototype-manage-main {
  display: flex;
  min-height: 176rpx;
}

.prototype-manage-main > image,
.prototype-manage-placeholder {
  flex: 0 0 auto;
  width: 176rpx;
  height: 176rpx;
  border-radius: 20rpx;
}

.prototype-manage-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef3f0;
  font-size: 60rpx;
}

.prototype-manage-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  margin-left: 32rpx;
  flex-direction: column;
}

.prototype-manage-copy > text:first-child {
  display: -webkit-box;
  overflow: hidden;
  color: #202321;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.38;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.prototype-manage-copy > text:nth-child(2) {
  margin-top: 26rpx;
  color: #999d9a;
  font-size: 24rpx;
}

.prototype-manage-copy > view {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
}

.prototype-manage-copy > view > text:first-child {
  color: #999d9a;
  font-size: 23rpx;
}

.prototype-manage-copy > view > text:last-child {
  color: #ff4d55;
  font-size: 46rpx;
  font-weight: 650;
}

.prototype-manage-copy i {
  margin-right: 4rpx;
  font-size: 23rpx;
  font-style: normal;
}

.prototype-manage-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56rpx;
  margin-top: 20rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #eef0ee;
  color: #999d9a;
  font-size: 24rpx;
}

.prototype-manage-footer text:last-child {
  color: #ff9518;
}

.tab-scroll {
  border: 0;
  background: #fff;
}

.tabs .active::after {
  background: #95f51f;
}

.filters {
  margin: 18rpx 24rpx;
  border: 0;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
}

.result-grid {
  gap: 24rpx;
  padding: 0 32rpx 40rpx;
}
</style>
