<script lang="ts" setup>
withDefaults(defineProps<{ type?: 'empty' | 'error' | 'offline' | 'login', title: string, description: string, action?: string }>(), { type: 'empty', action: '' });
const emit = defineEmits<{ action: [] }>();
const icons = { empty: '🗂', error: '⚠', offline: '☁', login: '👋' };
</script>

<template>
  <view class="state-panel">
    <view class="state-icon">
      {{ icons[type] }}
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
  border-radius: 36rpx;
  background: #e8f5f1;
  font-size: 52rpx;
}
.state-title {
  margin-top: 28rpx;
  color: #18201e;
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
  margin-top: 30rpx;
  padding: 0 34rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #16a085;
  font-size: 26rpx;
  font-weight: 700;
}
</style>
