<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import type { CampusTradeContact, CampusTradeOrder } from '@/services/api/content';
import TradeChatPanel from '@/components/TradeChatPanel/index.vue';
import {
  cancelCampusTradeOrder,
  createCampusErrandOrder,
  createCampusTradeOrder,
  createCampusTradePayment,
  getCampusPost,
  getCampusTradeContact,
  getCampusTradeOrder,
  getCampusTradePaymentStatus,
} from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { resolveCampusMediaUrl } from '@/utils/avatar';

const postId = ref(0);
const orderId = ref(0);
const checkoutMode = ref<'trade' | 'errand'>('trade');
const post = ref<CampusPost>();
const order = ref<CampusTradeOrder>();
const contact = ref<CampusTradeContact>();
const loading = ref(true);
const busy = ref(false);
const paymentPending = ref(false);
const paymentTimedOut = ref(false);
const showPurchaseSuccess = ref(false);
const remainingSeconds = ref(0);
const loadError = ref(false);
const userStore = useUserStore();
const contentStore = useCampusContentStore();
let countdownTimer: ReturnType<typeof setInterval> | null = null;
let paymentPollingTimer: ReturnType<typeof setInterval> | null = null;
let paymentPollingBusy = false;
let paymentSubmittedAt = 0;
const PAYMENT_CONFIRMATION_GRACE_MS = 30_000;

const isErrand = computed(() => checkoutMode.value === 'errand' || order.value?.bizType === 4);
const isPaid = computed(() => Boolean(order.value?.paidAt) || [1, 2, 4].includes(Number(order.value?.status)));
const isWaitingPayment = computed(() => order.value?.status === 0 && remainingSeconds.value > 0);
const isPaymentPending = computed(() => paymentPending.value && order.value?.status === 0);
const isBuyer = computed(() => Number(userStore.userInfo?.id) === Number(order.value?.buyerId));
const participantName = computed(() => isBuyer.value
  ? (contact.value?.sellerName || order.value?.sellerName || '卖家')
  : (order.value?.buyerName || '买家'));
const productImage = computed(() => resolveCampusMediaUrl(order.value?.coverImage || post.value?.coverImage || post.value?.images?.[0] || ''));
const countdownText = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60).toString().padStart(2, '0');
  const seconds = (remainingSeconds.value % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
});
const displayAmount = computed(() => Number(order.value?.amount ?? post.value?.price ?? 0).toFixed(2));

onLoad(async (query) => {
  postId.value = Number(query?.postId || 0);
  orderId.value = Number(query?.orderId || 0);
  checkoutMode.value = query?.mode === 'errand' ? 'errand' : 'trade';
  await loadCheckout();
  if (isErrand.value)
    uni.setNavigationBarTitle({ title: '支付任务赏金' });
});

onShow(() => {
  if (paymentPending.value && order.value?.status === 0)
    startPaymentPolling();
});

onUnload(() => {
  stopCountdown();
  stopPaymentPolling();
});

async function loadCheckout() {
  if (!postId.value && !orderId.value) {
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
        content: isErrand.value ? '登录后才能支付任务赏金。' : '登录后才能创建二手交易订单。',
        showCancel: false,
        success: () => uni.navigateBack(),
      });
      return;
    }
    if (orderId.value) {
      order.value = await getCampusTradeOrder(orderId.value);
      if (order.value.bizType === 4)
        checkoutMode.value = 'errand';
      postId.value = order.value.postId;
      // Keep order history readable even when the original listing is gone.
      try {
        post.value = await getCampusPost(postId.value);
      } catch {
        post.value = undefined;
      }
    } else {
      post.value = await getCampusPost(postId.value);
      order.value = await createCampusTradeOrder(postId.value);
    }
    startCountdown();
    if (isPaid.value && !isErrand.value) {
      try {
        await loadContact();
      } catch {
        // 联系电话是支付后的增强信息；接口升级期间仍应正常展示订单和在线咨询入口。
        contact.value = undefined;
      }
    } else if (order.value.status === 0 && isBuyer.value) {
      // Reconcile once when reopening the page. If WeChat still says NOTPAY,
      // keep the payment button available; the backend rechecks the existing
      // WeChat order before creating another prepay order.
      const result = await getCampusTradePaymentStatus(order.value.id);
      if (result.paid)
        await applyPaidOrder(result.paidAt);
      else if (result.status === 3)
        order.value = await getCampusTradeOrder(order.value.id);
    }
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

