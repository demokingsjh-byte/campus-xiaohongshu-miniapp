<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import type { CampusTradeContact, CampusTradeOrder } from '@/services/api/content';
import {
  createCampusTradeOrder,
  createCampusTradePayment,
  getCampusPost,
  getCampusTradeContact,
  getCampusTradePaymentStatus,
} from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';

const postId = ref(0);
const post = ref<CampusPost>();
const order = ref<CampusTradeOrder>();
const contact = ref<CampusTradeContact>();
const loading = ref(true);
const busy = ref(false);
const paymentPending = ref(false);
const remainingSeconds = ref(0);
const loadError = ref(false);
const userStore = useUserStore();
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const isPaid = computed(() => order.value?.status === 1);
const isWaitingPayment = computed(() => order.value?.status === 0 && remainingSeconds.value > 0);
const isPaymentPending = computed(() => paymentPending.value && order.value?.status === 0);
const productImage = computed(() => order.value?.coverImage || post.value?.coverImage || post.value?.images?.[0] || '');
const countdownText = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60).toString().padStart(2, '0');
  const seconds = (remainingSeconds.value % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
});
const displayAmount = computed(() => Number(order.value?.amount ?? post.value?.price ?? 0).toFixed(2));

onLoad(async (query) => {
  postId.value = Number(query?.postId || 0);
  await loadCheckout();
});

onUnload(() => stopCountdown());

async function loadCheckout() {
  if (!postId.value) {
    loadError.value = true;
    loading.value = false;
    return;
  }
  try {
    await userStore.initUserInfo();
    if (!userStore.loggedIn) {
      loadError.value = true;
      uni.showModal({
        title: '请先登录',
        content: '登录后才能创建二手交易订单。',
        showCancel: false,
        success: () => uni.navigateBack(),
      });
      return;
    }
    post.value = await getCampusPost(postId.value);
    order.value = await createCampusTradeOrder(postId.value);
    startCountdown();
    if (order.value.status === 1)
      await loadContact();
  } catch (error: any) {
    loadError.value = true;
    const message = getReadableError(error);
    uni.showModal({ title: '订单加载失败', content: message, showCancel: false });
  } finally {
    loading.value = false;
  }
}

function getReadableError(error: any) {
  const raw = String(error?.message || error?.errMsg || '').trim();
  const matched = raw.match(/请求错误\[\d+\]：(.+)$/);
  return (matched?.[1] || raw || '请确认后端服务和订单数据库迁移已经更新。').slice(0, 120);
}

function startCountdown() {
  stopCountdown();
  updateCountdown();
  countdownTimer = setInterval(updateCountdown, 1000);
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function updateCountdown() {
  const expiresAt = order.value?.expiresAt;
  if (!expiresAt || order.value?.status !== 0) {
    remainingSeconds.value = 0;
    return;
  }
  const seconds = Math.max(Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000), 0);
  remainingSeconds.value = seconds;
  if (!seconds && order.value?.status === 0) {
    order.value = { ...order.value, status: 3, statusText: '已关闭', expired: true, closeReason: 'TIMEOUT' };
    stopCountdown();
  }
}

function requestWechatPayment(params: Awaited<ReturnType<typeof createCampusTradePayment>>) {
  return new Promise<void>((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: params.timeStamp || '',
      nonceStr: params.nonceStr || '',
      package: params.packageValue || '',
      signType: (params.signType || 'RSA') as any,
      paySign: params.paySign || '',
      success: () => resolve(),
      fail: error => reject(error),
    });
  });
}

async function syncPaymentStatus(maxAttempts = 5) {
  if (!order.value)
    return false;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    try {
      const result = await getCampusTradePaymentStatus(order.value.id);
      if (result.paid) {
        await applyPaidOrder(result.paidAt);
        return true;
      }
      if (result.status === 3) {
        order.value = await getCampusTradeOrder(order.value.id);
        paymentPending.value = false;
        return false;
      }
    } catch {
      // Payment callbacks and WeChat order queries are asynchronous. Keep the
      // page in a recoverable confirmation state instead of starting payment again.
    }
    if (attempt < maxAttempts - 1)
      await new Promise(resolve => setTimeout(resolve, 800));
  }
  return false;
}

