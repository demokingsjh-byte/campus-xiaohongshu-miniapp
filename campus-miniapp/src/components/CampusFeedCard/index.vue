<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import { resolveCampusAvatar } from '@/utils/avatar';

const props = withDefaults(defineProps<{
  post: CampusPost
  variant?: string
  ownerContext?: boolean
}>(), {
  variant: '',
  ownerContext: false,
});

const channelVariants: Record<string, string> = {
  二手: 'idle',
  二手闲置: 'idle',
  表白: 'confession',
  表白墙: 'confession',
  互助: 'errand',
  代拿代办: 'errand',
  社团: 'fun',
  校园趣事: 'fun',
  校园趋势: 'fun',
  兼职: 'job',
  兼职信息: 'job',
  探店: 'groupbuy',
  团购: 'groupbuy',
  商家团购: 'groupbuy',
};

const displayVariant = computed(() => {
  if (props.variant === 'note')
    return 'confession';
  if (props.variant && props.variant !== 'recommend')
    return props.variant;
  return channelVariants[props.post.channel] || props.post.type || 'recommend';
});

const isRecommendCard = computed(() => props.variant === 'recommend');
const isNoteCard = computed(() => props.variant === 'note');
const hasImage = computed(() => Boolean(props.post.coverImage || props.post.images?.some(Boolean)));
const isWideCategoryCard = computed(() => ['job', 'groupbuy'].includes(props.variant));
const isCompactGridCard = computed(() => props.variant !== 'note' && !isWideCategoryCard.value);
const isTextNoteCard = computed(() => displayVariant.value === 'confession' && !hasImage.value);
// 只在表白墙分类页显示，首页推荐卡片不显示；本人发布的内容也不引导自己表白。
const showConfessionAction = computed(() => props.variant === 'confession' && !props.ownerContext && props.post.owner !== true);

const categoryLabels: Record<string, string> = {
  idle: '二手',
  errand: '代办',
  fun: '趣事',
  job: '兼职',
  groupbuy: '团购',
  confession: '表白',
};
const categoryLabel = computed(() => categoryLabels[displayVariant.value] || props.post.channel || '校园');
const dealDiscount = computed(() => {
  const taggedDiscount = props.post.tags?.find(tag => /^\d+(?:\.\d+)?折$/.test(tag));
  if (taggedDiscount)
    return taggedDiscount;
  const price = Number(props.post.price);
  const originalPrice = Number(props.post.originalPrice);
  return price > 0 && originalPrice > price ? `${(price / originalPrice * 10).toFixed(1)}折` : '';
});
const dealDistance = computed(() => props.post.tags?.find(tag => /^\d+(?:\.\d+)?(?:m|km)$/i.test(tag)) || props.post.campusName || '校内');
const urgentLabel = computed(() => props.post.tags?.find(tag => /急招|急/.test(tag)) || '急招');

const fallbackEmoji = computed(() => ({
  idle: '📦',
  errand: '🏃',
  fun: '🎒',
  job: '🧰',
  groupbuy: '🍲',
}[displayVariant.value] || props.post.coverEmoji || '📌'));

const isFreshIdle = computed(() => props.post.tags?.some(tag => /全新|未拆|九成新/.test(tag)) || false);
// “想要”使用真实点赞数，不再用浏览量或默认值补数。
const wantCount = computed(() => Number(props.post.likes || 0));
// 便签热度只汇总真实互动数据，不使用人为权重或固定假数据。
const hotCount = computed(() => ['views', 'likes', 'collects', 'comments'].reduce((total, key) => {
  const value = Number(props.post[key as keyof CampusPost] || 0);
  return total + (Number.isFinite(value) ? Math.max(0, value) : 0);
}, 0));

function openDetail(id: number) {
  const ownerQuery = props.ownerContext || props.post.owner === true ? '&mine=1' : '';
  uni.navigateTo({ url: `/pages/detail/index?id=${id}${ownerQuery}` });
}
</script>

