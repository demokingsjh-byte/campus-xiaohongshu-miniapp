<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';

defineProps<{ post: CampusPost }>();

function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` });
}
</script>

<template>
  <view class="post-card" @click="openDetail(post.id)">
    <view class="cover" :class="`cover-${post.height}`" :style="{ background: post.coverColor }">
      <text class="channel-badge">{{ post.channel }}</text>
      <view class="cover-art">
        <view class="art-block" />
        <view class="art-line line-one" />
        <view class="art-line line-two" />
        <text>{{ post.channel }}</text>
      </view>
      <text class="cover-label">{{ post.coverLabel }}</text>
    </view>
    <view class="body">
      <view class="post-title">{{ post.title }}</view>
      <view class="meta-line">
        <view v-if="post.price" class="price"><text class="yen">¥</text>{{ post.price }}</view>
        <text v-else class="tag"># {{ post.tags[0] }}</text>
        <text class="distance">校内</text>
      </view>
      <view class="author-row">
        <view class="avatar">{{ post.avatarText }}</view>
        <text class="author">{{ post.author }}</text>
        <text class="like">♡ {{ post.likes }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.post-card {
  overflow: hidden;
  margin-bottom: 16rpx;
  border: 1rpx solid #e5e5df;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 5rpx 18rpx rgba(28, 45, 40, 0.045);
}
.cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-short { height: 206rpx; }
.cover-medium { height: 250rpx; }
.cover-tall { height: 286rpx; }
.channel-badge,
.cover-label {
  position: absolute;
  z-index: 2;
  left: 14rpx;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
  font-size: 19rpx;
  font-weight: 600;
}
.channel-badge { top: 14rpx; color: #fff; background: rgba(24, 32, 30, 0.72); }
.cover-label { bottom: 14rpx; color: #24332f; background: rgba(255, 255, 255, 0.92); }
.cover-art {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  border: 3rpx solid rgba(24, 65, 57, 0.18);
  border-radius: 32rpx;
  color: #fff;
  background: #177d73;
  box-shadow: 0 12rpx 24rpx rgba(28, 69, 61, 0.14);
  transform: rotate(-4deg);
}
.cover-art text { position: relative; z-index: 2; font-size: 25rpx; font-weight: 800; letter-spacing: 2rpx; }
.art-block { position: absolute; top: 16rpx; right: 16rpx; width: 24rpx; height: 24rpx; border-radius: 8rpx; background: #ff806c; }
.art-line { position: absolute; left: 18rpx; width: 45rpx; height: 6rpx; border-radius: 8rpx; background: rgba(255, 255, 255, 0.28); }
.line-one { bottom: 22rpx; }
.line-two { bottom: 34rpx; width: 30rpx; }
.body { padding: 17rpx 17rpx 16rpx; }
.post-title {
  display: -webkit-box;
  overflow: hidden;
  color: #18201e;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 1.42;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.meta-line { display: flex; align-items: center; justify-content: space-between; min-height: 38rpx; margin-top: 11rpx; }
.tag { color: #0f766e; font-size: 21rpx; }
.distance { color: #9aa39f; font-size: 19rpx; }
.price { color: #ff6b5e; font-size: 30rpx; font-weight: 800; }
.yen { margin-right: 2rpx; font-size: 21rpx; }
.author-row { display: flex; align-items: center; margin-top: 12rpx; padding-top: 12rpx; border-top: 1rpx solid #f0eee8; color: #7a8581; font-size: 20rpx; }
.avatar { display: flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; margin-right: 8rpx; border-radius: 50%; color: #0f766e; background: #e8f5f1; font-size: 18rpx; font-weight: 700; }
.author { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.like { flex: 0 0 auto; }
</style>
