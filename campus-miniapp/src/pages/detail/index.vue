<script lang="ts" setup>
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusPosts } from '@/mock/campus';
import { reportCampusPost } from '@/services/api/content';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const postId = ref(2001);
const liked = ref(false);
const collected = ref(false);
const followed = ref(false);
const interactionBusy = ref(false);
const pageState = ref<'loading' | 'content' | 'error'>('loading');
const comment = ref('');
const contentStore = useCampusContentStore();
const userStore = useUserStore();
const post = computed(() => contentStore.getPost(postId.value) || campusPosts[0]);
const related = computed(() => contentStore.allPosts.filter(item => item.id !== post.value.id && item.tenantId === post.value.tenantId).slice(0, 2));
const comments = ref([{ name: '小满同学', avatar: '满', time: '8分钟前', content: '请问具体时间和地点方便再确认一下吗？', likes: 3 }, { name: '山风同学', avatar: '山', time: '刚刚', content: '可以的，直接点下方联系我就好。', likes: 1 }]);

onLoad(async (query) => {
  postId.value = Number(query?.id || 2001);
  pageState.value = 'loading';
  try {
    const loaded = await contentStore.loadPost(postId.value);
    liked.value = Boolean(loaded.liked);
    collected.value = Boolean(loaded.collected);
    pageState.value = 'content';
  } catch {
    pageState.value = 'error';
  }
});
function ensureLogin() {
  if (userStore.loggedIn)
    return true;
  uni.showModal({ title: '登录后参与互动', content: '登录后可以评论、收藏和联系发布者。', confirmText: '去登录', success: res => res.confirm && uni.navigateTo({ url: '/pages/login/index' }) });
  return false;
}
function sendComment() {
  if (!ensureLogin())
    return;
  if (!comment.value.trim())
    return;
  comments.value.push({ name: '佳佳同学', avatar: '佳', time: '刚刚', content: comment.value, likes: 0 });
  comment.value = '';
  uni.showToast({ title: '评论成功', icon: 'success' });
}
function contact() {
  if (ensureLogin())
    uni.showToast({ title: '已发送联系请求', icon: 'success' });
}
async function toggleLike() {
  if (!ensureLogin() || interactionBusy.value)
    return;
  interactionBusy.value = true;
  try {
    const updated = await contentStore.setPostLike(postId.value, !liked.value);
    liked.value = Boolean(updated.liked);
  } catch {
    uni.showToast({ title: '点赞失败，请重试', icon: 'none' });
  } finally {
    interactionBusy.value = false;
  }
}
async function toggleCollect() {
  if (!ensureLogin() || interactionBusy.value)
    return;
  interactionBusy.value = true;
  try {
    const updated = await contentStore.setPostCollect(postId.value, !collected.value);
    collected.value = Boolean(updated.collected);
    uni.showToast({ title: collected.value ? '已加入收藏' : '已取消收藏', icon: 'none' });
  } catch {
    uni.showToast({ title: '收藏失败，请重试', icon: 'none' });
  } finally {
    interactionBusy.value = false;
  }
}
function toggleFollow() {
  if (ensureLogin())
    followed.value = !followed.value;
}
function managePost() {
  uni.showActionSheet({
    itemList: ['删除这条发布'],
    success: ({ tapIndex }) => {
      if (tapIndex !== 0)
        return;
      uni.showModal({
        title: '删除发布',
        content: '删除后无法恢复，确定继续吗？',
        confirmText: '删除',
        confirmColor: '#FF453A',
        success: async (result) => {
          if (!result.confirm)
            return;
          try {
            await contentStore.removePost(postId.value);
            uni.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => uni.navigateBack(), 500);
          } catch {
            uni.showToast({ title: '删除失败，请重试', icon: 'none' });
          }
        },
      });
    },
  });
}

function reportPost() {
  if (!ensureLogin())
    return;
  const reasons = ['广告诈骗', '人身攻击', '色情低俗', '虚假信息', '侵犯隐私', '其他'];
  uni.showActionSheet({
    itemList: reasons,
    success: ({ tapIndex }) => {
      const reason = reasons[tapIndex];
      if (!reason)
        return;
      uni.showModal({
        title: `举报：${reason}`,
        content: '可补充说明，帮助校园运营人员核实处理',
        editable: true,
        placeholderText: '选填，最多 300 字',
        confirmText: '提交举报',
        success: async (result) => {
          if (!result.confirm)
            return;
          try {
            await reportCampusPost(postId.value, { reason, detail: result.content?.trim().slice(0, 300) });
            uni.showToast({ title: '举报已提交', icon: 'success' });
          } catch {
            uni.showToast({ title: '提交失败，请稍后重试', icon: 'none' });
          }
        },
      });
    },
  });
}
</script>