<template>
  <view
    class="post-card"
    :class="[
      `variant-${displayVariant}`,
      {
        'compact-grid-card': isCompactGridCard,
        'wide-category-card': isWideCategoryCard,
        'note-card': isNoteCard,
        'text-note-card': isTextNoteCard,
        'has-confession-action': showConfessionAction,
      },
    ]"
    role="button"
    :aria-label="`${post.title}，查看详情`" @click="openDetail(post.id)"
  >
    <template v-if="isNoteCard || (displayVariant === 'confession' && !hasImage)">
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
          <image
            class="confession-note-quote"
            src="/static/images/home-prototype/quote.png" mode="aspectFit"
          />
          <view v-if="isNoteCard" class="note-heat">
            <text>🔥</text><text>热度 {{ hotCount }}</text>
          </view>
          <view v-if="showConfessionAction" class="confession-action" @click.stop="openDetail(post.id)">
            <text>去表白</text><text class="confession-action-heart">♥</text>
          </view>
        </view>
      </view>
    </template>

    <template v-else-if="displayVariant === 'job' && !isCompactGridCard">
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
            <view class="job-author-copy">
              <text>{{ post.author || '校园招聘方' }}</text>
              <text>今日活跃</text>
            </view>
            <text class="job-location">
              {{ post.location || post.campusName || '校内' }}
            </text>
          </view>
          <view class="job-urgent">
            {{ urgentLabel }}
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

    <template v-else-if="displayVariant === 'groupbuy' && !isCompactGridCard">
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
          <text>{{ post.location || post.tags?.[0] || '校内门店' }}</text>
          <text>{{ dealDistance }}</text>
        </view>
        <view class="deal-bottom">
          <view class="deal-price">
            <text>¥</text>{{ post.price || '到店优惠' }}
          </view>
          <text v-if="dealDiscount" class="deal-discount">
            {{ dealDiscount }}
          </text>
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
        <view v-if="displayVariant === 'idle' && !isRecommendCard" class="trade-badge" :class="{ fresh: isFreshIdle }">
          <text>{{ isFreshIdle ? '全新' : '二手' }}</text>
          <text>校内</text>
        </view>
        <view v-if="post.images?.length && post.images.length > 1" class="media-multiple" aria-label="多张图片">
          <view class="media-stack-back" />
          <view class="media-stack-front" />
        </view>
        <view v-if="isRecommendCard" class="recommend-category-badge" :class="{ fresh: displayVariant === 'idle' && isFreshIdle }">
          <text>{{ displayVariant === 'idle' && isFreshIdle ? '全新' : categoryLabel }}</text>
          <text>校内</text>
        </view>
      </view>

      <view class="card-body">
        <view class="post-title">
          {{ post.title }}
        </view>
        <view v-if="displayVariant === 'idle' || (isCompactGridCard && post.price)" class="trade-line">
          <view class="price">
            <text class="currency">
              ￥
            </text><text class="price-value">{{ post.price || '面议' }}</text>
          </view>
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
            <image
              class="heart-icon"
              :src="post.liked ? '/static/icons/ui/heart-liked.svg' : '/static/icons/ui/heart-default.svg'"
              mode="aspectFit"
            />
            <text>{{ post.likes || 0 }}</text>
          </view>
        </view>
        <view v-if="showConfessionAction" class="confession-action confession-image-action" @click.stop="openDetail(post.id)">
          <text>去表白</text><text class="confession-action-heart">♥</text>
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

.media-multiple {
  position: absolute;
  top: 14rpx;
  right: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  background: rgba(31, 33, 31, 0.55);
}

.media-stack-back,
.media-stack-front {
  position: absolute;
  box-sizing: border-box;
  width: 21rpx;
  height: 18rpx;
  border: 2rpx solid #fff;
  border-radius: 3rpx;
}

.media-stack-back {
  top: 11rpx;
  left: 11rpx;
  opacity: 0.72;
}

.media-stack-front {
  right: 10rpx;
  bottom: 10rpx;
  background: rgba(31, 33, 31, 0.28);
}

.card-body {
  padding: 21rpx 18rpx 19rpx;
}

