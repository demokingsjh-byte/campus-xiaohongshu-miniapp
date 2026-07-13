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
        <text class="cover-emoji">
          {{ post.coverEmoji }}
        </text>
        <view class="photo-caption">
          校园实拍
        </view>
      </view>
      <text class="cover-label">
        {{ post.coverLabel }}
      </text>
      <text class="photo-count">
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
          {{ post.avatarText }}
        </view>
        <text class="author">
          {{ post.author }}
        </text>
        <text class="like">
          ♡ {{ post.likes }}
        </text>
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
  box-shadow: 0 8rpx 26rpx rgba(28, 45, 40, 0.065);
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
  border: 1rpx solid rgba(255, 255, 255, 0.42);
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
  background: rgba(21, 31, 28, 0.08);
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
  background: rgba(24, 32, 30, 0.74);
  backdrop-filter: blur(8rpx);
}
.cover-label {
  bottom: 14rpx;
  color: #24332f;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 4rpx 12rpx rgba(32, 48, 43, 0.08);
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
  background: rgba(255, 255, 255, 0.48);
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
  background: rgba(22, 118, 104, 0.08);
}
.cover-art {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 124rpx;
  height: 144rpx;
  padding: 12rpx 10rpx 10rpx;
  border: 6rpx solid rgba(255, 255, 255, 0.96);
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.56);
  box-shadow: 0 16rpx 34rpx rgba(28, 69, 61, 0.16);
  transform: rotate(-3deg);
}
.photo-corner {
  position: absolute;
  top: -9rpx;
  right: 13rpx;
  width: 30rpx;
  height: 11rpx;
  border-radius: 3rpx;
  background: rgba(255, 118, 95, 0.86);
  transform: rotate(4deg);
}
.cover-emoji {
  font-size: 62rpx;
  line-height: 1;
  filter: drop-shadow(0 6rpx 8rpx rgba(33, 60, 53, 0.12));
}
.photo-caption {
  margin-top: 11rpx;
  color: #41504b;
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
  padding: 18rpx 17rpx 16rpx;
}
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
.meta-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38rpx;
  margin-top: 11rpx;
}
.tag {
  color: #0f766e;
  font-size: 21rpx;
}
.distance {
  padding: 4rpx 8rpx;
  border-radius: 999rpx;
  color: #687570;
  background: #f1f4f1;
  font-size: 18rpx;
}
.price {
  color: #ff6b5e;
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
  border-top: 1rpx solid #f0eee8;
  color: #7a8581;
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
  color: #0f766e;
  background: #e8f5f1;
  font-size: 18rpx;
  font-weight: 700;
}
.author {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.like {
  flex: 0 0 auto;
}
</style>
