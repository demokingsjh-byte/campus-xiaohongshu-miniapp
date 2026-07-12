<script lang="ts" setup>
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusPosts } from '@/mock/campus';

const postId = ref(1001);
const liked = ref(false);
const collected = ref(false);
const pageState = ref<'loading' | 'content' | 'error'>('loading');
const comment = ref('');
const post = computed(() => campusPosts.find(item => item.id === postId.value) || campusPosts[0]);
const related = computed(() => campusPosts.filter(item => item.id !== post.value.id).slice(0, 2));
const comments = ref([{ name: '小满同学', avatar: '满', time: '8分钟前', content: '请问桌子的尺寸大概是多少呀？宿舍上床下桌旁边能放吗？', likes: 3 }, { name: '晚风同学', avatar: '晚', time: '刚刚', content: '回复 @小满同学：80×50cm，床边可以放，我之前就是这样用的。', likes: 1 }]);

onLoad((query) => { postId.value = Number(query?.id || 1001); setTimeout(() => pageState.value = post.value ? 'content' : 'error', 500); });
function sendComment() {
  if (!comment.value.trim())
    return; comments.value.push({ name: '佳佳同学', avatar: '佳', time: '刚刚', content: comment.value, likes: 0 }); comment.value = ''; uni.showToast({ title: '评论成功', icon: 'success' });
}
function contact() {
  if (!uni.getStorageSync('yd-demo-login'))
    uni.navigateTo({ url: '/pages/login/index' }); else uni.showToast({ title: '已发送联系请求', icon: 'success' });
}
</script>

