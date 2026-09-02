<script lang="ts" setup>
import type { CampusPostComment, CampusTradeOrder } from '@/services/api/content';
import StatePanel from '@/components/StatePanel/index.vue';
import { campusPosts } from '@/mock/campus';
import { acceptCampusErrandOrder, cancelCampusErrandOrder, confirmCampusErrandOrder, createCampusContactRequest, createCampusErrandOrder, createCampusPostComment, deleteCampusComment, disputeCampusErrandOrder, getCampusErrandOrderByPost, getCampusPostCommentPage, getCampusUserFollowStatus, migrateLocalCampusFollows, reportCampusComment, reportCampusPost, setCampusCommentLike, setCampusUserFollow, submitCampusErrandOrder } from '@/services/api/content';
import { uploadCampusCommentImage, uploadCampusErrandEvidence } from '@/services/api/file';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar, resolveCampusMediaUrl } from '@/utils/avatar';
import { recordCampusHistory } from '@/utils/personalRecords';

const postId = ref(2001);
const liked = ref(false);
const collected = ref(false);
const followed = ref(false);
const followBusy = ref(false);
const interactionBusy = ref(false);
const pageState = ref<'loading' | 'content' | 'error'>('loading');
const comment = ref('');
const commentError = ref('');
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
const commentInputFocused = ref(false);
const keyboardHeight = ref(0);
const contentExpanded = ref(false);
const ownerContext = ref(false);
const emojiList = ['😀', '😂', '🥹', '😍', '😎', '👍', '❤️', '👏', '🎉', '🤔', '😭', '🙏', '🐱', '✨', '😊', '🔥'];
const expandedReplyCounts = ref<Record<number, number>>({});
const contactSubmitting = ref(false);
const errandOrder = ref<CampusTradeOrder>();
const showErrandEvidenceComposer = ref(false);
const errandEvidenceMode = ref<'complete' | 'dispute'>('complete');
const errandEvidenceText = ref('');
const errandEvidenceImages = ref<string[]>([]);
const errandEvidenceSubmitting = ref(false);
const nowTick = ref(Date.now());
let errandClockTimer: ReturnType<typeof setInterval> | undefined;
const coverImageFailed = ref(false);
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
const isConfession = computed(() => post.value.channel === '表白' || post.value.type === 'confession');
const isIdlePost = computed(() => post.value.channel === '二手' || post.value.type === 'idle');
const isShopPost = computed(() => post.value.channel === '探店' || post.value.type === 'shop');
const canNavigateToMerchant = computed(() => {
  if (!isShopPost.value || post.value.merchantLatitude == null || post.value.merchantLongitude == null)
    return false;
  const latitude = Number(post.value.merchantLatitude);
  const longitude = Number(post.value.merchantLongitude);
  return Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180;
});
const downlisted = computed(() => post.value.downlisted === true);
const soldOut = computed(() => isIdlePost.value
  && (post.value.soldOut === true || Number(post.value.stockAvailable) <= 0));
const saleCompleted = computed(() => {
  if (!soldOut.value)
    return false;
  const total = Number(post.value.stockTotal);
  const sold = Number(post.value.soldCount);
  return post.value.stockTotal === undefined || post.value.soldCount === undefined
    || (Number.isFinite(total) && Number.isFinite(sold) && sold >= total);
});
const stockSummary = computed(() => {
  if (!isIdlePost.value || post.value.stockAvailable === undefined)
    return '';
  if (downlisted.value)
    return '商品已下架';
  return soldOut.value
    ? (saleCompleted.value
        ? `商品已卖出 · 已售 ${Number(post.value.soldCount || 0)} 件`
        : '商品交易中 · 库存已由待付款订单锁定')
    : `剩余 ${Number(post.value.stockAvailable || 0)} 件 · 已售 ${Number(post.value.soldCount || 0)} 件`;
});
const isErrandPost = computed(() => post.value.type === 'help');
const currentUserId = computed(() => Number(userStore.userInfo?.id || 0));
const isErrandHelper = computed(() => Boolean(errandOrder.value?.sellerId)
  && Number(errandOrder.value?.sellerId) === currentUserId.value);
const hasMarkedPrice = computed(() => String(post.value.price ?? '').trim().length > 0);
const postHotCount = computed(() => [post.value.views, post.value.likes, post.value.collects, post.value.comments].reduce((total, current) => {
  const value = Number(current || 0);
  return total + (Number.isFinite(value) ? Math.max(0, value) : 0);
}, 0));
const postImages = computed(() => {
  const images = Array.isArray(post.value.images) ? post.value.images.filter(Boolean) : [];
  const coverImage = typeof post.value.coverImage === 'string' ? post.value.coverImage.trim() : '';
  return coverImage && !images.includes(coverImage) ? [coverImage, ...images] : images;
});
const showCoverImage = computed(() => postImages.value.length > 0 && !coverImageFailed.value);
const hasMoreComments = computed(() => comments.value.length < commentTotal.value);
const topLevelComments = computed(() => comments.value.filter(item => !item.parentId));
const replyAuthor = computed(() => {
  const author = replyTarget.value?.author;
  return typeof author === 'string' ? author.trim() : '';
});
const postAuthor = computed(() => {
  const author = post.value?.author;
  return typeof author === 'string' ? author.trim() : '';
});

function normalizeIdentityText(value?: string) {
  return String(value || '')
    .normalize('NFKC')
    .replace(/[\s\u200B-\u200D\uFEFF]/g, '')
    .toLocaleLowerCase();
}

function normalizeIdentityUrl(value?: string) {
  return String(value || '').trim().replace(/^http:/i, 'https:');
}

