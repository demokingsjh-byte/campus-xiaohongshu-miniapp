<script lang="ts" setup>
import type { CampusPost } from '@/mock/campus';
import StatePanel from '@/components/StatePanel/index.vue';
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import {
  addCampusDownlistedRecord,
  getCampusDownlistedRecords,
  type CampusDownlistedRecord,
} from '@/utils/personalRecords';

type PublishTab = '在卖' | '已卖出' | '草稿' | '已下架';

const userStore = useUserStore();
const contentStore = useCampusContentStore();
const activeTab = ref<PublishTab>('在卖');
const loading = ref(false);
const loadError = ref(false);
const draft = ref<Record<string, any> | null>(null);
const downlisted = ref<CampusDownlistedRecord[]>([]);
const selectedPost = ref<CampusPost | null>(null);
const statusBarHeight = ref(0);
const navigationStyle = computed(() => ({ '--status-bar-height': `${statusBarHeight.value}px` }));

const allPublishedPosts = computed(() => contentStore.publishedPosts);
function isSoldPost(post: CampusPost) {
  if (!post.soldOut)
    return false;
  const total = Number(post.stockTotal);
  const sold = Number(post.soldCount);
  return post.stockTotal === undefined || post.soldCount === undefined
    || (Number.isFinite(total) && Number.isFinite(sold) && sold >= total);
}
const sellingPosts = computed(() => allPublishedPosts.value.filter(post => !isSoldPost(post) && !post.downlisted));
const soldPosts = computed(() => allPublishedPosts.value.filter(post => isSoldPost(post) && !post.downlisted));
const visiblePublishedPosts = computed(() => activeTab.value === '已卖出' ? soldPosts.value : sellingPosts.value);
const tabs = computed(() => [
  { label: '在卖' as const, count: sellingPosts.value.length },
  { label: '已卖出' as const, count: soldPosts.value.length },
  { label: '草稿' as const, count: draft.value ? 1 : 0 },
  { label: '已下架' as const, count: downlisted.value.length },
]);

onLoad(() => {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight || 0;
});

onShow(() => void loadData());

onShareAppMessage(() => ({
  title: selectedPost.value?.title || '校园内容分享',
  path: selectedPost.value ? `/pages/detail/index?id=${selectedPost.value.id}` : '/pages/index/index',
  imageUrl: selectedPost.value?.coverImage || selectedPost.value?.images?.[0] || undefined,
}));

