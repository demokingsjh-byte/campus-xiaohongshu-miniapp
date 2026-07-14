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
  [{ label: '我的交易', note: '查看回应', action: 'messages' }, { label: '我的发布', note: '', action: 'published' }, { label: '收藏与足迹', note: '最近浏览', action: 'favorites' }],
  [{ label: '校园认证', note: '', action: 'profile' }, { label: '设置与隐私', note: '', action: 'settings' }, { label: '帮助与反馈', note: '', action: 'help' }],
];
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
  } else if (action === 'settings') {
    uni.navigateTo({ url: '/pages/settings/index' });
  } else {
    uni.navigateTo({ url: '/pages/settings/index' });
  }
}
</script>

<template>
  <view class="mine-page safe-bottom">
    <view class="mine-status" />
    <view class="mine-top">
      <text>我的</text><view class="message" @click="uni.navigateTo({ url: '/pages/messages/index' })">
        <view class="mine-bell" /><i />
      </view>
    </view>

    <view v-if="!loggedIn" class="guest-card">
      <view class="guest-avatar">
        云
      </view><view class="guest-copy">
        <view class="guest-title">
          登录后开启校园生活
        </view><view class="guest-desc">
          发布、收藏并及时查看同学回应
        </view>
      </view><button @click="goLogin()">
        微信一键登录
      </button>
    </view>
    <view v-else class="profile-card">
      <view class="profile-head">
        <view class="profile-avatar">
          <image v-if="profile?.avatar" :src="profile.avatar" mode="aspectFill" />
          <text v-else>
            {{ profile?.nickname?.slice(0, 1) || '同' }}
          </text>
        </view><view class="profile-info">
          <view class="nickname">
            {{ profile?.nickname || '同校同学' }} <text>✓ 已认证</text>
          </view><view class="student-id">
            {{ currentSchool }} · {{ currentCampus }} · {{ profile?.grade || '学生' }}
          </view><view class="bio">
            慢慢逛校园，也认真过生活。
          </view>
        </view><text class="edit" @click="goLogin('edit')">
          编辑
        </text>
      </view>
      <view class="stats">
        <view><b>{{ myPublishCount }}</b><span>发布</span></view>
        <view><b>{{ myFavoriteCount }}</b><span>收藏</span></view>
        <view><b>{{ receivedLikeCount }}</b><span>获赞</span></view>
        <view><b>5</b><span>关注</span></view>
      </view>
    </view>

    <view class="campus-pass yd-card" @click="handleCampusPass">
      <view class="pass-icon">
        校
      </view><view>
        <view class="pass-title">
          {{ currentSchool }}校园卡
        </view><view class="pass-note">
          {{ loggedIn ? '同校认证 · 内容优先推荐' : '当前浏览校园 · 登录后可认证' }}
        </view>
      </view><text>›</text>
    </view>

    <view v-if="loggedIn" class="quick-grid yd-card">
      <view><text>待回应</text><b>2</b></view><view><text>待确认</text><b>1</b></view><view><text>进行中</text><b>3</b></view><view><text>已完成</text><b>18</b></view>
    </view>

    <view v-for="(group, groupIndex) in menuGroups" :key="groupIndex" class="menu-card yd-card">
      <view v-for="item in group" :key="item.label" class="menu-row" @click="handleMenu(item.action, groupIndex === 0)">
        <view class="menu-icon">
          {{ item.label.slice(0, 1) }}
        </view><text class="menu-label">
          {{ item.label }}
        </text><text class="menu-note">
          {{ item.action === 'published' ? `${myPublishCount}` : (item.action === 'profile' ? certificationNote : item.note) }}
        </text><text class="arrow">
          ›
        </text>
      </view>
    </view>
    <view class="version">
      云点校园 v2.1 · 让校园生活更近一点
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  padding: 0 24rpx 36rpx;
  background: var(--yd-paper);
}
.mine-status {
  height: calc(48rpx + env(safe-area-inset-top));
}
.mine-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 0 18rpx 2rpx;
  font-size: 38rpx;
  font-weight: 900;
}
.message {
  position: relative;
  display: none;
  align-items: center;
  justify-content: center;
  width: 66rpx;
  height: 66rpx;
  border: 1rpx solid #e5e5df;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(31, 56, 49, 0.04);
}
.mine-bell {
  position: relative;
  width: 25rpx;
  height: 27rpx;
  border: 3rpx solid #3a3a3c;
  border-bottom: 0;
  border-top-left-radius: 16rpx;
  border-top-right-radius: 16rpx;
}
.mine-bell::before {
  position: absolute;
  left: -7rpx;
  bottom: -5rpx;
  width: 33rpx;
  height: 3rpx;
  border-radius: 9rpx;
  background: #3a3a3c;
  content: '';
}
.mine-bell::after {
  position: absolute;
  left: 8rpx;
  bottom: -11rpx;
  width: 8rpx;
  height: 5rpx;
  border-radius: 0 0 8rpx 8rpx;
  background: #3a3a3c;
  content: '';
}
.message i {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 13rpx;
  height: 13rpx;
  border-radius: 50%;
  background: var(--yd-coral);
}
.guest-card,
.profile-card {
  padding: 28rpx;
  border: 1rpx solid #b7d4c8;
  border-radius: 21rpx;
  color: var(--yd-ink);
  background: var(--yd-mint);
  box-shadow: 0 12rpx 30rpx rgba(33, 50, 86, 0.08);
}
.guest-card {
  display: grid;
  grid-template-columns: 78rpx minmax(0, 1fr);
  align-items: center;
  gap: 0 18rpx;
  text-align: left;
}
.guest-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 78rpx;
  height: 78rpx;
  margin: 0;
  border-radius: 24rpx;
  color: #fff;
  background: var(--yd-green-dark);
  font-size: 32rpx;
  font-weight: 900;
}
.guest-title {
  margin-top: 0;
  font-size: 29rpx;
  font-weight: 900;
}
.guest-desc {
  margin-top: 5rpx;
  color: #657970;
  font-size: 20rpx;
}
.guest-card button {
  grid-column: 1 / -1;
  width: 100%;
  height: 76rpx;
  margin: 22rpx 0 0;
  padding: 0 28rpx;
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  font-size: 26rpx;
  font-weight: 800;
  line-height: 1;
}
.profile-head {
  display: flex;
  align-items: center;
}
.profile-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 104rpx;
  height: 104rpx;
  border: 4rpx solid #fffdf8;
  border-radius: 50%;
  color: #fff;
  background: var(--yd-green-dark);
  font-size: 36rpx;
  font-weight: 900;
}
.profile-avatar image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}
.profile-info {
  flex: 1;
  margin-left: 20rpx;
}
.nickname {
  font-size: 32rpx;
  font-weight: 900;
}
.nickname text {
  margin-left: 8rpx;
  color: var(--yd-green);
  font-size: 19rpx;
}
.student-id,
.bio {
  margin-top: 6rpx;
  color: #657970;
  font-size: 21rpx;
}
.edit {
  align-self: flex-start;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  color: var(--yd-green-dark);
  background: rgba(255, 253, 248, 0.72);
  font-size: 20rpx;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx dashed #9fc6b7;
}
.stats view {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stats b {
  font-size: 30rpx;
}
.stats span {
  margin-top: 5rpx;
  color: #71837c;
  font-size: 20rpx;
}
.campus-pass {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  padding: 19rpx 22rpx;
}
.pass-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  margin-right: 16rpx;
  border-radius: 16rpx 16rpx 16rpx 5rpx;
  color: #fff;
  background: var(--yd-green-dark);
  font-size: 24rpx;
  font-weight: 800;
}
.pass-title {
  font-size: 26rpx;
  font-weight: 800;
}
.pass-note {
  margin-top: 5rpx;
  color: #8a9490;
  font-size: 20rpx;
}
.campus-pass > view:nth-child(2) {
  flex: 1;
}
.campus-pass > text {
  color: #9ba39f;
  font-size: 34rpx;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 18rpx;
  padding: 24rpx 10rpx;
}
.quick-grid view {
  display: flex;
  flex-direction: column-reverse;
  align-items: center;
  gap: 8rpx;
  color: #6f7a76;
  font-size: 21rpx;
}
.quick-grid b {
  color: #18201e;
  font-size: 28rpx;
}
.menu-card {
  overflow: hidden;
  margin-top: 14rpx;
}
.menu-row {
  display: flex;
  align-items: center;
  height: 84rpx;
  padding: 0 22rpx;
  border-bottom: 1rpx solid #efebe5;
}
.menu-row:last-child {
  border-bottom: 0;
}
.menu-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46rpx;
  height: 46rpx;
  border-radius: 14rpx;
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 20rpx;
  font-weight: 800;
}
.menu-label {
  flex: 1;
  margin-left: 12rpx;
  font-size: 26rpx;
  font-weight: 700;
}
.menu-note {
  color: #84908b;
  font-size: 21rpx;
}
.arrow {
  margin-left: 12rpx;
  color: #aab1ae;
  font-size: 32rpx;
}
.version {
  margin-top: 22rpx;
  color: #a1a9a6;
  font-size: 20rpx;
  text-align: center;
}

/* Apple-inspired glass theme */
.message,
.profile-card,
.guest-card,
.campus-pass,
.quick-grid view,
.menu-card {
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 18rpx 44rpx rgba(33, 50, 86, 0.085);
  backdrop-filter: blur(30rpx) saturate(155%);
  -webkit-backdrop-filter: blur(30rpx) saturate(155%);
}
.guest-avatar,
.profile-avatar,
.pass-icon,
.menu-icon {
  background: rgba(10, 132, 255, 0.1);
  color: var(--yd-green-dark);
}
.guest-card button,
.edit {
  background: var(--yd-green);
  box-shadow: 0 10rpx 24rpx rgba(10, 132, 255, 0.2);
}
.menu-row {
  border-color: rgba(60, 60, 67, 0.1);
}
</style>
