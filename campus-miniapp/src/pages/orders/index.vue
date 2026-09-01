<script lang="ts" setup>
import type { CampusTradeOrder } from '@/services/api/content';
import { getAllCampusTradeOrders } from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusMediaUrl } from '@/utils/avatar';

type OrderRole = 'buyer' | 'seller';

const userStore = useUserStore();
const allOrders = ref<CampusTradeOrder[]>([]);
const orders = ref<CampusTradeOrder[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref(false);
const errorMessage = ref('订单记录暂时无法加载');
const authError = ref(false);
const activeRole = ref<OrderRole>('buyer');
const activeStatus = ref<number | undefined>();
const statusBarHeight = ref(0);
const navigationStyle = computed(() => ({ '--status-bar-height': `${statusBarHeight.value}px` }));
let loadVersion = 0;
const statusCounts = computed(() => allOrders.value.reduce<Record<number, number>>((counts, order) => {
  counts[order.status] = (counts[order.status] || 0) + 1;
  return counts;
}, {}));
const statusTabs = computed(() => [
  { label: `全部 ${allOrders.value.length}`, value: undefined },
  { label: `待付款 ${statusCounts.value[0] || 0}`, value: 0 },
  { label: `已付款 ${statusCounts.value[1] || 0}`, value: 1 },
  { label: `已完成 ${statusCounts.value[2] || 0}`, value: 2 },
  { label: `已关闭 ${statusCounts.value[3] || 0}`, value: 3 },
  { label: `已退款 ${statusCounts.value[4] || 0}`, value: 4 },
]);
const prototypeStatusTabs = computed(() => [
  { label: `全部 ${allOrders.value.length}`, value: undefined },
  { label: `待支付(${statusCounts.value[0] || 0})`, value: 0 },
  { label: `已支付(${statusCounts.value[1] || 0})`, value: 1 },
  { label: `已退款(${statusCounts.value[4] || 0})`, value: 4 },
]);
const activeStatusLabel = computed(() => {
  if (activeStatus.value === undefined)
    return '';
  return activeStatus.value === 0
    ? '待支付'
    : activeStatus.value === 1
      ? '已付款'
      : activeStatus.value === 2
        ? '已完成'
        : activeStatus.value === 3 ? '已关闭' : '已退款';
});
const pageTitle = computed(() => {
  if (activeRole.value === 'seller')
    return '卖出订单';
  if (activeStatus.value === 0)
    return '待支付订单';
  if (activeStatus.value === 1)
    return '已支付订单';
  if (activeStatus.value === 4)
    return '退款订单';
  return '买到订单';
});
const otherOrderCount = computed(() => Math.max(0, allOrders.value.length - orders.value.length));
const emptyTitle = computed(() => activeStatusLabel.value ? `${activeStatusLabel.value}暂无订单` : '还没有订单记录');
const emptyNote = computed(() => {
  if (!activeStatusLabel.value)
    return '完成一次校园交易后，付款记录会显示在这里';
  if (otherOrderCount.value > 0)
    return `当前没有${activeStatusLabel.value}订单，另有 ${otherOrderCount.value} 条其他状态订单，可点击“全部”查看`;
  return `当前账号没有${activeStatusLabel.value}订单`;
});

onLoad((query) => {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight || 0;
  activeRole.value = query?.role === 'seller' ? 'seller' : 'buyer';
  const requestedStatus = Number(query?.status);
  activeStatus.value = ['0', '1', '2', '3', '4'].includes(String(query?.status)) ? requestedStatus : undefined;
});

onShow(() => {
  void loadOrders();
});

async function loadOrders() {
  const requestVersion = ++loadVersion;
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
    const result = await withTimeout(getAllCampusTradeOrders(activeRole.value), 10000);
    if (requestVersion !== loadVersion)
      return;
    allOrders.value = result.list;
    applyStatusFilter();
  } catch (error: any) {
    if (requestVersion !== loadVersion)
      return;
    loadError.value = true;
    const detail = String(error?.message || error?.errMsg || '');
    if (/401|未登录|登录状态/.test(detail)) {
      authError.value = true;
      errorMessage.value = '登录状态已失效，请重新登录后查看订单';
    } else if (detail.includes('timeout')) {
      errorMessage.value = '订单接口响应超时，请确认后端服务已启动';
    }
  } finally {
    if (requestVersion === loadVersion)
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
  activeRole.value = role;
  activeStatus.value = undefined;
  allOrders.value = [];
  orders.value = [];
  total.value = 0;
  void loadOrders();
}

function changeStatus(status?: number) {
  activeStatus.value = status;
  applyStatusFilter();
}

function applyStatusFilter() {
  orders.value = activeStatus.value === undefined
    ? allOrders.value
    : allOrders.value.filter(order => order.status === activeStatus.value);
  total.value = orders.value.length;
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' });
}

function retry() {
  if (authError.value) {
    goLogin();
    return;
  }
  void loadOrders();
}

function openOrder(order: CampusTradeOrder) {
  if (order.bizType === 4) {
    uni.navigateTo({ url: `/pages/detail/index?id=${order.postId}` });
    return;
  }
  uni.navigateTo({ url: `/pages/checkout/index?orderId=${order.id}&postId=${order.postId}` });
}

function formatTime(value?: unknown) {
  if (!value)
    return '时间未知';
  let date: Date;
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value.map(Number);
    date = new Date(year, month - 1, day, hour, minute, second);
  } else if (typeof value === 'object') {
    const item = value as Record<string, unknown>;
    const year = Number(item.year);
    const month = Number(item.month ?? item.monthValue);
    const day = Number(item.day ?? item.dayOfMonth);
    if (year && month && day) {
      date = new Date(year, month - 1, day, Number(item.hour || 0), Number(item.minute || 0), Number(item.second || 0));
    } else {
      date = new Date(String(item.value ?? item.date ?? ''));
    }
  } else {
    const normalized = typeof value === 'string' ? value.replace(' ', 'T') : value;
    date = new Date(normalized);
  }
  if (Number.isNaN(date.getTime()))
    return typeof value === 'string' ? value : '时间未知';
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  return `${year}-${month}-${day}—${hour}:${minute}`;
}

