<script lang="ts" setup>
import type { CampusPostComment } from '@/services/api/content';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusPosts } from '@/mock/campus';
import { createCampusContactRequest, createCampusPostComment, deleteCampusComment, getCampusPostCommentPage, reportCampusComment, reportCampusPost, setCampusCommentLike } from '@/services/api/content';
import { uploadCampusCommentImage } from '@/services/api/file';
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
const commentSort = ref<'latest' | 'likes'>('latest');
const replyTarget = ref<CampusPostComment | null>(null);
const commentImages = ref<string[]>([]);
const mentionUserIds = ref<number[]>([]);
const showEmojiPanel = ref(false);
const showMentionPanel = ref(false);
const showCommentComposer = ref(false);
const emojiList = ['😀', '😂', '🥹', '😍', '😎', '👍', '❤️', '👏', '🎉', '🤔', '😭', '🙏', '🐱', '✨', '😊', '🔥'];
const expandedReplyCounts = ref<Record<number, number>>({});
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
const hasMoreComments = computed(() => comments.value.length < commentTotal.value);
const topLevelComments = computed(() => comments.value.filter(item => !item.parentId));
const mentionCandidates = computed(() => {
  const candidates = new Map<number, { id: number, name: string, avatar?: string }>();
  if (post.value.userId && post.value.author) {
    candidates.set(Number(post.value.userId), { id: Number(post.value.userId), name: post.value.author, avatar: post.value.avatar });
  }
  comments.value.forEach((item) => {
    if (item.userId && item.author)
      candidates.set(Number(item.userId), { id: Number(item.userId), name: item.author, avatar: item.avatar });
  });
  return [...candidates.values()];
});

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
  if ((!content && !commentImages.value.length) || commentSubmitting.value)
    return;
  if (content.length > 300) {
    uni.showToast({ title: '评论最多 300 个字', icon: 'none' });
    return;
  }
  commentSubmitting.value = true;
  try {
    const uploadedImages = await Promise.all(commentImages.value.map(image => uploadCampusCommentImage(image)));
    const created = await createCampusPostComment(postId.value, {
      content,
      parentId: replyTarget.value?.id,
      replyToUserId: replyTarget.value?.userId,
      mentionUserIds: mentionUserIds.value,
      images: uploadedImages,
    });
    if (Number(created.postId) !== postId.value)
      throw new Error('评论所属帖子不一致');
    comments.value.unshift(created);
    commentTotal.value += 1;
    post.value.comments = commentTotal.value;
    comment.value = '';
    commentImages.value = [];
    mentionUserIds.value = [];
    replyTarget.value = null;
    showEmojiPanel.value = false;
    showMentionPanel.value = false;
    showCommentComposer.value = false;
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
  replyTarget.value = item;
  showCommentComposer.value = true;
  showEmojiPanel.value = false;
  showMentionPanel.value = false;
}

function openCommentComposer() {
  showCommentComposer.value = true;
}

function closeCommentComposer() {
  showEmojiPanel.value = false;
  showMentionPanel.value = false;
  showCommentComposer.value = false;
}

function toggleMentionPanel() {
  showMentionPanel.value = !showMentionPanel.value;
  showEmojiPanel.value = false;
}

function toggleEmojiPanel() {
  showEmojiPanel.value = !showEmojiPanel.value;
  showMentionPanel.value = false;
}

function insertEmoji(emoji: string) {
  comment.value += emoji;
}

function insertMention(candidate: { id: number, name: string }) {
  comment.value += `@${candidate.name} `;
  if (!mentionUserIds.value.includes(candidate.id))
    mentionUserIds.value.push(candidate.id);
  showMentionPanel.value = false;
}

function handleCommentInput(event: any) {
  const value = String(event?.detail?.value || '');
  comment.value = value;
  showMentionPanel.value = value.endsWith('@');
}

function chooseCommentImages() {
  const remain = 3 - commentImages.value.length;
  if (remain <= 0) {
    uni.showToast({ title: '每条评论最多上传 3 张图片', icon: 'none' });
    return;
  }
  uni.chooseImage({
    count: remain,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: result => commentImages.value.push(...(result.tempFilePaths || []).slice(0, remain)),
  });
}

function removeCommentImage(index: number) {
  commentImages.value.splice(index, 1);
}

function previewCommentImages(images: string[], current: string) {
  uni.previewImage({ urls: images, current });
}

function repliesOf(parentId: number) {
  return comments.value.filter(item => Number(item.parentId) === Number(parentId));
}

function visibleRepliesOf(parentId: number) {
  const replies = repliesOf(parentId);
  const count = expandedReplyCounts.value[parentId] || 3;
  return replies.slice(0, count);
}

