<script lang="ts" setup>
import type { CampusNotification } from '@/services/api/notification';
import StatePanel from '@/components/StatePanel/index.vue';
import { useCampusNotificationStore } from '@/stores/modules/notification';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar, resolveCampusMediaUrl } from '@/utils/avatar';

type MessageTab = '互动' | '关注';

const userStore = useUserStore();
const notificationStore = useCampusNotificationStore();
const activeTab = ref<MessageTab>('互动');
const networkError = ref(false);
const statusBarHeight = ref(0);
const navBarHeight = ref(44);
const capsuleSafeRight = ref(16);
const navigationStyle = computed(() => ({
  '--status-bar-height': `${statusBarHeight.value}px`,
  '--nav-bar-height': `${navBarHeight.value}px`,
  '--capsule-safe-right': `${capsuleSafeRight.value}px`,
}));
const tabs: Array<{ key: MessageTab, label: string }> = [
  { key: '互动', label: '互动消息' },
  { key: '关注', label: '新关注我的' },
];

const filtered = computed(() => {
  if (activeTab.value === '关注')
    return notificationStore.notifications.filter(item => item.eventType === 'FOLLOW');
  return notificationStore.notifications.filter(item => item.type === 'INTERACTION' && item.eventType !== 'FOLLOW');
});

function unreadCount(tab: MessageTab) {
  if (tab === '关注')
    return notificationStore.notifications.filter(item => item.eventType === 'FOLLOW' && !item.read).length;
  return notificationStore.notifications.filter(item => item.type === 'INTERACTION' && item.eventType !== 'FOLLOW' && !item.read).length;
}

function actorName(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return item.title || '校园通知';
  return item.actorNickname || '校园同学';
}

function rowTag(item: CampusNotification) {
  return item.mutual ? '互相关注' : '';
}

function actionText(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return item.content || '你收到了一条校园通知';

  const targetName = item.targetType === 'PRODUCT' ? '商品' : '内容';
  switch (item.eventType) {
    case 'LIKE': return `赞了你的${targetName}`;
    case 'COLLECT': return `收藏了你的${targetName}`;
    case 'COMMENT_LIKE': return '赞了你的评论';
    case 'FOLLOW': return '关注了你';
    case 'MENTION': return item.content ? `在内容中提到了你：${item.content}` : '在内容中提到了你';
    case 'COMMENT': return item.content ? `评论：${item.content}` : '评论了你的内容';
    case 'REPLY': return item.content ? `回复：${item.content}` : '回复了你的评论';
    default: return item.content || item.title;
  }
}

function badgeText(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return '!';
  if (item.eventType === 'MENTION')
    return '@';
  if (item.eventType === 'LIKE' || item.eventType === 'COLLECT' || item.eventType === 'COMMENT_LIKE')
    return '♥';
  if (item.eventType === 'FOLLOW')
    return '+';
  return '';
}

function badgeClass(item: CampusNotification) {
  if (item.type === 'SYSTEM')
    return 'badge-system';
  if (item.eventType === 'MENTION')
    return 'badge-mention';
  if (item.eventType === 'FOLLOW')
    return 'badge-follow';
  if (item.eventType === 'LIKE' || item.eventType === 'COLLECT' || item.eventType === 'COMMENT_LIKE')
    return 'badge-like';
  return 'badge-reply';
}

function hasQuickActions(item: CampusNotification) {
  return item.type === 'INTERACTION' && (item.eventType === 'COMMENT' || item.eventType === 'REPLY');
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

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  uni.reLaunch({ url: '/pages/index/index' });
}

onMounted(() => {
  const runtime = uni as any;
  const windowInfo = runtime.getWindowInfo?.() || runtime.getSystemInfoSync?.() || {};
  const menuButton = runtime.getMenuButtonBoundingClientRect?.();
  statusBarHeight.value = Number(windowInfo.statusBarHeight || 0);
  if (menuButton?.height && menuButton?.top) {
    navBarHeight.value = menuButton.height + 2 * Math.max(0, menuButton.top - statusBarHeight.value);
    capsuleSafeRight.value = Math.max(16, Number(windowInfo.windowWidth || 0) - menuButton.left + 8);
  }
});

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
    uni.showToast({ title: item.content || '这是一条系统通知', icon: 'none' });
}

onShow(loadMessages);
</script>

