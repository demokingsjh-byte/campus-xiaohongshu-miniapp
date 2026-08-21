<script lang="ts" setup>
interface HelpQuestion {
  title: string
  summary: string
  answer: string
}

const questions: HelpQuestion[] = [
  {
    title: '账号与登录',
    summary: '登录失败、资料不同步、关注记录异常',
    answer: '请先确认当前微信账号与此前使用的账号一致，并检查网络连接。若仍无法恢复，请通过下方“问题反馈”提交发生时间、页面截图和操作步骤。',
  },
  {
    title: '发布与内容',
    summary: '发布失败、内容不显示、帖子状态异常',
    answer: '请确认标题和正文符合发布要求。发布成功后可在“我的—已发布”查看；若列表未更新，可返回首页重新进入，仍有问题请提交反馈。',
  },
  {
    title: '交易与支付',
    summary: '订单、待支付、已买到或已卖出问题',
    answer: '订单状态以服务端记录为准。未完成支付的订单会持续显示在“待支付”，支付成功或订单取消后状态会自动更新。',
  },
  {
    title: '图片与头像',
    summary: '图片空白、头像不显示或加载失败',
    answer: '请切换到可用网络后重试。体验版图片仍为空时，请在反馈中附上页面截图和发生时间，便于排查图片地址或访问权限。',
  },
];

const statusBarHeight = ref(0);
const navBarHeight = ref(44);
const navigationStyle = computed(() => ({
  '--status-bar-height': `${statusBarHeight.value}px`,
  '--nav-bar-height': `${navBarHeight.value}px`,
}));

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

function showAnswer(item: HelpQuestion) {
  uni.showModal({
    title: item.title,
    content: item.answer,
    showCancel: false,
    confirmText: '我知道了',
  });
}
</script>

<template>
  <view class="help-screen" :style="navigationStyle">
    <view class="help-nav">
      <view class="help-nav-bar">
        <view class="help-nav-back" @click="goBack">
          <image src="/static/icons/ui/back.svg" mode="aspectFit" />
        </view>
        <text class="help-nav-title">帮助与反馈</text>
        <view class="help-nav-capsule-space" />
      </view>
    </view>

    <view class="help-page">
    <view class="help-intro">
      <text class="help-intro-title">需要什么帮助？</text>
      <text class="help-intro-copy">先查看常见问题，也可以直接向我们提交反馈</text>
    </view>

    <view class="help-card">
      <view class="help-section-title">常见问题</view>
      <view v-for="item in questions" :key="item.title" class="help-row" @click="showAnswer(item)">
        <view class="help-row-copy">
          <text class="help-row-title">{{ item.title }}</text>
          <text class="help-row-summary">{{ item.summary }}</text>
        </view>
        <text class="help-arrow">›</text>
      </view>
    </view>

    <view class="help-card support-card">
      <view class="help-section-title">反馈与支持</view>
      <button class="help-row help-button" open-type="feedback">
        <view class="help-row-copy">
          <text class="help-row-title">问题反馈</text>
          <text class="help-row-summary">提交故障截图和问题说明</text>
        </view>
        <text class="help-arrow">›</text>
      </button>
      <button class="help-row help-button" open-type="feedback">
        <view class="help-row-copy">
          <text class="help-row-title">功能建议</text>
          <text class="help-row-summary">告诉我们你希望增加的功能</text>
        </view>
        <text class="help-arrow">›</text>
      </button>
      <button class="help-row help-button" open-type="feedback">
        <view class="help-row-copy">
          <text class="help-row-title">举报与申诉</text>
          <text class="help-row-summary">提交违规线索或处理结果申诉</text>
        </view>
        <text class="help-arrow">›</text>
      </button>
      <button class="help-row help-button" open-type="contact">
        <view class="help-row-copy">
          <text class="help-row-title">联系在线客服</text>
          <text class="help-row-summary">通过微信客服获得进一步帮助</text>
        </view>
        <text class="help-arrow">›</text>
      </button>
    </view>
  </view>
  </view>
</template>

<style lang="scss" scoped>
.help-screen {
  min-height: 100vh;
  background: #f4f4f4;
}

.help-nav {
  padding-top: var(--status-bar-height);
  background: #edfbf0;
}

.help-nav-bar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--nav-bar-height);
}

.help-nav-title {
  color: #1d1b18;
  font-family: "PingFang SC", sans-serif;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.help-nav-back {
  position: absolute;
  left: 31rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 64rpx;
  height: 64rpx;
}

.help-nav-back image {
  width: 30rpx;
  height: 30rpx;
}

.help-nav-capsule-space {
  position: absolute;
  right: 0;
  width: 190rpx;
  height: 64rpx;
}

.help-page {
  min-height: 100vh;
  padding: 30rpx 31rpx calc(60rpx + env(safe-area-inset-bottom));
  color: #1d1b18;
  background: #f4f4f4;
  box-sizing: border-box;
}

.help-intro {
  display: flex;
  padding: 10rpx 8rpx 30rpx;
  flex-direction: column;
}

.help-intro-title {
  font-size: 38rpx;
  font-weight: 600;
  line-height: 52rpx;
}

.help-intro-copy {
  margin-top: 8rpx;
  color: #8b8b8b;
  font-size: 25rpx;
  line-height: 36rpx;
}

.help-card {
  overflow: hidden;
  border-radius: 31rpx;
  background: #fff;
}

.support-card {
  margin-top: 31rpx;
}

.help-section-title {
  display: flex;
  align-items: center;
  height: 78rpx;
  padding: 0 25rpx;
  font-size: 32rpx;
  font-weight: 600;
  box-sizing: border-box;
}

.help-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 122rpx;
  padding: 18rpx 25rpx;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  box-sizing: border-box;
}

.help-button {
  margin: 0;
  border-radius: 0;
  font-size: inherit;
  line-height: normal;
}

.help-button::after {
  border: 0;
}

.help-row-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.help-row-title {
  color: #1d1b18;
  font-size: 31rpx;
  font-weight: 500;
  line-height: 43rpx;
}

.help-row-summary {
  overflow: hidden;
  margin-top: 7rpx;
  color: #8b8b8b;
  font-size: 23rpx;
  line-height: 33rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.help-arrow {
  width: 24rpx;
  margin-left: 16rpx;
  color: #999;
  font-size: 38rpx;
  font-weight: 300;
  text-align: right;
}
</style>