function toggleReplies(parentId: number) {
  const replies = repliesOf(parentId);
  const current = expandedReplyCounts.value[parentId] || 3;
  expandedReplyCounts.value[parentId] = current >= replies.length ? 3 : replies.length;
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

async function changeCommentSort(sort: 'latest' | 'likes') {
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
        <view class="detail-actions">
          <view class="detail-action" :class="{ active: liked }" @click="toggleLike">
            <image src="/static/icons/mine/heart.svg" mode="aspectFit" /><text>{{ post.likes || 0 }}</text>
          </view>
          <view class="detail-action" :class="{ active: collected }" @click="toggleCollect">
            <image src="/static/icons/ui/star.svg" mode="aspectFit" /><text>{{ collected ? '已收藏' : '收藏' }}</text>
          </view>
          <button class="detail-contact" :disabled="contactSubmitting" @click="contact">
            {{ contactSubmitting ? '提交中…' : '联系TA' }}
          </button>
        </view>
      </view>

      <view class="comments-card">
        <view class="section-title">
          评论 {{ commentTotal }}
        </view>
        <view class="comment-sort">
          <text :class="{ active: commentSort === 'latest' }" @click="changeCommentSort('latest')">最新</text>
          <text :class="{ active: commentSort === 'likes' }" @click="changeCommentSort('likes')">最热</text>
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
        <view v-for="item in topLevelComments" :key="item.id" class="comment-block">
          <view class="comment" @click="replyToComment(item)">
            <view class="comment-avatar">
              <image :src="resolveCampusAvatar(item.avatar)" mode="aspectFill" />
            </view><view class="comment-main">
              <view class="comment-name">
                {{ item.author }}
              </view><view class="comment-content">
                {{ item.content }}
              </view>
              <view v-if="item.images?.length" class="comment-images">
                <image v-for="image in item.images" :key="image" :src="image" mode="aspectFill" @click.stop="previewCommentImages(item.images || [], image)" />
              </view>
              <view class="comment-meta-row">
                <view class="comment-left-actions">
                  <text v-if="!item.owner" class="comment-report" @click.stop="reportCommentItem(item)">举报</text>
                  <text v-if="item.owner" class="comment-report danger" @click.stop="removeComment(item)">删除</text>
                </view>
                <text class="comment-time">{{ item.time }}</text>
                <text class="comment-reply-action" @click.stop="replyToComment(item)">回复</text>
                <text
                  class="comment-like" :class="{ active: item.liked }" @click.stop="toggleCommentLike(item)"
                >{{ item.liked ? '♥' : '♡' }} {{ item.likeCount || 0 }}</text>
              </view>
            </view><view v-if="item.owner" class="comment-owner">
              我
            </view>
          </view>
          <view v-if="repliesOf(item.id).length" class="reply-thread">
            <view
              v-for="reply in visibleRepliesOf(item.id)" :key="reply.id" class="comment comment-reply"
              @click="replyToComment(reply)"
            >
              <view class="comment-avatar">
                <image :src="resolveCampusAvatar(reply.avatar)" mode="aspectFill" />
              </view><view class="comment-main">
                <view class="comment-name">
                  {{ reply.author }}
                </view><view class="comment-content">
                  <text class="reply-mark">回复 {{ reply.replyToAuthor || item.author }}：</text>{{ reply.content }}
                </view>
                <view v-if="reply.images?.length" class="comment-images">
                  <image
                    v-for="image in reply.images" :key="image" :src="image" mode="aspectFill"
                    @click.stop="previewCommentImages(reply.images || [], image)"
                  />
                </view>
                <view class="comment-meta-row">
                  <view class="comment-left-actions">
                    <text v-if="!reply.owner" class="comment-report" @click.stop="reportCommentItem(reply)">举报</text>
                    <text v-if="reply.owner" class="comment-report danger" @click.stop="removeComment(reply)">删除</text>
                  </view>
                  <text class="comment-time">{{ reply.time }}</text>
                  <text class="comment-reply-action" @click.stop="replyToComment(reply)">回复</text>
                  <text
                    class="comment-like" :class="{ active: reply.liked }" @click.stop="toggleCommentLike(reply)"
                  >{{ reply.liked ? '♥' : '♡' }} {{ reply.likeCount || 0 }}</text>
                </view>
              </view><view v-if="reply.owner" class="comment-owner">
                我
              </view>
            </view>
          </view>
          <view v-if="repliesOf(item.id).length > 3" class="reply-expand" @click="toggleReplies(item.id)">
            {{ (expandedReplyCounts[item.id] || 3) >= repliesOf(item.id).length ? '收起回复' : `展开 ${repliesOf(item.id).length} 条回复` }}
          </view>
        </view><view v-if="hasMoreComments" class="all-comments" @click="loadComments(true)">
          {{ commentsLoadingMore ? '加载中…' : '加载更多评论 ›' }}
        </view><view v-else-if="comments.length" class="all-comments no-more-comments">
          没有更多评论了
        </view>
      </view>

      <view class="bottom-bar">
        <view class="comment-trigger" @click="openCommentComposer">
          <text>{{ replyTarget ? `回复 ${replyTarget.author}…` : '写下你的评论…' }}</text>
        </view>
      </view>
      <view v-if="showCommentComposer" class="comment-overlay" @click="closeCommentComposer">
        <view class="comment-composer" @click.stop>
          <view class="composer-header">
            <text>{{ replyTarget ? `回复 ${replyTarget.author}` : `评论 ${post.author}` }}</text>
            <text class="composer-close" @click="closeCommentComposer">×</text>
          </view>
          <textarea
            class="composer-textarea" :value="comment" :disabled="commentSubmitting" maxlength="300"
            :placeholder="replyTarget ? `回复 ${replyTarget.author}…` : '写下你的评论…'"
            auto-height @input="handleCommentInput" @confirm="sendComment"
          />
          <view v-if="commentImages.length" class="comment-upload-preview">
            <view v-for="(image, index) in commentImages" :key="image" class="comment-upload-item">
              <image :src="image" mode="aspectFill" /><text @click="removeCommentImage(index)">×</text>
            </view>
          </view>
          <view class="composer-toolbar">
            <view class="comment-tools">
              <text :class="{ active: showMentionPanel }" @click="toggleMentionPanel">＠</text>
              <text :class="{ active: showEmojiPanel }" @click="toggleEmojiPanel">☺</text>
              <text @click="chooseCommentImages">▧</text>
            </view>
            <text class="comment-send" :class="{ disabled: !comment.trim() && !commentImages.length }" @click="sendComment">
              {{ commentSubmitting ? '发送中' : '发布' }}
            </text>
          </view>
          <view v-if="showMentionPanel" class="mention-panel">
            <view v-for="candidate in mentionCandidates" :key="candidate.id" class="mention-item" @click="insertMention(candidate)">
              <image :src="resolveCampusAvatar(candidate.avatar)" mode="aspectFill" /><text>@{{ candidate.name }}</text>
            </view>
          </view>
          <view v-if="showEmojiPanel" class="emoji-panel">
            <view class="emoji-title">全部表情</view>
            <text v-for="emoji in emojiList" :key="emoji" @click="insertEmoji(emoji)">{{ emoji }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding-bottom: 132rpx;
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
.comments-card {
  padding: 30rpx 26rpx 38rpx;
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
.detail-actions {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 22rpx;
}
.detail-action {
  display: flex;
  min-width: 78rpx;
  height: 60rpx;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  color: var(--yd-muted);
  font-size: 20rpx;
}
.detail-action image {
  width: 30rpx;
  height: 30rpx;
}
.detail-action.active {
  color: var(--yd-coral);
}
.detail-contact {
  display: flex;
  flex: 1;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  margin: 0 0 0 auto;
  padding: 0 24rpx;
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1;
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
  margin-top: 32rpx;
}
.comment-reply {
  margin: 16rpx 0 0;
  padding: 14rpx 16rpx;
  border-radius: 16rpx;
  background: rgba(16, 167, 121, 0.045);
}
.reply-thread .comment-reply:first-child {
  margin-top: 0;
}
.reply-thread {
  margin: 20rpx 0 0 76rpx;
  padding: 6rpx 0 6rpx 18rpx;
  border-left: 6rpx solid rgba(16, 167, 121, 0.28);
  border-radius: 0 20rpx 20rpx 0;
  background: rgba(16, 167, 121, 0.025);
}
.comment-avatar {
  overflow: hidden;
  width: 60rpx;
  height: 60rpx;
  flex: 0 0 auto;
  border-radius: 50%;
}
.comment-avatar image {
  width: 100%;
  height: 100%;
}
.comment-main {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
}
.comment-name {
  font-size: 24rpx;
  font-weight: 800;
}
.comment-name text {
  margin-left: 8rpx;
  color: #98a09d;
  font-size: 19rpx;
  font-weight: 400;
}
.comment-content {
  margin-top: 12rpx;
  color: #505c57;
  font-size: 27rpx;
  line-height: 1.55;
}
.comment-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 14rpx;
}
.comment-images image {
  width: 150rpx;
  height: 150rpx;
  border-radius: 12rpx;
  background: rgba(118, 118, 128, 0.08);
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
.comment-meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-height: 34rpx;
  margin-top: 18rpx;
  color: var(--yd-muted);
  font-size: 19rpx;
}
.comment-left-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10rpx;
}
.comment-like {
  flex: 0 0 auto;
  margin-left: auto;
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  color: #7b8581;
  font-size: 22rpx;
  font-weight: 700;
}
.comment-like.active {
  color: #e45858;
  background: rgba(228, 88, 88, 0.08);
}
.comment-report {
  padding: 5rpx 8rpx;
  color: #7b8581;
}
.comment-time {
  flex: 0 0 auto;
  color: #98a09d;
}
.comment-reply-action {
  flex: 0 0 auto;
  padding: 5rpx 8rpx;
  color: var(--yd-green-dark);
  font-weight: 700;
}
.reply-expand {
  margin: 18rpx 0 0 96rpx;
  padding: 8rpx 0;
  color: var(--yd-green-dark);
  font-size: 21rpx;
  font-weight: 700;
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
.no-more-comments {
  color: var(--yd-muted);
}
.bottom-bar {
  position: fixed;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  padding: 12rpx 22rpx calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--yd-line);
  background: rgba(246, 248, 252, 0.76);
}
.comment-trigger {
  display: flex;
  width: 100%;
  height: 76rpx;
  align-items: center;
  padding: 0 26rpx;
  border-radius: 999rpx;
  color: #8c9691;
  background: rgba(118, 118, 128, 0.1);
  font-size: 28rpx;
}
.comment-tools {
  display: flex;
  gap: 24rpx;
  height: 32rpx;
  align-items: center;
  color: var(--yd-muted);
  font-size: 27rpx;
}
.comment-tools text.active,
.comment-tools text:active {
  color: var(--yd-green-dark);
}
.comment-input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.comment-input {
  display: flex;
  flex: 1;
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
.comment-overlay {
  position: fixed;
  z-index: 50;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.42);
}
.comment-composer {
  position: relative;
  width: 100%;
  min-height: 50vh;
  max-height: 84vh;
  box-sizing: border-box;
  padding: 28rpx 34rpx calc(28rpx + env(safe-area-inset-bottom));
  border-radius: 30rpx 30rpx 0 0;
  background: #fff;
  box-shadow: 0 -10rpx 40rpx rgba(0, 0, 0, 0.12);
}
.composer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--yd-ink);
  font-size: 29rpx;
  font-weight: 800;
}
.composer-close {
  display: flex;
  width: 52rpx;
  height: 52rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #7b8581;
  background: rgba(118, 118, 128, 0.1);
  font-size: 38rpx;
  font-weight: 400;
}
.composer-textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 180rpx;
  max-height: 320rpx;
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  color: var(--yd-ink);
  background: rgba(118, 118, 128, 0.08);
  font-size: 29rpx;
  line-height: 1.55;
}
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
}
.composer-toolbar .comment-tools {
  height: 58rpx;
  font-size: 38rpx;
}
.composer-toolbar .comment-send {
  min-width: 110rpx;
  height: 58rpx;
  font-size: 24rpx;
}
.comment-send.disabled {
  opacity: 0.45;
}
.comment-send {
  display: flex;
  flex: 0 0 auto;
  min-width: 76rpx;
  height: 76rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 21rpx;
  font-weight: 800;
}
.comment-upload-preview {
  display: flex;
  gap: 10rpx;
}
.comment-upload-item {
  position: relative;
  width: 78rpx;
  height: 78rpx;
}
.comment-upload-item image {
  width: 100%;
  height: 100%;
  border-radius: 10rpx;
}
.comment-upload-item text {
  position: absolute;
  top: -10rpx;
  right: -10rpx;
  display: flex;
  width: 28rpx;
  height: 28rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: #d95757;
  font-size: 22rpx;
}
.emoji-panel,
.mention-panel {
  position: static;
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  max-height: 350rpx;
  overflow-y: auto;
  margin-top: 20rpx;
  padding: 20rpx 8rpx 4rpx;
  border-top: 1rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 0;
  border: 1rpx solid rgba(60, 60, 67, 0.1);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}
.emoji-title {
  width: 100%;
  margin-bottom: 4rpx;
  color: var(--yd-muted);
  font-size: 23rpx;
}
.emoji-panel text {
  width: 58rpx;
  height: 58rpx;
  font-size: 40rpx;
  line-height: 58rpx;
  text-align: center;
}
.mention-panel {
  display: block;
}
.mention-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 8rpx;
  color: var(--yd-ink);
  font-size: 22rpx;
}
.mention-item image {
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
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
.detail-contact {
  box-shadow: 0 10rpx 26rpx rgba(16, 167, 121, 0.24);
}
</style>
