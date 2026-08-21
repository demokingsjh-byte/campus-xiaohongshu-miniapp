<script lang="ts" setup>
import { useCampusContentStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import {
  clearCampusLocalData,
  getPrivacyConsent,
  openPolicyPage,
  PRIVACY_POLICY_VERSION,
  revokePrivacyConsent,
} from '@/utils/privacy';

const userStore = useUserStore();
const contentStore = useCampusContentStore();
const busy = ref(false);
const consent = ref(getPrivacyConsent());
const statusBarHeight = ref(0);
const navBarHeight = ref(44);
const navigationStyle = computed(() => ({
  '--status-bar-height': `${statusBarHeight.value}px`,
  '--nav-bar-height': `${navBarHeight.value}px`,
}));
const loggedIn = computed(() => userStore.loggedIn);
const consentTime = computed(() => {
  if (!consent.value?.agreedAt)
    return '尚未记录';
  const date = new Date(consent.value.agreedAt);
  return Number.isNaN(date.getTime()) ? '已同意' : `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
});

onShow(() => {
  consent.value = getPrivacyConsent();
});

onMounted(() => {
  const runtime = uni as any;
  const windowInfo = runtime.getWindowInfo?.() || runtime.getSystemInfoSync?.() || {};
  const menuButton = runtime.getMenuButtonBoundingClientRect?.();
  statusBarHeight.value = windowInfo.statusBarHeight || 0;
  if (menuButton?.height && menuButton?.top) {
    navBarHeight.value = menuButton.height + 2 * Math.max(0, menuButton.top - statusBarHeight.value);
  }
});

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  uni.reLaunch({ url: '/pages/about/index' });
}

function clearLocalRecords() {
  uni.showModal({
    title: '清理本地数据',
    content: '将清除搜索记录、发布草稿和本机浏览偏好，不会删除账号及已发布内容。',
    confirmText: '确认清理',
    success: (res) => {
      if (!res.confirm)
        return;
      clearCampusLocalData({ keepConsent: true });
      uni.showToast({ title: '本地数据已清理', icon: 'success' });
    },
  });
}

function openWechatPrivacyContract() {
  const privacyApi = (uni as any).openPrivacyContract;
  if (typeof privacyApi !== 'function') {
    openPolicyPage('privacy');
    return;
  }
  privacyApi({
    fail: () => openPolicyPage('privacy'),
  });
}

function withdrawConsent() {
  uni.showModal({
    title: '撤回隐私同意',
    content: loggedIn.value
      ? '撤回后将退出登录并清除本机草稿、搜索记录和登录状态。公开内容仍可游客浏览。'
      : '撤回后将清除本机的同意记录和使用偏好，公开内容仍可游客浏览。',
    confirmText: '撤回并清理',
    confirmColor: '#FF453A',
    success: async (res) => {
      if (!res.confirm)
        return;
      if (loggedIn.value) {
        await userStore.logout({ clearConsent: true });
      } else {
        clearCampusLocalData();
        revokePrivacyConsent();
      }
      consent.value = null;
      contentStore.clearPersonalContent();
      uni.showToast({ title: '已撤回', icon: 'success' });
    },
  });
}

function logout() {
  uni.showModal({
    title: '退出当前账号',
    content: '将清除本机登录状态和未发布草稿，不会删除账号及已发布内容。',
    confirmText: '退出登录',
    success: async (res) => {
      if (!res.confirm)
        return;
      await userStore.logout();
      contentStore.clearPersonalContent();
      uni.showToast({ title: '已退出登录', icon: 'success' });
    },
  });
}

function deleteAccount() {
  uni.showModal({
    title: '注销账号前请确认',
    content: '注销后账号资料、发布内容、点赞和收藏将被删除或匿名化，且无法恢复。是否继续？',
    confirmText: '继续注销',
    confirmColor: '#FF453A',
    success: (first) => {
      if (!first.confirm)
        return;
      uni.showModal({
        title: '最后确认',
        content: '请输入“注销账号”确认本人操作',
        editable: true,
        placeholderText: '注销账号',
        confirmText: '永久注销',
        confirmColor: '#FF453A',
        success: async (second) => {
          if (!second.confirm)
            return;
          if (second.content?.trim() !== '注销账号') {
            uni.showToast({ title: '输入内容不正确', icon: 'none' });
            return;
          }
          busy.value = true;
          uni.showLoading({ title: '正在注销', mask: true });
          try {
            await userStore.deleteAccount();
            contentStore.clearPersonalContent();
            consent.value = null;
            uni.hideLoading();
            uni.showToast({ title: '账号已注销', icon: 'success' });
            setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 700);
          } catch {
            uni.hideLoading();
            uni.showToast({ title: '注销失败，请稍后重试', icon: 'none' });
          } finally {
            busy.value = false;
          }
        },
      });
    },
  });
}
</script>

<template>
  <view class="settings-screen" :style="navigationStyle">
    <view class="settings-nav">
      <view class="settings-nav-bar">
        <view class="settings-nav-back" @click="goBack">
          <image src="/static/icons/ui/back.svg" mode="aspectFit" />
        </view>
        <text class="settings-nav-title">设置与隐私</text>
        <view class="settings-nav-capsule-space" />
      </view>
    </view>

    <view class="settings-page">
    <view class="settings-card privacy-card">
      <view class="section-label">
        透明说明
      </view>
      <view class="setting-row" @click="openPolicyPage('privacy')">
        <view class="row-main">
          <text class="row-title">隐私政策</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openWechatPrivacyContract">
        <view class="row-main">
          <text class="row-title">查看隐私保护指引</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openPolicyPage('permissions')">
        <view class="row-main">
          <text class="row-title">权限信息与清单</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="arrow">
          ›
        </text>
      </view>
    </view>

    <view class="settings-card authorization-card">
      <view class="section-label">
        数据与授权
      </view>
      <view class="setting-row static-row">
        <view class="row-main">
          <text class="row-title">当前隐私状态</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="value">
          {{ consent ? '有效' : '未记录' }}
        </text><text class="arrow">›</text>
      </view>
      <view class="setting-row static-row">
        <view class="row-main">
          <text class="row-title">个性化推荐</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="value quiet">
          未启用
        </text><text class="arrow">›</text>
      </view>
      <view class="setting-row" @click="clearLocalRecords">
        <view class="row-main">
          <text class="row-title">清理本地数据</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" :class="{ disabled: !consent }" @click="consent && withdrawConsent()">
        <view class="row-main">
          <text class="row-title">撤回隐私同意</text>
          <text class="row-subtitle">查看信息收集、使用与说明</text>
        </view><text class="arrow">
          ›
        </text>
      </view>
    </view>
  </view>
  </view>
</template>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(60rpx + env(safe-area-inset-bottom));
  color: var(--color-text);
  background: var(--yd-paper);
}
.privacy-hero {
  display: flex;
  align-items: center;
  padding: 30rpx 28rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 20rpx 56rpx rgba(20, 91, 70, 0.1);
  backdrop-filter: blur(30rpx) saturate(150%);
}
.shield {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 88rpx;
  border-radius: 27rpx 27rpx 32rpx 32rpx;
  background: var(--yd-green);
  box-shadow: 0 12rpx 28rpx rgba(16, 167, 121, 0.24);
}
.shield-check {
  width: 27rpx;
  height: 15rpx;
  border-bottom: 5rpx solid #fff;
  border-left: 5rpx solid #fff;
  transform: rotate(-45deg) translate(2rpx, -2rpx);
}
.hero-main {
  flex: 1;
  margin-left: 22rpx;
}
.hero-title {
  font-size: 31rpx;
  font-weight: 780;
}
.hero-desc {
  margin-top: 8rpx;
  color: #777b84;
  font-size: 22rpx;
}
.status-chip {
  padding: 9rpx 16rpx;
  border-radius: 999rpx;
  color: #167047;
  background: rgba(48, 209, 88, 0.12);
  font-size: 20rpx;
  font-weight: 700;
}
.status-chip.inactive {
  color: #74777e;
  background: rgba(118, 118, 128, 0.1);
}
.section-label {
  margin: 32rpx 12rpx 13rpx;
  color: #777b84;
  font-size: 21rpx;
  font-weight: 650;
}
.settings-card {
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.8);
  border-radius: 25rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 15rpx 42rpx rgba(20, 91, 70, 0.09);
  backdrop-filter: blur(26rpx) saturate(145%);
}
.setting-row,
.contact-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: var(--yd-touch-row);
  margin: 0;
  padding: 17rpx 22rpx;
  border: 0;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.08);
  border-radius: 0;
  color: inherit;
  background: transparent;
  font-size: inherit;
  line-height: normal;
  text-align: left;
}
.setting-row:last-child,
.contact-row:last-child {
  border-bottom: 0;
}
.setting-row:active,
.contact-row:active {
  background: rgba(118, 118, 128, 0.06);
}
.row-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: var(--yd-icon-regular);
  height: var(--yd-icon-regular);
  margin-right: var(--yd-icon-gap);
  border-radius: 18rpx;
  background: var(--color-primary-soft);
}
.row-icon image {
  width: 36rpx;
  height: 36rpx;
}
.blue {
  background: rgba(16, 167, 121, 0.14);
}
.cyan {
  background: rgba(50, 173, 230, 0.12);
}
.indigo {
  background: rgba(94, 92, 230, 0.11);
}
.gray {
  background: rgba(142, 142, 147, 0.11);
}
.orange {
  background: rgba(255, 159, 10, 0.12);
}
.soft {
  background: rgba(16, 167, 121, 0.1);
}
.danger-icon {
  background: rgba(255, 69, 58, 0.1);
}
.row-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.row-title {
  font-size: 26rpx;
  font-weight: 650;
  line-height: 1.35;
}
.row-subtitle {
  display: block;
  margin-top: var(--yd-copy-gap);
  overflow: hidden;
  color: #858992;
  font-size: 20rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-main.no-icon {
  margin-left: 4rpx;
}
.arrow {
  margin-left: 16rpx;
  color: #b5b8bf;
  font-size: 37rpx;
  font-weight: 300;
}
.value {
  margin-left: 14rpx;
  color: var(--yd-green);
  font-size: 21rpx;
  font-weight: 650;
}
.value.quiet {
  color: #8e8e93;
}
.danger .row-main text {
  color: #ff453a;
}
.danger.disabled {
  opacity: 0.5;
}
.footer-note {
  padding: 30rpx 24rpx 0;
  color: #999da5;
  font-size: 20rpx;
  line-height: 1.65;
  text-align: center;
}

/* 蓝湖原型：设置与隐私 */
.settings-screen {
  min-height: 100vh;
  background: #f4f4f4;
}

.settings-nav {
  padding-top: var(--status-bar-height);
  background: #edfbf0;
}

.settings-nav-bar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--nav-bar-height);
}

.settings-nav-title {
  color: #1d1b18;
  font-family: "PingFang SC", sans-serif;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.settings-nav-back {
  position: absolute;
  left: 31rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 64rpx;
  height: 64rpx;
}

.settings-nav-back image {
  width: 30rpx;
  height: 30rpx;
}

.settings-nav-capsule-space {
  position: absolute;
  right: 0;
  width: 190rpx;
  height: 64rpx;
}

.settings-page {
  padding: 31rpx 31rpx calc(60rpx + env(safe-area-inset-bottom));
  color: #1f1f1f;
  background: #f4f4f4;
  box-sizing: border-box;
}

.section-label {
  display: flex;
  flex: 0 0 72rpx;
  align-items: center;
  min-height: 72rpx;
  margin: 0;
  padding: 0 24rpx;
  color: #1d1b18;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 500;
  line-height: 42.31rpx;
}

.settings-card {
  display: flex;
  flex-direction: column;
  width: 688rpx;
  overflow: hidden;
  border: 0;
  border-radius: 31rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
  box-sizing: border-box;
}

.privacy-card {
  height: 448rpx;
}

.authorization-card {
  height: 569rpx;
}

.settings-card + .settings-card {
  margin-top: 31rpx;
}

.setting-row {
  display: flex;
  flex: 1;
  align-items: center;
  width: 100%;
  min-height: 0;
  padding: 0 24rpx;
  border-bottom: 0;
  background: transparent;
  box-sizing: border-box;
}

.setting-row:active {
  background: transparent;
}

.row-title {
  display: block;
  color: #1d1b18;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 500;
  line-height: 42.31rpx;
}

.row-subtitle {
  display: block;
  margin-top: 8rpx;
  color: #8b8b8b;
  font-family: "PingFang SC", sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 32.69rpx;
}

.arrow {
  width: 20rpx;
  margin-left: 12rpx;
  color: #9b9b9b;
  font-size: 34rpx;
  font-weight: 300;
  line-height: 42rpx;
  text-align: right;
}

.value {
  margin-left: 16rpx;
  color: #45b82f;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 500;
  line-height: 42.31rpx;
  white-space: nowrap;
}

.value.quiet {
  color: #8b8b8b;
}
</style>