function stopPaymentPolling() {
  if (paymentPollingTimer) {
    clearInterval(paymentPollingTimer);
    paymentPollingTimer = null;
  }
  paymentPollingBusy = false;
}

function startPaymentPolling() {
  if (paymentPollingTimer || !order.value || order.value.status !== 0)
    return;
  let attempts = 0;
  const poll = async () => {
    if (!order.value || order.value.status !== 0 || attempts >= 90) {
      stopPaymentPolling();
      return;
    }
    if (paymentPollingBusy)
      return;
    paymentPollingBusy = true;
    attempts += 1;
    try {
      const result = await getCampusTradePaymentStatus(order.value.id);
      if (result.paid) {
        await applyPaidOrder(result.paidAt);
        stopPaymentPolling();
      } else if (result.status === 3 || (result.retryable && !shouldKeepConfirming(result))) {
        paymentPending.value = false;
        paymentSubmittedAt = 0;
        stopPaymentPolling();
      }
    } catch {
      // Keep polling briefly: the WeChat callback/query can arrive later.
    } finally {
      paymentPollingBusy = false;
    }
  };
  void poll();
  paymentPollingTimer = setInterval(() => void poll(), 2000);
}

function shouldKeepConfirming(result: Awaited<ReturnType<typeof getCampusTradePaymentStatus>>) {
  return result.wechatTradeState === 'NOTPAY'
    && paymentSubmittedAt > 0
    && Date.now() - paymentSubmittedAt < PAYMENT_CONFIRMATION_GRACE_MS;
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
  paymentTimedOut.value = false;
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
        paymentSubmittedAt = 0;
        stopPaymentPolling();
        return false;
      }
      if (result.retryable && !shouldKeepConfirming(result)) {
        paymentPending.value = false;
        paymentSubmittedAt = 0;
        stopPaymentPolling();
        return false;
      }
    } catch {
      // Payment callbacks and WeChat order queries are asynchronous. Keep the
      // page in a recoverable confirmation state instead of starting payment again.
    }
    if (attempt < maxAttempts - 1)
      await new Promise(resolve => setTimeout(resolve, 800));
  }
  paymentTimedOut.value = true;
  // A delayed callback/query is not a payment failure. Keep the order locked
  // in confirmation mode and continue the background reconciliation.
  paymentPending.value = true;
  startPaymentPolling();
  return false;
}

async function applyPaidOrder(paidAt?: string) {
  if (!order.value)
    return;
  const wasPaid = order.value.status === 1;
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
  paymentTimedOut.value = false;
  paymentSubmittedAt = 0;
  stopPaymentPolling();
  if (!isErrand.value) {
    // 支付确认后立即同步最新库存；Pinia 会把售罄商品从首页缓存移除，
    // 多库存商品则只更新剩余数量，不必等用户重新进入首页。
    try {
      post.value = await contentStore.loadPost(postId.value);
    } catch {
      // 订单快照仍可保证购买历史可读，列表页下次 onShow 会重新拉取。
    }
    try {
      await loadContact();
    } catch {
      contact.value = undefined;
    }
  }
  if (!wasPaid)
    openPurchaseSuccess();
}

function openPurchaseSuccess() {
  showPurchaseSuccess.value = true;
  uni.setNavigationBarTitle({ title: '' });
  uni.setNavigationBarColor({ frontColor: '#000000', backgroundColor: '#edfbf0' });
}

function buyAgain() {
  if (isErrand.value) {
    uni.redirectTo({ url: `/pages/detail/index?id=${postId.value}&mine=1` });
    return;
  }
  uni.switchTab({ url: '/pages/index/index' });
}

async function loadContact() {
  if (!order.value)
    return;
  contact.value = await getCampusTradeContact(order.value.id);
}