function displayStatus(status: number, fallback: string) {
  if (status === 0)
    return '待支付';
  if (status === 1)
    return '已支付';
  if (status === 4)
    return '已退款';
  if (status === 3)
    return '已取消';
  return fallback || '已完成';
}

function displayOrderStatus(order: CampusTradeOrder) {
  return order.bizType === 4
    ? (order.fulfillmentStatusText || displayStatus(order.status, order.statusText))
    : displayStatus(order.status, order.statusText);
}

function orderFooterText(order: CampusTradeOrder) {
  if (order.bizType === 4) {
    if (order.fulfillmentStatus === 1)
      return '赏金已托管，点击查看任务进度';
    if ([2, 3, 4].includes(Number(order.fulfillmentStatus)))
      return activeRole.value === 'seller' && order.fulfillmentStatus === 4
        ? `收益 ¥${Number(order.incomeAmount || order.amount || 0).toFixed(2)} 已到账`
        : '点击查看任务进度并联系对方';
    return '点击查看任务订单详情';
  }
  return order.status === 1 || order.status === 2 ? '点击查看联系方式和交易会话' : '点击查看订单详情';
}

function goBack() {
  uni.navigateBack();
}

function statusTone(status: number) {
  if (status === 0)
    return 'pending';
  if (status === 1)
    return 'paid';
  if (status === 2)
    return 'completed';
  return status === 4 ? 'refunded' : 'closed';
}
</script>

