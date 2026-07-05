<script lang="ts" setup>
import { getDefaultTenant } from '@/mock/campus';

const loggedIn = ref(false);
const userInfo = ref({ nickname: '校园体验用户', avatar: '' });
const currentTenant = ref(getDefaultTenant());

function handleLoginOut() {
  loggedIn.value = false;
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' });
}
</script>

<template>
  <view class="page">
    <view class="profile-card">
      <image class="avatar" :src="userInfo?.avatar || '/static/images/avatar.png'" mode="aspectFill" />
      <view class="profile-main">
        <view class="nickname">{{ loggedIn ? userInfo?.nickname : '校园体验用户' }}</view>
        <view class="hint">{{ loggedIn ? '已登录' : '先体验页面，后续接微信授权登录' }}</view>
      </view>
      <button v-if="loggedIn" class="plain-btn" size="mini" @click="handleLoginOut">退出</button>
      <button v-else class="plain-btn" size="mini" @click="goLogin">登录</button>
    </view>

    <view class="tenant-card">
      <view class="label">当前校区租户</view>
      <view class="tenant-name">{{ currentTenant.name }}</view>
      <view class="tenant-meta">{{ currentTenant.areaName }} · 代理人 {{ currentTenant.agentName }}</view>
    </view>

    <view class="stats">
      <view class="stat-item">
        <view class="stat-value">12</view>
        <view class="stat-label">我的发布</view>
      </view>
      <view class="stat-item">
        <view class="stat-value">3</view>
        <view class="stat-label">收藏校区</view>
      </view>
      <view class="stat-item">
        <view class="stat-value">0.00</view>
        <view class="stat-label">待结算</view>
      </view>
    </view>

    <view class="menu">
      <view class="menu-item">
        <text>校区代理中心</text>
        <text class="menu-desc">分润、邀请和校区数据后续接入</text>
      </view>
      <view class="menu-item">
        <text>我的交易</text>
        <text class="menu-desc">闲置订单、退款售后</text>
      </view>
      <view class="menu-item">
        <text>客服与反馈</text>
        <text class="menu-desc">处理校区运营问题</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f7f8fa;
}

.profile-card,
.tenant-card,
.stats,
.menu {
  border-radius: 28rpx;
  background: #fff;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx;
}

.avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 52rpx;
}

.profile-main {
  flex: 1;
}

.nickname {
  color: #111827;
  font-size: 34rpx;
  font-weight: 800;
}

.hint,
.label,
.tenant-meta,
.menu-desc,
.stat-label {
  color: #64748b;
  font-size: 24rpx;
}

.plain-btn {
  margin: 0;
  border-radius: 999rpx;
}

.tenant-card {
  margin-top: 22rpx;
  padding: 28rpx;
}

.tenant-name {
  margin-top: 10rpx;
  color: #111827;
  font-size: 38rpx;
  font-weight: 800;
}

.tenant-meta {
  margin-top: 10rpx;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 22rpx;
  padding: 24rpx 0;
}

.stat-item {
  text-align: center;
}

.stat-value {
  color: #111827;
  font-size: 34rpx;
  font-weight: 800;
}

.menu {
  margin-top: 22rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 26rpx 28rpx;
  border-bottom: 1rpx solid #edf2f7;
  color: #111827;
  font-size: 28rpx;
  font-weight: 700;
}
</style>
