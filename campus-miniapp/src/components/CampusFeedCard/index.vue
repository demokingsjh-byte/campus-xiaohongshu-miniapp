<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import { resolveCampusAvatar } from '@/utils/avatar';

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
  <view
    class="post-card" role="button" :aria-label="`${post.title}，查看详情`"
    @click="openDetail(post.id)"
  >
    <view class="cover" :class="[`cover-${post.height}`, channelClass]" :style="{ background: post.coverColor }">
      <image v-if="post.coverImage" class="cover-image" :src="post.coverImage" mode="aspectFill" lazy-load />
      <text class="channel-badge">
        {{ post.channel }}
      </text>
      <view v-if="!post.coverImage" class="cover-art">
        <view class="cover-icon-wrap">
          <image class="cover-icon" :src="channelIcon" mode="aspectFit" />
        </view>
        <text class="cover-copy">
          {{ post.coverLabel }}
        </text>
        <text class="cover-campus">
          {{ post.school.slice(0, 4) }} · 同校
        </text>
      </view>
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
          #{{ post.tags[0] }}
        </text>
        <text class="distance">
          校内
        </text>
      </view>
      <view class="author-row">
        <view class="avatar">
          <image :src="resolveCampusAvatar(post.avatar)" mode="aspectFill" lazy-load />
        </view>
        <view class="author-copy">
          <text class="author">
            {{ post.author }}
          </text>
          <text class="time">
            {{ post.time }}
          </text>
        </view>
        <view class="like">
          <image src="/static/icons/mine/heart.svg" mode="aspectFit" />
          <text>{{ post.likes }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.post-card {
  overflow: hidden;
  margin-bottom: var(--space-2);
  border: 1rpx solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-glass-strong);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(20rpx) saturate(145%);
  -webkit-backdrop-filter: blur(20rpx) saturate(145%);
}

.cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--color-page-deep);
}

.cover-short {
  height: 208rpx;
}
.cover-medium {
  height: 240rpx;
}
.cover-tall {
  height: 272rpx;
}

.cover-image {
  width: 100%;
  height: 100%;
}

.channel-badge {
  position: absolute;
  z-index: 2;
  top: var(--space-2);
  left: var(--space-2);
  padding: 7rpx var(--space-2);
  border-radius: var(--radius-pill);
  color: var(--color-text);
  border: 1rpx solid rgba(255, 255, 255, 0.62);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14rpx) saturate(145%);
  -webkit-backdrop-filter: blur(14rpx) saturate(145%);
  font-size: var(--font-size-caption);
  font-weight: 700;
  line-height: 1.2;
}

.cover-art {
  display: flex;
  width: 100%;
  height: 100%;
  padding: 38rpx var(--space-4) var(--space-4);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.cover-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.76);
}

.cover-icon {
  width: 40rpx;
  height: 40rpx;
}

.cover-copy {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--space-3);
  color: var(--color-text);
  font-size: var(--font-size-label);
  font-weight: 800;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.cover-campus {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-caption);
}

.body {
  padding: var(--space-3);
}

.post-title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text);
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
  min-height: 40rpx;
  margin-top: var(--space-1);
}

.price {
  color: var(--color-accent);
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1;
}

.yen {
  margin-right: 2rpx;
  font-size: var(--font-size-meta);
}

.tag {
  overflow: hidden;
  max-width: 190rpx;
  color: var(--color-primary-strong);
  font-size: var(--font-size-meta);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distance {
  flex: 0 0 auto;
  margin-left: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

.author-row {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-top: var(--space-2);
}

.avatar {
  flex: 0 0 auto;
  overflow: hidden;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: var(--color-primary-soft);
}

.avatar image {
  width: 100%;
  height: 100%;
}

.author-copy {
  display: flex;
  overflow: hidden;
  flex: 1;
  min-width: 0;
  margin-left: var(--space-1);
  align-items: baseline;
}

.author {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-meta);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  flex: 0 0 auto;
  margin-left: 6rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

.like {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  margin-left: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-meta);
}

.like image {
  width: 25rpx;
  height: 25rpx;
  margin-right: 4rpx;
  opacity: 0.62;
}
</style>
