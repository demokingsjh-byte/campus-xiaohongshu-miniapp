<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import { resolveCampusAvatar } from '@/utils/avatar';

const props = withDefaults(defineProps<{
  post: CampusPost
  variant?: string
}>(), {
  variant: '',
});

const channelVariants: Record<string, string> = {
  二手: 'idle',
  表白: 'confession',
  互助: 'errand',
  社团: 'fun',
  兼职: 'job',
  探店: 'groupbuy',
};

const displayVariant = computed(() => {
  if (props.variant && props.variant !== 'recommend')
    return props.variant;
  return channelVariants[props.post.channel] || props.post.type || 'recommend';
});

const fallbackEmoji = computed(() => ({
  idle: '📦',
  errand: '🏃',
  fun: '🎒',
  job: '🧰',
  groupbuy: '🍲',
}[displayVariant.value] || props.post.coverEmoji || '📌'));

const isFreshIdle = computed(() => props.post.tags?.some(tag => /全新|未拆|九成新/.test(tag)) || false);
const wantCount = computed(() => props.post.views || props.post.collects || 1);

function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` });
}
</script>

<template>
  <view
    class="post-card" :class="`variant-${displayVariant}`" role="button"
    :aria-label="`${post.title}，查看详情`" @click="openDetail(post.id)"
  >
    <template v-if="displayVariant === 'confession'">
      <view class="confession-card">
        <image
          class="notebook-hole notebook-hole-top"
          src="/static/images/home-prototype/notebook-hole.png" mode="aspectFit"
        />
        <image
          class="notebook-hole notebook-hole-bottom"
          src="/static/images/home-prototype/notebook-hole.png" mode="aspectFit"
        />
        <view class="confession-author-row">
          <image class="confession-avatar" :src="resolveCampusAvatar(post.avatar)" mode="aspectFill" lazy-load />
          <view class="confession-author-copy">
            <text>{{ post.author || '匿名用户' }}</text>
            <text>{{ post.time || '刚刚' }}</text>
          </view>
        </view>
        <view class="confession-message">
          <text class="confession-title">
            {{ post.title }}
          </text>
          <text class="confession-content">
            {{ post.content }}
          </text>
        </view>
        <view class="confession-bottom">
          <view class="confession-note-stamp">
            <view class="stamp-sheet" />
            <view class="stamp-fold" />
          </view>
          <view class="confession-action">
            去表白
          </view>
        </view>
      </view>
    </template>

    <template v-else-if="displayVariant === 'job'">
      <view class="job-body">
        <view class="job-main">
          <view class="job-title">
            {{ post.title }}
          </view>
          <view class="job-company">
            {{ post.content }}
          </view>
          <view class="job-meta">
            <image class="avatar" :src="resolveCampusAvatar(post.avatar)" mode="aspectFill" lazy-load />
            <text>{{ post.author || '校园招聘方' }}</text>
            <text class="job-location">
              {{ post.location || post.campusName || '校内' }}
            </text>
          </view>
        </view>
        <view class="job-side">
          <text class="job-salary">
            <text>¥</text>{{ post.price || '面议' }}
          </text>
          <view class="job-apply">
            申请
          </view>
        </view>
      </view>
    </template>

    <template v-else-if="displayVariant === 'groupbuy'">
      <view class="deal-cover" :style="{ background: post.coverColor || '#fff0e7' }">
        <image
          v-if="post.coverImage || post.images?.[0]" class="cover-image"
          :src="post.coverImage || post.images?.[0]" mode="aspectFill" lazy-load
        />
        <view v-else class="cover-fallback">
          <text class="fallback-emoji">
            {{ fallbackEmoji }}
          </text>
          <text class="fallback-label">
            {{ post.coverLabel || '校园团购' }}
          </text>
        </view>
      </view>
      <view class="deal-body">
        <view class="deal-title">
          {{ post.title }}
        </view>
        <view class="deal-location">
          {{ post.location || post.tags?.[0] || '距学校 500m' }}
        </view>
        <view class="deal-bottom">
          <view class="deal-price">
            <text>¥</text>{{ post.price || '到店优惠' }}
          </view>
          <text v-if="post.originalPrice" class="deal-original">
            ¥{{ post.originalPrice }}
          </text>
          <view class="deal-action">
            抢
          </view>
        </view>
      </view>
    </template>

    <template v-else>
      <view class="cover" :style="{ background: post.coverColor || '#eef5f1' }">
        <image
          v-if="post.coverImage || post.images?.[0]" class="cover-image"
          :src="post.coverImage || post.images?.[0]" mode="aspectFill" lazy-load
        />
        <view v-else class="cover-fallback">
          <text class="fallback-emoji">
            {{ fallbackEmoji }}
          </text>
          <text class="fallback-label">
            {{ post.coverLabel || post.channel }}
          </text>
        </view>
        <view v-if="displayVariant === 'idle'" class="trade-badge" :class="{ fresh: isFreshIdle }">
          <text>{{ isFreshIdle ? '全新' : '二手' }}</text>
          <text>校内</text>
        </view>
        <view v-if="post.images?.length && post.images.length > 1" class="media-play">
          ▶
        </view>
      </view>

      <view class="card-body">
        <view class="post-title">
          {{ post.title }}
        </view>
        <view v-if="displayVariant === 'idle'" class="trade-line">
          <text class="price">
            <text class="currency">
              ¥
            </text>{{ post.price || '面议' }}
          </text>
          <text class="want-count">
            {{ wantCount }}人想要
          </text>
        </view>
        <view class="author-row">
          <image class="avatar" :src="resolveCampusAvatar(post.avatar)" mode="aspectFill" lazy-load />
          <text class="author">
            {{ post.author || '同校同学' }}
          </text>
          <view class="like">
            <text class="heart">
              ♥
            </text>
            <text>{{ post.likes || 0 }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.post-card {
  overflow: hidden;
  margin-bottom: 24rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(56, 68, 82, 0.025);
}

.cover {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: 0;
  padding-top: 161.8%;
  background: #eef5f1;
}

.cover > .cover-image,
.cover > .cover-fallback {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

.cover-image {
  display: block;
  width: 100%;
  height: 100%;
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  flex-direction: column;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.5));
}

.fallback-emoji {
  font-size: 72rpx;
  line-height: 1;
  filter: drop-shadow(0 8rpx 10rpx rgba(38, 58, 46, 0.08));
}

.fallback-label {
  margin-top: 18rpx;
  color: rgba(36, 48, 42, 0.58);
  font-size: 22rpx;
}

.trade-badge {
  position: absolute;
  left: 18rpx;
  bottom: 16rpx;
  display: flex;
  align-items: center;
  height: 42rpx;
  padding: 0 13rpx;
  border-radius: 12rpx;
  color: #fff;
  background: #ff7608;
  font-size: 21rpx;
  line-height: 42rpx;
}

.trade-badge text + text {
  margin-left: 8rpx;
  padding-left: 8rpx;
  border-left: 2rpx solid rgba(255, 255, 255, 0.75);
}

.trade-badge.fresh {
  color: #152605;
  background: #8df510;
}

.trade-badge.fresh text + text {
  border-left-color: rgba(29, 49, 7, 0.48);
}

.media-play {
  position: absolute;
  top: 14rpx;
  right: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  color: #fff;
  background: rgba(31, 33, 31, 0.55);
  font-size: 18rpx;
  text-indent: 2rpx;
}

.card-body {
  padding: 21rpx 18rpx 19rpx;
}

.post-title {
  display: -webkit-box;
  overflow: hidden;
  color: #232626;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.trade-line {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 58rpx;
  margin-top: 5rpx;
}

.price {
  color: #ff4c58;
  font-size: 40rpx;
  font-weight: 650;
}

.currency {
  margin-right: 2rpx;
  font-size: 24rpx;
}

.want-count {
  padding-bottom: 5rpx;
  color: #a0a2a3;
  font-size: 21rpx;
}

.author-row {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 46rpx;
  margin-top: 8rpx;
}

.avatar {
  flex: 0 0 auto;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #f0f1f2;
}

.author {
  overflow: hidden;
  flex: 1;
  min-width: 0;
  margin-left: 10rpx;
  color: #8e9292;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.like {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  margin-left: 8rpx;
  color: #999d9d;
  font-size: 24rpx;
}

.heart {
  margin-right: 7rpx;
  color: #ff4b56;
  font-size: 32rpx;
  line-height: 1;
}

.variant-confession {
  overflow: visible;
  flex: 0 0 auto;
  width: 394rpx;
  height: 336rpx;
  margin: 0;
  border-radius: 32rpx;
  background: linear-gradient(150deg, #f2ffe9 0%, #fff 42%, #fff 100%);
}

.confession-card {
  position: relative;
  display: flex;
  overflow: visible;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 42rpx 32rpx 26rpx 40rpx;
  flex-direction: column;
}

.notebook-hole {
  position: absolute;
  z-index: 2;
  left: -21rpx;
  width: 55rpx;
  height: 45rpx;
}

.notebook-hole-top {
  top: 91rpx;
}

.notebook-hole-bottom {
  top: 242rpx;
}

.confession-author-row {
  display: flex;
  align-items: center;
}

.confession-avatar {
  width: 58rpx;
  height: 58rpx;
  border-radius: 50%;
}

.confession-author-copy {
  display: flex;
  min-width: 0;
  margin-left: 13rpx;
  flex-direction: column;
}

.confession-author-copy text:first-child {
  overflow: hidden;
  color: #232626;
  font-size: 27rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confession-author-copy text:last-child {
  margin-top: 3rpx;
  color: #919494;
  font-size: 23rpx;
}

.confession-message {
  display: flex;
  overflow: hidden;
  margin-top: 30rpx;
  color: #1d2020;
  flex-direction: column;
}

.confession-title,
.confession-content {
  overflow: hidden;
  font-size: 27rpx;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confession-content {
  margin-top: 2rpx;
}

.confession-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
}

.confession-note-stamp {
  position: relative;
  overflow: hidden;
  width: 54rpx;
  height: 48rpx;
  border: 3rpx dotted #e7c9dc;
  background: #fff8fc;
  box-sizing: border-box;
}

.stamp-sheet {
  position: absolute;
  right: 4rpx;
  bottom: 3rpx;
  width: 34rpx;
  height: 30rpx;
  background: repeating-linear-gradient(90deg, #f8d9ea 0 7rpx, #fff3fa 7rpx 13rpx);
}

.stamp-fold {
  position: absolute;
  top: 0;
  right: 0;
  width: 12rpx;
  height: 12rpx;
  background: linear-gradient(135deg, transparent 50%, #e7c9dc 51%);
}

.confession-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  height: 64rpx;
  border-radius: 22rpx;
  color: #ff3d67;
  background: #ffe7ef;
  font-size: 28rpx;
  font-weight: 500;
}

.variant-job,
.variant-groupbuy {
  margin-bottom: 18rpx;
  border-radius: 18rpx;
}

.variant-groupbuy {
  display: flex;
  padding: 18rpx;
}

.deal-cover {
  overflow: hidden;
  flex: 0 0 auto;
  width: 222rpx;
  height: 178rpx;
  border-radius: 13rpx;
}

.deal-cover .fallback-emoji {
  font-size: 58rpx;
}

.deal-cover .fallback-label {
  margin-top: 10rpx;
  font-size: 19rpx;
}

.deal-body {
  display: flex;
  flex: 1;
  min-width: 0;
  padding: 4rpx 0 2rpx 22rpx;
  flex-direction: column;
}

.deal-title,
.job-title {
  display: -webkit-box;
  overflow: hidden;
  color: #202321;
  font-size: 27rpx;
  font-weight: 650;
  line-height: 1.38;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.deal-location {
  overflow: hidden;
  margin-top: 11rpx;
  color: #969b98;
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deal-bottom {
  display: flex;
  align-items: flex-end;
  margin-top: auto;
}

.deal-price {
  color: #ff5557;
  font-size: 33rpx;
  font-weight: 700;
}

.deal-price text,
.job-salary text {
  margin-right: 2rpx;
  font-size: 20rpx;
}

.deal-original {
  margin: 0 0 4rpx 9rpx;
  color: #bbbebc;
  font-size: 18rpx;
  text-decoration: line-through;
}

.deal-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 59rpx;
  height: 53rpx;
  margin-left: auto;
  border-radius: 12rpx;
  color: #fff;
  background: #ff8b1e;
  font-size: 25rpx;
  font-weight: 750;
}

.job-body {
  display: flex;
  min-height: 205rpx;
  padding: 24rpx 22rpx 22rpx 26rpx;
}

.job-main {
  flex: 1;
  min-width: 0;
}

.job-title {
  font-size: 28rpx;
  -webkit-line-clamp: 1;
}

.job-company {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 13rpx;
  color: #858b87;
  font-size: 21rpx;
  line-height: 1.42;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.job-meta {
  display: flex;
  align-items: center;
  overflow: hidden;
  margin-top: 20rpx;
  color: #929894;
  font-size: 20rpx;
  white-space: nowrap;
}

.job-meta .avatar {
  width: 32rpx;
  height: 32rpx;
  margin-right: 8rpx;
}

.job-location {
  overflow: hidden;
  margin-left: 15rpx;
  padding-left: 15rpx;
  border-left: 1rpx solid #e4e7e5;
  text-overflow: ellipsis;
}

.job-side {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-end;
  width: 154rpx;
  flex-direction: column;
}

.job-salary {
  color: #ff5759;
  font-size: 30rpx;
  font-weight: 700;
}

.job-apply {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 58rpx;
  margin-top: auto;
  border-radius: 13rpx;
  color: #263513;
  background: #91f41d;
  font-size: 23rpx;
  font-weight: 650;
}
</style>