const isOwnPost = computed(() => {
  if (ownerContext.value)
    return true;
  if (post.value?.owner === true)
    return true;
  if (contentStore.publishedPosts.some(item => Number(item.id) === Number(postId.value)))
    return true;
  const currentUserId = Number(userStore.userInfo?.id || 0);
  const postUserId = Number(post.value?.userId || 0);
  if (currentUserId > 0 && postUserId > 0 && currentUserId === postUserId)
    return true;

  // 兼容历史线上数据：部分旧帖子 owner/userId 不正确，但作者快照仍然完整。
  // 昵称相同后继续核对头像；没有头像时核对学校，避免只凭昵称误判。
  const currentNickname = normalizeIdentityText(userStore.userInfo?.nickname);
  const authorNickname = normalizeIdentityText(post.value?.author);
  if (!currentNickname || currentNickname !== authorNickname)
    return false;
  const currentAvatar = normalizeIdentityUrl(userStore.userInfo?.avatar);
  const authorAvatar = normalizeIdentityUrl(post.value?.avatar);
  const currentSchool = normalizeIdentityText(userStore.userInfo?.schoolName);
  const authorSchool = normalizeIdentityText(post.value?.school);
  const avatarMatches = Boolean(currentAvatar && authorAvatar && currentAvatar === authorAvatar);
  const schoolMatches = Boolean(currentSchool && authorSchool && currentSchool === authorSchool);
  return avatarMatches || schoolMatches;
});
const isErrandPublisher = computed(() => {
  const publisherId = Number(errandOrder.value?.buyerId || 0);
  return publisherId > 0 ? publisherId === currentUserId.value : isOwnPost.value;
});
const canOpenErrandChat = computed(() => {
  const order = errandOrder.value;
  return Boolean(isErrandPost.value && order?.sellerId
    && (isErrandPublisher.value || isErrandHelper.value)
    && [1, 2].includes(Number(order.status))
    && [2, 3, 4].includes(Number(order.fulfillmentStatus)));
});
const errandChatText = computed(() => isErrandPublisher.value ? '联系接单人' : '联系发布人');
const errandConfirmRemainingText = computed(() => {
  const expiresAt = errandOrder.value?.confirmExpiresAt;
  if (!expiresAt)
    return '';
  const remaining = new Date(expiresAt).getTime() - nowTick.value;
  if (remaining <= 0)
    return '确认期已结束，等待系统自动结算';
  const totalMinutes = Math.ceil(remaining / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `剩余 ${hours} 小时 ${minutes} 分钟`;
});
const errandStatusNote = computed(() => {
  const order = errandOrder.value;
  if (!order)
    return '';
  if (order.disputeStatus === 1)
    return '申诉期间赏金保持冻结，等待平台核对双方凭证并裁决';
  if (order.disputeStatus === 2)
    return order.disputeResolution || '平台已裁决接单人完成任务，赏金已结算';
  if (order.disputeStatus === 3)
    return order.disputeResolution || '平台已裁决发布人胜诉，赏金将原路退款';
  if (order.fulfillmentStatus === 1)
    return '赏金已托管，等待同校学生接单；24 小时无人接单将自动退款';
  if (order.fulfillmentStatus === 2)
    return '接单人办理中，双方可通过任务会话沟通';
  if (order.fulfillmentStatus === 3)
    return `${errandConfirmRemainingText.value}；发布人可确认或申诉，逾期自动结算`;
  if (order.fulfillmentStatus === 4)
    return order.autoConfirmed ? '发布人超时未操作，系统已自动确认并结算收益' : '任务已确认，接单人收益已结算';
  return '任务进度以订单状态为准';
});
const contentExpandable = computed(() => String(post.value?.content || '').trim().length > 60);
const contactButtonText = computed(() => {
  if (downlisted.value)
    return '商品已下架';
  if (isErrandPost.value) {
    const order = errandOrder.value;
    if (!order)
      return isOwnPost.value ? '支付赏金' : '等待付款';
    if (order.status === 0)
      return isErrandPublisher.value ? '支付赏金' : '等待付款';
    if (order.disputeStatus === 1)
      return '申诉处理中';
    if (order.disputeStatus === 2)
      return isErrandHelper.value ? '收益已入账' : '平台已裁决';
    if (order.disputeStatus === 3)
      return isErrandPublisher.value ? (order.refundStatus === 2 ? '赏金已退款' : '退款处理中') : '平台已裁决';
    if (order.status === 1 && order.fulfillmentStatus === 5)
      return order.refundStatus === 3 ? '重新申请退款' : '退款处理中';
    if (order.status === 3 || order.status === 4)
      return isErrandPublisher.value ? '重新发布并付款' : '任务已取消';
    if (order.fulfillmentStatus === 1)
      return isErrandPublisher.value ? '取消任务并退款' : '立即接单';
    if (order.fulfillmentStatus === 2)
      return isErrandHelper.value ? '提交完成' : (isErrandPublisher.value ? '接单人办理中' : '已被接单');
    if (order.fulfillmentStatus === 3)
      return isErrandPublisher.value ? '确认完成' : (isErrandHelper.value ? '等待确认' : '确认中');
    if (order.fulfillmentStatus === 4)
      return isErrandHelper.value ? '收益已入账' : '任务已完成';
  }
  if (isOwnPost.value)
    return isIdlePost.value ? '管理商品' : '管理内容';
  if (soldOut.value)
    return saleCompleted.value ? '已卖出' : '交易中';
  return isIdlePost.value && Number(post.value?.price || 0) > 0 ? '立即购买' : '联系TA';
});
const composerTitle = computed(() => replyAuthor.value ? `回复 ${replyAuthor.value}` : (postAuthor.value ? `评论 ${postAuthor.value}` : '评论内容'));
const composerPlaceholder = computed(() => replyAuthor.value ? `回复@${replyAuthor.value}` : '留下你的评论');
const composerKeyboardStyle = computed(() => ({ bottom: `${keyboardHeight.value}px` }));
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

watch([() => comment.value.trim().length, () => commentImages.value.length], ([length, imageCount]) => {
  if (commentError.value && (length >= 2 || imageCount > 0))
    commentError.value = '';
});

watch(() => post.value.coverImage, () => {
  coverImageFailed.value = false;
});

onLoad(async (query) => {
  resetCommentComposer();
  contentExpanded.value = false;
  postId.value = Number(query?.id || 2001);
  ownerContext.value = query?.mine === '1';
  pageState.value = 'loading';
  try {
    await userStore.initUserInfo();
    const loaded = await contentStore.loadPost(postId.value);
    // 某些环境的详情接口没有稳定返回 owner。登录状态下再用“我的发布”
    // 接口核验一次，避免从首页进入自己的帖子时被当成普通访客。
    if (!ownerContext.value && loaded.owner !== true && userStore.loggedIn) {
      try {
        await contentStore.loadMyPosts();
      } catch {
        // 本人列表核验失败不影响详情本身继续展示，仍可依赖 userId 判断。
      }
    }
    liked.value = Boolean(loaded.liked);
    collected.value = Boolean(loaded.collected);
    followed.value = false;
    const targetUserId = Number(loaded.userId || 0);
    const currentUserId = Number(userStore.userInfo?.id || 0);
    if (userStore.loggedIn && targetUserId > 0 && targetUserId !== currentUserId) {
      try {
        await migrateLocalCampusFollows(currentUserId);
        followed.value = await getCampusUserFollowStatus(targetUserId);
      } catch {
        // 关注状态加载失败不阻塞帖子详情，用户点击时会再次请求服务端。
      }
    }
    if (userStore.loggedIn)
      recordCampusHistory(userStore.userInfo?.id, loaded);
    if (loaded.type === 'help' && userStore.loggedIn)
      await loadErrandOrder();
    pageState.value = 'content';
    await loadComments();
  } catch {
    pageState.value = 'error';
  }
});

async function loadErrandOrder() {
  try {
    errandOrder.value = await getCampusErrandOrderByPost(postId.value);
  } catch {
    errandOrder.value = undefined;
  }
}

function startErrandClock() {
  if (errandClockTimer)
    clearInterval(errandClockTimer);
  nowTick.value = Date.now();
  errandClockTimer = setInterval(() => {
    nowTick.value = Date.now();
  }, 30000);
}

function stopErrandClock() {
  if (errandClockTimer) {
    clearInterval(errandClockTimer);
    errandClockTimer = undefined;
  }
}

onHide(() => {
  resetCommentComposer();
  stopErrandClock();
});

onShow(() => {
  startErrandClock();
  if (isErrandPost.value && userStore.loggedIn)
    void loadErrandOrder();
});

onUnload(() => stopErrandClock());

onShareAppMessage(() => ({
  title: postAuthor.value ? `${postAuthor.value}发布的校园内容` : '校园内容分享',
  path: `/pages/detail/index?id=${postId.value}`,
  imageUrl: postImages.value[0] || undefined,
}));

function resetCommentComposer() {
  comment.value = '';
  commentError.value = '';
  replyTarget.value = null;
  commentImages.value = [];
  mentionUserIds.value = [];
  showEmojiPanel.value = false;
  showMentionPanel.value = false;
  showCommentComposer.value = false;
  commentInputFocused.value = false;
}

function handleCoverImageError() {
  coverImageFailed.value = true;
}

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
    const postComments = (page.list || [])
      .filter(item => Number(item.postId) === requestedPostId)
      .map(item => ({
        ...item,
        avatar: resolveCampusAvatar(item.avatar),
        images: (item.images || []).map(resolveCampusMediaUrl).filter(Boolean),
      }));
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
  if (commentSubmitting.value)
    return;
  if (!commentImages.value.length && content.length < 2) {
    commentError.value = '评论至少填写 2 个字，或添加图片';
    return;
  }
  if (content.length > 300) {
    uni.showToast({ title: '评论最多 300 个字', icon: 'none' });
    return;
  }
  commentSubmitting.value = true;
  try {
    const uploadedImages = await Promise.all(commentImages.value.map(image => uploadCampusCommentImage(image)));
    const createdResult = await createCampusPostComment(postId.value, {
      content,
      parentId: replyTarget.value?.id,
      replyToUserId: replyTarget.value?.userId,
      mentionUserIds: mentionUserIds.value,
      images: uploadedImages,
    });
    const created = {
      ...createdResult,
      avatar: resolveCampusAvatar(createdResult.avatar),
      images: (createdResult.images || []).map(resolveCampusMediaUrl).filter(Boolean),
    };
    if (Number(created.postId) !== postId.value)
      throw new Error('评论所属帖子不一致');
    const awaitingReview = created.status === 0;
    if (!awaitingReview) {
      comments.value.unshift(created);
      commentTotal.value += 1;
      post.value.comments = commentTotal.value;
    }
    comment.value = '';
    commentImages.value = [];
    mentionUserIds.value = [];
    replyTarget.value = null;
    showEmojiPanel.value = false;
    showMentionPanel.value = false;
    showCommentComposer.value = false;
    commentInputFocused.value = false;
    commentState.value = 'content';
    // Re-read only visible comments; pending comments are deliberately excluded by the server.
    if (!awaitingReview)
      await loadComments();
    uni.showToast({ title: awaitingReview ? '评论审核中' : '评论成功', icon: awaitingReview ? 'none' : 'success' });
  } catch (error) {
    const message = error instanceof Error ? error.message.replace(/^.*：/, '') : '评论发布失败，请重试';
    uni.showModal({
      title: '评论未发布',
      content: message || '请检查网络后重试',
      showCancel: false,
      confirmText: '知道了',
    });
  } finally {
    commentSubmitting.value = false;
  }
}

