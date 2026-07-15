<script lang="ts" setup>
withDefaults(defineProps<{ type?: 'empty' | 'error' | 'offline' | 'login', title: string, description: string, action?: string }>(), { type: 'empty', action: '' });
const emit = defineEmits<{ action: [] }>();
const icons = {
  empty: '/static/icons/ui/empty.svg',
  error: '/static/icons/ui/alert.svg',
  offline: '/static/icons/ui/offline.svg',
  login: '/static/images/avatar-default-cartoon.png',
};
</script>

<template>
  <view class="state-panel">
    <view class="state-icon">
      <image :src="icons[type]" mode="aspectFit" />
    </view>
    <view class="state-title">
      {{ title }}
    </view>
    <view class="state-desc">
      {{ description }}
    </view>
    <button v-if="action" class="state-action" @click="emit('action')">
      {{ action }}
    </button>
  </view>
</template>

<style scoped>
.state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430rpx;
  padding: 64rpx 36rpx;
  text-align: center;
}
.state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 32rpx;
  background: var(--yd-mint);
  box-shadow: 0 16rpx 38rpx rgba(43, 82, 126, 0.1);
}
.state-icon image {
  width: 58rpx;
  height: 58rpx;
}
.state-title {
  margin-top: 28rpx;
  color: var(--yd-ink);
  font-size: 32rpx;
  font-weight: 800;
}
.state-desc {
  max-width: 520rpx;
  margin-top: 14rpx;
  color: #7a8581;
  font-size: 25rpx;
  line-height: 1.55;
}
.state-action {
  min-width: 220rpx;
  height: var(--yd-control-regular);
  margin-top: 30rpx;
  padding: 0 34rpx;
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 10rpx 24rpx rgba(10, 132, 255, 0.22);
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1;
}
</style>
