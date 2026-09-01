<script lang="ts" setup>
import type { CampusTradeContact, CampusTradeOrder } from '@/services/api/content';
import TradeChatPanel from '@/components/TradeChatPanel/index.vue';
import { getCampusTradeContact, getCampusTradeOrder } from '@/services/api/content';
import { useUserStore } from '@/stores/modules/user';

const orderId = ref(0);
const order = ref<CampusTradeOrder>();
const contact = ref<CampusTradeContact>();
const loading = ref(true);
const loadFailed = ref(false);
const userStore = useUserStore();

const isBuyer = computed(() => Number(userStore.userInfo?.id) === Number(order.value?.buyerId));
const isErrand = computed(() => order.value?.bizType === 4);
const participantName = computed(() => contact.value?.participantName || (isBuyer.value
  ? (contact.value?.sellerName || order.value?.sellerName || (isErrand.value ? '接单同学' : '卖家'))
  : (order.value?.buyerName || (isErrand.value ? '任务发布人' : '买家'))));

onLoad(async (query) => {
  orderId.value = Number(query?.orderId || 0);
  await loadPage();
});

async function loadPage() {
  if (!orderId.value) {
    loadFailed.value = true;
    loading.value = false;
    return;
  }
  loading.value = true;
  loadFailed.value = false;
  try {
    await userStore.initUserInfo();
    order.value = await getCampusTradeOrder(orderId.value);
    uni.setNavigationBarTitle({ title: order.value.bizType === 4 ? '任务沟通' : '交易咨询' });
    try {
      contact.value = await getCampusTradeContact(orderId.value);
    } catch {
      // 电话信息不可用时，订单双方仍可通过站内交易会话沟通。
      contact.value = undefined;
    }
  } catch {
    loadFailed.value = true;
  } finally {
    loading.value = false;
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
</script>

<template>
  <view class="consultation-page">
    <view v-if="loading" class="page-state">咨询入口加载中…</view>
    <view v-else-if="loadFailed || !order" class="page-state">
      <text>咨询入口暂时无法加载</text>
      <button class="retry-button" @click="loadPage">重新加载</button>
    </view>
    <template v-else>
      <view class="consultation-header">
        <text class="consultation-label">{{ isErrand ? '任务双方沟通' : '联系该用户' }}</text>
        <text class="participant-name">{{ participantName }}</text>
        <text class="order-title">{{ isErrand ? '代办任务' : '订单商品' }}：{{ order.title }}</text>
        <view v-if="contact?.contact" class="phone-row">
          <text class="phone-number" @click="copyContact">{{ contact.contact }}</text>
          <text class="phone-action" @click="copyContact">复制</text>
          <text class="phone-action call" @click="callContact">拨打</text>
        </view>
        <text v-else class="phone-tip">联系电话暂未填写，可先通过下方订单对话联系对方。</text>
      </view>

      <TradeChatPanel
        :order-id="order.id" :current-user-id="userStore.userInfo?.id"
        :participant-name="participantName" :readonly="![1, 2].includes(order.status)"
        :scene="isErrand ? 'errand' : 'trade'"
      />
    </template>
  </view>
</template>

<style lang="scss" scoped>
.consultation-page {
  min-height: 100vh;
  padding: 28rpx 28rpx calc(36rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #f4f4f4;
}

.consultation-header {
  margin-bottom: 28rpx;
  padding: 30rpx 26rpx;
  border-radius: 30rpx;
  background: #fff;
}

.consultation-label,
.participant-name,
.order-title,
.phone-tip {
  display: block;
}

.consultation-label {
  color: #168c65;
  font-size: 24rpx;
}

.participant-name {
  margin-top: 10rpx;
  color: #202321;
  font-size: 34rpx;
  font-weight: 600;
}

.order-title {
  overflow: hidden;
  margin-top: 12rpx;
  color: #8b918d;
  font-size: 23rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.phone-row {
  display: flex;
  align-items: center;
  margin-top: 22rpx;
  gap: 20rpx;
}

.phone-number {
  min-width: 0;
  flex: 1;
  color: #303532;
  font-size: 28rpx;
}

.phone-action {
  color: #168c65;
  font-size: 24rpx;
}

.phone-action.call {
  color: #2483ee;
}

.phone-tip {
  margin-top: 20rpx;
  color: #929894;
  font-size: 23rpx;
  line-height: 1.5;
}

.page-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  color: #858c88;
  font-size: 26rpx;
  flex-direction: column;
}

.retry-button {
  height: 68rpx;
  margin-top: 28rpx;
  padding: 0 32rpx;
  border: 0;
  border-radius: 22rpx;
  color: #17200f;
  background: #95f51f;
  font-size: 25rpx;
  line-height: 68rpx;
}

.retry-button::after {
  border: 0;
}
</style>