async function loadData() {
  loading.value = true;
  loadError.value = false;
  try {
    if (!userStore.userInfo)
      await userStore.initUserInfo();
    if (!userStore.loggedIn) {
      uni.navigateTo({ url: '/pages/login/index' });
      return;
    }
    await contentStore.loadMyPosts();
    const storedDraft = uni.getStorageSync('campus-publish-draft');
    draft.value = storedDraft && typeof storedDraft === 'object' ? storedDraft : null;
    downlisted.value = getCampusDownlistedRecords(userStore.userInfo?.id);
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

function openDetail(post: CampusPost) {
  uni.navigateTo({ url: `/pages/detail/index?id=${post.id}&mine=1` });
}

function openMore(post: CampusPost) {
  selectedPost.value = post;
}

function closeMore() {
  selectedPost.value = null;
}

function downlistPost() {
  const target = selectedPost.value;
  if (!target)
    return;
  uni.showModal({
    title: '下架这条内容',
    content: '下架后内容将不再对其他用户展示。',
    confirmText: '下架',
    confirmColor: '#ff4747',
    success: async (result) => {
      if (!result.confirm)
        return;
      try {
        await contentStore.removePost(target.id);
        downlisted.value = addCampusDownlistedRecord(userStore.userInfo?.id, target);
        selectedPost.value = null;
        uni.showToast({ title: '已下架', icon: 'success' });
      } catch {
        uni.showToast({ title: '下架失败，请重试', icon: 'none' });
      }
    },
  });
}

function editDraft() {
  uni.navigateTo({ url: '/pages/publish/index' });
}

function goBack() {
  uni.navigateBack();
}

function goBuy() {
  uni.switchTab({ url: '/pages/index/index' });
}

function goPublish() {
  uni.reLaunch({ url: '/pages/publish/index' });
}

function formatTime(value?: unknown) {
  if (value === undefined || value === null || value === '')
    return '';

  let date: Date;
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value.map(Number);
    date = new Date(year, month - 1, day, hour, minute, second);
  } else if (value instanceof Date) {
    date = value;
  } else if (typeof value === 'number') {
    date = new Date(value < 1_000_000_000_000 ? value * 1000 : value);
  } else if (typeof value === 'object') {
    const item = value as Record<string, unknown>;
    const year = Number(item.year);
    const month = Number(item.month ?? item.monthValue);
    const day = Number(item.day ?? item.dayOfMonth);
    date = year && month && day
      ? new Date(year, month - 1, day, Number(item.hour || 0), Number(item.minute || 0), Number(item.second || 0))
      : new Date(String(item.value ?? item.date ?? ''));
  } else {
    const text = String(value).trim();
    if (/^\d{10,13}$/.test(text)) {
      const timestamp = Number(text);
      date = new Date(text.length === 10 ? timestamp * 1000 : timestamp);
    } else {
      date = new Date(text.replace(' ', 'T'));
    }
  }

  if (Number.isNaN(date.getTime()))
    return typeof value === 'string' ? value : '';
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function draftTitle() {
  return String(draft.value?.title || draft.value?.content || '未命名草稿');
}

function publicationStatus(post: CampusPost) {
  if (isSoldPost(post))
    return '已卖出';
  if (post.soldOut)
    return '交易中';
  if (post.status === 0)
    return '审核中';
  if (post.status === 2)
    return post.auditReason ? `未通过：${post.auditReason}` : '已下架';
  return '';
}
</script>

<template>
  <view class="published-page" :style="navigationStyle">
    <view class="published-nav">
      <view class="published-back" @click="goBack">
        <image src="/static/icons/ui/back.svg" mode="aspectFit" />
      </view>
      <text>我发布的</text>
      <view class="published-capsule-space" />
    </view>
    <view class="publish-tabs">
      <text
        v-for="tab in tabs" :key="tab.label" :class="{ active: activeTab === tab.label }"
        @click="activeTab = tab.label"
      >
        {{ tab.label }}({{ tab.count }})
      </text>
    </view>

    <StatePanel v-if="loading && !allPublishedPosts.length" title="正在加载发布记录" description="只展示当前账号的真实内容" />
    <StatePanel
      v-else-if="loadError && !allPublishedPosts.length" type="offline" title="发布记录加载失败" description="请检查网络后重试"
      action="重新加载" @action="loadData"
    />

    <view v-else-if="(activeTab === '在卖' || activeTab === '已卖出') && visiblePublishedPosts.length" class="publish-list">
      <view
        v-for="post in visiblePublishedPosts" :key="post.id" class="publish-card"
        @longpress.stop="openMore(post)"
      >
        <view class="publish-main" @click="openDetail(post)">
          <image :src="post.coverImage || post.images?.[0] || '/static/icons/ui/empty.svg'" mode="aspectFill" />
          <view class="publish-copy">
            <text class="publish-title">{{ post.title }}</text>
            <text v-if="publicationStatus(post)" class="publish-status" :class="{ rejected: post.status === 2 }">
              {{ publicationStatus(post) }}
            </text>
            <text class="publish-view">浏览 {{ post.views || 0 }}</text>
            <view class="publish-meta">
              <text>{{ formatTime(post.createTime) || post.time }}</text>
              <text v-if="post.price" class="publish-price"><text>¥</text>{{ post.price }}</text>
            </view>
          </view>
        </view>
        <view class="publish-actions">
          <text @click="openMore(post)">更多</text>
          <text @click="openDetail(post)">管理</text>
        </view>
      </view>
    </view>

    <view v-else-if="activeTab === '草稿' && draft" class="publish-list">
      <view class="publish-card draft-card" @click="editDraft">
        <view class="publish-main">
          <image :src="draft.images?.[0] || '/static/icons/ui/empty.svg'" mode="aspectFill" />
          <view class="publish-copy">
            <text class="publish-title">{{ draftTitle() }}</text>
            <text class="publish-view">本机保存的真实草稿</text>
            <text class="draft-edit">继续编辑</text>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="activeTab === '已下架' && downlisted.length" class="publish-list">
      <view v-for="item in downlisted" :key="item.post.id" class="publish-card offline-card">
        <view class="publish-main">
          <image :src="item.post.coverImage || item.post.images?.[0] || '/static/icons/ui/empty.svg'" mode="aspectFill" />
          <view class="publish-copy">
            <text class="publish-title">{{ item.post.title }}</text>
            <text class="publish-view">已下架，不再对其他用户展示</text>
            <text class="offline-time">{{ new Date(item.downlistedAt).toLocaleDateString() }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="activeTab === '在卖'" class="published-empty-state">
      <image src="/static/icons/ui/published-empty.svg" mode="aspectFit" />
      <text>暂无订单信息</text>
      <button @click="goBuy">去购买</button>
      <button @click="goPublish">去发布</button>
    </view>

    <StatePanel
      v-else-if="activeTab === '已卖出'" title="还没有卖出记录"
      description="商品售罄后会保留在这里，不再出现在首页和闲置频道。"
    />

    <StatePanel
      v-else :title="activeTab === '草稿' ? '没有保存的草稿' : '没有已下架内容'"
      description="这里只展示当前账号实际存在的记录"
    />

    <view v-if="selectedPost" class="sheet-mask" @click="closeMore">
      <view class="action-sheet" @click.stop>
        <button open-type="share">分享</button>
        <button class="danger" @click="downlistPost">下架</button>
        <button class="cancel" @click="closeMore">取消</button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.published-page {
  box-sizing: border-box;
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 88rpx) 36rpx 60rpx;
  background: #f5f5f5;
}

.published-nav {
  position: fixed;
  z-index: 20;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
  height: calc(var(--status-bar-height) + 88rpx);
  padding-top: var(--status-bar-height);
  background: #edfff3;
  font-size: 31rpx;
  font-weight: 600;
}

.published-back {
  position: absolute;
  bottom: 14rpx;
  left: 31rpx;
  display: flex;
  align-items: center;
  width: 52rpx;
  height: 60rpx;
}

.published-back image { width: 28rpx; height: 28rpx; }
.published-capsule-space { position: absolute; right: 0; bottom: 12rpx; width: 190rpx; height: 64rpx; }

.publish-tabs { display: flex; gap: 30rpx; margin: 0 -36rpx 24rpx; padding: 12rpx 36rpx 18rpx; background: #edfff3; }
.publish-tabs text { padding: 10rpx 18rpx; border-radius: 14rpx; color: #8b8b8b; background: #fff; font-size: 25rpx; }
.publish-tabs text.active { color: #1f1f1f; background: #8cf51a; font-weight: 600; }
.publish-list { display: flex; gap: 22rpx; flex-direction: column; }

.published-empty-state {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  padding-top: 210rpx;
  flex-direction: column;
}

.published-empty-state image {
  width: 232rpx;
  height: 202rpx;
}

.published-empty-state > text {
  margin-top: 38rpx;
  color: #999d9a;
  font-family: "PingFang SC", sans-serif;
  font-size: 27rpx;
  line-height: 38rpx;
}

.published-empty-state button {
  width: 140rpx;
  height: 64rpx;
  margin: 28rpx 0 0;
  padding: 0;
  border: 0;
  border-radius: 23rpx;
  color: #1f1f1f;
  background: #95f51f;
  box-shadow: none;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 64rpx;
}

.published-empty-state button + button {
  margin-top: 76rpx;
}

.published-empty-state button::after {
  border: 0;
}

.publish-card { overflow: hidden; border-radius: 28rpx; background: #fff; }
.publish-main { display: flex; padding: 24rpx 24rpx 14rpx; }
.publish-main > image { flex: 0 0 auto; width: 160rpx; height: 160rpx; border-radius: 20rpx; background: #eee; }
.publish-copy { display: flex; overflow: hidden; flex: 1; min-width: 0; margin-left: 24rpx; flex-direction: column; }
.publish-title { display: -webkit-box; overflow: hidden; color: #1f1f1f; font-size: 30rpx; font-weight: 600; line-height: 40rpx; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.publish-status {
  overflow: hidden;
  margin-top: 10rpx;
  color: #b26d00;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-status.rejected {
  color: #e24f45;
}
.publish-view { margin-top: 12rpx; color: #999; font-size: 23rpx; }
.publish-meta { display: flex; align-items: flex-end; justify-content: space-between; margin-top: auto; color: #999; font-size: 23rpx; }
.publish-price { color: #ff4747; font-size: 38rpx; font-weight: 700; }
.publish-price > text { margin-right: 3rpx; font-size: 22rpx; font-weight: 400; }
.publish-actions { display: flex; justify-content: space-between; margin: 0 24rpx; padding: 16rpx 0 20rpx; border-top: 1rpx solid #eee; color: #969696; font-size: 25rpx; }
.publish-actions text:last-child { color: #ff8a18; }
.draft-edit { margin-top: auto; color: #ff8a18; font-size: 25rpx; }
.offline-time { margin-top: auto; color: #aaa; font-size: 22rpx; }
.offline-card { opacity: .82; }

.sheet-mask {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, .45);
}

.action-sheet { width: 100%; padding-bottom: env(safe-area-inset-bottom); border-radius: 28rpx 28rpx 0 0; background: #fff; }
.action-sheet button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 94rpx;
  margin: 0;
  padding: 0;
  border-radius: 0;
  color: #1f1f1f;
  background: #fff;
  font-size: 28rpx;
  line-height: 94rpx;
  text-align: center;
  box-sizing: border-box;
}
.action-sheet button::after { border: 0; border-bottom: 1rpx solid #eee; border-radius: 0; }
.action-sheet .danger { color: #1f1f1f; }
.action-sheet .cancel { margin-top: 12rpx; color: #8b8b8b; border-top: 12rpx solid #f3f3f3; }
</style>
