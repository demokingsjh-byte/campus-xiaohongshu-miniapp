<script lang="ts" setup>
import StatePanel from '@/components/StatePanel/index.vue';
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const loggedIn = ref(false);
const activeTab = ref('全部');
const networkError = ref(false);
const tabs = ['全部', '评论', '赞与收藏', '系统'];
const messages = reactive([
  { type: '评论', icon: '评', color: '#DFF1EC', title: '小满同学 评论了你的发布', content: '请问桌子的尺寸大概是多少呀？', time: '8分钟前', unread: true },
  { type: '赞与收藏', icon: '♡', color: '#FFF0ED', title: '3 位同学赞了你的内容', content: '毕业出九成新折叠桌和台灯', time: '32分钟前', unread: true },
  { type: '系统', icon: '云', color: '#E8EFF0', title: '校园认证已通过', content: '你已获得浙江理工大学同校标识', time: '昨天', unread: false },
  { type: '评论', icon: '复', color: '#FFF0D9', title: '赶高铁 回复了你', content: '可以的，周五 18:20 东门见。', time: '周五', unread: false },
]);
const filtered = computed(() => activeTab.value === '全部' ? messages : messages.filter(item => item.type === activeTab.value));
const unreadCount = computed(() => messages.filter(item => item.unread).length);
const commentUnreadCount = computed(() => messages.filter(item => item.type === '评论' && item.unread).length);
onShow(() => loggedIn.value = userStore.loggedIn || Boolean(uni.getStorageSync('yd-demo-login')));
function markRead() {
  if (!unreadCount.value)
    return;
  messages.forEach(item => item.unread = false);
  uni.showToast({ title: '已全部标为已读', icon: 'none' });
}
function markSingle(item: typeof messages[number]) { item.unread = false; }
</script>

<template>
  <view class="messages-page">
    <view class="message-actions">
      <text>{{ unreadCount }} 条未读</text><text :class="{ disabled: !unreadCount }" @click="markRead">
        全部已读
      </text>
    </view>
    <scroll-view scroll-x class="message-tabs">
      <view>
        <text v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">
          {{ tab }}<i v-if="tab === '评论' && commentUnreadCount">{{ commentUnreadCount }}</i>
        </text>
      </view>
    </scroll-view>

    <StatePanel v-if="!loggedIn" type="login" title="登录后查看消息" description="评论、点赞、交易回应和校园通知都会出现在这里。" action="去登录" @action="uni.navigateTo({ url: '/pages/login/index' })" />
    <StatePanel v-else-if="networkError" type="offline" title="网络连接不可用" description="检查网络后重试，消息不会丢失。" action="重新连接" @action="networkError = false" />
    <StatePanel v-else-if="!filtered.length" title="暂时没有新消息" description="参与评论或发布内容后，校园里的回应会出现在这里。" />
    <view v-else class="message-list">
      <view v-for="item in filtered" :key="item.title" class="message-row" :class="{ unread: item.unread }" @click="markSingle(item)">
        <view class="message-icon" :style="{ background: item.color }">
          {{ item.icon }}
        </view><view class="message-main">
          <view class="message-title">
            {{ item.title }}
          </view><view class="message-content">
            {{ item.content }}
          </view><view class="message-time">
            {{ item.time }}
          </view>
        </view><view v-if="item.unread" class="unread-dot" />
      </view>
      <view class="security-card">
        <view>🔔 开启微信服务通知</view><text>及时收到交易和重要校园消息 ›</text>
      </view>
      <view class="debug-error" @click="networkError = true">
        模拟离线状态
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.messages-page {
  min-height: 100vh;
  background: #faf8f3;
}
.message-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  color: #8a9490;
  font-size: 21rpx;
}
.message-actions text:last-child {
  color: #0f766e;
}
.message-actions .disabled { color: #aab2af; }
.message-tabs {
  background: #fff;
  white-space: nowrap;
}
.message-tabs > view {
  display: flex;
  gap: 42rpx;
  padding: 0 24rpx;
}
.message-tabs text {
  position: relative;
  padding: 21rpx 0;
  color: #717c78;
  font-size: 24rpx;
}
.message-tabs .active {
  color: #18201e;
  font-weight: 800;
}
.message-tabs .active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 36rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: #16a085;
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
  border-radius: 999rpx;
  color: #fff;
  background: #ff6b5e;
  font-size: 17rpx;
  font-style: normal;
}
.message-list {
  padding: 16rpx 0 40rpx;
}
.message-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  padding: 24rpx;
  border-bottom: 1rpx solid #eeeae3;
  background: #fff;
}
.message-row.unread {
  background: #f6fbf9;
}
.message-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84rpx;
  height: 84rpx;
  border-radius: 26rpx;
  color: #174f48;
  font-size: 26rpx;
  font-weight: 900;
}
.message-main {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}
.message-title {
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
  width: 14rpx;
  height: 14rpx;
  margin-top: 8rpx;
  border-radius: 50%;
  background: #ff6b5e;
}
.security-card {
  margin: 22rpx 24rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  color: #174f48;
  background: #dff1ec;
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
.debug-error {
  margin-top: 30rpx;
  color: #b2b8b5;
  font-size: 18rpx;
  text-align: center;
}
</style>
