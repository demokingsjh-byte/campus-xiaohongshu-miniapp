<script lang="ts" setup>
import { getDefaultTenant } from '@/mock/campus';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
const loggedIn = computed(() => userStore.loggedIn);
const profile = computed(() => userStore.userInfo);
const currentSchool = computed(() => profile.value?.schoolName || tenantStore.tenantName || getDefaultTenant().name);
const currentCampus = computed(() => profile.value?.campusName || (currentSchool.value === '吉首大学' ? '吉首校区' : '主校区'));
const myPublishCount = computed(() => contentStore.publishedPosts.length);
const myFavoriteCount = computed(() => contentStore.favoritePosts.length);
const receivedLikeCount = computed(() => contentStore.publishedPosts.reduce((total, post) => total + post.likes, 0));
const certificationNote = computed(() => loggedIn.value ? (userStore.profileCompleted ? '已认证' : '待完善') : '登录后认证');
const menuGroups = [
  [{ label: '我的交易', note: '查看回应', action: 'messages', icon: '/static/icons/mine/wallet.svg' }, { label: '我的发布', note: '', action: 'published', icon: '/static/icons/mine/post.svg' }, { label: '收藏与足迹', note: '最近浏览', action: 'favorites', icon: '/static/icons/mine/heart.svg' }],
  [{ label: '校园认证', note: '', action: 'profile', icon: '/static/icons/mine/badge.svg' }, { label: '设置与隐私', note: '', action: 'settings', icon: '/static/icons/mine/settings.svg' }, { label: '帮助与反馈', note: '', action: 'help', icon: '/static/icons/mine/help.svg' }],
];
const menuGroupTitles = ['内容与交易', '服务与设置'];

onShow(async () => {
  if (!userStore.userInfo) {
    try {
      await userStore.initUserInfo();
    } catch {}
  }
  if (userStore.loggedIn) {
    try {
      await Promise.all([contentStore.loadMyPosts(), contentStore.loadFavorites()]);
    } catch {
      uni.showToast({ title: '个人数据加载失败，请稍后重试', icon: 'none' });
    }
  }
});

function goLogin(mode: 'login' | 'edit' = 'login') {
  uni.navigateTo({ url: `/pages/login/index${mode === 'edit' ? '?mode=edit' : ''}` });
}
function handleCampusPass() {
  goLogin(loggedIn.value ? 'edit' : 'login');
}
function handleMenu(action: string, requiresLogin: boolean) {
  if (requiresLogin && !loggedIn.value) {
    goLogin();
    return;
  }
  if (action === 'messages') {
    uni.navigateTo({ url: '/pages/messages/index' });
  } else if (action === 'published') {
    uni.navigateTo({ url: '/pages/search/index?mine=1' });
  } else if (action === 'favorites') {
    uni.navigateTo({ url: '/pages/search/index?favorites=1' });
  } else if (action === 'profile') {
    goLogin('edit');
  } else {
    uni.navigateTo({ url: '/pages/settings/index' });
  }
}
</script>

