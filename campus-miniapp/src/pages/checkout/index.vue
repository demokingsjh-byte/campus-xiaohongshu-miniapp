<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import type { CampusTradeContact } from '@/services/api/content';
import { createCampusTradePayment, getCampusPost, getCampusTradeContact } from '@/services/api/content';

const postId = ref(0);
const post = ref<CampusPost>();
const loading = ref(true);
const paying = ref(false);
const contact = ref<CampusTradeContact>();

onLoad(async (query) => {
  postId.value = Number(query?.postId || 0);
  try {
    post.value = await getCampusPost(postId.value);
    contact.value = await getCampusTradeContact(postId.value);
  } catch {
    uni.showToast({ title: '订单信息加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

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

async function waitForPaid() {
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const result = await getCampusTradeContact(postId.value);
    if (result.paid) {
      contact.value = result;
      return true;
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  return false;
}

async function pay() {
  if (paying.value || !post.value)
    return;
  paying.value = true;
  try {
    const params = await createCampusTradePayment(postId.value);
    if (params.status !== 1)
      await requestWechatPayment(params);
    const paid = await waitForPaid();
    if (!paid) {
      uni.showModal({ title: '支付结果确认中', content: '微信已受理支付，请稍后重新进入本页面查看联系方式。', showCancel: false });
      return;
    }
    uni.showToast({ title: '支付成功', icon: 'success' });
  } catch (error: any) {
    const cancelled = String(error?.errMsg || error?.message || '').includes('cancel');
    uni.showToast({ title: cancelled ? '已取消支付' : '支付未完成，请重试', icon: 'none' });
  } finally {
    paying.value = false;
  }
}

function copyContact() {
  if (contact.value?.contact)
    uni.setClipboardData({ data: contact.value.contact });
}
</script>

<template>
  <view class="checkout-page">
    <view v-if="loading" class="state">
      订单加载中…
    </view>
    <template v-else-if="post">
      <view class="card product-card">
        <image v-if="post.coverImage" class="cover" :src="post.coverImage" mode="aspectFill" />
        <view class="product-main">
          <text class="title">
            {{ post.title }}
          </text>
          <text class="meta">
            {{ post.tradeMode || '校内当面交易' }} · {{ post.location || post.school }}
          </text>
          <text class="price">
            ¥{{ post.price }}
          </text>
        </view>
      </view>

      <view class="card notice">
        <text class="notice-title">
          购买说明
        </text>
        <text>支付金额以服务端保存的帖子价格为准。</text>
        <text>支付成功后将显示发布者预留的联系方式，请先沟通验货和交付安排。</text>
      </view>

      <view v-if="contact?.paid" class="card contact-card">
        <text class="success">
          支付成功 · 联系方式已解锁
        </text>
        <text class="seller">
          发布者：{{ contact.sellerName || post.author }}
        </text>
        <view class="contact-value" @click="copyContact">
          <text>{{ contact.contact || '发布者尚未填写联系方式，请联系平台处理' }}</text>
          <text v-if="contact.contact" class="copy">
            复制
          </text>
        </view>
      </view>

      <button v-else class="pay-button" :disabled="paying" @click="pay">
        {{ paying ? '正在确认支付…' : `微信支付 ¥${post.price}` }}
      </button>
    </template>
    <view v-else class="state">
      商品不存在或已下架
    </view>
  </view>
</template>

<style lang="scss" scoped>
.checkout-page {
  min-height: 100vh;
  padding: 28rpx;
  background: #edf6f2;
  box-sizing: border-box;
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
.pay-button[disabled] {
  opacity: 0.65;
}
.state {
  padding: 160rpx 20rpx;
  color: #78827e;
  text-align: center;
}
</style>