<template>
  <view class="detail-page safe-bottom">
    <view v-if="pageState === 'loading'" class="detail-loading">
      <view class="hero-sk" /><view class="line-sk w80" /><view class="line-sk" /><view class="line-sk w60" />
    </view>
    <StatePanel v-else-if="pageState === 'error'" type="error" title="内容不见了" description="这条内容可能已下架或被作者删除。" action="返回首页" @action="uni.switchTab({ url: '/pages/index/index' })" />
    <template v-else>
      <swiper class="media" indicator-dots indicator-active-color="#16A085">
        <swiper-item>
          <view class="media-item" :style="{ background: post.coverColor }">
            <text>{{ post.coverEmoji }}</text><view>{{ post.coverLabel }}</view>
          </view>
        </swiper-item><swiper-item>
          <view class="media-item second">
            <text>📐</text><view>尺寸与细节</view>
          </view>
        </swiper-item>
      </swiper>
      <view class="content-card">
        <view class="author-row">
          <view class="author-avatar">
            {{ post.avatarText }}
          </view><view class="author-main">
            <view>{{ post.author }} <text>✓ 同校</text></view><span>{{ post.school }} · {{ post.time }}</span>
          </view><button>＋ 关注</button>
        </view>
        <view v-if="post.price" class="price">
          <text>¥</text>{{ post.price }}
        </view><view class="title">
          {{ post.title }}
        </view><view class="body">
          {{ post.content }}
          <text v-if="post.id === 1001">
            \n\n桌子无明显磕碰，台灯三档亮度都正常。毕业离校前出掉，生活二区宿舍楼下自提，爽快的同学可小刀。
          </text>
        </view>
        <view class="tags">
          <text v-for="tag in post.tags" :key="tag">
            # {{ tag }}
          </text>
        </view>
        <view class="meta">
          📍 浙江理工大学 · 生活二区 <text>浏览 386</text>
        </view>
      </view>

      <view class="comments-card">
        <view class="section-title">
          评论 {{ comments.length + 14 }}
        </view><view v-for="item in comments" :key="item.content" class="comment">
          <view class="comment-avatar">
            {{ item.avatar }}
          </view><view class="comment-main">
            <view class="comment-name">
              {{ item.name }} <text>{{ item.time }}</text>
            </view><view class="comment-content">
              {{ item.content }}
            </view>
          </view><view class="comment-like">
            ♡ {{ item.likes }}
          </view>
        </view><view class="all-comments">
          查看全部评论 ›
        </view>
      </view>

      <view class="related">
        <view class="section-title">
          同校同学还在看
        </view><view class="related-grid">
          <CampusPostCard v-for="item in related" :key="item.id" :post="item" />
        </view>
      </view>

      <view class="bottom-bar">
        <view class="comment-input">
          <input v-model="comment" placeholder="友善评论一下…" confirm-type="send" @confirm="sendComment">
        </view><view class="action" :class="{ active: liked }" @click="liked = !liked">
          <text>{{ liked ? '♥' : '♡' }}</text><span>{{ post.likes + (liked ? 1 : 0) }}</span>
        </view><view class="action" :class="{ active: collected }" @click="collected = !collected">
          <text>☆</text><span>收藏</span>
        </view><button @click="contact">
          联系TA
        </button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding-bottom: 140rpx;
  background: #faf8f3;
}
.media {
  height: 620rpx;
}
.media-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.media-item > text {
  font-size: 160rpx;
  filter: drop-shadow(0 18rpx 18rpx rgba(0, 0, 0, 0.08));
}
.media-item > view {
  position: absolute;
  left: 28rpx;
  bottom: 28rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.88);
  font-size: 22rpx;
  font-weight: 700;
}
.media-item.second {
  background: #eef0eb;
}
.content-card,
.comments-card {
  margin-top: 16rpx;
  padding: 28rpx 24rpx;
  background: #fff;
}
.author-row {
  display: flex;
  align-items: center;
}
.author-avatar,
.comment-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 78rpx;
  height: 78rpx;
  border-radius: 50%;
  color: #0f766e;
  background: #e2f2ed;
  font-size: 28rpx;
  font-weight: 800;
}
.author-main {
  flex: 1;
  margin-left: 16rpx;
  font-size: 27rpx;
  font-weight: 800;
}
.author-main text {
  color: #16a085;
  font-size: 19rpx;
}
.author-main span {
  display: block;
  margin-top: 6rpx;
  color: #8a9490;
  font-size: 20rpx;
  font-weight: 400;
}
.author-row button {
  padding: 0 22rpx;
  border: 1rpx solid #16a085;
  border-radius: 999rpx;
  color: #0f766e;
  background: #fff;
  font-size: 22rpx;
}
.price {
  margin-top: 28rpx;
  color: #ff6b5e;
  font-size: 46rpx;
  font-weight: 900;
}
.price text {
  margin-right: 4rpx;
  font-size: 26rpx;
}
.title {
  margin-top: 12rpx;
  font-size: 38rpx;
  font-weight: 900;
  line-height: 1.35;
}
.body {
  margin-top: 18rpx;
  color: #46514d;
  font-size: 27rpx;
  line-height: 1.75;
  white-space: pre-line;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 20rpx;
}
.tags text {
  padding: 9rpx 15rpx;
  border-radius: 999rpx;
  color: #0f766e;
  background: #e8f5f1;
  font-size: 21rpx;
}
.meta {
  display: flex;
  justify-content: space-between;
  margin-top: 26rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid #eeeae3;
  color: #89948f;
  font-size: 21rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 900;
}
.comment {
  display: flex;
  align-items: flex-start;
  margin-top: 28rpx;
}
.comment-avatar {
  width: 64rpx;
  height: 64rpx;
  font-size: 23rpx;
}
.comment-main {
  flex: 1;
  margin-left: 14rpx;
}
.comment-name {
  font-size: 23rpx;
  font-weight: 800;
}
.comment-name text {
  margin-left: 8rpx;
  color: #98a09d;
  font-size: 19rpx;
  font-weight: 400;
}
.comment-content {
  margin-top: 7rpx;
  color: #505c57;
  font-size: 24rpx;
  line-height: 1.55;
}
.comment-like {
  color: #87918d;
  font-size: 20rpx;
}
.all-comments {
  margin-top: 28rpx;
  color: #16a085;
  font-size: 23rpx;
  text-align: center;
}
.related {
  padding: 32rpx 20rpx;
}
.related .section-title {
  margin: 0 4rpx 20rpx;
}
.related-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
}
.bottom-bar {
  position: fixed;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #eeeae3;
  background: #fff;
}
.comment-input {
  flex: 1;
  height: 68rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #f1f0eb;
}
.comment-input input {
  height: 100%;
  font-size: 22rpx;
}
.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #717c77;
  font-size: 18rpx;
}
.action > text {
  font-size: 30rpx;
}
.action.active {
  color: #ff6b5e;
}
.bottom-bar button {
  padding: 0 24rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #16a085;
  font-size: 23rpx;
  font-weight: 800;
}
.detail-loading {
  padding: 24rpx;
}
.hero-sk,
.line-sk {
  border-radius: 20rpx;
  background: #e9e7e1;
  animation: pulse 1.1s infinite alternate;
}
.hero-sk {
  height: 560rpx;
}
.line-sk {
  width: 100%;
  height: 30rpx;
  margin-top: 22rpx;
}
.w80 {
  width: 80%;
}
.w60 {
  width: 60%;
}
@keyframes pulse {
  to {
    opacity: 0.45;
  }
}
</style>