.post-title {
  display: -webkit-box;
  overflow: hidden;
  color: #232626;
  font-size: 30rpx;
  font-weight: 600;
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

.heart-icon {
  flex: 0 0 auto;
  width: 25rpx;
  height: 21rpx;
  margin-right: 7rpx;
}

.post-card.compact-grid-card {
  display: block;
  overflow: hidden;
  box-sizing: border-box;
  width: 332.69rpx;
  height: 523.08rpx;
  margin-bottom: 23.08rpx;
  padding: 0;
  border-radius: 15.38rpx;
  background: #fff;
}

.compact-grid-card .cover {
  width: 332.69rpx;
  height: 330rpx;
  padding-top: 0;
  border-radius: 15.38rpx;
}

.compact-grid-card .card-body {
  position: relative;
  box-sizing: border-box;
  width: 332.69rpx;
  height: 193.08rpx;
  padding: 0 15.38rpx;
}

.compact-grid-card .post-title {
  position: relative;
  box-sizing: border-box;
  width: 301.92rpx;
  max-height: 80.77rpx;
  margin: 15.38rpx 0 0;
  padding: 0;
  color: #1f1f1f;
  font-family: 'PingFang SC', sans-serif;
  font-size: 30rpx;
  font-weight: 600;
  font-style: normal;
  letter-spacing: 0;
  line-height: 40rpx;
  text-align: left;
  text-transform: none;
}

.compact-grid-card .trade-line {
  position: relative;
  display: block;
  width: 301.92rpx;
  height: 34.62rpx;
  margin: 7.69rpx 0 0;
}

.compact-grid-card .price {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: flex-end;
  flex-direction: row;
  height: 34.62rpx;
  color: #ff4747;
  font-family: 'Satoshi Variable', sans-serif;
  font-size: 38.46rpx;
  font-weight: 700;
  line-height: 34.62rpx;
}

.compact-grid-card .currency {
  display: flex;
  align-items: flex-end;
  flex: 0 0 23.08rpx;
  width: 23.08rpx;
  height: 26.92rpx;
  margin-right: 0;
  font-family: 'Satoshi Variable', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.compact-grid-card .price-value {
  display: block;
  height: 34.62rpx;
  line-height: 34.62rpx;
}

.compact-grid-card .want-count {
  position: absolute;
  top: 9.62rpx;
  right: 0;
  height: 26.92rpx;
  padding: 0;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.compact-grid-card .author-row {
  position: relative;
  top: auto;
  bottom: auto;
  left: auto;
  width: 301.92rpx;
  height: 46.15rpx;
  margin: 8rpx 0 0;
}

.compact-grid-card .avatar {
  width: 46.15rpx;
  height: 46.15rpx;
}

.compact-grid-card .author {
  height: 26.92rpx;
  margin-left: 13.47rpx;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.compact-grid-card .like {
  position: absolute;
  top: 11.54rpx;
  right: -1.92rpx;
  justify-content: flex-end;
  width: 82.69rpx;
  height: 26.92rpx;
  margin-left: 0;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.compact-grid-card .heart-icon {
  display: block;
  width: 24.68rpx;
  height: 21.34rpx;
  margin-right: 7rpx;
}

.post-card.compact-grid-card.text-note-card {
  overflow: visible;
  background: linear-gradient(150deg, #f2ffe9 0%, #fff 42%, #fff 100%);
}

.compact-grid-card.text-note-card .confession-card {
  padding: 32rpx 24rpx 26rpx 30rpx;
}

.compact-grid-card.text-note-card .confession-message {
  margin-top: 28rpx;
}

.compact-grid-card.text-note-card .confession-title,
.compact-grid-card.text-note-card .confession-content {
  display: -webkit-box;
  overflow: hidden;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.compact-grid-card.text-note-card .confession-bottom {
  padding-bottom: 4rpx;
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

.confession-note-quote {
  width: 50rpx;
  height: 38.46rpx;
  opacity: 1;
}

.note-heat {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 38.46rpx;
  margin-left: auto;
  color: #8b8b8b;
  font-size: 23.08rpx;
  line-height: 26.92rpx;
}

.note-heat text + text {
  margin-left: 6rpx;
}

.confession-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 154rpx;
  height: 62rpx;
  border-radius: 23rpx;
  color: #ff6a00;
  background: #fff4a7;
  font-size: 27rpx;
  font-weight: 500;
}

.confession-action-heart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  margin-left: 7rpx;
  border-radius: 50%;
  color: #ff8a00;
  background: #ffd737;
  font-size: 29rpx;
  line-height: 48rpx;
}

.confession-image-action { position: absolute; right: 15.38rpx; bottom: 12rpx; }

.compact-grid-card.variant-confession .confession-image-action { bottom: 13rpx; }

/* 表白墙分类页需要额外容纳“去表白”按钮；推荐页中的表白帖仍使用更大的图片。 */
.post-card.compact-grid-card.has-confession-action .cover {
  height: 269.23rpx;
}

.post-card.compact-grid-card.has-confession-action .card-body {
  height: 253.85rpx;
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

.confession-title {
  font-size: 30rpx;
  font-weight: 600;
}

.recommend-category-badge {
  position: absolute;
  z-index: 2;
  left: 17.31rpx;
  bottom: 15.38rpx;
  display: flex;
  overflow: hidden;
  align-items: center;
  box-sizing: border-box;
  width: 130.77rpx;
  height: 38.46rpx;
  border-radius: 11.54rpx;
  color: #fff;
  background: #ff6a00;
  backdrop-filter: blur(19.23rpx);
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 500;
  line-height: 26.92rpx;
}

.recommend-category-badge text {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-width: 0;
  height: 26.92rpx;
}

.recommend-category-badge text + text {
  border-left: 2rpx solid rgba(255, 255, 255, 0.78);
}

.recommend-category-badge.fresh {
  color: #152605;
  background: #8df510;
}

.recommend-category-badge.fresh text + text {
  border-left-color: rgba(29, 49, 7, 0.48);
}

.compact-grid-card .trade-badge {
  left: 17.31rpx;
  bottom: 15.38rpx;
  justify-content: center;
  box-sizing: border-box;
  width: 130.77rpx;
  height: 38.46rpx;
  padding: 0;
  border-radius: 11.54rpx;
  background: #ff6a00;
  backdrop-filter: blur(19.23rpx);
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 500;
  line-height: 26.92rpx;
}

.compact-grid-card .trade-badge text {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 26.92rpx;
}

.compact-grid-card .trade-badge text + text {
  margin-left: 0;
  padding-left: 0;
}

.compact-grid-card .trade-badge.fresh {
  color: #152605;
  background: #8df510;
}

.post-card.wide-category-card.variant-groupbuy {
  display: flex;
  box-sizing: border-box;
  width: 688.46rpx;
  height: 257.69rpx;
  margin-bottom: 30.77rpx;
  padding: 15.38rpx;
  border-radius: 30.77rpx;
  background: #fff;
}

.variant-groupbuy .deal-cover {
  width: 226.92rpx;
  height: 226.92rpx;
  border-radius: 15.38rpx;
}

.variant-groupbuy .deal-body {
  position: relative;
  flex: 0 0 auto;
  box-sizing: border-box;
  width: 430.77rpx;
  height: 226.92rpx;
  padding: 0 7.69rpx 0 30.77rpx;
}

.variant-groupbuy .deal-title {
  width: 392.31rpx;
  height: 76.92rpx;
  color: #1f1f1f;
  font-family: 'PingFang SC', sans-serif;
  font-size: 30.77rpx;
  font-weight: 600;
  line-height: 38.46rpx;
}

.variant-groupbuy .deal-location {
  position: absolute;
  top: 100rpx;
  left: 30.77rpx;
  display: flex;
  justify-content: space-between;
  width: 392.31rpx;
  height: 26.92rpx;
  margin: 0;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.variant-groupbuy .deal-bottom {
  position: absolute;
  right: 7.69rpx;
  bottom: 0;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  width: 392.31rpx;
  height: 61.54rpx;
}

.variant-groupbuy .deal-bottom::before {
  position: absolute;
  z-index: 0;
  right: 0;
  width: 246.15rpx;
  height: 61.54rpx;
  border-radius: 23.08rpx;
  background: linear-gradient(90deg, rgba(255, 244, 167, 0), #fff4a7);
  content: '';
}

.variant-groupbuy .deal-price,
.variant-groupbuy .deal-discount,
.variant-groupbuy .deal-original,
.variant-groupbuy .deal-action {
  position: relative;
  z-index: 1;
}

.variant-groupbuy .deal-price {
  display: flex;
  align-items: flex-start;
  height: 46.15rpx;
  color: #ff4d4f;
  font-family: 'Satoshi Variable', sans-serif;
  font-size: 34.62rpx;
  font-weight: 700;
  line-height: 30.77rpx;
}

.variant-groupbuy .deal-price text {
  margin-right: 4rpx;
  font-size: 23.08rpx;
  line-height: 30.77rpx;
}

.variant-groupbuy .deal-discount {
  margin-left: 9rpx;
  color: #ff4d4f;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 30.77rpx;
}

.variant-groupbuy .deal-original {
  margin: 0 0 0 8rpx;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 30.77rpx;
}

.variant-groupbuy .deal-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 69.23rpx;
  height: 61.54rpx;
  margin-left: auto;
  border-radius: 0 23.08rpx 23.08rpx 0;
  color: #fff;
  background: #ff5a00;
  font-size: 34.62rpx;
  font-weight: 700;
}

.post-card.wide-category-card.variant-job {
  box-sizing: border-box;
  width: 688.46rpx;
  height: 307.69rpx;
  margin-bottom: 30.77rpx;
  border-radius: 30.77rpx;
  background: #fff;
}

.variant-job .job-body {
  position: relative;
  box-sizing: border-box;
  width: 688.46rpx;
  height: 307.69rpx;
  min-height: 0;
  padding: 0;
}

.variant-job .job-main,
.variant-job .job-side {
  position: static;
}

.variant-job .job-title {
  position: absolute;
  top: 23.07rpx;
  left: 23.08rpx;
  width: 430.77rpx;
  height: 38.46rpx;
  color: #1f1f1f;
  font-family: 'PingFang SC', sans-serif;
  font-size: 30.77rpx;
  font-weight: 600;
  line-height: 38.46rpx;
}

.variant-job .job-company {
  position: absolute;
  top: 73.08rpx;
  left: 23.08rpx;
  width: 580rpx;
  height: 30.77rpx;
  margin: 0;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 30.77rpx;
  -webkit-line-clamp: 1;
}

.variant-job .job-meta {
  position: absolute;
  top: 130.77rpx;
  left: 23.08rpx;
  width: 653.85rpx;
  height: 69.23rpx;
  margin: 0;
  color: #1f1f1f;
}

.variant-job .job-meta .avatar {
  width: 69.23rpx;
  height: 69.23rpx;
  margin: 0;
}

.job-author-copy {
  display: flex;
  height: 69.23rpx;
  margin-left: 13.46rpx;
  justify-content: center;
  flex-direction: column;
}

.job-author-copy text:first-child {
  color: #1f1f1f;
  font-size: 23.08rpx;
  line-height: 26.92rpx;
}

.job-author-copy text:last-child {
  color: #02bd43;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
}

.variant-job .job-location {
  position: absolute;
  top: 19.23rpx;
  right: 11.54rpx;
  width: 169.23rpx;
  height: 30.77rpx;
  margin: 0;
  padding: 0;
  border: 0;
  color: #8b8b8b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 26.92rpx;
  font-weight: 400;
  line-height: 30.77rpx;
}

.variant-job .job-salary {
  position: absolute;
  top: 23.07rpx;
  right: 23.08rpx;
  height: 38.46rpx;
  color: #ff4d4f;
  font-size: 30.77rpx;
  line-height: 38.46rpx;
}

.variant-job .job-salary text {
  font-size: 23.08rpx;
}

.job-urgent {
  position: absolute;
  left: 23.08rpx;
  bottom: 23.08rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 69.23rpx;
  height: 42.31rpx;
  border-radius: 11.54rpx;
  color: #888db6;
  background: rgba(136, 141, 182, 0.1);
  font-size: 23.08rpx;
}

.variant-job .job-apply {
  position: absolute;
  right: 23.08rpx;
  bottom: 23.08rpx;
  width: 134.62rpx;
  height: 61.54rpx;
  margin: 0;
  border-radius: 23.08rpx;
  color: #1f1f1f;
  background: #a4f62b;
  font-family: 'PingFang SC', sans-serif;
  font-size: 26.92rpx;
  font-weight: 400;
  line-height: 30.77rpx;
}
</style>
