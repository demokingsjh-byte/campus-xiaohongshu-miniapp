<script lang="ts" setup>
import type { CampusPostComment } from '@/services/api/content';
import CampusPostCard from '@/components/CampusFeedCard/index.vue';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusPosts } from '@/mock/campus';
import { createCampusContactRequest, createCampusPostComment, deleteCampusComment, getCampusPostCommentPage, reportCampusComment, reportCampusPost, setCampusCommentLike } from '@/services/api/content';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar } from '@/utils/avatar';

const postId = ref(2001);
const liked = ref(false);
const collected = ref(false);
const followed = ref(false);
const interactionBusy = ref(false);
const pageState = ref<'loading' | 'content' | 'error'>('loading');
const comment = ref('');
const comments = ref<CampusPostComment[]>([]);
const commentTotal = ref(0);
const commentPageNo = ref(1);
const commentState = ref<'loading' | 'content' | 'error'>('loading');
const commentSubmitting = ref(false);
const commentsLoadingMore = ref(false);
let commentsRequestToken = 0;
const commentSort = ref<'latest' | 'likes' | 'seller'>('latest');
const replyTarget = ref<CampusPostComment | null>(null);
const contactSubmitting = ref(false);
const contentStore = useCampusContentStore();
const userStore = useUserStore();
const post = computed(() => contentStore.getPost(postId.value) || campusPosts[0]);
const channelIcons: Record<string, string> = {
  二手: '/static/icons/login/trade.svg',
  互助: '/static/icons/login/help.svg',
  拼车: '/static/icons/publish/ride.svg',
  探店: '/static/icons/publish/shop.svg',
  失物: '/static/icons/publish/lost.svg',
  社团: '/static/icons/login/event.svg',
};
const channelIcon = computed(() => channelIcons[post.value.channel] || '/static/icons/mine/cloud.svg');
const related = computed(() => contentStore.allPosts.filter(item => item.id !== post.value.id && item.tenantId === post.value.tenantId).slice(0, 2));
const hasMoreComments = computed(() => comments.value.length < commentTotal.value);

onLoad(async (query) => {
  postId.value = Number(query?.id || 2001);
  pageState.value = 'loading';
  try {
    const loaded = await contentStore.loadPost(postId.value);
    liked.value = Boolean(loaded.liked);
    collected.value = Boolean(loaded.collected);
    pageState.value = 'content';
    await loadComments();
  } catch {
    pageState.value = 'error';
  }
});

async function loadComments(append = false) {
  if (append) {
    if (!hasMoreComments.value || commentsLoadingMore.value)
      return;
    commentsLoadingMore.value = true;
  } else {
    commentState.value = 'loading';
    commentPageNo.value = 1;
  }
  const requestedPostId = postId.value;
  const requestToken = ++commentsRequestToken;
  const targetPage = append ? commentPageNo.value + 1 : 1;
  try {
    const page = await getCampusPostCommentPage(requestedPostId, { pageNo: targetPage, pageSize: 20, sort: commentSort.value });
    // The detail page can be reused while a previous request is still pending.
    // Ignore stale responses and defensively keep only this post's comments.
    if (requestToken !== commentsRequestToken || requestedPostId !== postId.value)
      return;
    const postComments = (page.list || []).filter(item => Number(item.postId) === requestedPostId);
    comments.value = append
      ? [...comments.value, ...postComments.filter(item => !comments.value.some(existing => existing.id === item.id))]
      : postComments;
    commentTotal.value = page.total || 0;
    commentPageNo.value = targetPage;
    commentState.value = 'content';
  } catch {
    if (requestToken !== commentsRequestToken || requestedPostId !== postId.value)
      return;
    if (!append)
      commentState.value = 'error';
    else
      uni.showToast({ title: '更多评论加载失败，请重试', icon: 'none' });
  } finally {
    commentsLoadingMore.value = false;
  }
}
function ensureLogin() {
  if (userStore.loggedIn)
    return true;
  uni.showModal({ title: '登录后参与互动', content: '登录后可以评论、收藏和联系发布者。', confirmText: '去登录', success: res => res.confirm && uni.navigateTo({ url: '/pages/login/index' }) });
  return false;
}
async function sendComment() {
  if (!ensureLogin())
    return;
  const content = comment.value.trim();
  if (!content || commentSubmitting.value)
    return;
  if (content.length > 300) {
    uni.showToast({ title: '评论最多 300 个字', icon: 'none' });
    return;
  }
  commentSubmitting.value = true;
  try {
    const created = await createCampusPostComment(postId.value, content, replyTarget.value?.id);
    if (Number(created.postId) !== postId.value)
      throw new Error('评论所属帖子不一致');
    comments.value.unshift(created);
    commentTotal.value += 1;
    post.value.comments = commentTotal.value;
    comment.value = '';
    replyTarget.value = null;
    commentState.value = 'content';
    // Re-read the current post's comments so the list and total use server data.
    await loadComments();
    uni.showToast({ title: '评论成功', icon: 'success' });
  } catch {
    uni.showToast({ title: '评论发布失败，请重试', icon: 'none' });
  } finally {
    commentSubmitting.value = false;
  }
}