async function pay() {
  if (!order.value || !isWaitingPayment.value || paymentPending.value || busy.value)
    return;
  busy.value = true;
  let paymentWasInvoked = false;
  try {
    paymentTimedOut.value = false;
    const params = await createCampusTradePayment(order.value.id);
    if (params.status === 1) {
      await applyPaidOrder();
      return;
    }
    if (!params.packageValue)
      throw new Error('后端未返回微信支付参数');
    paymentWasInvoked = true;
    if (params.packageValue)
      await requestWechatPayment(params);
    paymentSubmittedAt = Date.now();
    paymentPending.value = true;
    startPaymentPolling();
    const paid = await syncPaymentStatus();
    if (!paid) {
      uni.showToast({
        title: paymentPending.value ? '支付已提交，正在确认订单状态' : '微信支付未完成，请重新支付',
        icon: 'none',
      });
    }
  } catch (error: any) {
    const message = String(error?.errMsg || error?.message || '').toLowerCase();
    if (message.includes('cancel')) {
      paymentSubmittedAt = 0;
      paymentPending.value = false;
      stopPaymentPolling();
      uni.showToast({ title: '已取消支付', icon: 'none' });
    } else if (paymentWasInvoked) {
      // requestPayment may fail after WeChat has accepted the payment. Always
      // query the server once more before deciding that the payment failed.
      paymentSubmittedAt = Date.now();
      paymentPending.value = true;
      startPaymentPolling();
      const paid = await syncPaymentStatus(4);
      if (!paid && !paymentPending.value) {
        uni.showToast({ title: '微信支付未完成，请重新支付', icon: 'none' });
      } else {
        const detail = String(error?.errMsg || error?.message || '微信支付调用失败，请稍后刷新支付状态').slice(0, 120);
        uni.showModal({ title: '微信支付调用失败', content: detail, showCancel: false });
      }
    } else {
      const detail = String(error?.errMsg || error?.message || '发起微信支付失败，请稍后重试').slice(0, 120);
      uni.showModal({ title: '发起支付失败', content: detail, showCancel: false });
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
    if (!paid && order.value?.status === 0 && paymentPending.value) {
      uni.showToast({ title: paymentTimedOut.value ? '支付状态暂未确认，请稍后刷新' : '支付状态仍在确认，请稍后刷新', icon: 'none' });
    } else if (order.value?.status === 0) {
      uni.showToast({ title: '微信支付未完成，请重新支付', icon: 'none' });
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
    stopPaymentPolling();
    order.value = isErrand.value
      ? await createCampusErrandOrder(postId.value)
      : await createCampusTradeOrder(postId.value);
    paymentPending.value = false;
    paymentTimedOut.value = false;
    paymentSubmittedAt = 0;
    startCountdown();
    uni.showToast({ title: '已重新生成订单', icon: 'success' });
  } catch {
    uni.showToast({ title: '重新下单失败，请稍后重试', icon: 'none' });
  } finally {
    busy.value = false;
  }
}

async function cancelCurrentOrder() {
  if (!order.value || order.value.status !== 0 || busy.value || paymentPending.value)
    return;
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '取消订单',
      content: '确定取消当前订单吗？取消后可以重新下单。',
      success: result => resolve(!!result.confirm),
      fail: () => resolve(false),
    });
  });
  if (!confirmed)
    return;
  busy.value = true;
  try {
    await cancelCampusTradeOrder(order.value.id);
    try {
      order.value = await getCampusTradeOrder(order.value.id);
    } catch {
      // The cancel request may have succeeded while the follow-up read is
      // briefly unavailable. Reflect the successful local action immediately.
      order.value = {
        ...order.value,
        status: 3,
        statusText: '订单已关闭',
        expired: true,
        closeReason: 'USER_CANCEL',
      };
    }
    paymentPending.value = false;
    paymentTimedOut.value = false;
    paymentSubmittedAt = 0;
    stopCountdown();
    stopPaymentPolling();
    uni.showToast({ title: '订单已取消', icon: 'success' });
  } catch {
    uni.showToast({ title: '取消订单失败，请稍后重试', icon: 'none' });
  } finally {
    busy.value = false;
  }
}