function replyToComment(item: CampusPostComment) {
  replyTarget.value = item;
  showCommentComposer.value = true;
  showEmojiPanel.value = false;
  showMentionPanel.value = false;
  focusCommentInput();
}

function openCommentComposer() {
  replyTarget.value = null;
  showCommentComposer.value = true;
  focusCommentInput();
}

function focusCommentInput() {
  commentInputFocused.value = false;
  nextTick(() => {
    commentInputFocused.value = true;
  });
}

function closeCommentComposer() {
  replyTarget.value = null;
  showEmojiPanel.value = false;
  showMentionPanel.value = false;
  showCommentComposer.value = false;
  commentInputFocused.value = false;
  keyboardHeight.value = 0;
}

function handleKeyboardHeightChange(event: any) {
  keyboardHeight.value = Math.max(0, Number(event?.detail?.height || 0));
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

function previewPostImage(current: string) {
  if (!postImages.value.length)
    return;
  uni.previewImage({ urls: postImages.value, current });
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

function confirmErrandRefund(order: CampusTradeOrder, retry = false) {
  const amount = Number(order.amount || 0).toFixed(2);
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: retry ? '重新申请退款' : '取消任务并退款',
      content: retry
        ? `此前退款申请未成功。确认后将重新向微信支付申请退回 ¥${amount}，到账时间以微信支付处理结果为准。确定继续吗？`
        : `确认后任务将立即关闭，其他学生不能再接单；¥${amount} 赏金将按原支付方式退回，到账时间以微信支付处理结果为准。确定取消任务并申请退款吗？`,
      cancelText: '暂不取消',
      confirmText: '确认退款',
      confirmColor: '#E85D3F',
      success: result => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    });
  });
}

async function handleErrandAction() {
  if (!ensureLogin() || contactSubmitting.value)
    return;
  const needsOrderCreation = !errandOrder.value && isOwnPost.value;
  contactSubmitting.value = true;
  try {
    let order = errandOrder.value;
    if (!order) {
      if (!isOwnPost.value) {
        uni.showToast({ title: '发布人尚未完成赏金支付', icon: 'none' });
        return;
      }
      order = await createCampusErrandOrder(postId.value);
      errandOrder.value = order;
    }
    if (isErrandPublisher.value && order.status === 1 && order.fulfillmentStatus === 5) {
      if (order.refundStatus !== 3) {
        uni.showToast({ title: order.refundStatus === 2 ? '赏金已原路退回' : '退款正在处理中', icon: 'none' });
        return;
      }
      if (!await confirmErrandRefund(order, true))
        return;
      errandOrder.value = await cancelCampusErrandOrder(order.id);
      uni.showToast({ title: errandOrder.value.refundStatus === 2 ? '赏金已退款' : '退款正在处理中', icon: 'none' });
      return;
    }
    if (isErrandPublisher.value && (order.status === 0 || order.status === 3 || order.status === 4)) {
      if (order.status !== 0)
        order = await createCampusErrandOrder(postId.value);
      uni.navigateTo({ url: `/pages/checkout/index?orderId=${order.id}&postId=${postId.value}&mode=errand` });
      return;
    }
    if (order.status !== 1 && order.status !== 2) {
      uni.showToast({ title: '任务当前不可操作', icon: 'none' });
      return;
    }
    if (order.disputeStatus === 1) {
      uni.showToast({ title: '申诉处理中，赏金已冻结', icon: 'none' });
      return;
    }
    if (order.fulfillmentStatus === 1 && isErrandPublisher.value) {
      const confirmed = await confirmErrandRefund(order);
      if (!confirmed)
        return;
      errandOrder.value = await cancelCampusErrandOrder(order.id);
      uni.showToast({ title: errandOrder.value.refundStatus === 2 ? '赏金已退款' : '退款正在原路退回', icon: 'none' });
      return;
    }
    if (order.fulfillmentStatus === 1) {
      const confirmed = await new Promise<boolean>((resolve) => {
        uni.showModal({
          title: '确认接单',
          content: `完成任务并由发布人确认后，你将获得 ¥${Number(order?.amount || 0).toFixed(2)} 收益。`,
          confirmText: '确认接单',
          success: result => resolve(Boolean(result.confirm)),
          fail: () => resolve(false),
        });
      });
      if (!confirmed)
        return;
      errandOrder.value = await acceptCampusErrandOrder(order.id);
      uni.showToast({ title: '接单成功，请按要求完成任务', icon: 'success' });
      setTimeout(() => openErrandChat(), 450);
      return;
    }
    if (order.fulfillmentStatus === 2 && isErrandHelper.value) {
      openErrandEvidenceComposer('complete');
      return;
    }
    if (order.fulfillmentStatus === 3 && isErrandPublisher.value) {
      const amount = Number(order.amount || 0).toFixed(2);
      const confirmed = await new Promise<boolean>((resolve) => {
        uni.showModal({
          title: '确认任务已经完成？',
          content: `请确认接单人已完成本次任务。确认后，¥${amount} 赏金将立即结算到接单人的可提现收益账户，订单同时完成，且无法撤销。若对完成情况有异议，请先返回并发起异议。`,
          cancelText: '再检查一下',
          confirmText: '确认并结算',
          confirmColor: '#10A779',
          success: result => resolve(Boolean(result.confirm)),
          fail: () => resolve(false),
        });
      });
      if (!confirmed)
        return;
      errandOrder.value = await confirmCampusErrandOrder(order.id);
      uni.showToast({ title: '已计入接单人可提现收益', icon: 'success' });
      return;
    }
    uni.showToast({ title: contactButtonText.value, icon: 'none' });
  } catch (error: any) {
    const message = String(error?.message || '操作失败，请稍后重试').replace(/^.*：/, '').slice(0, 100);
    uni.showModal({
      title: needsOrderCreation ? '暂时无法进入付款' : '任务状态更新失败',
      content: needsOrderCreation && /系统异常/.test(message)
        ? '线上服务器尚未完成代办订单和数据库升级，请部署最新后端后再试。'
        : message,
      showCancel: false,
    });
    await loadErrandOrder();
  } finally {
    contactSubmitting.value = false;
  }
}

