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
            setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 700);
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
  <view class="settings-page">
    <view class="privacy-hero">
      <view class="shield">
        <view class="shield-check" />
      </view>
      <view class="hero-main">
        <view class="hero-title">
          隐私保护中心
        </view>
        <view class="hero-desc">
          你的信息，由你掌控
        </view>
      </view>
      <view class="status-chip" :class="{ inactive: !consent }">
        {{ consent ? '已同意' : '未同意' }}
      </view>
    </view>

    <view class="section-label">
      透明说明
    </view>
    <view class="settings-card">
      <view class="setting-row" @click="openPolicyPage('privacy')">
        <view class="row-icon blue">
          隐
        </view><view class="row-main">
          <text>隐私政策</text><span>查看信息收集、使用与保存说明</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openWechatPrivacyContract">
        <view class="row-icon cyan">
          微
        </view><view class="row-main">
          <text>微信隐私保护指引</text><span>查看微信平台备案的正式指引</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openPolicyPage('permissions')">
        <view class="row-icon indigo">
          权
        </view><view class="row-main">
          <text>权限与信息清单</text><span>头像、图片、登录标识的调用时机</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openPolicyPage('agreement')">
        <view class="row-icon gray">
          约
        </view><view class="row-main">
          <text>用户协议</text><span>账号、内容与交易规则</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view class="setting-row" @click="openPolicyPage('community')">
        <view class="row-icon orange">
          规
        </view><view class="row-main">
          <text>社区发布规范</text><span>校园内容与交易安全边界</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
    </view>

    <view class="section-label">
      数据与授权
    </view>
    <view class="settings-card">
      <view class="setting-row static-row">
        <view class="row-main no-icon">
          <text>当前隐私版本</text><span>版本 {{ PRIVACY_POLICY_VERSION }} · {{ consentTime }}</span>
        </view><text class="value">
          {{ consent ? '有效' : '未记录' }}
        </text>
      </view>
      <view class="setting-row static-row">
        <view class="row-main no-icon">
          <text>个性化推荐</text><span>当前未启用跨校或广告画像推荐</span>
        </view><text class="value quiet">
          未启用
        </text>
      </view>
      <view class="setting-row" @click="clearLocalRecords">
        <view class="row-main no-icon">
          <text>清理本地数据</text><span>搜索记录、草稿和本机偏好</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view v-if="consent" class="setting-row" @click="withdrawConsent">
        <view class="row-main no-icon">
          <text>撤回隐私同意</text><span>退出登录并停止后续非必要处理</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
    </view>

    <view class="section-label">
      账号与安全
    </view>
    <view class="settings-card">
      <button class="contact-row" open-type="contact">
        <view class="row-main no-icon">
          <text>隐私问题与反馈</text><span>联系客服，提交查阅、更正或投诉请求</span>
        </view><text class="arrow">
          ›
        </text>
      </button>
      <view v-if="loggedIn" class="setting-row" @click="logout">
        <view class="row-main no-icon">
          <text>退出登录</text><span>保留账号和已发布内容</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
      <view v-if="loggedIn" class="setting-row danger" :class="{ disabled: busy }" @click="!busy && deleteAccount()">
        <view class="row-main no-icon">
          <text>注销账号</text><span>永久删除或匿名化账号关联信息</span>
        </view><text class="arrow">
          ›
        </text>
      </view>
    </view>

    <view class="footer-note">
      游客无需登录即可浏览公开内容。云点校园不申请通讯录、后台定位、麦克风或蓝牙权限。
    </view>
  </view>
</template>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(60rpx + env(safe-area-inset-bottom));
  color: #1d1d1f;
  background: var(--yd-paper);
}
.privacy-hero {
  display: flex;
  align-items: center;
  padding: 30rpx 28rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 20rpx 54rpx rgba(31, 45, 70, 0.085);
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
  box-shadow: 0 12rpx 28rpx rgba(10, 132, 255, 0.22);
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
  box-shadow: 0 15rpx 40rpx rgba(31, 45, 70, 0.065);
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
  color: #fff;
  font-size: 22rpx;
  font-weight: 750;
}
.blue {
  background: #0a84ff;
}
.cyan {
  background: #32ade6;
}
.indigo {
  background: #5e5ce6;
}
.gray {
  background: #8e8e93;
}
.orange {
  background: #ff9f0a;
}
.row-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.row-main text {
  font-size: 26rpx;
  font-weight: 650;
  line-height: 1.35;
}
.row-main span {
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
</style>
