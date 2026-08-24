<script lang="ts" setup>
import type { CampusNotification } from '@/services/api/notification';
import type { CampusFollowUser } from '@/services/api/content';
import StatePanel from '@/components/StatePanel/index.vue';
import { getCampusFollowingPage, migrateLocalCampusFollows, setCampusUserFollow } from '@/services/api/content';
import { getCampusNotificationPage } from '@/services/api/notification';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar, resolveCampusMediaUrl } from '@/utils/avatar';
import {
  clearCampusHistory,
  getCampusHistoryRecords,
  type CampusHistoryRecord,
} from '@/utils/personalRecords';

type PageMode = 'following' | 'likes' | 'history';

const userStore = useUserStore();
const contentStore = useCampusContentStore();
const mode = ref<PageMode>('following');
const loading = ref(false);
const loadError = ref(false);
const keyword = ref('');
const followingTab = ref<'全部' | '互关' | '关注'>('全部');
const likeTab = ref<'全部' | '赞过' | '商品获赞' | '评论获赞' | '我的评论'>('全部');
const followingTabs = ['全部', '互关', '关注'] as const;
const likeTabs = ['全部', '赞过', '商品获赞', '评论获赞', '我的评论'] as const;
const followingRecords = ref<CampusFollowUser[]>([]);
const historyRecords = ref<CampusHistoryRecord[]>([]);
const notifications = ref<CampusNotification[]>([]);
const statusBarHeight = ref(0);
const navigationStyle = computed(() => ({ '--status-bar-height': `${statusBarHeight.value}px` }));

const titles: Record<PageMode, string> = {
  following: '我的关注',
  likes: '我的获赞',
  history: '历史浏览',
};

const filteredFollowing = computed(() => {
  if (followingTab.value === '互关')
    return followingRecords.value.filter(item => item.mutual);
  const normalized = keyword.value.trim().toLocaleLowerCase();
  return followingRecords.value.filter(item => !normalized || item.nickname.toLocaleLowerCase().includes(normalized));
});

const filteredNotifications = computed(() => {
  if (likeTab.value === '全部')
    return notifications.value;
  if (likeTab.value === '赞过')
    return notifications.value.filter(item => item.eventType === 'LIKE');
  if (likeTab.value === '商品获赞')
    return notifications.value.filter(item => item.eventType === 'LIKE' && isProductNotification(item));
  if (likeTab.value === '评论获赞')
    return notifications.value.filter(item => item.eventType === 'COMMENT_LIKE');
  return notifications.value.filter(item => item.eventType === 'COMMENT' || item.eventType === 'REPLY');
});

const likeTabCounts = computed(() => ({
  全部: notifications.value.length,
  赞过: notifications.value.filter(item => item.eventType === 'LIKE').length,
  商品获赞: notifications.value.filter(item => item.eventType === 'LIKE' && isProductNotification(item)).length,
  评论获赞: notifications.value.filter(item => item.eventType === 'COMMENT_LIKE').length,
  我的评论: notifications.value.filter(item => item.eventType === 'COMMENT' || item.eventType === 'REPLY').length,
}));

onLoad((query) => {
  updateNavigationLayout();
  const requestedMode = String(query?.mode || 'following') as PageMode;
  mode.value = requestedMode in titles ? requestedMode : 'following';
});

onShow(() => void loadData());