async function applyPaidOrder(paidAt?: string) {
  if (!order.value)
    return;
  try {
    order.value = await getCampusTradeOrder(order.value.id);
  } catch {
    order.value = {
      ...order.value,
      status: 1,
      statusText: '已付款',
      paidAt: paidAt || order.value.paidAt,
    };
  }
  paymentPending.value = false;
  try {
    await loadContact();
  } catch {
    contact.value = undefined;
  }
}

async function loadContact() {
  contact.value = await getCampusTradeContact(postId.value);
}

async function pay() {
  if (!order.value || !isWaitingPayment.value || paymentPending.value || busy.value)
    return;
  busy.value = true;
  try {
    const params = await createCampusTradePayment(order.value.id);
    if (params.status !== 1 && params.packageValue)
      await requestWechatPayment(params);
    paymentPending.value = true;
    const paid = await syncPaymentStatus();
    if (!paid) {
      uni.showToast({ title: '支付已提交，正在确认订单状态', icon: 'none' });
      return;
    }
    uni.showToast({ title: '支付成功', icon: 'success' });
  } catch (error: any) {
    const message = String(error?.errMsg || error?.message || '').toLowerCase();
    if (message.includes('cancel')) {
      uni.showToast({ title: '已取消支付', icon: 'none' });
    } else {
      uni.showToast({ title: '支付未完成，请稍后重试', icon: 'none' });
    }
  } finally {
    busy.value = false;
  }
}

async function refreshPaymentStatus() {
  if (!order.value || busy.value)
    return;
  busy.value = true;
  try {
    paymentPending.value = true;
    const paid = await syncPaymentStatus(4);
    if (paid) {
      uni.showToast({ title: '支付成功', icon: 'success' });
    } else if (order.value?.status === 0) {
      uni.showToast({ title: '支付状态仍在确认，请稍后刷新', icon: 'none' });
    }
  } finally {
    busy.value = false;
  }
}

async function recreateOrder() {
  if (busy.value)
    return;
  busy.value = true;
  try {
    order.value = await createCampusTradeOrder(postId.value);
    paymentPending.value = false;
    startCountdown();
    uni.showToast({ title: '已重新生成订单', icon: 'success' });
  } catch {
    uni.showToast({ title: '重新下单失败，请稍后重试', icon: 'none' });
  } finally {
    busy.value = false;
  }
}

function copyContact() {
  if (contact.value?.contact)
    uni.setClipboardData({ data: contact.value.contact });
}
</script>