<template>
  <view class="detail-page safe-bottom">
    <view v-if="pageState === 'loading'" class="detail-loading">
      <view class="hero-sk" /><view class="line-sk w80" /><view class="line-sk" /><view class="line-sk w60" />
    </view>
    <StatePanel
      v-else-if="pageState === 'error'" type="error" title="内容不见了"
      description="这条内容可能已下架或被作者删除。" action="返回首页"
      @action="uni.switchTab({ url: '/pages/index/index' })"
    />
    <template v-else>
      <swiper class="media" indicator-dots indicator-active-color="#0A84FF">
        <swiper-item>
          <image v-if="post.coverImage" class="detail-photo" :src="post.coverImage" mode="aspectFill" />
          <view v-else class="media-item" :style="{ background: post.coverColor }">
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
            <view class="author-name">
              <text>{{ post.author }}</text><text class="verified-badge">
                ✓ 同校
              </text>
            </view><view class="author-sub">
              {{ post.school }} · {{ post.time }}
            </view>
          </view><button class="follow-btn" :class="{ followed: followed || post.owner }" @click="post.owner ? managePost() : toggleFollow()">
            {{ post.owner ? '管理' : (followed ? '已关注' : '＋ 关注') }}
          </button>
        </view>
        <view v-if="post.price" class="price">
          <text>¥</text>{{ post.price }}
        </view><view class="title">
          {{ post.title }}
        </view><view class="body">
          {{ post.content }}
        </view>
        <view class="tags">
          <text v-for="tag in post.tags" :key="tag">
            # {{ tag }}
          </text>
        </view>
        <view class="meta">
          <text class="meta-location">
            📍 {{ post.location || `${post.school} · 校内` }}
          </text><view class="meta-actions">
            <text>浏览 {{ post.views || 0 }}</text><text v-if="!post.owner" class="report-entry" @click="reportPost">
              举报
            </text>
          </view>
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
        </view><view class="action" :class="{ active: liked }" @click="toggleLike">
          <text>{{ liked ? '♥' : '♡' }}</text><span>{{ post.likes }}</span>
        </view><view class="action" :class="{ active: collected }" @click="toggleCollect">
          <text>{{ collected ? '★' : '☆' }}</text><span>收藏</span>
        </view><button class="contact-btn" @click="contact">
          联系TA
        </button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding-bottom: 190rpx;
  background: var(--yd-paper);
}
.media {
  height: 620rpx;
}
.detail-photo {
  width: 100%;
  height: 100%;
  background: #eef0eb;
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
  border-radius: 8rpx;
  background: rgba(255, 253, 248, 0.9);
  font-size: 22rpx;
  font-weight: 700;
}
.media-item.second {
  background: #eef0eb;
}
.content-card,
.comments-card {
  margin: 16rpx 18rpx 0;
  padding: 28rpx 24rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 26rpx;
  background: var(--yd-card);
  box-shadow: 0 5rpx 0 rgba(75, 59, 44, 0.035);
}
.author-row {
  display: flex;
  align-items: center;
  min-width: 0;
  padding-bottom: 22rpx;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.1);
}
.author-avatar,
.comment-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 28rpx;
  font-weight: 800;
}
.author-main {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  font-size: 27rpx;
  font-weight: 800;
}
.author-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8rpx;
}
.author-name > text:first-child {
  overflow: hidden;
  color: var(--yd-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.author-main .verified-badge {
  flex: 0 0 auto;
  color: var(--yd-green);
  font-size: 19rpx;
}
.author-sub {
  display: block;
  margin-top: 6rpx;
  color: #8a9490;
  font-size: 20rpx;
  font-weight: 400;
}
.follow-btn {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 64rpx;
  margin-left: 16rpx;
  padding: 0 14rpx;
  border: 1rpx solid var(--yd-green);
  border-radius: var(--yd-control-radius);
  color: var(--yd-green-dark);
  background: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}
.follow-btn.followed {
  border-color: rgba(60, 60, 67, 0.12);
  color: var(--yd-muted);
  background: rgba(118, 118, 128, 0.09);
}
.price {
  margin-top: 28rpx;
  color: var(--yd-coral);
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
  border-radius: 8rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
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
.meta-location {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 18rpx;
  margin-left: 18rpx;
}
.report-entry {
  color: #777b84;
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
  min-width: 0;
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
  flex: 0 0 auto;
  min-width: 52rpx;
  margin-left: 10rpx;
  color: #87918d;
  font-size: 20rpx;
  text-align: right;
}
.all-comments {
  margin-top: 28rpx;
  color: var(--yd-green);
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
  display: grid;
  grid-template-columns: minmax(172rpx, 1fr) 70rpx 70rpx 198rpx;
  align-items: center;
  gap: 10rpx;
  padding: 14rpx 22rpx calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--yd-line);
  background: rgba(246, 248, 252, 0.76);
}
.comment-input {
  min-width: 0;
  height: 76rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: var(--yd-paper-deep);
}
.comment-input input {
  height: 100%;
  font-size: 22rpx;
}
.action {
  display: flex;
  width: 70rpx;
  height: 76rpx;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #717c77;
  font-size: 17rpx;
  line-height: 1.05;
}
.action > text {
  margin-bottom: 5rpx;
  font-size: 29rpx;
  line-height: 1;
}
.action.active {
  color: var(--yd-coral);
}
.contact-btn {
  display: flex;
  width: 100%;
  height: var(--yd-control-regular);
  margin: 0;
  padding: 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  font-size: 26rpx;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
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

/* Apple-inspired glass theme */
.content-card,
.comments-card,
.related,
.bottom-bar {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 18rpx 46rpx rgba(33, 50, 86, 0.09);
  backdrop-filter: blur(30rpx) saturate(155%);
  -webkit-backdrop-filter: blur(30rpx) saturate(155%);
}
.content-card,
.comments-card {
  border-radius: 26rpx;
}
.tags text,
.comment-input {
  border-color: rgba(60, 60, 67, 0.1);
  background: rgba(118, 118, 128, 0.08);
}
.contact-btn {
  background: var(--yd-green);
  box-shadow: 0 10rpx 26rpx rgba(10, 132, 255, 0.24);
}
</style>