<template>
  <view class="messages-page" :style="navigationStyle">
    <view class="message-header">
      <view class="header-inner">
        <view class="back-button" aria-label="返回" @click="goBack">
          <text class="back-icon" />
        </view>
        <view class="notification-tabs">
          <view
            v-for="tab in tabs" :key="tab.key" class="tab-item"
            :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key"
          >
            <text>{{ tab.label }}</text>
            <i v-if="unreadCount(tab.key)" class="tab-unread">
              {{ unreadCount(tab.key) > 99 ? '99+' : unreadCount(tab.key) }}
            </i>
          </view>
        </view>
      </view>
    </view>

    <StatePanel
      v-if="!userStore.loggedIn" type="login" title="登录后查看消息"
      description="评论、点赞和校园通知都会出现在这里。" action="去登录"
      @action="uni.navigateTo({ url: '/pages/login/index' })"
    />
    <StatePanel
      v-else-if="networkError" type="offline" title="网络连接不可用"
      description="检查网络后重试，消息不会丢失。" action="重新连接" @action="loadMessages"
    />
    <StatePanel
      v-else-if="!filtered.length" title="暂时没有新消息"
      :description="activeTab === '互动' ? '评论、点赞或提到你的消息会出现在这里。' : '新关注你的同学会出现在这里。'"
    />

    <view v-else class="notification-list">
      <view
        v-for="item in filtered" :key="item.id" class="notification-row"
        :class="{ unread: !item.read, 'with-actions': hasQuickActions(item) }"
        @click="openNotification(item)"
      >
        <view class="avatar-wrap">
          <image class="actor-avatar" :src="resolveCampusAvatar(item.actorAvatar)" mode="aspectFill" lazy-load />
          <view class="event-badge" :class="badgeClass(item)">
            <view v-if="item.eventType === 'COMMENT' || item.eventType === 'REPLY'" class="comment-symbol">
              <i class="comment-dot" /><i class="comment-dot" /><i class="comment-dot" />
            </view>
            <text v-else-if="item.eventType === 'MENTION'" class="mention-symbol">@</text>
            <text v-else class="badge-text">{{ badgeText(item) }}</text>
          </view>
        </view>

        <view class="notification-main">
          <view class="identity-line">
            <text class="actor-name">{{ actorName(item) }}</text>
            <text v-if="rowTag(item)" class="relation-tag">{{ rowTag(item) }}</text>
            <i v-if="!item.read" class="row-unread" />
          </view>
          <view class="action-line">
            <text class="action-text">{{ actionText(item) }}</text>
            <text class="message-time">{{ item.time }}</text>
          </view>
          <view v-if="hasQuickActions(item)" class="quick-actions">
            <view class="quick-action" @click.stop="openNotification(item)">
              <text class="quick-avatar">↩</text><text>回复评论</text>
            </view>
            <view class="quick-action like-action" @click.stop="openNotification(item)">
              <text class="quick-heart">♥</text><text>赞</text>
            </view>
          </view>
        </view>

        <view v-if="item.eventType !== 'FOLLOW'" class="target-preview">
          <image
            v-if="item.targetImage" class="target-image"
            :src="resolveCampusMediaUrl(item.targetImage)" mode="aspectFill" lazy-load
          />
          <view v-else class="target-placeholder" :class="{ system: item.type === 'SYSTEM' }">
            <view class="placeholder-shape" />
            <text>{{ item.type === 'SYSTEM' ? '通知' : '内容' }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.messages-page {
  min-height: 100vh;
  padding-bottom: env(safe-area-inset-bottom);
  color: #161616;
  background: #fff;
}

.message-header {
  padding-top: var(--status-bar-height);
  border-bottom: 1rpx solid #ededed;
  background: rgba(255, 255, 255, 0.98);
}

.header-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--nav-bar-height, 44px);
  padding-right: var(--capsule-safe-right, 16px);
  padding-left: 100rpx;
  box-sizing: border-box;
}

.back-button {
  position: absolute;
  z-index: 2;
  top: 0;
  left: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 100%;
}

.back-icon {
  width: 25rpx;
  height: 25rpx;
  border-bottom: 5rpx solid #171717;
  border-left: 5rpx solid #171717;
  transform: rotate(45deg);
}

.notification-tabs {
  display: flex;
  flex: 1;
  align-self: stretch;
  justify-content: center;
  gap: 18rpx;
  min-width: 0;
}

.tab-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-width: 0;
  max-width: 190rpx;
  padding: 0 8rpx;
  color: #858585;
  font-size: 31rpx;
  font-weight: 500;
  line-height: var(--nav-bar-height, 44px);
  white-space: nowrap;
}

.tab-item.active {
  color: #171717;
  font-weight: 700;
}

.tab-item.active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 38rpx;
  height: 5rpx;
  border-radius: 99rpx;
  background: #191919;
  content: '';
  transform: translateX(-50%);
}