function replyToComment(item: CampusPostComment) {
  if (!ensureLogin())
    return;
  replyTarget.value = item;
}

async function toggleCommentLike(item: CampusPostComment) {
  if (!ensureLogin())
    return;
  try {
    const updated = await setCampusCommentLike(item.id, !item.liked);
    Object.assign(item, updated);
  } catch {
    uni.showToast({ title: '评论点赞失败，请重试', icon: 'none' });
  }
}

async function removeComment(item: CampusPostComment) {
  if (!item.owner)
    return;
  const result = await new Promise<UniApp.ShowModalRes>(resolve => uni.showModal({
    title: '删除评论',
    content: '删除后无法恢复，确定继续吗？',
    confirmColor: '#FF453A',
    success: resolve,
  }));
  if (!result.confirm)
    return;
  try {
    await deleteCampusComment(item.id);
    const removedIds = new Set([item.id, ...comments.value.filter(commentItem => commentItem.parentId === item.id).map(commentItem => commentItem.id)]);
    comments.value = comments.value.filter(commentItem => !removedIds.has(commentItem.id));
    commentTotal.value = Math.max(0, commentTotal.value - removedIds.size);
    post.value.comments = commentTotal.value;
    uni.showToast({ title: '评论已删除', icon: 'success' });
  } catch {
    uni.showToast({ title: '评论删除失败，请重试', icon: 'none' });
  }
}

function reportCommentItem(item: CampusPostComment) {
  if (!ensureLogin())
    return;
  const reasons = ['广告信息', '虚假交易', '不文明言论', '违规联系方式', '其他'];
  uni.showActionSheet({
    itemList: reasons,
    success: ({ tapIndex }) => {
      const reason = reasons[tapIndex];
      if (!reason)
        return;
      uni.showModal({
        title: `举报：${reason}`,
        editable: true,
        placeholderText: '可补充说明，最多 300 字',
        confirmText: '提交举报',
        success: async (result) => {
          if (!result.confirm)
            return;
          try {
            await reportCampusComment(item.id, { reason, detail: result.content?.trim().slice(0, 300) });
            uni.showToast({ title: '举报已提交', icon: 'success' });
          } catch {
            uni.showToast({ title: '举报提交失败，请重试', icon: 'none' });
          }
        },
      });
    },
  });
}

