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
  min-height: 620rpx;
  padding: 80rpx 36rpx;
  text-align: center;
}
.state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  height: 170rpx;
  border: 0;
  border-radius: 22rpx;
  background: #dedede;
  box-shadow: none;
}
.state-icon image {
  width: 86rpx;
  height: 86rpx;
  filter: grayscale(1) brightness(2.1);
}
.state-title {
  margin-top: 28rpx;
  color: #999d9a;
  font-size: 28rpx;
  font-weight: 500;
}
.state-desc {
  max-width: 520rpx;
  margin-top: 14rpx;
  color: #a0a3a1;
  font-size: 24rpx;
  line-height: 1.55;
}
.state-action {
  min-width: 140rpx;
  height: 64rpx;
  margin-top: 28rpx;
  padding: 0 28rpx;
  border-radius: 23rpx;
  color: #14200a;
  background: #95f51f;
  box-shadow: none;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1;
}
</style>