<template>
  <view class="checkout-page">
    <view v-if="loading" class="state">订单信息加载中…</view>
    <view v-else-if="loadError" class="state">订单信息暂时无法加载</view>
    <template v-else-if="post && order">
      <view class="card product-card">
        <image v-if="productImage" class="cover" :src="productImage" mode="aspectFill" />
        <view class="product-main">
          <text class="title">{{ order.title || post.title }}</text>
          <text class="meta">{{ post.tradeMode || '校内当面交易' }} · {{ post.location || post.school }}</text>
          <text class="price">¥{{ displayAmount }}</text>
        </view>
      </view>

      <view class="card order-card">
        <view class="order-row"><text>订单状态</text><text class="order-status">{{ isPaymentPending ? '支付确认中' : order.statusText }}</text></view>
        <view v-if="order.status === 0" class="order-row">
          <text>支付倒计时</text>
          <text :class="['countdown', { danger: !isWaitingPayment }]">{{ isWaitingPayment ? countdownText : '已过期' }}</text>
        </view>
        <text class="order-no">订单号：{{ order.orderNo }}</text>
      </view>

      <view class="card notice">
        <text class="notice-title">购买说明</text>
        <text>支付金额以订单中锁定的商品价格为准。</text>
        <text>支付成功后才会显示发布者预留的联系方式，请先沟通验货和交付安排。</text>
      </view>

      <view v-if="isPaid && contact?.paid" class="card contact-card">
        <text class="success">支付成功 · 联系方式已解锁</text>
        <text class="seller">发布者：{{ contact.sellerName || post.author }}</text>
        <view class="contact-value" @click="copyContact">
          <text>{{ contact.contact || '发布者尚未填写联系方式，请联系平台处理' }}</text>
          <text v-if="contact.contact" class="copy">复制</text>
        </view>
      </view>

      <button v-if="isWaitingPayment && !isPaymentPending" class="pay-button" :disabled="busy" @click="pay">
        {{ busy ? '正在发起支付…' : `微信支付 ¥${displayAmount}` }}
      </button>
      <button v-else-if="isPaymentPending" class="pay-button pending" :disabled="busy" @click="refreshPaymentStatus">
        {{ busy ? '正在确认支付…' : '刷新支付状态' }}
      </button>
      <button v-else-if="order.status === 3" class="pay-button secondary" :disabled="busy" @click="recreateOrder">
        {{ busy ? '正在重新下单…' : '订单已过期，重新下单' }}
      </button>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.checkout-page {
  min-height: 100vh;
  padding: 28rpx 28rpx 170rpx;
  box-sizing: border-box;
  background: #edf6f2;
}
.card {
  margin-bottom: 24rpx;
  padding: 28rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 28rpx;
  background: #fff;
}
.product-card {
  display: flex;
  gap: 24rpx;
}
.cover {
  width: 180rpx;
  height: 180rpx;
  flex: 0 0 auto;
  border-radius: 20rpx;
  background: #eef2ef;
}
.product-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.title {
  color: #18221f;
  font-size: 32rpx;
  font-weight: 700;
}
.meta {
  margin-top: 14rpx;
  color: #7b8581;
  font-size: 24rpx;
}
.price {
  margin-top: auto;
  color: #e44b38;
  font-size: 38rpx;
  font-weight: 800;
}
.order-card {
  display: flex;
  gap: 20rpx;
  flex-direction: column;
}
.order-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #66716d;
  font-size: 27rpx;
}
.order-status {
  color: #10a779;
  font-weight: 700;
}
.countdown {
  color: #e44b38;
  font-weight: 800;
}
.countdown.danger {
  color: #89948f;
}
.order-no {
  color: #98a09d;
  font-size: 22rpx;
}
.notice {
  display: flex;
  gap: 12rpx;
  flex-direction: column;
  color: #66716d;
  font-size: 25rpx;
  line-height: 1.6;
}
.notice-title {
  color: #26332f;
  font-size: 29rpx;
  font-weight: 700;
}
.contact-card {
  border-color: rgba(16, 167, 121, 0.25);
}
.success {
  display: block;
  color: #10a779;
  font-size: 30rpx;
  font-weight: 700;
}
.seller {
  display: block;
  margin-top: 18rpx;
  color: #65706c;
  font-size: 25rpx;
}
.contact-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 18rpx;
  background: #edf8f4;
  color: #173d31;
  font-size: 29rpx;
}
.copy {
  color: #10a779;
  font-size: 24rpx;
}
.pay-button {
  position: fixed;
  right: 28rpx;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  left: 28rpx;
  height: 94rpx;
  border-radius: 24rpx;
  background: #10a779;
  color: #fff;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 94rpx;
}
.pay-button.secondary {
  background: #6f8e83;
}
.pay-button.pending {
  background: #6f8e83;
}
.pay-button[disabled] {
  opacity: 0.65;
}
.state {
  padding: 160rpx 20rpx;
  color: #78827e;
  text-align: center;
}
</style>
