<script lang="ts" setup>
import type { CampusNotification } from '@/services/api/notification';
import StatePanel from '@/components/StatePanel/index.vue';
import { useCampusNotificationStore } from '@/stores/modules/notification';
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const notificationStore = useCampusNotificationStore();
const activeTab = ref<'全部' | '互动' | '系统'>('全部');
const networkError = ref(false);
const tabs: Array<typeof activeTab.value> = ['全部', '互动', '系统'];

const filtered = computed(() => {
  if (activeTab.value === '全部')
    return notificationStore.notifications;
  const type = activeTab.value === '互动' ? 'INTERACTION' : 'SYSTEM';
  return notificationStore.notifications.filter(item => item.type === type);
});

const interactionUnreadCount = computed(() => notificationStore.notifications.filter(item => item.type === 'INTERACTION' && !item.read).length);
const systemUnreadCount = computed(() => notificationStore.notifications.filter(item => item.type === 'SYSTEM' && !item.read).length);

function iconOf(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return '/static/icons/mine/cloud.svg';
  if (item.eventType === 'LIKE' || item.eventType === 'COLLECT')
    return '/static/icons/mine/heart.svg';
  if (item.eventType === 'REPLY')
    return '/static/icons/ui/reply.svg';
  return '/static/icons/ui/comment.svg';
}

function colorOf(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return '#DFF4EC';
  if (item.eventType === 'LIKE' || item.eventType === 'COLLECT')
    return '#FFF0ED';
  if (item.eventType === 'REPLY')
    return '#FFF6E5';
  return '#E9F2FF';
}

async function loadMessages() {
  if (!userStore.loggedIn)
    return;
  networkError.value = false;
  try {
    await notificationStore.load();
  } catch {
    networkError.value = true;
  }
}

async function markAllRead() {
  if (!notificationStore.unreadCount)
    return;
  try {
    await notificationStore.markAllRead();
    uni.showToast({ title: '已全部标为已读', icon: 'none' });
  } catch {
    uni.showToast({ title: '操作失败，请重试', icon: 'none' });
  }
}

async function openNotification(item: CampusNotification) {
  try {
    await notificationStore.markRead(item);
  } catch {
    uni.showToast({ title: '通知状态更新失败', icon: 'none' });
    return;
  }
  if ((item.targetType === 'POST' || item.targetType === 'PRODUCT') && item.targetId) {
    uni.navigateTo({ url: `/pages/detail/index?id=${item.targetId}` });
    return;
  }
  if (item.type === 'SYSTEM')
    uni.showToast({ title: '这是一条系统通知', icon: 'none' });
}

onShow(loadMessages);
</script>