function openErrandEvidenceComposer(mode: 'complete' | 'dispute') {
  if (!errandOrder.value || errandEvidenceSubmitting.value)
    return;
  errandEvidenceMode.value = mode;
  errandEvidenceText.value = '';
  errandEvidenceImages.value = [];
  showErrandEvidenceComposer.value = true;
}

function closeErrandEvidenceComposer() {
  if (!errandEvidenceSubmitting.value)
    showErrandEvidenceComposer.value = false;
}

function chooseErrandEvidenceImages() {
  const remaining = 3 - errandEvidenceImages.value.length;
  if (remaining <= 0) {
    uni.showToast({ title: '最多上传 3 张凭证', icon: 'none' });
    return;
  }
  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    success: ({ tempFilePaths }) => {
      errandEvidenceImages.value.push(...tempFilePaths.slice(0, remaining));
    },
  });
}

function removeErrandEvidenceImage(index: number) {
  errandEvidenceImages.value.splice(index, 1);
}

function previewErrandEvidence(current: string) {
  uni.previewImage({ urls: errandOrder.value?.completionImages || [], current });
}

async function submitErrandEvidence() {
  const order = errandOrder.value;
  if (!order || errandEvidenceSubmitting.value)
    return;
  const text = errandEvidenceText.value.trim();
  if (errandEvidenceMode.value === 'complete' && !text && !errandEvidenceImages.value.length) {
    uni.showToast({ title: '请填写完成说明或上传凭证', icon: 'none' });
    return;
  }
  if (errandEvidenceMode.value === 'dispute' && !text) {
    uni.showToast({ title: '请填写申诉原因', icon: 'none' });
    return;
  }
  errandEvidenceSubmitting.value = true;
  try {
    const images: string[] = [];
    for (const image of errandEvidenceImages.value)
      images.push(await uploadCampusErrandEvidence(image));
    errandOrder.value = errandEvidenceMode.value === 'complete'
      ? await submitCampusErrandOrder(order.id, { note: text, images })
      : await disputeCampusErrandOrder(order.id, { reason: text, images });
    showErrandEvidenceComposer.value = false;
    uni.showToast({
      title: errandEvidenceMode.value === 'complete' ? '已提交，24 小时内待确认' : '申诉已提交，赏金已冻结',
      icon: 'none',
    });
  }
  catch (error: any) {
    uni.showToast({ title: String(error?.message || '提交失败，请重试').slice(0, 80), icon: 'none' });
    await loadErrandOrder();
  }
  finally {
    errandEvidenceSubmitting.value = false;
  }
}

function startErrandDispute() {
  const order = errandOrder.value;
  if (!isOwnPost.value || !order || order.fulfillmentStatus !== 3 || order.disputeStatus)
    return;
  openErrandEvidenceComposer('dispute');
}