async function changeCommentSort(sort: 'latest' | 'likes' | 'seller') {
  if (commentSort.value === sort)
    return;
  commentSort.value = sort;
  await loadComments();
}
async function contact() {
  if (!ensureLogin() || contactSubmitting.value)
    return;
  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfo();
    } catch {
      uni.showToast({ title: '用户资料加载失败，请重试', icon: 'none' });
      return;
    }
  }
  if (!userStore.userInfo?.mobileBound && !userStore.userInfo?.mobile) {
    uni.showModal({
      title: '先绑定手机号',
      content: '联系申请会提交给校园运营，请先授权绑定手机号，以便工作人员联系你。',
      confirmText: '去绑定',
      success: res => res.confirm && uni.navigateTo({ url: '/pages/login/index?mode=edit' }),
    });
    return;
  }
  contactSubmitting.value = true;
  try {
    await createCampusContactRequest(postId.value);
    uni.showToast({ title: '联系申请已提交', icon: 'success' });
  } catch {
    uni.showToast({ title: '提交失败，请稍后重试', icon: 'none' });
  } finally {
    contactSubmitting.value = false;
  }
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
      <swiper class="media" indicator-dots indicator-active-color="#10A779">
        <swiper-item>
          <image v-if="post.coverImage" class="detail-photo" :src="post.coverImage" mode="aspectFill" />
          <view v-else class="media-item" :style="{ background: post.coverColor }">
            <image :src="channelIcon" mode="aspectFit" /><view>{{ post.coverLabel }}</view>
          </view>
        </swiper-item>
      </swiper>
      <view class="content-card">
        <view class="author-row">
          <view class="author-avatar">
            <image :src="resolveCampusAvatar(post.avatar)" mode="aspectFill" />
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
          <view class="meta-location">
            <image src="/static/icons/ui/location.svg" mode="aspectFit" /><text>{{ post.location || `${post.school} · 校内` }}</text>
          </view><view class="meta-actions">
            <text>浏览 {{ post.views || 0 }}</text><text v-if="!post.owner" class="report-entry" @click="reportPost">
              举报
            </text>
          </view>
        </view>
      </view>

      <view class="comments-card">
        <view class="section-title">
          评论 {{ commentTotal }}
        </view>
        <view class="comment-sort">
          <text :class="{ active: commentSort === 'latest' }" @click="changeCommentSort('latest')">最新</text>
          <text :class="{ active: commentSort === 'likes' }" @click="changeCommentSort('likes')">最热</text>
          <text :class="{ active: commentSort === 'seller' }" @click="changeCommentSort('seller')">卖家回复</text>
        </view>
        <view v-if="commentState === 'loading'" class="comment-status">
          评论加载中…
        </view>
        <view v-else-if="commentState === 'error'" class="comment-status comment-retry" @click="loadComments()">
          评论加载失败，点击重试
        </view>
        <view v-else-if="!comments.length" class="comment-status">
          还没有评论，来聊聊你的想法吧
        </view>
        <view v-for="item in comments" :key="item.id" class="comment" :class="{ 'comment-reply': item.parentId }">
          <view class="comment-avatar">
            <image :src="resolveCampusAvatar(item.avatar)" mode="aspectFill" />
          </view><view class="comment-main">
            <view class="comment-name">
              {{ item.author }} <text>{{ item.time }}</text>
            </view><view class="comment-content">
              <text v-if="item.parentId" class="reply-mark">回复评论：</text>
              {{ item.content }}
            </view>
            <view class="comment-actions">
              <text @click="replyToComment(item)">回复</text>
              <text :class="{ active: item.liked }" @click="toggleCommentLike(item)">赞 {{ item.likeCount || 0 }}</text>
              <text v-if="!item.owner" @click="reportCommentItem(item)">举报</text>
              <text v-if="item.owner" class="danger" @click="removeComment(item)">删除</text>
            </view>
          </view><view v-if="item.owner" class="comment-owner">
            我
          </view>
        </view><view v-if="hasMoreComments" class="all-comments" @click="loadComments(true)">
          {{ commentsLoadingMore ? '加载中…' : '加载更多评论 ›' }}
        </view>
      </view>

      <view class="related">
        <view class="section-title">
          同校同学还在看
        </view><view class="related-grid">
          <CampusPostCard v-for="item in related" :key="item.id" :post="item" />
        </view>
      </view>

      <view v-if="replyTarget" class="replying-bar" @click="replyTarget = null">
        正在回复 {{ replyTarget.author }}，点击取消
      </view>
      <view class="bottom-bar">
        <view class="comment-input">
          <input v-model="comment" :disabled="commentSubmitting" maxlength="300" placeholder="友善评论一下…" confirm-type="send" @confirm="sendComment">
          <text v-if="comment.trim()" class="comment-send" @click="sendComment">
            {{ commentSubmitting ? '发送中' : '发送' }}
          </text>
        </view><view class="action" :class="{ active: liked }" @click="toggleLike">
          <image src="/static/icons/mine/heart.svg" mode="aspectFit" /><text>{{ post.likes }}</text>
        </view><view class="action" :class="{ active: collected }" @click="toggleCollect">
          <image src="/static/icons/ui/star.svg" mode="aspectFit" /><text>收藏</text>
        </view><button class="contact-btn" :disabled="contactSubmitting" @click="contact">
          {{ contactSubmitting ? '提交中…' : '联系TA' }}
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
  background: var(--color-page-deep);
}
.media-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.media-item > image {
  width: 150rpx;
  height: 150rpx;
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
.author-avatar,
.comment-avatar {
  overflow: hidden;
}
.author-avatar image,
.comment-avatar image {
  width: 100%;
  height: 100%;
}
.author-main {
  flex: 1;
  min-width: 0;
  margin-left: var(--yd-icon-gap);
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
  margin-top: var(--yd-copy-gap);
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
  display: flex;
  overflow: hidden;
  align-items: center;
  gap: 8rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-location image {
  flex: 0 0 auto;
  width: 26rpx;
  height: 26rpx;
}
.meta-location text {
  overflow: hidden;
  text-overflow: ellipsis;
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
.comment-sort {
  display: flex;
  gap: 22rpx;
  margin-top: 18rpx;
  color: var(--yd-muted);
  font-size: 21rpx;
}
.comment-sort text.active {
  color: var(--yd-green-dark);
  font-weight: 800;
}
.comment {
  display: flex;
  align-items: flex-start;
  margin-top: 28rpx;
}
.comment-reply {
  margin-left: 48rpx;
}
.comment-avatar {
  overflow: hidden;
  width: 64rpx;
  height: 64rpx;
}
.comment-avatar image {
  width: 100%;
  height: 100%;
}
.comment-main {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
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
  margin-top: var(--yd-copy-gap);
  color: #505c57;
  font-size: 24rpx;
  line-height: 1.55;
}
.reply-mark {
  color: var(--yd-green-dark);
  font-size: 22rpx;
  font-weight: 700;
}
.comment-actions {
  display: flex;
  gap: 22rpx;
  margin-top: 14rpx;
  color: var(--yd-muted);
  font-size: 20rpx;
}
.comment-actions text.active {
  color: var(--yd-green-dark);
  font-weight: 800;
}
.comment-actions .danger {
  color: #d95757;
}
.comment-owner {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  min-width: 44rpx;
  height: 34rpx;
  margin-left: 10rpx;
  border-radius: 999rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 18rpx;
}
.comment-status {
  padding: 42rpx 10rpx 24rpx;
  color: var(--yd-muted);
  font-size: 23rpx;
  text-align: center;
}
.comment-retry {
  color: var(--yd-green-dark);
}
.all-comments {
  margin-top: 28rpx;
  color: var(--yd-green);
  font-size: 23rpx;
  text-align: center;
}
.replying-bar {
  position: fixed;
  z-index: 11;
  right: 0;
  bottom: 142rpx;
  left: 0;
  padding: 12rpx 24rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 21rpx;
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
  display: flex;
  min-width: 0;
  height: 76rpx;
  align-items: center;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: var(--yd-paper-deep);
}
.comment-input input {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: 22rpx;
}
.comment-send {
  flex: 0 0 auto;
  margin-left: 8rpx;
  color: var(--yd-green-dark);
  font-size: 21rpx;
  font-weight: 800;
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
.action image {
  width: 30rpx;
  height: 30rpx;
  margin-bottom: 6rpx;
}
.action > text {
  font-size: 17rpx;
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
.contact-btn[disabled] {
  opacity: 0.62;
}
.detail-loading {
  padding: 24rpx;
}
.hero-sk,
.line-sk {
  border-radius: 20rpx;
  background: var(--color-page-deep);
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

/* Emerald glass theme */
.content-card,
.comments-card,
.related,
.bottom-bar {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 18rpx 48rpx rgba(20, 91, 70, 0.1);
  backdrop-filter: blur(30rpx) saturate(155%);
  -webkit-backdrop-filter: blur(30rpx) saturate(155%);
}
.content-card,
.comments-card {
  border-radius: 26rpx;
}
.related {
  margin: 16rpx 18rpx 0;
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-lg);
}
.tags text,
.comment-input {
  border-color: rgba(60, 60, 67, 0.1);
  background: rgba(118, 118, 128, 0.08);
}
.contact-btn {
  background: var(--yd-green);
  box-shadow: 0 10rpx 26rpx rgba(16, 167, 121, 0.24);
}
</style>