async function loadData() {
  loading.value = true;
  loadError.value = false;
  try {
    if (!userStore.userInfo)
      await userStore.initUserInfo();
    if (!userStore.loggedIn) {
      uni.navigateTo({ url: '/pages/login/index' });
      return;
    }
    if (mode.value === 'following') {
      await migrateLocalCampusFollows(userStore.userInfo?.id);
      const page = await getCampusFollowingPage({ pageNo: 1, pageSize: 100 });
      followingRecords.value = page.list || [];
    } else if (mode.value === 'history') {
      historyRecords.value = getCampusHistoryRecords(userStore.userInfo?.id);
    } else {
      const [page] = await Promise.all([
        getCampusNotificationPage({ type: 'INTERACTION', pageNo: 1, pageSize: 100 }),
        contentStore.loadMyPosts(),
      ]);
      notifications.value = (page.list || []).filter(item => ['LIKE', 'COLLECT', 'COMMENT_LIKE', 'COMMENT', 'REPLY'].includes(item.eventType));
    }
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

function updateNavigationLayout() {
  const info = uni.getWindowInfo();
  statusBarHeight.value = info.statusBarHeight || 0;
}

function goBack() {
  uni.navigateBack();
}

function targetPost(item: CampusNotification) {
  return contentStore.publishedPosts.find(post => Number(post.id) === Number(item.targetId));
}

function isProductNotification(item: CampusNotification) {
  const post = targetPost(item);
  return post?.type === 'idle' || post?.channel === '二手';
}

function targetImage(item: CampusNotification) {
  const notificationImage = resolveCampusMediaUrl(item.targetImage);
  if (notificationImage)
    return notificationImage;
  const post = targetPost(item);
  return resolveCampusMediaUrl(post?.coverImage || post?.images?.[0]);
}

function interactionDescription(item: CampusNotification) {
  if (item.eventType === 'LIKE')
    return isProductNotification(item) ? '赞了你的商品' : '赞了你的内容';
  if (item.eventType === 'COLLECT')
    return '收藏了你的内容';
  if (item.eventType === 'COMMENT')
    return `评论：${item.content || '评论了你的内容'}`;
  if (item.eventType === 'REPLY')
    return `回复我：${item.content || '回复了你的评论'}`;
  return item.content || item.title;
}

function followingTabLabel(tab: typeof followingTabs[number]) {
  if (tab === '全部')
    return `全部 ${followingRecords.value.length}`;
  if (tab === '互关')
    return `互关(${followingRecords.value.filter(item => item.mutual).length})`;
  return `关注(${followingRecords.value.length})`;
}

function likeTabLabel(tab: typeof likeTabs[number]) {
  return `${tab}(${likeTabCounts.value[tab] || 0})`;
}

async function unfollow(item: CampusFollowUser) {
  try {
    await setCampusUserFollow(item.userId, false);
    followingRecords.value = followingRecords.value.filter(record => record.userId !== item.userId);
    uni.showToast({ title: '已取消关注', icon: 'none' });
  } catch {
    uni.showToast({ title: '取消关注失败，请重试', icon: 'none' });
  }
}

function clearHistory() {
  if (!historyRecords.value.length)
    return;
  uni.showModal({
    title: '清空历史浏览',
    content: '只会清除当前账号在本机记录的浏览历史。',
    confirmText: '清空',
    confirmColor: '#ff4747',
    success: (result) => {
      if (!result.confirm)
        return;
      clearCampusHistory(userStore.userInfo?.id);
      historyRecords.value = [];
    },
  });
}

function openPost(id?: number) {
  if (id)
    uni.navigateTo({ url: `/pages/detail/index?id=${id}` });
}

function goFollow() {
  uni.switchTab({ url: '/pages/index/index' });
}

function formatDate(timestamp: number) {
  if (!timestamp)
    return '';
  const date = new Date(timestamp);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${month}-${day}`;
}
</script>

<template>
  <view
    class="personal-page"
    :class="[`mode-${mode}`, { 'is-following-empty': mode === 'following' && !loading && !followingRecords.length }]"
    :style="navigationStyle"
  >
    <view class="personal-header">
      <view class="custom-nav">
        <view class="nav-back" @click="goBack">
          <image src="/static/icons/ui/back.svg" mode="aspectFit" />
        </view>
        <text>{{ titles[mode] }}</text>
        <view class="nav-capsule-space" />
      </view>

      <template v-if="mode === 'following'">
        <view class="search-box">
          <image src="/static/icons/ui/search.svg" mode="aspectFit" />
          <input v-model="keyword" placeholder="搜索用户昵称" />
        </view>
        <view v-if="followingRecords.length" class="following-tabs tabs">
          <text
            v-for="tab in followingTabs" :key="tab"
            :class="{ active: followingTab === tab }" @click="followingTab = tab"
          >
            {{ followingTabLabel(tab) }}
          </text>
        </view>
      </template>

      <scroll-view v-else-if="mode === 'likes'" class="likes-tab-scroll" scroll-x :show-scrollbar="false">
        <view class="likes-tabs tabs">
          <text
            v-for="tab in likeTabs" :key="tab"
            :class="{ active: likeTab === tab }" @click="likeTab = tab"
          >
            {{ likeTabLabel(tab) }}
          </text>
        </view>
      </scroll-view>
    </view>

    <view v-if="mode === 'history'" class="history-head">
      <text>仅展示当前账号真实浏览过的内容</text>
      <text class="clear" @click="clearHistory">清空</text>
    </view>

    <view class="personal-content">
      <StatePanel v-if="loading" title="正在加载真实记录" description="请稍候…" />
      <StatePanel
        v-else-if="loadError" type="offline" title="数据加载失败" description="请检查网络后重新加载"
        action="重新加载" @action="loadData"
      />

      <view v-else-if="mode === 'following' && filteredFollowing.length" class="record-card following-list">
        <view v-for="item in filteredFollowing" :key="item.userId" class="following-row">
          <image class="following-avatar" :src="resolveCampusAvatar(item.avatar)" mode="aspectFill" />
          <view class="record-main">
            <text class="record-title">{{ item.nickname }}</text>
          </view>
          <button @click="unfollow(item)">已关注</button>
        </view>
      </view>

      <view v-else-if="mode === 'likes' && filteredNotifications.length" class="record-card likes-list">
        <view
          v-for="item in filteredNotifications" :key="item.id" class="notification-row"
          @click="openPost(item.targetId)"
        >
          <image class="notification-avatar" :src="resolveCampusAvatar(item.actorAvatar)" mode="aspectFill" />
          <view class="record-main notification-main">
            <text class="record-title">{{ item.actorNickname || item.title }}</text>
            <text class="record-note">{{ interactionDescription(item) }}</text>
            <text class="record-time">{{ item.time }}</text>
          </view>
          <image
            v-if="targetImage(item)" class="notification-target" :src="targetImage(item)"
            mode="aspectFill"
          />
        </view>
      </view>

      <view v-else-if="mode === 'history' && historyRecords.length" class="history-list">
        <view v-for="item in historyRecords" :key="item.post.id" class="history-row" @click="openPost(item.post.id)">
          <image :src="item.post.coverImage || item.post.images?.[0] || '/static/icons/ui/empty.svg'" mode="aspectFill" />
          <view class="record-main">
            <text class="record-title history-title">{{ item.post.title }}</text>
            <view class="history-meta">
              <text>{{ item.post.author }}</text>
              <text class="history-date">{{ formatDate(item.viewedAt) }}</text>
            </view>
            <text v-if="item.post.price" class="history-price">¥{{ item.post.price }}</text>
          </view>
        </view>
      </view>

      <view v-else-if="mode === 'following' && !followingRecords.length" class="following-empty-state">
        <image src="/static/icons/ui/following-empty.svg" mode="aspectFit" />
        <text>暂无关注</text>
        <button @click="goFollow">去关注</button>
      </view>

      <StatePanel
        v-else :title="mode === 'following' ? '暂无关注记录' : mode === 'likes' ? '暂无获赞记录' : '暂无浏览记录'"
        :description="mode === 'following' ? '当前本地账号关注用户后会显示在这里' : mode === 'likes' ? '别人对当前账号内容产生互动后会显示在这里' : '打开内容详情后会记录在这里'"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.personal-page {
  box-sizing: border-box;
  min-height: 100vh;
  color: #1f1f1f;
  background: #fff;
}

.personal-header {
  padding-top: var(--status-bar-height);
  background: linear-gradient(180deg, #edfff3 0%, #f7fff9 72%, #fff 100%);
}

.custom-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  font-family: "PingFang SC", sans-serif;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.nav-back {
  position: absolute;
  left: 31rpx;
  display: flex;
  align-items: center;
  width: 52rpx;
  height: 60rpx;
}

.nav-back image { width: 28rpx; height: 28rpx; }
.nav-capsule-space { position: absolute; right: 0; width: 190rpx; height: 64rpx; }

.search-box {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  width: calc(100% - 62rpx);
  height: 72rpx;
  margin: 6rpx 31rpx 0;
  padding: 0 24rpx;
  border: 1rpx solid #e6e6e6;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: none;
}

.search-box image { width: 32rpx; height: 32rpx; opacity: .58; }
.search-box input { flex: 1; margin-left: 12rpx; color: #1f1f1f; font-size: 26rpx; }

.tabs { display: flex; align-items: center; white-space: nowrap; }
.tabs text { box-sizing: border-box; color: #999; font-size: 26rpx; line-height: 50rpx; text-align: center; }
.tabs text.active {
  position: relative;
  z-index: 1;
  border-radius: 14rpx;
  color: #1f1f1f !important;
  background: #8cf51a;
  font-weight: 600;
}

.following-tabs { gap: 28rpx; height: 92rpx; padding: 0 31rpx; }
.following-tabs text { min-width: 118rpx; padding: 0 13rpx; }

.likes-tab-scroll { width: 100%; height: 88rpx; }
.likes-tabs { display: inline-flex; gap: 18rpx; height: 88rpx; padding: 0 37rpx; }
.likes-tabs text { min-width: 112rpx; padding: 0 12rpx; }
.likes-tabs text:first-child { min-width: 126rpx; }

.personal-content { background: #fff; }
.record-card { background: #fff; }

.is-following-empty .personal-header { padding-bottom: 24rpx; background: #edfbf0; }
.is-following-empty .personal-content { min-height: calc(100vh - var(--status-bar-height) - 190rpx); background: #f4f4f4; }
.following-empty-state {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  padding-top: 196rpx;
  flex-direction: column;
}
.following-empty-state image { width: 232rpx; height: 202rpx; }
.following-empty-state > text { margin-top: 20rpx; color: #9b9b9b; font-size: 25rpx; line-height: 35rpx; }
.following-empty-state button {
  width: 132rpx;
  height: 62rpx;
  margin: 24rpx 0 0;
  padding: 0;
  border-radius: 23rpx;
  color: #1f1f1f;
  background: #8cf51a;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 62rpx;
}
.following-empty-state button::after { border: 0; }

.following-list { padding: 0 31rpx; }
.following-row {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 166rpx;
  border-bottom: 1rpx solid #ededed;
}

.following-avatar { flex: 0 0 auto; width: 104rpx; height: 104rpx; border-radius: 50%; }
.record-main { display: flex; overflow: hidden; flex: 1; min-width: 0; margin-left: 23rpx; flex-direction: column; }
.record-title { overflow: hidden; color: #1f1f1f; font-size: 30rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }

.following-row button {
  flex: 0 0 auto;
  width: 135rpx;
  height: 62rpx;
  margin: 0;
  padding: 0;
  border-radius: 26rpx;
  color: #929292;
  background: #f1f1f1;
  font-size: 26rpx;
  line-height: 62rpx;
}
.following-row button::after { border: 0; }

.likes-list { padding: 0 37rpx; }
.notification-row {
  display: flex;
  align-items: flex-start;
  box-sizing: border-box;
  min-height: 220rpx;
  padding: 31rpx 0;
  border-bottom: 1rpx solid #ededed;
}
.notification-avatar { flex: 0 0 auto; width: 72rpx; height: 72rpx; border-radius: 50%; }
.notification-main { align-self: stretch; margin-left: 18rpx; padding-top: 3rpx; }
.record-note {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 12rpx;
  color: #999;
  font-size: 26rpx;
  line-height: 36rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.record-time { margin-top: auto; color: #999; font-size: 25rpx; }
.notification-target { flex: 0 0 auto; width: 164rpx; height: 164rpx; margin-left: 20rpx; border-radius: 20rpx; background: #f2f2f2; }

.history-head { display: flex; justify-content: space-between; padding: 20rpx 31rpx; color: #969696; font-size: 23rpx; }
.history-head .clear { color: #ff4747; }
.history-list { margin: 0 31rpx; overflow: hidden; border-radius: 24rpx; background: #fff; }
.history-row { display: flex; align-items: center; min-height: 164rpx; border-bottom: 1rpx solid #eee; }
.history-row > image { width: 130rpx; height: 130rpx; border-radius: 18rpx; background: #eee; }
.history-title { white-space: normal; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 12rpx;
  color: #999;
  font-size: 26rpx;
  line-height: 36rpx;
}
.history-meta > text:first-child {
  overflow: hidden;
  min-width: 0;
  margin-right: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-date { flex: 0 0 auto; text-align: right; }
.history-price { margin-top: 8rpx; color: #ff4747; font-size: 29rpx; font-weight: 700; }
</style>