<template>
  <view class="orders-page safe-bottom" :style="navigationStyle">
    <view class="orders-nav">
      <view class="orders-back" @click="goBack">
        <image src="/static/icons/ui/back.svg" mode="aspectFit" />
      </view>
      <text>{{ pageTitle }}</text>
      <view class="orders-capsule-space" />
    </view>
    <view class="orders-head">
      <view>
        <text class="eyebrow">
          交易记录
        </text>
        <text class="page-title">
          我的订单
        </text>
      </view>
      <text class="total">
        共 {{ total }} 笔
      </text>
    </view>

    <view class="role-tabs">
      <view class="role-tab" :class="[{ active: activeRole === 'buyer' }]" @click="changeRole('buyer')">
        我买/发布的
      </view>
      <view class="role-tab" :class="[{ active: activeRole === 'seller' }]" @click="changeRole('seller')">
        我卖/接的
      </view>
    </view>

    <scroll-view class="status-scroll" scroll-x :show-scrollbar="false">
      <view class="status-tabs">
        <view
          v-for="tab in prototypeStatusTabs" :key="tab.label" :class="{ active: activeStatus === tab.value }"
          @click="changeStatus(tab.value)"
        >
          {{ tab.label }}
        </view>
      </view>
    </scroll-view>

    <view v-if="loading && !allOrders.length" class="state">
      订单记录加载中…
    </view>
    <view v-else-if="loadError" class="state error-state">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @click="retry">
        {{ authError ? '重新登录' : '重新加载' }}
      </button>
    </view>
    <view v-else-if="!orders.length" class="empty-state">
      <image src="/static/icons/ui/empty.svg" mode="aspectFit" />
      <text class="empty-title">
        {{ emptyTitle }}
      </text>
      <text class="empty-note">
        {{ emptyNote }}
      </text>
    </view>
    <view v-else class="order-list">
      <view
        v-for="item in orders" :key="item.id" class="order-card clickable"
        @click="openOrder(item)"
      >
        <view class="order-card-head">
          <text class="order-number">
            订单号 {{ item.orderNo }}
          </text>
          <text class="order-status" :class="[statusTone(item.status)]">
            {{ displayOrderStatus(item) }}
          </text>
        </view>
        <view class="order-product">
          <image v-if="item.coverImage" class="order-cover" :src="resolveCampusMediaUrl(item.coverImage)" mode="aspectFill" />
          <view v-else class="order-cover cover-placeholder">
            云点
          </view>
          <view class="order-copy">
            <text class="order-title">
              {{ item.title || (item.bizType === 4 ? '校园代办任务' : '校园交易商品') }}
            </text>
            <text class="order-person">
              {{ item.bizType === 4
                ? (activeRole === 'buyer' ? `接单人：${item.sellerName || '等待接单'}` : `发布人：${item.buyerName || '校园同学'}`)
                : (activeRole === 'buyer' ? `卖家：${item.sellerName || '校园同学'}` : `买家：${item.buyerName || '校园同学'}`) }}
            </text>
            <text class="order-time">
              {{ formatTime(item.paidAt || item.expiresAt) }}
            </text>
          </view>
          <view class="order-price">
            <text class="currency">
              ¥
            </text>{{ Number(item.amount || 0).toFixed(2) }}
          </view>
        </view>
        <view class="order-footer">
          {{ orderFooterText(item) }}
        </view>
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
  gap: 14rpx;
}
.role-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 70rpx;
  border: 1rpx solid rgba(22, 89, 69, 0.1);
  border-radius: 18rpx;
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
}
.role-tab.active {
  border-color: rgba(16, 167, 121, 0.28);
  color: var(--color-primary-strong);
  background: rgba(16, 167, 121, 0.12);
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
.status-tabs > view {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 170rpx;
  height: 70rpx;
  border: 1rpx solid rgba(22, 89, 69, 0.1);
  border-radius: 999rpx;
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
}
.status-tabs > view.active {
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
.order-status.pending {
  color: #ff9500;
}
.order-status.paid {
  color: var(--color-primary);
}
.order-status.completed {
  color: #5856d6;
}
.order-status.refunded {
  color: #ff9500;
}
.order-status.closed {
  color: var(--color-text-tertiary);
}
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

/* 蓝湖原型：买到/卖出订单 */
.orders-page {
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 88rpx) 26rpx 60rpx;
  color: #202321;
  background: #f4f4f4;
  box-sizing: border-box;
}

.orders-nav {
  position: fixed;
  z-index: 20;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
  height: calc(var(--status-bar-height) + 88rpx);
  padding-top: var(--status-bar-height);
  background: #edfff3;
  font-size: 31rpx;
  font-weight: 600;
}

.orders-back {
  position: absolute;
  bottom: 14rpx;
  left: 31rpx;
  display: flex;
  align-items: center;
  width: 52rpx;
  height: 60rpx;
}

.orders-back image { width: 28rpx; height: 28rpx; }
.orders-capsule-space { position: absolute; right: 0; bottom: 12rpx; width: 190rpx; height: 64rpx; }

.orders-head,
.role-tabs {
  display: none;
}

.status-scroll {
  height: 84rpx;
  margin: 0 -26rpx 20rpx;
  background: #edfff3;
}

.status-tabs {
  min-width: 100%;
  height: 84rpx;
  padding: 8rpx 26rpx;
  gap: 24rpx;
  box-sizing: border-box;
}

.status-tabs > view {
  width: auto;
  min-width: 122rpx;
  height: 54rpx;
  padding: 0 15rpx;
  border: 0;
  border-radius: 14rpx;
  color: #999d9a;
  background: #fff;
  font-size: 25rpx;
  font-weight: 500;
}

.status-tabs > view.active {
  border: 0;
  color: #17200d;
  background: #96f51f;
  font-weight: 600;
}

.order-list {
  gap: 28rpx;
}

.order-card {
  min-height: 328rpx;
  padding: 20rpx 22rpx;
  border: 0;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: none;
}

.order-card-head {
  height: 48rpx;
  margin-bottom: 18rpx;
}

.order-number {
  max-width: 72%;
  color: #9a9e9b;
  font-size: 23rpx;
}

.order-status {
  padding: 5rpx 10rpx;
  border: 2rpx solid #d6dcf2;
  border-radius: 10rpx;
  color: #8290bb;
  background: #f6f7ff;
  font-size: 21rpx;
  font-weight: 550;
}

.order-status.pending {
  border-color: #b8d8ff;
  color: #2382ef;
  background: #eff7ff;
}

.order-status.paid,
.order-status.completed {
  border-color: #bde6ad;
  color: #43b525;
  background: #f2ffed;
}

.order-status.refunded,
.order-status.closed {
  color: #8791b4;
}

.order-product {
  align-items: stretch;
}

.order-cover {
  width: 146rpx;
  height: 146rpx;
  border-radius: 20rpx;
}

.order-copy {
  margin-left: 26rpx;
}

.order-title {
  display: -webkit-box;
  overflow: hidden;
  color: #202321;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.35;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.order-person,
.order-time {
  margin-top: 18rpx;
  color: #999d9a;
  font-size: 23rpx;
}

.order-time {
  margin-top: auto;
}

.order-price {
  align-self: flex-end;
  margin: 0 0 2rpx 10rpx;
  color: #ff4d55;
  font-size: 42rpx;
  font-weight: 650;
}

.currency {
  color: #ff4d55;
  font-size: 23rpx;
}

.order-footer {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #eef0ee;
  color: #ff9518;
  font-size: 24rpx;
}

.state,
.empty-state {
  min-height: 680rpx;
  color: #9a9e9b;
}

.empty-state image {
  width: 150rpx;
  height: 150rpx;
  opacity: 0.45;
}

.empty-title {
  color: #999d9a;
  font-size: 27rpx;
  font-weight: 500;
}

.retry-button {
  color: #14200a;
  background: #95f51f;
}
</style>
