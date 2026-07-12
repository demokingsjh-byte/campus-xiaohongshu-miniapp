<script lang="ts" setup>
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const loggedIn = ref(Boolean(uni.getStorageSync('yd-demo-login')));
const profile = computed(() => userStore.userInfo);
const menuGroups = [
  [{ icon: '📦', label: '我的交易', note: '待确认 1' }, { icon: '✎', label: '我的发布', note: '12' }, { icon: '♡', label: '收藏与足迹', note: '36' }],
  [{ icon: '🎓', label: '校园认证', note: '已认证' }, { icon: '⚙', label: '设置与隐私', note: '' }, { icon: '◉', label: '帮助与反馈', note: '' }],
];
onShow(async () => {
  const hasLoginMarker = Boolean(uni.getStorageSync('yd-demo-login'));
  if (hasLoginMarker && !userStore.userInfo) {
    try {
      await userStore.initUserInfo();
    } catch {}
  }
  loggedIn.value = userStore.loggedIn || hasLoginMarker;
});
function goLogin(mode: 'login' | 'edit' = 'login') {
  uni.navigateTo({ url: `/pages/login/index${mode === 'edit' ? '?mode=edit' : ''}` });
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
      </view><view class="guest-title">
        登录后开启校园生活
      </view><view class="guest-desc">
        发布内容、收藏好物，也不错过同学的回应
      </view><button @click="goLogin()">
        微信一键登录
      </button>
    </view>
    <view v-else class="profile-card">
      <view class="profile-head">
        <view class="profile-avatar">
          <image v-if="profile?.avatar" :src="profile.avatar" mode="aspectFill" />
          <text v-else>{{ profile?.nickname?.slice(0, 1) || '同' }}</text>
        </view><view class="profile-info">
          <view class="nickname">
            {{ profile?.nickname || '同校同学' }} <text>✓ 已认证</text>
          </view><view class="student-id">
            {{ profile?.schoolName || '浙江理工大学' }} · {{ profile?.campusName || '下沙校区' }} · {{ profile?.grade || '学生' }}
          </view><view class="bio">
            慢慢逛校园，也认真过生活。
          </view>
        </view><text class="edit" @click="goLogin('edit')">
          编辑
        </text>
      </view>
      <view class="stats">
        <view><b>12</b><span>发布</span></view><view><b>28</b><span>收藏</span></view><view><b>168</b><span>获赞</span></view><view><b>9</b><span>关注</span></view>
      </view>
    </view>

    <view class="campus-pass yd-card">
      <view class="pass-icon">
        校
      </view><view>
        <view class="pass-title">
          浙江理工大学校园卡
        </view><view class="pass-note">
          同校认证 · 内容优先推荐
        </view>
      </view><text>›</text>
    </view>

    <view v-if="loggedIn" class="quick-grid yd-card">
      <view><text>待回应</text><b>2</b></view><view><text>待确认</text><b>1</b></view><view><text>进行中</text><b>3</b></view><view><text>已完成</text><b>18</b></view>
    </view>

    <view v-for="(group, groupIndex) in menuGroups" :key="groupIndex" class="menu-card yd-card">
      <view v-for="item in group" :key="item.label" class="menu-row" @click="!loggedIn && groupIndex === 0 ? goLogin() : null">
        <view class="menu-icon">
          {{ item.label.slice(0, 1) }}
        </view><text class="menu-label">
          {{ item.label }}
        </text><text class="menu-note">
          {{ item.note }}
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
  background: #f7f7f3;
}
.mine-status {
  height: calc(72rpx + env(safe-area-inset-top));
}
.mine-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 184rpx 20rpx 2rpx;
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
.mine-bell { position: relative; width: 25rpx; height: 27rpx; border: 3rpx solid #273632; border-bottom: 0; border-top-left-radius: 16rpx; border-top-right-radius: 16rpx; }
.mine-bell::before { position: absolute; left: -7rpx; bottom: -5rpx; width: 33rpx; height: 3rpx; border-radius: 9rpx; background: #273632; content: ''; }
.mine-bell::after { position: absolute; left: 8rpx; bottom: -11rpx; width: 8rpx; height: 5rpx; border-radius: 0 0 8rpx 8rpx; background: #273632; content: ''; }
.message i {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 13rpx;
  height: 13rpx;
  border-radius: 50%;
  background: #ff6b5e;
}
.guest-card,
.profile-card {
  padding: 34rpx 28rpx;
  border-radius: 30rpx;
  color: #fff;
  background: #174f48;
  box-shadow: 0 14rpx 32rpx rgba(23, 79, 72, 0.16);
}
.guest-card {
  text-align: center;
}
.guest-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 94rpx;
  height: 94rpx;
  margin: 0 auto;
  border-radius: 32rpx;
  color: #174f48;
  background: #d8f0e9;
  font-size: 38rpx;
  font-weight: 900;
}
.guest-title {
  margin-top: 18rpx;
  font-size: 34rpx;
  font-weight: 900;
}
.guest-desc {
  margin-top: 9rpx;
  color: rgba(255, 255, 255, 0.72);
  font-size: 23rpx;
}
.guest-card button {
  width: 300rpx;
  margin: 24rpx auto 0;
  border-radius: 999rpx;
  color: #174f48;
  background: #fff;
  font-size: 26rpx;
  font-weight: 800;
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
  border: 4rpx solid rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  color: #174f48;
  background: #d9eee8;
  font-size: 36rpx;
  font-weight: 900;
}
.profile-avatar image { width: 100%; height: 100%; border-radius: 50%; }
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
  color: #bde9dd;
  font-size: 19rpx;
}
.student-id,
.bio {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 21rpx;
}
.edit {
  align-self: flex-start;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  font-size: 20rpx;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.13);
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
  color: rgba(255, 255, 255, 0.62);
  font-size: 20rpx;
}
.campus-pass {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 22rpx;
}
.pass-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  margin-right: 16rpx;
  border-radius: 22rpx;
  color: #fff;
  background: #1f675f;
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
  margin-top: 18rpx;
}
.menu-row {
  display: flex;
  align-items: center;
  height: 92rpx;
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
  color: #0f766e;
  background: #e8f5f1;
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
  margin-top: 28rpx;
  color: #a1a9a6;
  font-size: 20rpx;
  text-align: center;
}
</style>