<template>
  <view class="mine-page safe-bottom">
    <view class="ambient-layer">
      <view class="ambient ambient-blue" />
      <view class="ambient ambient-coral" />
      <view class="ambient ambient-sky" />
    </view>
    <view class="mine-status" />

    <view v-if="!loggedIn" class="guest-card glass-card">
      <view class="guest-main">
        <view class="guest-avatar">
          <image src="/static/icons/mine/cloud.svg" mode="aspectFit" />
        </view>
        <view class="guest-copy">
          <view class="guest-title">
            登录后开启校园生活
          </view>
          <view class="guest-desc">
            发布、收藏并及时查看同学回应
          </view>
        </view>
      </view>
      <button class="login-button" @click="goLogin()">
        微信一键登录
      </button>
    </view>

    <view v-else class="profile-card glass-card">
      <view class="profile-head">
        <view class="profile-avatar">
          <image :src="profile?.avatar || '/static/icons/ui/avatar-default.svg'" mode="aspectFill" />
        </view>
        <view class="profile-info">
          <view class="nickname-row">
            <text class="nickname">
              {{ profile?.nickname || '同校同学' }}
            </text>
            <text class="verified" :class="{ pending: !userStore.profileCompleted }">
              {{ userStore.profileCompleted ? '✓ 已认证' : '待完善' }}
            </text>
          </view>
          <view class="student-id">
            {{ currentSchool }} · {{ currentCampus }} · {{ profile?.grade || '学生' }}
          </view>
          <view class="bio">
            慢慢逛校园，也认真过生活。
          </view>
        </view>
        <button class="edit-button" @click="goLogin('edit')">
          编辑
        </button>
      </view>
      <view class="stats">
        <view>
          <text class="stat-value">
            {{ myPublishCount }}
          </text><text class="stat-label">
            发布
          </text>
        </view>
        <view>
          <text class="stat-value">
            {{ myFavoriteCount }}
          </text><text class="stat-label">
            收藏
          </text>
        </view>
        <view>
          <text class="stat-value">
            {{ receivedLikeCount }}
          </text><text class="stat-label">
            获赞
          </text>
        </view>
        <view>
          <text class="stat-value">
            5
          </text><text class="stat-label">
            关注
          </text>
        </view>
      </view>
    </view>

    <view class="campus-pass glass-card" @click="handleCampusPass">
      <view class="pass-icon">
        <image src="/static/icons/mine/school.svg" mode="aspectFit" />
      </view>
      <view class="pass-copy">
        <view class="pass-title">
          {{ currentSchool }}校园卡
        </view>
        <view class="pass-note">
          {{ loggedIn ? '同校身份已关联，内容优先推荐' : '当前浏览校园，登录后可完成认证' }}
        </view>
      </view>
      <text class="pass-arrow">
        ›
      </text>
    </view>

    <view v-if="loggedIn" class="trade-card glass-card" @click="handleMenu('messages', true)">
      <view class="section-head">
        <text>交易动态</text>
        <text>查看全部 ›</text>
      </view>
      <view class="quick-grid">
        <view>
          <text class="quick-value">
            2
          </text><text class="quick-label">
            待回应
          </text>
        </view>
        <view>
          <text class="quick-value">
            1
          </text><text class="quick-label">
            待确认
          </text>
        </view>
        <view>
          <text class="quick-value">
            3
          </text><text class="quick-label">
            进行中
          </text>
        </view>
        <view>
          <text class="quick-value">
            18
          </text><text class="quick-label">
            已完成
          </text>
        </view>
      </view>
    </view>

    <view v-for="(group, groupIndex) in menuGroups" :key="groupIndex" class="service-section">
      <view class="section-title">
        {{ menuGroupTitles[groupIndex] }}
      </view>
      <view class="menu-card glass-card">
        <view v-for="item in group" :key="item.label" class="menu-row" @click="handleMenu(item.action, groupIndex === 0)">
          <view class="menu-icon">
            <image :src="item.icon" mode="aspectFit" />
          </view>
          <text class="menu-label">
            {{ item.label }}
          </text>
          <text class="menu-note">
            {{ item.action === 'published' ? `${myPublishCount}` : (item.action === 'profile' ? certificationNote : item.note) }}
          </text>
          <text class="arrow">
            ›
          </text>
        </view>
      </view>
    </view>

    <view class="version">
      云点校园 v2.1 · 让校园生活更近一点
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mine-page {
  position: relative;
  min-height: 100vh;
  padding: 0 24rpx 36rpx;
  background: #eef2f7;
}
.ambient-layer {
  position: fixed;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.ambient {
  position: absolute;
  border-radius: 50%;
  filter: blur(70rpx);
  opacity: 0.72;
}
.ambient-blue {
  top: 24rpx;
  right: -120rpx;
  width: 420rpx;
  height: 420rpx;
  background: rgba(10, 132, 255, 0.22);
}
.ambient-coral {
  top: 520rpx;
  left: -160rpx;
  width: 360rpx;
  height: 360rpx;
  background: rgba(255, 107, 95, 0.13);
}
.ambient-sky {
  right: -100rpx;
  bottom: 80rpx;
  width: 340rpx;
  height: 340rpx;
  background: rgba(93, 190, 255, 0.12);
}
.mine-status,
.guest-card,
.profile-card,
.campus-pass,
.trade-card,
.service-section,
.version {
  position: relative;
  z-index: 1;
}
.mine-status {
  height: calc(30rpx + env(safe-area-inset-top));
}
.glass-card {
  border: 1rpx solid rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.64);
  box-shadow: 0 18rpx 50rpx rgba(37, 50, 77, 0.1);
  backdrop-filter: blur(34rpx) saturate(160%);
  -webkit-backdrop-filter: blur(34rpx) saturate(160%);
}
.guest-card,
.profile-card {
  padding: 26rpx;
  border-radius: 30rpx;
}
.guest-main,
.profile-head {
  display: flex;
  align-items: center;
}
.guest-avatar,
.profile-avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 3rpx solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  background: rgba(10, 132, 255, 0.1);
  box-shadow: 0 12rpx 30rpx rgba(10, 132, 255, 0.12);
}
.guest-avatar {
  width: 82rpx;
  height: 82rpx;
}
.guest-avatar image {
  width: 42rpx;
  height: 42rpx;
}
.guest-copy {
  min-width: 0;
  margin-left: 18rpx;
}
.guest-title {
  color: #1d1d1f;
  font-size: 29rpx;
  font-weight: 800;
}
.guest-desc {
  margin-top: 7rpx;
  color: #7c7d83;
  font-size: 21rpx;
}
.login-button {
  width: 100%;
  height: 76rpx;
  margin-top: 22rpx;
  border-radius: 19rpx;
  color: #fff;
  background: #0a84ff;
  box-shadow: 0 12rpx 28rpx rgba(10, 132, 255, 0.24);
  font-size: 25rpx;
  font-weight: 750;
}
.profile-avatar {
  width: 100rpx;
  height: 100rpx;
}
.profile-avatar image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}
.profile-info {
  overflow: hidden;
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}
.nickname-row {
  display: flex;
  align-items: center;
  min-width: 0;
}
.nickname {
  overflow: hidden;
  color: #1d1d1f;
  font-size: 31rpx;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.verified {
  flex: 0 0 auto;
  margin-left: 9rpx;
  padding: 5rpx 9rpx;
  border-radius: 999rpx;
  color: #0a6fd8;
  background: rgba(10, 132, 255, 0.1);
  font-size: 17rpx;
  font-weight: 700;
}
.verified.pending {
  color: #d55c52;
  background: rgba(255, 107, 95, 0.12);
}
.student-id,
.bio {
  overflow: hidden;
  margin-top: 7rpx;
  color: #73747a;
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bio {
  color: #929399;
}
.edit-button {
  flex: 0 0 auto;
  align-self: flex-start;
  height: 48rpx;
  margin-left: 10rpx;
  padding: 0 17rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.1);
  border-radius: 999rpx;
  color: #0a6fd8;
  background: rgba(255, 255, 255, 0.66);
  font-size: 19rpx;
  font-weight: 650;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 24rpx;
  padding: 18rpx 6rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 22rpx;
  background: rgba(245, 247, 251, 0.5);
}
.stats view {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stats view:not(:last-child)::after {
  position: absolute;
  top: 8rpx;
  right: 0;
  width: 1rpx;
  height: 42rpx;
  background: rgba(60, 60, 67, 0.1);
  content: '';
}
.stat-value {
  color: #1d1d1f;
  font-size: 28rpx;
  font-weight: 800;
}
.stat-label {
  margin-top: 4rpx;
  color: #85868c;
  font-size: 19rpx;
}
.campus-pass {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 24rpx;
}
.pass-icon,
.menu-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  background: rgba(10, 132, 255, 0.1);
}
.pass-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 17rpx;
}
.pass-icon image {
  width: 32rpx;
  height: 32rpx;
}
.pass-copy {
  overflow: hidden;
  flex: 1;
  min-width: 0;
  margin-left: 15rpx;
}
.pass-title {
  color: #2c2c2e;
  font-size: 25rpx;
  font-weight: 750;
}
.pass-note {
  overflow: hidden;
  margin-top: 5rpx;
  color: #8b8c92;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pass-arrow {
  margin-left: 12rpx;
  color: #a1a2a8;
  font-size: 32rpx;
}
.trade-card {
  margin-top: 16rpx;
  padding: 20rpx 20rpx 18rpx;
  border-radius: 26rpx;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-head text:first-child {
  color: #2c2c2e;
  font-size: 25rpx;
  font-weight: 750;
}
.section-head text:last-child {
  color: #8a8b91;
  font-size: 19rpx;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 18rpx;
}
.quick-grid view {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.quick-grid view:not(:last-child)::after {
  position: absolute;
  top: 4rpx;
  right: 0;
  width: 1rpx;
  height: 48rpx;
  background: rgba(60, 60, 67, 0.09);
  content: '';
}
.quick-value {
  color: #1d1d1f;
  font-size: 27rpx;
  font-weight: 800;
}
.quick-label {
  margin-top: 6rpx;
  color: #85868c;
  font-size: 19rpx;
}
.service-section {
  margin-top: 22rpx;
}
.section-title {
  margin: 0 6rpx 11rpx;
  color: #6e6f75;
  font-size: 21rpx;
  font-weight: 700;
}
.menu-card {
  overflow: hidden;
  border-radius: 26rpx;
}
.menu-row {
  display: flex;
  align-items: center;
  min-height: 94rpx;
  padding: 0 19rpx;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.09);
}
.menu-row:last-child {
  border-bottom: 0;
}
.menu-icon {
  width: 54rpx;
  height: 54rpx;
  border-radius: 16rpx;
}
.menu-icon image {
  width: 30rpx;
  height: 30rpx;
}
.menu-label {
  flex: 1;
  margin-left: 15rpx;
  color: #2c2c2e;
  font-size: 25rpx;
  font-weight: 650;
}
.menu-note {
  color: #96979c;
  font-size: 19rpx;
}
.arrow {
  margin-left: 10rpx;
  color: #a9aab0;
  font-size: 30rpx;
}
.version {
  margin-top: 24rpx;
  color: #a1a2a8;
  font-size: 19rpx;
  text-align: center;
}
</style>