function openErrandChat() {
  if (!canOpenErrandChat.value || !errandOrder.value)
    return;
  uni.navigateTo({ url: `/pages/trade-chat/index?orderId=${errandOrder.value.id}` });
}
function navigateToMerchant() {
  if (!canNavigateToMerchant.value) {
    uni.showToast({ title: '商家暂未提供地图导航位置', icon: 'none' });
    return;
  }
  uni.openLocation({
    latitude: Number(post.value.merchantLatitude),
    longitude: Number(post.value.merchantLongitude),
    name: post.value.merchantLocationName || post.value.title,
    address: post.value.merchantAddress || post.value.location || '',
    scale: 18,
    fail: () => uni.showToast({ title: '地图打开失败，请稍后重试', icon: 'none' }),
  });
}
async function contact() {
  if (downlisted.value) {
    uni.showToast({ title: '商品已下架', icon: 'none' });
    return;
  }
  if (isErrandPost.value) {
    await handleErrandAction();
    return;
  }
  if (soldOut.value) {
    uni.showToast({ title: '商品已售罄', icon: 'none' });
    return;
  }
  if (isOwnPost.value) {
    managePost();
    return;
  }
  if (!ensureLogin() || contactSubmitting.value)
    return;
  if (isIdlePost.value && Number(post.value.price || 0) > 0) {
    uni.navigateTo({ url: `/pages/checkout/index?postId=${postId.value}` });
    return;
  }
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
  if (downlisted.value) {
    uni.showToast({ title: '已下架商品不能点赞', icon: 'none' });
    return;
  }
  if (!ensureLogin() || interactionBusy.value)
    return;
  interactionBusy.value = true;
  try {
    const updated = await contentStore.setPostLike(postId.value, !liked.value);
    liked.value = Boolean(updated.liked);
  } catch {
    uni.showToast({ title: '操作失败，请重试', icon: 'none' });
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
async function toggleFollow() {
  if (!ensureLogin() || followBusy.value)
    return;
  const targetUserId = Number(post.value.userId || 0);
  if (!targetUserId) {
    uni.showToast({ title: '该用户信息不完整，暂时无法关注', icon: 'none' });
    return;
  }
  followBusy.value = true;
  const active = !followed.value;
  try {
    followed.value = await setCampusUserFollow(targetUserId, active);
    uni.showToast({ title: followed.value ? '已关注' : '已取消关注', icon: 'none' });
  } catch {
    uni.showToast({ title: '关注操作失败，请重试', icon: 'none' });
  } finally {
    followBusy.value = false;
  }
}
function managePost() {
  if (isErrandPost.value && errandOrder.value
    && [0, 1].includes(errandOrder.value.status)
    && [0, 1, 2, 3].includes(Number(errandOrder.value.fulfillmentStatus))) {
    uni.showModal({
      title: '任务订单仍在进行',
      content: errandOrder.value.fulfillmentStatus === 1
        ? '请先通过页面底部按钮取消任务并退款，再删除发布。'
        : '已有接单人参与，任务完成前不能删除发布。',
      showCancel: false,
    });
    return;
  }
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
      @action="uni.reLaunch({ url: '/pages/index/index' })"
    />
    <template v-else>
      <swiper v-if="showCoverImage || !isConfession" class="media" indicator-dots indicator-active-color="#10A779">
        <template v-if="showCoverImage">
          <swiper-item v-for="image in postImages" :key="image">
            <image
              class="detail-photo" :src="image" mode="aspectFill"
              @click.stop="previewPostImage(image)" @error="handleCoverImageError"
            />
          </swiper-item>
        </template>
        <swiper-item v-else>
          <view class="media-item" :style="{ background: post.coverColor }">
            <image :src="channelIcon" mode="aspectFit" /><view>{{ post.coverLabel }}</view>
          </view>
        </swiper-item>
      </swiper>
      <view v-else class="confession-detail-hero">
        <view class="confession-detail-icon">
          ♥
        </view>
        <text>校园表白墙</text>
        <text>每一份心意都值得被认真对待</text>
      </view>
      <view class="content-card" :class="{ 'confession-content-card': isConfession }">
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
          </view><button class="follow-btn" :class="{ followed: followed || isOwnPost }" @click="isOwnPost ? managePost() : toggleFollow()">
            {{ isOwnPost ? '管理' : (followed ? '已关注' : '＋ 关注') }}
          </button><button class="author-share" open-type="share" aria-label="转发给微信好友">
            <image src="/static/icons/ui/wechat-green.svg" mode="aspectFit" />
          </button>
        </view>
        <view v-if="hasMarkedPrice" class="price">
          <text>¥</text>{{ post.price }}
        </view>
        <view v-if="stockSummary" class="stock-summary" :class="{ sold: soldOut }">
          {{ stockSummary }}
        </view><view class="title">
          {{ post.title }}
        </view><view class="body" :class="{ expanded: contentExpanded }">
          {{ post.content }}
        </view>
        <view v-if="contentExpandable" class="body-toggle" @click="contentExpanded = !contentExpanded">
          {{ contentExpanded ? '收起全文' : '展开全文' }}
        </view>
        <view class="tags">
          <text v-for="tag in post.tags" :key="tag">
            # {{ tag }}
          </text>
        </view>
        <view v-if="isShopPost" class="groupbuy-location-card">
          <image src="/static/icons/ui/location.svg" mode="aspectFit" />
          <view class="groupbuy-location-copy">
            <text>{{ post.merchantLocationName || '门店位置' }}</text>
            <text class="groupbuy-exact-address">{{ post.merchantAddress || post.location || `${post.school} · 校内附近` }}</text>
            <text v-if="post.merchantAddress && post.location" class="groupbuy-approximate-address">大概位置：{{ post.location }}</text>
          </view>
          <button v-if="canNavigateToMerchant" class="merchant-navigation-button" @click.stop="navigateToMerchant">
            去导航
          </button>
        </view>
        <view class="meta">
          <view v-if="!isShopPost" class="meta-location">
            <image
              :src="isConfession ? '/static/icons/mine/heart.svg' : '/static/icons/ui/location.svg'" mode="aspectFit"
            /><text>{{ isConfession ? '仅展示在本校表白墙' : (post.location || `${post.school} · 校内`) }}</text>
          </view><view class="meta-actions">
            <text>浏览 {{ post.views || 0 }}</text><text v-if="!isOwnPost" class="report-entry" @click="reportPost">
              举报
            </text>
          </view>
        </view>
        <view v-if="isErrandPost && errandOrder" class="errand-status-card">
          <view>
            <text class="errand-status-title">{{ errandOrder.fulfillmentStatusText || errandOrder.statusText }}</text>
            <text class="errand-status-note">
              {{ errandStatusNote }}
            </text>
            <text v-if="errandOrder.completionNote" class="errand-evidence-note">完成说明：{{ errandOrder.completionNote }}</text>
            <view v-if="errandOrder.completionImages?.length" class="errand-evidence-preview">
              <image v-for="image in errandOrder.completionImages" :key="image" :src="image" mode="aspectFill" @click.stop="previewErrandEvidence(image)" />
            </view>
          </view>
          <view class="errand-side">
            <text class="errand-reward">赏金 ¥{{ Number(errandOrder.amount || 0).toFixed(2) }}</text>
            <button v-if="canOpenErrandChat" class="errand-chat-button" @tap.stop="openErrandChat">
              {{ errandChatText }}
            </button>
            <button
              v-if="isErrandPublisher && errandOrder.fulfillmentStatus === 3 && !errandOrder.disputeStatus"
              class="errand-dispute-button" @tap.stop="startErrandDispute"
            >
              完成情况有异议
            </button>
          </view>
        </view>
        <view class="detail-actions">
          <view class="detail-action" :class="{ active: liked }" @click="toggleLike">
            <text class="detail-heart">{{ liked ? '♥' : '♡' }}</text><text>{{ hasMarkedPrice ? `${post.likes || 0}人想要` : `热度 ${postHotCount}` }}</text>
          </view>
          <view class="detail-action" :class="{ active: collected }" @click="toggleCollect">
            <image src="/static/icons/ui/star.svg" mode="aspectFit" /><text>{{ post.collects || 0 }}人收藏</text>
          </view>
          <button
            v-if="isOwnPost || !isConfession" class="detail-contact" :class="{ 'errand-action': isErrandPost }"
            :disabled="contactSubmitting || (!isErrandPost && !isOwnPost && (soldOut || downlisted))" @tap.stop="contact"
          >
            {{ contactSubmitting ? '提交中…' : contactButtonText }}
          </button>
        </view>
      </view>

      <view class="comments-card">
        <view class="section-title">
          共 {{ commentTotal }} 条评论
        </view>
        <view class="comment-sort">
          <text :class="{ active: commentSort === 'latest' }" @click="changeCommentSort('latest')">
            最新
          </text>
          <text :class="{ active: commentSort === 'likes' }" @click="changeCommentSort('likes')">
            最热
          </text>
        </view>
        <view v-if="commentState === 'loading'" class="comment-status">
          评论加载中…
        </view>
        <view v-else-if="commentState === 'error'" class="comment-status comment-retry" @click="loadComments()">
          评论加载失败，点击重试
        </view>
        <view v-else-if="!comments.length" class="comment-empty-state">
          <image src="/static/icons/ui/comment-empty.svg" mode="aspectFit" />
          <text>暂无评论，快留下你的想法吧</text>
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
                  <text v-if="!item.owner" class="comment-report" @click.stop="reportCommentItem(item)">
                    举报
                  </text>
                  <text v-if="item.owner" class="comment-report danger" @click.stop="removeComment(item)">
                    删除
                  </text>
                </view>
                <text class="comment-time">
                  {{ item.time }}
                </text>
                <text class="comment-reply-action" @click.stop="replyToComment(item)">
                  回复
                </text>
                <text
                  class="comment-like" :class="{ active: item.liked }" @click.stop="toggleCommentLike(item)"
                >
                  {{ item.liked ? '♥' : '♡' }} {{ item.likeCount || 0 }}
                </text>
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
                  <text class="reply-mark">
                    回复 {{ reply.replyToAuthor || item.author }}：
                  </text>{{ reply.content }}
                </view>
                <view v-if="reply.images?.length" class="comment-images">
                  <image
                    v-for="image in reply.images" :key="image" :src="image" mode="aspectFill"
                    @click.stop="previewCommentImages(reply.images || [], image)"
                  />
                </view>
                <view class="comment-meta-row">
                  <view class="comment-left-actions">
                    <text v-if="!reply.owner" class="comment-report" @click.stop="reportCommentItem(reply)">
                      举报
                    </text>
                    <text v-if="reply.owner" class="comment-report danger" @click.stop="removeComment(reply)">
                      删除
                    </text>
                  </view>
                  <text class="comment-time">
                    {{ reply.time }}
                  </text>
                  <text class="comment-reply-action" @click.stop="replyToComment(reply)">
                    回复
                  </text>
                  <text
                    class="comment-like" :class="{ active: reply.liked }" @click.stop="toggleCommentLike(reply)"
                  >
                    {{ reply.liked ? '♥' : '♡' }} {{ reply.likeCount || 0 }}
                  </text>
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
          <text>✎ 留下你的想法</text>
        </view>
        <view
          class="prototype-bottom-action" :class="{ active: liked }"
          :aria-label="hasMarkedPrice ? `想要，当前${post.likes || 0}人` : `点赞，当前${post.likes || 0}人`" @click="toggleLike"
        >
          <text>{{ liked ? '♥' : '♡' }}</text><text>{{ post.likes || 0 }}</text>
        </view>
        <view
          class="prototype-bottom-action" :class="{ active: collected }"
          :aria-label="`收藏，当前${post.collects || 0}人`" @click="toggleCollect"
        >
          <text>{{ collected ? '★' : '☆' }}</text><text>{{ post.collects || 0 }}</text>
        </view>
        <button
          v-if="isOwnPost || !isConfession" class="prototype-buy" :class="{ 'errand-action': isErrandPost }"
          :disabled="contactSubmitting || (!isErrandPost && !isOwnPost && (soldOut || downlisted))" @tap.stop="contact"
        >
          {{ contactSubmitting ? '提交中…' : contactButtonText }}
        </button>
        <button v-else class="prototype-buy" @click="openCommentComposer">
          去表白
        </button>
      </view>
      <view v-if="showErrandEvidenceComposer" class="comment-overlay" @click="closeErrandEvidenceComposer">
        <view class="errand-evidence-composer" @click.stop>
          <view class="composer-header">
            <text>{{ errandEvidenceMode === 'complete' ? '提交任务完成凭证' : '发起任务申诉' }}</text>
            <text class="composer-close" @click="closeErrandEvidenceComposer">×</text>
          </view>
          <text class="errand-composer-tip">
            {{ errandEvidenceMode === 'complete' ? '提交后发布人有 24 小时确认；逾期未申诉将自动结算赏金。' : '申诉提交后赏金立即冻结，平台将依据双方沟通和凭证进行裁决。' }}
          </text>
          <textarea
            v-model="errandEvidenceText" class="errand-evidence-textarea" maxlength="500"
            :placeholder="errandEvidenceMode === 'complete' ? '说明放置位置、交付时间等完成情况' : '请具体说明任务未完成或不符合约定的情况'"
          />
          <view v-if="errandEvidenceImages.length" class="comment-upload-preview">
            <view v-for="(image, index) in errandEvidenceImages" :key="image" class="comment-upload-item">
              <image :src="image" mode="aspectFill" /><text @click="removeErrandEvidenceImage(index)">×</text>
            </view>
          </view>
          <view class="errand-evidence-actions">
            <button class="errand-add-evidence" @click="chooseErrandEvidenceImages">＋ 添加凭证（{{ errandEvidenceImages.length }}/3）</button>
            <button class="errand-submit-evidence" :disabled="errandEvidenceSubmitting" @click="submitErrandEvidence">
              {{ errandEvidenceSubmitting ? '提交中…' : (errandEvidenceMode === 'complete' ? '确认提交完成' : '提交申诉并冻结赏金') }}
            </button>
          </view>
        </view>
      </view>
      <view v-if="showCommentComposer" class="comment-overlay" @click="closeCommentComposer">
        <view class="comment-composer" :style="composerKeyboardStyle" @click.stop>
          <view class="composer-header">
            <text>{{ composerTitle }}</text>
            <text class="composer-close" @click="closeCommentComposer">
              ×
            </text>
          </view>
          <textarea
            class="composer-textarea" :class="{ invalid: commentError }" :value="comment" :disabled="commentSubmitting" maxlength="300"
            :placeholder="composerPlaceholder"
            :focus="commentInputFocused" :adjust-position="false" :show-confirm-bar="false" :hold-keyboard="true" :cursor-spacing="16"
            auto-height confirm-type="send" @input="handleCommentInput" @confirm="sendComment"
            @keyboardheightchange="handleKeyboardHeightChange"
          />
          <view v-if="commentError" class="composer-error">
            {{ commentError }}
          </view>
          <view v-if="commentImages.length" class="comment-upload-preview">
            <view v-for="(image, index) in commentImages" :key="image" class="comment-upload-item">
              <image :src="image" mode="aspectFill" /><text @click="removeCommentImage(index)">
                ×
              </text>
            </view>
          </view>
          <view class="composer-toolbar">
            <view class="comment-tools">
              <view class="comment-tool" :class="{ active: showEmojiPanel }" aria-label="添加表情" @click="toggleEmojiPanel">
                <image src="/static/icons/ui/comment-emoji.svg" mode="aspectFit" />
              </view>
              <view class="comment-tool" aria-label="添加图片" @click="chooseCommentImages">
                <image src="/static/icons/ui/comment-image.svg" mode="aspectFit" />
              </view>
              <view class="comment-tool" :class="{ active: showMentionPanel }" aria-label="艾特他人" @click="toggleMentionPanel">
                <image src="/static/icons/ui/comment-at.svg" mode="aspectFit" />
              </view>
            </view>
            <text class="comment-send" :class="{ disabled: !comment.trim() && !commentImages.length }" @click="sendComment">
              {{ commentSubmitting ? '发送中' : '发送' }}
            </text>
          </view>
          <view v-if="showMentionPanel" class="mention-panel">
            <view v-for="candidate in mentionCandidates" :key="candidate.id" class="mention-item" @click="insertMention(candidate)">
              <image :src="resolveCampusAvatar(candidate.avatar)" mode="aspectFill" /><text>@{{ candidate.name }}</text>
            </view>
          </view>
          <view v-if="showEmojiPanel" class="emoji-panel">
            <view class="emoji-title">
              全部表情
            </view>
            <text v-for="emoji in emojiList" :key="emoji" @click="insertEmoji(emoji)">
              {{ emoji }}
            </text>
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
.confession-detail-hero {
  display: flex;
  height: 360rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #a84e65;
  background: linear-gradient(145deg, #fff3f5, #fceaf0);
}
.confession-detail-icon {
  display: flex;
  width: 120rpx;
  height: 120rpx;
  align-items: center;
  justify-content: center;
  border-radius: 40rpx;
  color: #e85d76;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 16rpx 36rpx rgba(190, 85, 110, 0.16);
  font-size: 58rpx;
}
.confession-detail-hero > text:nth-child(2) {
  margin-top: 24rpx;
  font-size: 32rpx;
  font-weight: 900;
}
.confession-detail-hero > text:nth-child(3) {
  margin-top: 10rpx;
  color: #bb7c8b;
  font-size: 22rpx;
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
.stock-summary {
  display: inline-flex;
  margin: 8rpx 0 2rpx;
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  color: #2f7d63;
  background: rgba(16, 167, 121, 0.1);
  font-size: 21rpx;
}
.stock-summary.sold {
  color: #777;
  background: rgba(31, 31, 31, 0.08);
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
.errand-status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 24rpx;
  padding: 20rpx 22rpx;
  border-radius: 20rpx;
  background: rgba(16, 167, 121, 0.09);
}
.groupbuy-location-card {
  display: flex;
  align-items: center;
  margin-top: 22rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid #dcece6;
  border-radius: 18rpx;
  background: #f5fbf8;
}
.groupbuy-location-card image {
  width: 38rpx;
  height: 38rpx;
  margin-right: 15rpx;
}
.groupbuy-location-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.groupbuy-location-copy > text:first-child {
  color: #568075;
  font-size: 19rpx;
  font-weight: 700;
}
.groupbuy-exact-address {
  overflow: hidden;
  margin-top: 4rpx;
  color: #31463f;
  font-size: 23rpx;
  text-overflow: ellipsis;
  line-height: 1.45;
}
.groupbuy-approximate-address {
  margin-top: 5rpx;
  color: #8a9590;
  font-size: 18rpx;
}
.merchant-navigation-button {
  flex: 0 0 auto;
  height: 62rpx;
  margin: 0 0 0 16rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #10a779;
  font-size: 21rpx;
  font-weight: 700;
  line-height: 62rpx;
}
.merchant-navigation-button::after {
  border: 0;
}
.errand-status-card > view {
  display: flex;
  min-width: 0;
  flex-direction: column;
}
.errand-status-title {
  color: #087b59;
  font-size: 27rpx;
  font-weight: 750;
}
.errand-status-note {
  margin-top: 7rpx;
  color: #71817b;
  font-size: 21rpx;
}
.errand-reward {
  flex: 0 0 auto;
  color: #e44b38;
  font-size: 25rpx;
  font-weight: 750;
}
.errand-side {
  align-items: flex-end;
  flex: 0 0 auto;
}
.errand-chat-button {
  height: 48rpx;
  margin: 10rpx 0 0;
  padding: 0 18rpx;
  border: 0;
  border-radius: 24rpx;
  color: #fff;
  background: #10a779;
  font-size: 20rpx;
  line-height: 48rpx;
}
.errand-chat-button::after {
  border: 0;
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

.detail-heart {
  color: #8b8b8b;
  font-size: 34rpx;
  line-height: 1;
}

.detail-action.active .detail-heart {
  color: #ff4747;
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
  flex: 1;
  min-width: 0;
  height: 76rpx;
  align-items: center;
  padding: 0 26rpx;
  border-radius: 999rpx;
  color: #8c9691;
  background: rgba(118, 118, 128, 0.1);
  font-size: 28rpx;
}
.confession-content-card .title {
  color: #a84e65;
}
.confession-content-card .detail-action.active {
  color: #e85d76;
}
.current-user-avatar {
  display: flex;
  flex: 0 0 auto;
  width: 64rpx;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--yd-mint);
}
.current-user-avatar image {
  width: 100%;
  height: 100%;
}
.wechat-share {
  display: flex;
  flex: 0 0 auto;
  width: 76rpx;
  height: 76rpx;
  align-items: center;
  justify-content: center;
  margin: 0 0 0 16rpx;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #1aad19;
  box-shadow: 0 8rpx 22rpx rgba(26, 173, 25, 0.24);
}
.wechat-share::after {
  border: 0;
}
.wechat-share image {
  width: 48rpx;
  height: 48rpx;
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

/* 蓝湖原型：详情、评论和购买入口 */
.detail-page {
  min-height: 100vh;
  padding-bottom: calc(154rpx + env(safe-area-inset-bottom));
  color: #1f2220;
  background: #fff;
}

.media {
  overflow: hidden;
  height: 620rpx;
  margin: 0;
  border-radius: 30rpx 30rpx 0 0;
  background: #eee;
}

.detail-photo,
.media-item {
  width: 100%;
  height: 620rpx;
}

.content-card,
.comments-card {
  margin: 0;
  padding: 30rpx 32rpx;
  border: 0;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
}

.author-row {
  min-height: 88rpx;
}

.author-avatar {
  width: 72rpx;
  height: 72rpx;
  border: 0;
  border-radius: 50%;
}

.author-main {
  margin-left: 16rpx;
}

.author-name > text:first-child {
  color: #292c2a;
  font-size: 29rpx;
  font-weight: 600;
}

.author-main .verified-badge {
  padding: 3rpx 10rpx;
  border-radius: 10rpx;
  color: #33af1c;
  background: #e9ffe3;
  font-size: 20rpx;
}

.author-sub {
  margin-top: 5rpx;
  color: #949896;
  font-size: 24rpx;
}

.follow-btn {
  width: 140rpx;
  height: 64rpx;
  margin: 0 0 0 14rpx;
  padding: 0;
  border: 0;
  border-radius: 20rpx;
  color: #14200b;
  background: #95f51f;
  font-size: 28rpx;
  font-weight: 550;
  line-height: 64rpx;
}

.follow-btn.followed {
  color: #929693;
  background: #f0f0f0;
}

.follow-btn::after,
.author-share::after,
.prototype-buy::after {
  border: 0;
}

.author-share {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 70rpx;
  height: 70rpx;
  margin: 0 0 0 10rpx;
  padding: 0;
  border-radius: 50%;
  background: transparent;
}

.author-share image {
  width: 54rpx;
  height: 54rpx;
}

.price {
  margin-top: 30rpx;
  color: #ff4d55;
  font-size: 54rpx;
  font-weight: 650;
  line-height: 1.1;
}

.price text {
  margin-right: 5rpx;
  font-size: 25rpx;
}

.title {
  margin-top: 24rpx;
  color: #202321;
  font-size: 31rpx;
  font-weight: 600;
  line-height: 1.45;
}

.body {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 18rpx;
  color: #4f5652;
  font-size: 30.77rpx;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.body.expanded {
  display: block;
  overflow: visible;
  -webkit-line-clamp: unset;
}

.body-toggle {
  margin-top: 12rpx;
  color: #02a83e;
  font-size: 26.92rpx;
  font-weight: 500;
  line-height: 38.46rpx;
}

.tags {
  margin-top: 16rpx;
}

.tags text {
  border: 0;
  color: #44b62d;
  background: #efffe9;
}

.meta {
  min-height: 66rpx;
  margin-top: 18rpx;
  padding: 0;
  border: 0;
}

.meta-location {
  height: 45rpx;
  padding: 0 12rpx;
  border: 2rpx solid #e7e9e7;
  border-radius: 13rpx;
}

.meta-location image {
  width: 30rpx;
  height: 30rpx;
}

.meta-location text,
.meta-actions {
  color: #858986;
  font-size: 23rpx;
}

.report-entry {
  color: #ff454f;
}

.detail-actions {
  display: none;
}

.comments-card {
  padding-top: 18rpx;
  border-top: 1rpx solid #f1f2f1;
}

.comments-card .section-title {
  color: #1f2220;
  font-size: 31rpx;
  font-weight: 600;
}

.comment-sort {
  display: none;
}

.comment-status {
  padding: 58rpx 0 90rpx;
  color: #9a9e9b;
  font-size: 25rpx;
}

.comment-empty-state {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  min-height: 430rpx;
  padding-top: 58rpx;
  color: #9a9e9b;
  flex-direction: column;
}

.comment-empty-state image {
  width: 240rpx;
  height: 116rpx;
}

.comment-empty-state text {
  margin-top: 18rpx;
  font-size: 25rpx;
  line-height: 36rpx;
}

.comment-block {
  padding: 26rpx 0 4rpx;
  border: 0;
}

.comment {
  padding: 0;
}

.comment-avatar {
  width: 70rpx;
  height: 70rpx;
}

.comment-main {
  margin-left: 24rpx;
}

.comment-name {
  color: #737774;
  font-size: 27rpx;
  font-weight: 600;
}

.comment-content {
  margin-top: 12rpx;
  color: #222522;
  font-size: 28rpx;
  line-height: 1.65;
}

.comment-meta-row {
  margin-top: 12rpx;
  color: #9c9f9d;
  font-size: 23rpx;
}

.comment-left-actions {
  order: 5;
}

.comment-time {
  margin-left: 0;
}

.comment-reply-action {
  margin-left: 24rpx;
}

.comment-like {
  margin-left: auto;
  color: #9a9e9b;
  font-size: 25rpx;
}

.reply-thread {
  margin: 20rpx 0 0 94rpx;
  padding: 0;
  border: 0;
  background: transparent;
}

.comment-reply {
  padding: 14rpx 0;
}

.comment-reply .comment-avatar {
  width: 50rpx;
  height: 50rpx;
}

.reply-expand {
  margin: 18rpx 0 0 95rpx;
  color: #999d9a;
  font-size: 24rpx;
}

.bottom-bar {
  z-index: 50;
  display: flex;
  height: calc(154rpx + env(safe-area-inset-bottom));
  padding: 16rpx 32rpx calc(14rpx + env(safe-area-inset-bottom));
  border: 0;
  border-top: 1rpx solid #f2f3f2;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
  box-sizing: border-box;
}

.comment-trigger {
  flex: 1;
  height: 80rpx;
  margin: 0 16rpx 0 0;
  padding: 0 22rpx;
  border: 0;
  border-radius: 36rpx;
  color: #9b9e9c;
  background: #f7f7f7;
  font-size: 25rpx;
  line-height: 80rpx;
}

.prototype-bottom-action {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 82rpx;
  height: 82rpx;
  color: #969a97;
  flex-direction: column;
}

.prototype-bottom-action > text:first-child {
  font-size: 54rpx;
  line-height: 48rpx;
}

.prototype-bottom-action > text:last-child {
  margin-top: 8rpx;
  font-size: 22rpx;
}

.prototype-bottom-action.active > text:first-child {
  color: #ff4d55;
}

.prototype-buy {
  flex: 0 0 auto;
  width: 184rpx;
  height: 82rpx;
  margin: 0 0 0 10rpx;
  padding: 0;
  border-radius: 28rpx;
  color: #14200a;
  background: #95f51f;
  font-size: 29rpx;
  font-weight: 600;
  line-height: 82rpx;
}

.comment-overlay {
  z-index: 80;
  background: rgba(0, 0, 0, 0.28);
}

.comment-composer {
  min-height: 250rpx;
  max-height: 70vh;
  padding: 28rpx 32rpx 18rpx;
  border-radius: 32rpx 32rpx 0 0;
  background: #fff;
  transition: bottom .12s ease-out;
}

.composer-header {
  display: none;
}

.composer-textarea {
  min-height: 112rpx;
  padding: 22rpx;
  border: 0;
  border-radius: 28rpx;
  color: #222522;
  background: #f7f7f7;
  font-size: 27rpx;
}

.composer-textarea.invalid {
  color: #ff4d4f;
  box-shadow: inset 0 0 0 2rpx rgba(255, 77, 79, 0.72);
}

.composer-error {
  margin-top: 10rpx;
  color: #ff4d4f;
  font-size: 23.08rpx;
  line-height: 30.77rpx;
}

.composer-toolbar {
  min-height: 70rpx;
  margin-top: 8rpx;
}

.composer-toolbar .comment-tools {
  display: flex;
  align-items: center;
  height: 64rpx;
  gap: 24rpx;
}

.comment-tool {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
}

.comment-tool.active {
  background: #efffdf;
}

.comment-tool image {
  width: 42rpx;
  height: 42rpx;
}

.comment-send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  height: 64rpx;
  border-radius: 27rpx;
  color: #14200a;
  background: #95f51f;
  font-size: 28rpx;
  font-weight: 600;
}

.comment-send.disabled {
  color: #a2aa9f;
  background: #c8fb88;
}

.errand-evidence-note {
  margin-top: 12rpx;
  color: #475c55;
  font-size: 21rpx;
  line-height: 1.5;
}

.errand-evidence-preview {
  display: flex !important;
  flex-direction: row !important;
  gap: 10rpx;
  margin-top: 12rpx;
}

.errand-evidence-preview image {
  width: 82rpx;
  height: 82rpx;
  border-radius: 12rpx;
}

.errand-dispute-button {
  height: 46rpx;
  margin: 9rpx 0 0;
  padding: 0 15rpx;
  border: 1rpx solid #e38a72;
  border-radius: 24rpx;
  color: #c5533b;
  background: #fff8f5;
  font-size: 19rpx;
  line-height: 44rpx;
}

.errand-dispute-button::after {
  border: 0;
}

.errand-evidence-composer {
  width: 100%;
  box-sizing: border-box;
  padding: 30rpx 32rpx calc(28rpx + env(safe-area-inset-bottom));
  border-radius: 32rpx 32rpx 0 0;
  background: #fff;
}

.errand-evidence-composer .composer-header {
  display: flex;
}

.errand-composer-tip {
  display: block;
  margin-top: 18rpx;
  color: #71817b;
  font-size: 22rpx;
  line-height: 1.55;
}

.errand-evidence-textarea {
  width: 100%;
  height: 210rpx;
  box-sizing: border-box;
  margin-top: 20rpx;
  padding: 22rpx;
  border-radius: 20rpx;
  color: #283631;
  background: #f6f8f7;
  font-size: 25rpx;
  line-height: 1.55;
}

.errand-evidence-composer .comment-upload-preview {
  margin-top: 20rpx;
}

.errand-evidence-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.errand-add-evidence,
.errand-submit-evidence {
  flex: 1;
  height: 72rpx;
  margin: 0;
  padding: 0 12rpx;
  border-radius: 22rpx;
  font-size: 22rpx;
  line-height: 72rpx;
}

.errand-add-evidence {
  color: #39705e;
  background: #eff8f4;
}

.errand-submit-evidence {
  color: #14200a;
  background: #95f51f;
  font-weight: 750;
}

.errand-add-evidence::after,
.errand-submit-evidence::after {
  border: 0;
}

.prototype-buy.errand-action {
  display: flex;
  width: 230rpx;
  min-width: 230rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 0 14rpx;
  font-size: 24rpx;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-contact.errand-action {
  min-width: 220rpx;
  overflow: hidden;
  padding: 0 16rpx;
  font-size: 24rpx;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
