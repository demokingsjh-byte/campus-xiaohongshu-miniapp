<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';

const props = defineProps<{ post: CampusPost }>();
const channelClasses: Record<string, string> = {
  二手: 'idle',
  互助: 'help',
  拼车: 'ride',
  探店: 'shop',
  失物: 'lost',
  社团: 'club',
};
const channelClass = computed(() => `channel-${channelClasses[props.post.channel] || 'other'}`);
const channelIcons: Record<string, string> = {
  二手: '/static/icons/login/trade.svg',
  互助: '/static/icons/login/help.svg',
  拼车: '/static/icons/publish/ride.svg',
  探店: '/static/icons/publish/shop.svg',
  失物: '/static/icons/publish/lost.svg',
  社团: '/static/icons/login/event.svg',
};
const channelIcon = computed(() => channelIcons[props.post.channel] || '/static/icons/mine/cloud.svg');

function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` });
}
</script>

<template>
  <view class="post-card" @click="openDetail(post.id)">
    <view class="cover" :class="[`cover-${post.height}`, channelClass]" :style="{ background: post.coverColor }">
      <image v-if="post.coverImage" class="cover-image" :src="post.coverImage" mode="aspectFill" />
      <view v-if="post.coverImage" class="image-shade" />
      <view class="cover-glow glow-one" />
      <view class="cover-glow glow-two" />
      <text class="channel-badge">
        {{ post.channel }}
      </text>
      <view v-if="!post.coverImage" class="cover-art">
        <view class="photo-corner" />
        <image class="cover-icon" :src="channelIcon" mode="aspectFit" />
        <view class="photo-caption">
          {{ post.school.slice(0, 2) }} · 同校
        </view>
      </view>
      <text class="cover-label">
        {{ post.coverLabel }}
      </text>
      <text v-if="post.coverImage" class="photo-count">
        1/3
      </text>
    </view>
    <view class="body">
      <view class="post-title">
        {{ post.title }}
      </view>
      <view class="meta-line">
        <view v-if="post.price" class="price">
          <text class="yen">
            ¥
          </text>{{ post.price }}
        </view>
        <text v-else class="tag">
          # {{ post.tags[0] }}
        </text>
        <text class="distance">
          校内
        </text>
      </view>
      <view class="author-row">
        <view class="avatar">
          <image src="/static/icons/ui/avatar-default.svg" mode="aspectFill" />
        </view>
        <text class="author">
          {{ post.author }} · {{ post.time }}
        </text>
        <text class="like">
          <image src="/static/icons/mine/heart.svg" mode="aspectFit" />{{ post.likes }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.post-card {
  overflow: hidden;
  margin-bottom: 14rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 24rpx;
  background: var(--yd-card);
  box-shadow: 0 14rpx 34rpx rgba(33, 50, 86, 0.09);
  backdrop-filter: blur(24rpx) saturate(145%);
  -webkit-backdrop-filter: blur(24rpx) saturate(145%);
}
.cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  isolation: isolate;
}
.cover::after {
  position: absolute;
  z-index: 0;
  inset: 0;
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  content: '';
}
.cover-image {
  position: absolute;
  z-index: 0;
  width: 100%;
  height: 100%;
}
.image-shade {
  position: absolute;
  z-index: 1;
  inset: 0;
  background: rgba(16, 28, 52, 0.05);
}
.cover-short {
  height: 206rpx;
}
.cover-medium {
  height: 250rpx;
}
.cover-tall {
  height: 286rpx;
}
.channel-badge,
.cover-label {
  position: absolute;
  z-index: 3;
  left: 14rpx;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
  font-size: 19rpx;
  font-weight: 600;
}
.channel-badge {
  top: 14rpx;
  color: #fff;
  background: rgba(20, 24, 33, 0.64);
  backdrop-filter: blur(18rpx) saturate(145%);
  -webkit-backdrop-filter: blur(18rpx) saturate(145%);
}
.cover-label {
  bottom: 14rpx;
  border-radius: 7rpx;
  color: var(--yd-ink);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8rpx 22rpx rgba(20, 35, 67, 0.1);
  backdrop-filter: blur(18rpx);
  -webkit-backdrop-filter: blur(18rpx);
}
.photo-count {
  position: absolute;
  z-index: 3;
  right: 14rpx;
  bottom: 15rpx;
  color: rgba(47, 61, 56, 0.62);
  font-size: 18rpx;
  font-weight: 700;
}
.cover-glow {
  position: absolute;
  z-index: 0;
  border-radius: 50%;
  background: rgba(255, 253, 248, 0.38);
}
.glow-one {
  top: -48rpx;
  right: -30rpx;
  width: 150rpx;
  height: 150rpx;
}
.glow-two {
  bottom: -62rpx;
  left: -28rpx;
  width: 138rpx;
  height: 138rpx;
  background: rgba(10, 132, 255, 0.1);
}
.cover-art {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 132rpx;
  height: 150rpx;
  padding: 12rpx 10rpx 10rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.48);
  box-shadow: 0 18rpx 34rpx rgba(30, 47, 82, 0.14);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  transform: rotate(-3deg);
}
.photo-corner {
  position: absolute;
  top: -9rpx;
  right: 13rpx;
  width: 30rpx;
  height: 11rpx;
  border-radius: 3rpx;
  background: rgba(255, 107, 95, 0.88);
  transform: rotate(4deg);
}
.cover-icon {
  width: 64rpx;
  height: 64rpx;
}
.photo-caption {
  margin-top: 11rpx;
  color: var(--yd-green-dark);
  font-size: 17rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}
.channel-idle .cover-art {
  transform: rotate(-4deg);
}
.channel-lost .cover-art,
.channel-help .cover-art {
  transform: rotate(3deg);
}
.channel-ride .cover-art {
  transform: rotate(-2deg);
}
.channel-shop .cover-art {
  transform: rotate(4deg);
}
.channel-club .cover-art {
  transform: rotate(-5deg);
}
.body {
  padding: 17rpx 16rpx 15rpx;
}
.post-title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--yd-ink);
  font-size: 27rpx;
  font-weight: 750;
  line-height: 1.42;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.meta-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38rpx;
  margin-top: 11rpx;
}
.tag {
  color: var(--yd-green);
  font-size: 21rpx;
}
.distance {
  padding: 4rpx 8rpx;
  border-radius: 999rpx;
  color: #6e6e73;
  background: rgba(118, 118, 128, 0.1);
  font-size: 18rpx;
}
.price {
  color: var(--yd-coral);
  font-size: 30rpx;
  font-weight: 800;
}
.yen {
  margin-right: 2rpx;
  font-size: 21rpx;
}
.author-row {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid rgba(60, 60, 67, 0.1);
  color: var(--yd-muted);
  font-size: 20rpx;
}
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  margin-right: 8rpx;
  border-radius: 50%;
  overflow: hidden;
  background: var(--yd-mint);
}
.avatar image {
  width: 100%;
  height: 100%;
}
.author {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.like {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5rpx;
}
.like image {
  width: 24rpx;
  height: 24rpx;
}

/* Compact home feed card */
.post-card {
  margin-bottom: 12rpx;
  border: 0;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 6rpx 20rpx rgba(31, 43, 65, 0.055);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.cover::after,
.cover-glow,
.photo-corner {
  display: none;
}
.image-shade {
  background: rgba(20, 24, 33, 0.025);
}
.cover-short {
  height: 198rpx;
}
.cover-medium {
  height: 234rpx;
}
.cover-tall {
  height: 268rpx;
}
.channel-badge {
  top: 12rpx;
  left: 12rpx;
  padding: 6rpx 11rpx;
  color: #3a3a3c;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 4rpx 12rpx rgba(26, 31, 43, 0.05);
  font-size: 18rpx;
  backdrop-filter: blur(14rpx);
  -webkit-backdrop-filter: blur(14rpx);
}
.cover-label {
  bottom: 12rpx;
  left: 12rpx;
  padding: 6rpx 10rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.54);
  border-radius: 8rpx;
  color: #3a3a3c;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: none;
  font-size: 18rpx;
}
.photo-count {
  right: 12rpx;
  bottom: 13rpx;
  color: rgba(58, 58, 60, 0.56);
  font-size: 17rpx;
}
.cover-art,
.channel-idle .cover-art,
.channel-lost .cover-art,
.channel-help .cover-art,
.channel-ride .cover-art,
.channel-shop .cover-art,
.channel-club .cover-art {
  width: auto;
  height: auto;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  transform: none;
}
.cover-icon {
  width: 70rpx;
  height: 70rpx;
}
.photo-caption {
  margin-top: 15rpx;
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  color: #5d5e63;
  background: rgba(255, 255, 255, 0.68);
  font-size: 17rpx;
  font-weight: 650;
  letter-spacing: 0;
}
.body {
  padding: 14rpx 14rpx 13rpx;
}
.post-title {
  font-size: 25rpx;
  font-weight: 700;
  line-height: 1.42;
}
.meta-line {
  min-height: 35rpx;
  margin-top: 8rpx;
}
.tag {
  overflow: hidden;
  color: #0a84ff;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.distance {
  margin-left: 8rpx;
  padding: 3rpx 7rpx;
  font-size: 17rpx;
}
.price {
  font-size: 28rpx;
}
.yen {
  font-size: 19rpx;
}
.author-row {
  margin-top: 9rpx;
  padding-top: 0;
  border-top: 0;
  font-size: 18rpx;
}
.avatar {
  width: 30rpx;
  height: 30rpx;
  margin-right: 7rpx;
  color: #0a84ff;
  background: rgba(10, 132, 255, 0.1);
  font-size: 16rpx;
}
.like {
  margin-left: 6rpx;
}
</style>