function copyContact() {
  if (contact.value?.contact)
    uni.setClipboardData({ data: contact.value.contact });
}

function callContact() {
  if (contact.value?.contact)
    uni.makePhoneCall({ phoneNumber: contact.value.contact });
}

function openConsultation() {
  if (!order.value)
    return;
  uni.navigateTo({ url: `/pages/trade-chat/index?orderId=${order.value.id}` });
}
</script>

<template>
  <view class="checkout-page" :class="{ 'purchase-success-page': showPurchaseSuccess }">
    <view v-if="showPurchaseSuccess" class="purchase-success-state">
      <view class="purchase-success-hero">
        <view class="purchase-success-title">
          <view class="purchase-success-check">✓</view>
          <text>{{ isErrand ? '赏金支付成功' : '购买成功' }}</text>
        </view>
        <text class="purchase-success-desc">{{ isErrand ? '任务已进入待接单状态，24 小时无人接单将自动退款' : '付款已确认，请联系卖家沟通验货和交付' }}</text>
      </view>
      <view v-if="!isErrand && order" class="card consultation-entry success-contact-card" @click="openConsultation">
        <view class="consultation-icon">✉</view>
        <view class="consultation-main">
          <text class="consultation-title">联系该用户</text>
          <text class="consultation-user">{{ participantName }}</text>
          <text class="consultation-tip">进入小程序咨询入口，沟通验货、取货和交付</text>
          <view v-if="contact?.contact" class="consultation-phone" @click.stop>
            <text @click="copyContact">{{ contact.contact }}</text>
            <text class="copy" @click="copyContact">复制</text>
            <text class="call" @click="callContact">拨打</text>
          </view>
        </view>
        <text class="consultation-arrow">›</text>
      </view>
      <TradeChatPanel
        v-if="order && isPaid && !isErrand" class="success-chat" :order-id="order.id"
        :current-user-id="userStore.userInfo?.id" :participant-name="participantName"
        :readonly="![1, 2].includes(order.status)"
      />
      <button class="purchase-again" @click="buyAgain">{{ isErrand ? '查看任务进度' : '退出，继续逛校园' }}</button>
    </view>
    <view v-else-if="loading" class="state">
      订单信息加载中…
    </view>
    <view v-else-if="loadError" class="state">
      订单信息暂时无法加载
    </view>
    <template v-else-if="order">
      <view class="card product-card">
        <image v-if="productImage" class="cover" :src="productImage" mode="aspectFill" />
        <view class="product-main">
          <text class="title">
            {{ order.title || post?.title }}
          </text>
          <text class="meta">
            {{ post?.tradeMode || '校内自提' }}
          </text>
          <text class="price">
            ¥{{ displayAmount }}
          </text>
        </view>
      </view>

      <view class="card order-card">
        <view class="order-row">
          <text>订单状态</text><text class="order-status">
            {{ isPaymentPending ? (paymentTimedOut ? '支付状态待确认' : '支付确认中') : order.statusText }}
          </text>
        </view>
        <view v-if="order.status === 0" class="order-row">
          <text>支付倒计时</text>
          <text class="countdown" :class="[{ danger: !isWaitingPayment }]">
            {{ isWaitingPayment ? countdownText : '已过期' }}
          </text>
        </view>
        <view class="order-row order-number-row">
          <text>订单编号</text><text class="order-no">
            {{ order.orderNo }} ｜ 复制
          </text>
        </view>
      </view>

      <view class="card notice">
        <text class="notice-title">
          {{ isErrand ? '代办说明' : '购买说明' }}
        </text>
        <template v-if="isErrand">
          <text>赏金由任务发布人支付，付款成功后其他同校学生才能接单。</text>
          <text>24 小时无人接单会自动原路退款；接单后，发布人确认完成才会把收益结算给接单人。</text>
        </template>
        <template v-else>
          <text>支付金额以订单中锁定的商品价格为准。</text>
          <text>支付成功后才会显示发布者预留的联系方式，请先沟通验货和交付安排。</text>
        </template>
      </view>

      <view v-if="!isErrand && isPaid && contact?.paid" class="card contact-card">
        <text class="success">
          支付成功 · 联系方式已解锁
        </text>
        <text class="seller">
          发布者：{{ contact.sellerName || post?.author || order.sellerName || '校园同学' }}
        </text>
        <view class="contact-value" @click="copyContact">
          <text>{{ contact.contact || '发布者尚未填写联系方式，请联系平台处理' }}</text>
          <view v-if="contact.contact" class="contact-actions">
            <text class="copy">复制</text>
            <text class="call" @click.stop="callContact">拨打</text>
          </view>
        </view>
      </view>

      <TradeChatPanel
        v-if="isPaid && !isErrand" :order-id="order.id" :current-user-id="userStore.userInfo?.id"
        :participant-name="participantName" :readonly="![1, 2].includes(order.status)"
      />

      <view v-if="isBuyer" class="action-bar">
        <button v-if="isWaitingPayment && !isPaymentPending" class="pay-button" :disabled="busy" @click="pay">
          {{ busy ? '正在发起支付…' : `微信支付 ¥${displayAmount}` }}
        </button>
        <button v-if="isWaitingPayment && !isPaymentPending" class="cancel-button" :disabled="busy" @click="cancelCurrentOrder">
          取消订单
        </button>
        <button v-else-if="isPaymentPending" class="pay-button pending" :disabled="busy" @click="refreshPaymentStatus">
          {{ busy ? '正在确认支付…' : '刷新支付状态' }}
        </button>
        <button v-else-if="order.status === 3" class="pay-button secondary" :disabled="busy" @click="recreateOrder">
          {{ busy ? '正在重新下单…' : '订单已过期，重新下单' }}
        </button>
      </view>
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
.contact-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  margin-left: 20rpx;
  gap: 20rpx;
}
.call {
  color: #2483ee;
  font-size: 24rpx;
}
.pay-button {
  flex: 1;
  height: 94rpx;
  border-radius: 24rpx;
  background: #10a779;
  color: #fff;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 94rpx;
}
.action-bar {
  position: fixed;
  right: 28rpx;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  left: 28rpx;
  display: flex;
  gap: 18rpx;
}
.cancel-button {
  width: 190rpx;
  height: 94rpx;
  flex: 0 0 auto;
  border-radius: 24rpx;
  background: #fff;
  color: #65706c;
  font-size: 28rpx;
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

/* 蓝湖原型：确认购买 */
.checkout-page {
  min-height: 100vh;
  padding: 32rpx 32rpx calc(156rpx + env(safe-area-inset-bottom));
  color: #202321;
  background: #f4f4f4;
}

.card {
  margin-bottom: 32rpx;
  padding: 24rpx;
  border: 0;
  border-radius: 32rpx;
  background: #fff;
}

.product-card {
  min-height: 238rpx;
  gap: 32rpx;
  box-sizing: border-box;
}

.cover {
  width: 176rpx;
  height: 176rpx;
  border-radius: 20rpx;
}

.title {
  display: -webkit-box;
  overflow: hidden;
  color: #1e211f;
  font-size: 31rpx;
  font-weight: 600;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.meta {
  align-self: flex-start;
  height: 48rpx;
  margin-top: 20rpx;
  padding: 0 14rpx;
  border-radius: 13rpx;
  color: #ff8c16;
  background: #fff5e6;
  font-size: 25rpx;
  line-height: 48rpx;
}

.price {
  margin-top: 26rpx;
  color: #ff4d55;
  font-size: 48rpx;
  font-weight: 650;
}

.price::first-letter {
  font-size: 23rpx;
}

.order-card {
  gap: 0;
  padding: 10rpx 24rpx;
}

.order-row {
  min-height: 74rpx;
  color: #202321;
  font-size: 28rpx;
}

.order-status {
  padding: 5rpx 10rpx;
  border: 2rpx solid #b8d8ff;
  border-radius: 10rpx;
  color: #2483ee;
  background: #eef6ff;
  font-size: 23rpx;
  font-weight: 550;
}

.countdown {
  color: #ff4d55;
  font-size: 29rpx;
  font-weight: 500;
}

.order-number-row {
  align-items: center;
}

.order-no {
  overflow: hidden;
  max-width: 470rpx;
  color: #969a97;
  font-size: 24rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice {
  gap: 10rpx;
  padding: 26rpx 24rpx 30rpx;
  color: #959996;
  font-size: 26rpx;
  line-height: 1.75;
}

.notice-title {
  margin-bottom: 14rpx;
  color: #202321;
  font-size: 32rpx;
  font-weight: 600;
}

.action-bar {
  z-index: 40;
  right: 0;
  bottom: 0;
  left: 0;
  height: calc(146rpx + env(safe-area-inset-bottom));
  padding: 20rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
  gap: 24rpx;
  background: #fff;
  box-sizing: border-box;
}

.pay-button,
.cancel-button {
  height: 84rpx;
  margin: 0;
  padding: 0;
  border-radius: 28rpx;
  font-weight: 600;
  line-height: 84rpx;
}

.pay-button {
  color: #142008;
  background: #95f51f;
  font-size: 30rpx;
}

.cancel-button {
  width: 184rpx;
  border: 2rpx solid #eceeec;
  color: #949895;
  background: #fff;
  font-size: 27rpx;
}

.pay-button::after,
.cancel-button::after {
  border: 0;
}

.checkout-page.purchase-success-page {
  padding: 0 32rpx calc(48rpx + env(safe-area-inset-bottom));
  background: #f4f4f4;
}

.purchase-success-state {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  min-height: 100vh;
  padding-top: 112rpx;
  flex-direction: column;
}

.purchase-success-hero {
  display: flex;
  align-items: center;
  margin-bottom: 48rpx;
  flex-direction: column;
}

.success-contact-card,
.success-chat {
  width: 100%;
  box-sizing: border-box;
}

.consultation-entry {
  display: flex;
  align-items: center;
  padding: 28rpx 24rpx;
}

.consultation-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  flex: 0 0 auto;
  border-radius: 24rpx;
  color: #168c65;
  background: #ecf9f4;
  font-size: 34rpx;
}

.consultation-main {
  min-width: 0;
  margin-left: 22rpx;
  flex: 1;
}

.consultation-title,
.consultation-user,
.consultation-tip {
  display: block;
}

.consultation-title {
  color: #202321;
  font-size: 32rpx;
  font-weight: 600;
}

.consultation-user {
  margin-top: 6rpx;
  color: #4d5551;
  font-size: 25rpx;
}

.consultation-tip {
  margin-top: 8rpx;
  color: #929894;
  font-size: 22rpx;
  line-height: 1.5;
}

.consultation-phone {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
  gap: 18rpx;
  color: #303532;
  font-size: 25rpx;
}

.consultation-arrow {
  margin-left: 12rpx;
  color: #a5aaa7;
  font-size: 48rpx;
  line-height: 1;
}

.purchase-success-title {
  display: flex;
  align-items: center;
  justify-content: center;
}

.purchase-success-check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38rpx;
  height: 38rpx;
  margin-right: 18rpx;
  border-radius: 50%;
  color: #1f1f1f;
  background: #95f51f;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 38rpx;
}

.purchase-success-title text {
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 42rpx;
  font-weight: 600;
  line-height: 52rpx;
}

.purchase-success-desc {
  margin-top: 34rpx;
  color: #999d9a;
  font-family: "PingFang SC", sans-serif;
  font-size: 27rpx;
  line-height: 38rpx;
}

.purchase-again {
  min-width: 284rpx;
  height: 64rpx;
  margin: 0 auto;
  padding: 0 26rpx;
  border: 2rpx solid #dedfdd;
  border-radius: 23rpx;
  color: #1f1f1f;
  background: #f4f4f4;
  box-shadow: none;
  font-size: 27rpx;
  font-weight: 500;
  line-height: 60rpx;
}

.purchase-again::after {
  border: 0;
}
</style>