<template>
  <view class="messages-page">
    <view class="message-actions">
      <view class="unread-summary">
        <text class="unread-number">
          {{ notificationStore.unreadCount }}
        </text>
        <text>条未读</text>
      </view>
      <text :class="{ disabled: !notificationStore.unreadCount }" @click="markAllRead">
        全部标为已读
      </text>
    </view>

    <scroll-view scroll-x class="message-tabs">
      <view>
        <text v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">
          {{ tab }}
          <i v-if="tab === '互动' && interactionUnreadCount">{{ interactionUnreadCount > 99 ? '99+' : interactionUnreadCount }}</i>
          <i v-if="tab === '系统' && systemUnreadCount">{{ systemUnreadCount > 99 ? '99+' : systemUnreadCount }}</i>
        </text>
      </view>
    </scroll-view>

    <StatePanel
      v-if="!userStore.loggedIn" type="login" title="登录后查看消息"
      description="评论、点赞、交易回应和校园通知都会出现在这里。" action="去登录"
      @action="uni.navigateTo({ url: '/pages/login/index' })"
    />
    <StatePanel
      v-else-if="networkError" type="offline" title="网络连接不可用"
      description="检查网络后重试，消息不会丢失。" action="重新连接" @action="loadMessages"
    />
    <StatePanel
      v-else-if="!filtered.length" title="暂时没有新消息"
      description="参与评论或发布内容后，校园里的回应会出现在这里。"
    />
    <view v-else class="message-list">
      <view class="message-section-label">
        {{ activeTab === '系统' ? '系统通知' : activeTab === '互动' ? '互动通知' : '全部通知' }}
      </view>
      <view class="message-card">
        <view
          v-for="item in filtered" :key="item.id" class="message-row" :class="{ unread: !item.read }"
          @click="openNotification(item)"
        >
          <view class="message-icon" :style="{ background: colorOf(item) }">
            <image :src="iconOf(item)" mode="aspectFit" />
          </view>
          <view class="message-main">
            <view class="message-title">
              {{ item.title }}
            </view>
            <view class="message-content">
              {{ item.content }}
            </view>
            <view class="message-time">
              {{ item.time }}
            </view>
          </view>
          <view v-if="!item.read" class="unread-dot" />
          <text v-if="item.targetType === 'POST'" class="message-arrow">
            ›
          </text>
        </view>
      </view>
      <view class="security-card">
        <view><image src="/static/icons/ui/bell.svg" mode="aspectFit" /><text>开启微信服务通知</text></view>
        <text>及时收到交易和重要校园消息 ›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.messages-page {
  min-height: 100vh;
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--yd-paper);
}
.message-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 84rpx;
  margin: 14rpx 18rpx 0;
  padding: 12rpx 24rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 22rpx;
  color: #8a9490;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 16rpx 42rpx rgba(20, 91, 70, 0.1);
  backdrop-filter: blur(28rpx) saturate(155%);
  -webkit-backdrop-filter: blur(28rpx) saturate(155%);
  font-size: 21rpx;
}
.unread-summary {
  display: flex;
  align-items: baseline;
}
.unread-number {
  margin-right: 5rpx;
  color: var(--yd-green-dark);
  font-size: 34rpx;
  font-weight: 800;
}
.message-actions > text:last-child {
  color: var(--yd-green-dark);
}
.message-actions .disabled {
  color: #aab2af;
}
.message-tabs {
  width: auto;
  margin: 12rpx 18rpx 0;
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 16rpx 42rpx rgba(20, 91, 70, 0.1);
  backdrop-filter: blur(28rpx) saturate(155%);
  -webkit-backdrop-filter: blur(28rpx) saturate(155%);
  white-space: nowrap;
}
.message-tabs > view {
  display: flex;
  gap: 60rpx;
  padding: 0 28rpx;
}
.message-tabs text {
  position: relative;
  padding: 21rpx 0;
  color: #717c78;
  font-size: 24rpx;
}
.message-tabs .active {
  color: var(--yd-ink);
  font-weight: 800;
}
.message-tabs .active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 36rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: var(--yd-green);
  content: '';
  transform: translateX(-50%);
}
.message-tabs i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28rpx;
  height: 28rpx;
  margin-left: 6rpx;
  padding: 0 5rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #ef5b57;
  font-size: 17rpx;
  font-style: normal;
}
.message-list {
  padding: 20rpx 18rpx 40rpx;
}
.message-section-label {
  margin: 0 6rpx 12rpx;
  color: #65706c;
  font-size: 22rpx;
  font-weight: 700;
}
.message-card {
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 16rpx 42rpx rgba(20, 91, 70, 0.1);
}
.message-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  min-height: 128rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.1);
  background: transparent;
}
.message-row:last-child {
  border-bottom: 0;
}
.message-row.unread {
  background: var(--color-primary-soft);
}
.message-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: 17rpx 17rpx 17rpx 5rpx;
}
.message-icon image {
  width: 38rpx;
  height: 38rpx;
}
.message-main {
  flex: 1;
  min-width: 0;
  margin-left: 22rpx;
  padding-right: 28rpx;
}
.message-title {
  color: var(--yd-ink);
  font-size: 26rpx;
  font-weight: 800;
}
.message-content {
  overflow: hidden;
  margin-top: 8rpx;
  color: #65706c;
  font-size: 23rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-time {
  margin-top: 9rpx;
  color: #a0a8a4;
  font-size: 19rpx;
}
.unread-dot {
  position: absolute;
  top: 28rpx;
  right: 26rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ef5b57;
}
.message-arrow {
  position: absolute;
  right: 20rpx;
  bottom: 22rpx;
  color: #98a39e;
  font-size: 30rpx;
}
.security-card {
  margin: 22rpx 0;
  min-height: 112rpx;
  padding: 24rpx 26rpx;
  border: 1rpx dashed #9fc8b9;
  border-radius: 24rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 24rpx;
  font-weight: 800;
}
.security-card text {
  display: block;
  margin-top: 8rpx;
  color: #648079;
  font-size: 20rpx;
  font-weight: 400;
}
.security-card > view {
  display: flex;
  align-items: center;
  gap: 14rpx;
}
.security-card image {
  width: 36rpx;
  height: 36rpx;
}
.security-card > view text {
  margin-top: 0;
  color: var(--yd-green-dark);
  font-size: 24rpx;
  font-weight: 800;
}
</style>
