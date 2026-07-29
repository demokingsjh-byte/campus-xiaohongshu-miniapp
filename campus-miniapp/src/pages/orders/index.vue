<script lang="ts" setup>
import type { CampusTradeOrder } from '@/services/api/content';
import { getCampusTradeOrderPage } from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';

type OrderRole = 'buyer' | 'seller';

const userStore = useUserStore();
const orders = ref<CampusTradeOrder[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref(false);
const errorMessage = ref('订单记录暂时无法加载');
const authError = ref(false);
const activeRole = ref<OrderRole>('buyer');
const activeStatus = ref<number | undefined>();
const statusTabs: Array<{ label: string, value?: number }> = [
  { label: '全部' },
  { label: '待付款', value: 0 },
  { label: '已付款', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已关闭', value: 3 },
  { label: '已退款', value: 4 },
];

onShow(() => {
  void loadOrders();
});

async function loadOrders() {
  if (loading.value)
    return;
  loading.value = true;
  loadError.value = false;
  authError.value = false;
  errorMessage.value = '订单记录暂时无法加载';
  try {
    if (!userStore.userInfo)
      await withTimeout(userStore.initUserInfo(), 10000);
    if (!userStore.loggedIn) {
      uni.showModal({
        title: '请先登录',
        content: '登录后才能查看自己的订单和付款记录。',
        showCancel: false,
        success: () => uni.navigateTo({ url: '/pages/login/index' }),
      });
      return;
    }
    const result = await withTimeout(getCampusTradeOrderPage({
      role: activeRole.value,
      status: activeStatus.value,
      pageNo: 1,
      pageSize: 100,
    }), 10000);
    orders.value = result?.list || [];
    total.value = Number(result?.total || 0);
  } catch (error: any) {
    loadError.value = true;
    const detail = String(error?.message || error?.errMsg || '');
    if (/401|未登录|登录状态/i.test(detail)) {
      authError.value = true;
      errorMessage.value = '登录状态已失效，请重新登录后查看订单';
    } else if (detail.includes('timeout')) {
      errorMessage.value = '订单接口响应超时，请确认后端服务已启动';
    }
  } finally {
    loading.value = false;
  }
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number) {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('order-request-timeout')), timeoutMs);
    promise.then(resolve, reject).finally(() => clearTimeout(timer));
  });
}

function changeRole(role: OrderRole) {
  if (activeRole.value === role)
    return;
  activeRole.value = role;
  void loadOrders();
}

function changeStatus(status?: number) {
  activeStatus.value = status;
  void loadOrders();
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' });
}

function openOrder(order: CampusTradeOrder) {
  if (activeRole.value !== 'buyer')
    return;
  uni.navigateTo({ url: `/pages/checkout/index?orderId=${order.id}&postId=${order.postId}` });
}

