<script setup lang="ts">
import { campusChannels, campusTenants, getDefaultTenant, getPostsByTenant } from '@/mock/campus';

const activeChannel = ref('推荐');
const currentTenant = ref(getDefaultTenant());

const posts = computed(() => getPostsByTenant(currentTenant.value?.id, activeChannel.value));

function switchCampus() {
  uni.showActionSheet({
    itemList: campusTenants.map(item => `${item.name} · ${item.areaName}`),
    success: ({ tapIndex }) => {
      currentTenant.value = campusTenants[tapIndex];
      activeChannel.value = '推荐';
    },
  });
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view>
        <view class="eyebrow">当前校区</view>
        <view class="campus-name">{{ currentTenant.name }}</view>
        <view class="campus-meta">{{ currentTenant.areaName }} · 代理人 {{ currentTenant.agentName }}</view>
      </view>
      <button class="switch-btn" size="mini" @click="switchCampus">切换</button>
    </view>

    <view class="notice">{{ currentTenant.slogan }}</view>

    <scroll-view class="channel-scroll" scroll-x>
      <view class="channels">
        <view
          v-for="item in campusChannels"
          :key="item"
          class="channel"
          :class="{ active: activeChannel === item }"
          @click="activeChannel = item"
        >
          {{ item }}
        </view>
      </view>
    </scroll-view>

    <view class="feed">
      <view v-for="post in posts" :key="post.id" class="post-card">
        <view class="cover" :style="{ backgroundColor: post.coverColor }">
          <view class="cover-title">{{ post.channel }}</view>
        </view>
        <view class="post-body">
          <view class="post-title">{{ post.title }}</view>
          <view class="post-content">{{ post.content }}</view>
          <view class="tags">
            <text v-for="tag in post.tags" :key="tag">#{{ tag }}</text>
          </view>
          <view class="post-foot">
            <text>{{ post.author }}</text>
            <text v-if="post.price" class="price">￥{{ post.price }}</text>
            <text v-else>{{ post.likes }}赞</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  box-sizing: border-box;
  overflow: hidden;
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin: 24rpx;
  padding: 30rpx;
  border-radius: 28rpx;
  color: #fff;
  background: linear-gradient(135deg, #111827, #2563eb);
}

.eyebrow {
  font-size: 24rpx;
  opacity: .76;
}

.campus-name {
  margin-top: 8rpx;
  font-size: 44rpx;
  font-weight: 800;
}

.campus-meta {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: .86;
}

.switch-btn {
  margin: 0;
  color: #111827;
  background: #fff;
  border-radius: 999rpx;
}

.notice {
  margin: 0 24rpx 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  color: #475569;
  background: #fff;
  font-size: 26rpx;
}

.channel-scroll {
  white-space: nowrap;
}

.channels {
  display: flex;
  gap: 16rpx;
  padding: 4rpx 24rpx 20rpx;
}

.channel {
  flex: 0 0 auto;
  padding: 14rpx 24rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #475569;
  font-size: 26rpx;
}

.channel.active {
  background: #111827;
  color: #fff;
  font-weight: 700;
}

.feed {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  padding: 0 24rpx 32rpx;
}

.post-card {
  overflow: hidden;
  border-radius: 24rpx;
  background: #fff;
}

.cover {
  height: 170rpx;
  display: flex;
  align-items: flex-end;
  padding: 18rpx;
}

.cover-title {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .8);
  font-size: 22rpx;
}

.post-body {
  padding: 18rpx;
}

.post-title {
  color: #111827;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 1.35;
}

.post-content {
  margin-top: 10rpx;
  color: #64748b;
  font-size: 24rpx;
  line-height: 1.45;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 14rpx;
  color: #2563eb;
  font-size: 22rpx;
}

.post-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18rpx;
  color: #94a3b8;
  font-size: 22rpx;
}

.price {
  color: #ef4444;
  font-size: 28rpx;
  font-weight: 800;
}
</style>