.tab-unread {
  position: absolute;
  top: 21rpx;
  right: -4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  border: 3rpx solid #fff;
  border-radius: 99rpx;
  color: #fff;
  background: #ff4059;
  font-size: 17rpx;
  font-style: normal;
  font-weight: 600;
  line-height: 28rpx;
}

.notification-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  min-height: 190rpx;
  padding: 28rpx 32rpx 30rpx 38rpx;
  border-bottom: 1rpx solid #eee;
  background: #fff;
  box-sizing: border-box;
}

.notification-row.unread { background: #fffefa; }
.notification-row.with-actions { min-height: 238rpx; }

.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
  width: 96rpx;
  height: 96rpx;
}

.actor-avatar {
  display: block;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #f1f1f1;
}

.event-badge {
  position: absolute;
  right: -7rpx;
  bottom: -5rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  border: 5rpx solid #fff;
  border-radius: 50%;
  color: #fff;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 40rpx;
  box-sizing: content-box;
}

.badge-mention { background: #ffc515; }
.badge-reply { background: #35b7f4; }
.badge-like { background: #ff4066; font-size: 22rpx; }
.badge-system { background: #31c96b; }
.badge-follow { background: #31c96b; }

.mention-symbol,
.badge-text {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  line-height: 1;
}

.mention-symbol {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  font-family: Arial, sans-serif;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 40rpx;
  text-align: center;
  transform: translateY(-3rpx);
}

.comment-symbol {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  width: 28rpx;
  height: 20rpx;
  border-radius: 11rpx;
  background: #fff;
}

.comment-symbol::after {
  position: absolute;
  right: 4rpx;
  bottom: -3rpx;
  width: 7rpx;
  height: 7rpx;
  background: #fff;
  content: '';
  transform: rotate(45deg);
}

.comment-symbol .comment-dot {
  position: relative;
  z-index: 1;
  display: block;
  flex: 0 0 auto;
  width: 4rpx;
  height: 4rpx;
  border-radius: 50%;
  background: #35b7f4;
}

.notification-main {
  flex: 1;
  min-width: 0;
  margin: 4rpx 20rpx 0 24rpx;
}

.identity-line {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 40rpx;
}

.actor-name {
  overflow: hidden;
  max-width: 245rpx;
  color: #151515;
  font-size: 30rpx;
  font-weight: 650;
  line-height: 40rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-tag {
  flex: 0 0 auto;
  margin-left: 12rpx;
  padding: 2rpx 10rpx;
  border-radius: 3rpx;
  color: #7c7c7c;
  background: #f1f1f1;
  font-size: 20rpx;
  line-height: 28rpx;
}

.row-unread {
  flex: 0 0 auto;
  width: 11rpx;
  height: 11rpx;
  margin-left: 10rpx;
  border-radius: 50%;
  background: #ff4059;
}

.action-line {
  display: flex;
  align-items: baseline;
  min-width: 0;
  margin-top: 13rpx;
  line-height: 39rpx;
}

.action-text {
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  overflow: hidden;
  color: #292929;
  font-size: 27rpx;
  line-height: 39rpx;
  text-overflow: ellipsis;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.message-time {
  flex: 0 0 auto;
  margin-left: 12rpx;
  color: #969696;
  font-size: 23rpx;
  white-space: nowrap;
}

.quick-actions {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 18rpx;
}

.quick-action {
  display: flex;
  align-items: center;
  height: 54rpx;
  padding: 0 18rpx;
  border-radius: 27rpx;
  color: #777;
  background: #fafafa;
  font-size: 23rpx;
  line-height: 54rpx;
}

.quick-avatar,
.quick-heart {
  margin-right: 9rpx;
  color: #929292;
  font-size: 27rpx;
  font-weight: 700;
}

.like-action { min-width: 72rpx; justify-content: center; }

.target-preview {
  flex: 0 0 auto;
  width: 92rpx;
  height: 116rpx;
  overflow: hidden;
  margin-top: 1rpx;
  border-radius: 9rpx;
  background: #f0f0f0;
}

.target-image { display: block; width: 100%; height: 100%; }

.target-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #a6a6a6;
  background: #ededed;
  font-size: 18rpx;
}

.target-placeholder.system { color: #78a489; background: #edf7f0; }

.placeholder-shape {
  position: relative;
  width: 34rpx;
  height: 26rpx;
  margin-bottom: 8rpx;
  border: 4rpx solid currentColor;
  border-radius: 5rpx;
}

.placeholder-shape::after {
  position: absolute;
  right: 4rpx;
  bottom: 4rpx;
  width: 14rpx;
  height: 14rpx;
  border-left: 4rpx solid currentColor;
  border-top: 4rpx solid currentColor;
  content: '';
  transform: rotate(45deg);
}
</style>