function formatTime(value?: string) {
  if (!value)
    return '时间未知';
  const date = new Date(value.replace(' ', 'T'));
  if (Number.isNaN(date.getTime()))
    return value;
  return date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function statusTone(status: number) {
  if (status === 0)
    return 'pending';
  if (status === 1)
    return 'paid';
  if (status === 2)
    return 'completed';
  return 'closed';
}
</script>

<template>
  <view class="orders-page safe-bottom">
    <view class="orders-head">
      <view>
        <text class="eyebrow">交易记录</text>
        <text class="page-title">我的订单</text>
      </view>
      <text class="total">共 {{ total }} 笔</text>
    </view>

    <view class="role-tabs">
      <button :class="{ active: activeRole === 'buyer' }" @click="changeRole('buyer')">我买的</button>
      <button :class="{ active: activeRole === 'seller' }" @click="changeRole('seller')">我卖的</button>
    </view>

    <scroll-view class="status-scroll" scroll-x :show-scrollbar="false">
      <view class="status-tabs">
        <button
          v-for="tab in statusTabs" :key="tab.label" :class="{ active: activeStatus === tab.value }"
          @click="changeStatus(tab.value)"
        >
          {{ tab.label }}
        </button>
      </view>
    </scroll-view>

    <view v-if="loading" class="state">订单记录加载中…</view>
    <view v-else-if="loadError" class="state error-state">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @click="authError ? goLogin() : loadOrders">
        {{ authError ? '重新登录' : '重新加载' }}
      </button>
    </view>
    <view v-else-if="!orders.length" class="empty-state">
      <image src="/static/icons/ui/empty.svg" mode="aspectFit" />
      <text class="empty-title">还没有订单记录</text>
      <text class="empty-note">完成一次校园交易后，付款记录会显示在这里</text>
    </view>
    <view v-else class="order-list">
      <view
        v-for="item in orders" :key="item.id" class="order-card"
        :class="{ clickable: activeRole === 'buyer' }"
        @click="openOrder(item)"
      >
        <view class="order-card-head">
          <text class="order-number">订单号 {{ item.orderNo }}</text>
          <text :class="['order-status', statusTone(item.status)]">{{ item.statusText }}</text>
        </view>
        <view class="order-product">
          <image v-if="item.coverImage" class="order-cover" :src="item.coverImage" mode="aspectFill" />
          <view v-else class="order-cover cover-placeholder">云点</view>
          <view class="order-copy">
            <text class="order-title">{{ item.title || '校园交易商品' }}</text>
            <text class="order-person">{{ activeRole === 'buyer' ? `卖家：${item.sellerName || '校园同学'}` : `买家：${item.buyerName || '校园同学'}` }}</text>
            <text class="order-time">{{ formatTime(item.paidAt || item.expiresAt) }}</text>
          </view>
          <view class="order-price">
            <text class="currency">¥</text>{{ Number(item.amount || 0).toFixed(2) }}
          </view>
        </view>
        <view v-if="activeRole === 'buyer'" class="order-footer">点击查看订单详情 ›</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.orders-page {
  min-height: 100vh;
  padding: 30rpx 24rpx 48rpx;
  background: var(--color-page);
}
.orders-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 10rpx 4rpx 24rpx;
}
.orders-head > view,
.order-copy {
  display: flex;
  flex-direction: column;
}
.eyebrow {
  color: var(--color-primary);
  font-size: 21rpx;
  font-weight: 750;
}
.page-title {
  margin-top: 8rpx;
  color: var(--color-text);
  font-size: 42rpx;
  font-weight: 800;
}
.total {
  color: var(--color-text-tertiary);
  font-size: 22rpx;
}
.role-tabs {
  display: flex;
  padding: 6rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.68);
}
.role-tabs button {
  flex: 1;
  height: 70rpx;
  border-radius: 16rpx;
  color: var(--color-text-secondary);
  font-size: 25rpx;
}
.role-tabs button.active {
  color: var(--color-primary-strong);
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(22, 89, 69, 0.08);
  font-weight: 750;
}
.status-scroll {
  height: 88rpx;
  margin: 8rpx -24rpx 0;
}
.status-tabs {
  display: flex;
  gap: 14rpx;
  padding: 12rpx 24rpx;
  white-space: nowrap;
}
.status-tabs button {
  flex: 0 0 auto;
  height: 58rpx;
  padding: 0 22rpx;
  border: 1rpx solid rgba(22, 89, 69, 0.1);
  border-radius: 999rpx;
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.62);
  font-size: 22rpx;
}
.status-tabs button.active {
  border-color: rgba(16, 167, 121, 0.28);
  color: var(--color-primary-strong);
  background: rgba(16, 167, 121, 0.12);
  font-weight: 750;
}
.state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 520rpx;
  color: var(--color-text-tertiary);
  font-size: 25rpx;
}
.error-state,
.empty-state {
  flex-direction: column;
}
.retry-button {
  min-width: 190rpx;
  height: 64rpx;
  margin-top: 24rpx;
  border-radius: 18rpx;
  color: #fff;
  background: var(--color-primary);
  font-size: 23rpx;
}
.empty-state image {
  width: 120rpx;
  height: 120rpx;
  margin-bottom: 26rpx;
  opacity: 0.8;
}
.empty-title {
  color: var(--color-text);
  font-size: 29rpx;
  font-weight: 750;
}
.empty-note {
  margin-top: 12rpx;
  color: var(--color-text-tertiary);
  font-size: 22rpx;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.order-card {
  padding: 22rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.78);
  border-radius: 24rpx;
  background: var(--color-glass-strong);
  box-shadow: var(--shadow-card);
}
.order-card.clickable:active {
  opacity: 0.72;
}
.order-card-head,
.order-product {
  display: flex;
  align-items: center;
}
.order-card-head {
  justify-content: space-between;
  margin-bottom: 18rpx;
}
.order-number {
  overflow: hidden;
  max-width: 70%;
  color: var(--color-text-tertiary);
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-status {
  font-size: 22rpx;
  font-weight: 750;
}
.order-status.pending { color: #ff9500; }
.order-status.paid { color: var(--color-primary); }
.order-status.completed { color: #5856d6; }
.order-status.closed { color: var(--color-text-tertiary); }
.order-cover {
  flex: 0 0 auto;
  width: 142rpx;
  height: 142rpx;
  border-radius: 16rpx;
  background: var(--color-page-deep);
}
.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary-strong);
  font-size: 22rpx;
  font-weight: 750;
}
.order-copy {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}
.order-title {
  overflow: hidden;
  color: var(--color-text);
  font-size: 27rpx;
  font-weight: 750;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-person,
.order-time {
  overflow: hidden;
  margin-top: 12rpx;
  color: var(--color-text-secondary);
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-time {
  margin-top: 8rpx;
  color: var(--color-text-tertiary);
}
.order-price {
  flex: 0 0 auto;
  margin-left: 10rpx;
  color: var(--color-text);
  font-size: 29rpx;
  font-weight: 800;
}
.currency {
  margin-right: 2rpx;
  color: var(--color-accent);
  font-size: 20rpx;
}
.order-footer {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
  color: var(--color-primary-strong);
  font-size: 21rpx;
  text-align: right;
}
</style>
