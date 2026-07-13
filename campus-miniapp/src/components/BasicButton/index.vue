<script lang="ts" setup>
import { buttonProps } from '@/components/BasicButton/prpos';

const props = defineProps(buttonProps);
const emits = defineEmits(['click']);
function click() {
  if (props.disabled)
    return;
  emits('click');
}
</script>

<template>
  <view
    class="default-btn" :class="{ 'is-disabled': props.disabled }" role="button"
    :aria-disabled="props.disabled" hover-class="default-btn--pressed" :hover-stay-time="80" @tap="click"
  >
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.default-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 76rpx;
  padding: 0 32rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  border-radius: 20rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 12rpx 30rpx rgba(10, 132, 255, 0.24);
  font-size: 28rpx;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0.5rpx;
  transition:
    transform 120ms ease,
    opacity 120ms ease,
    box-shadow 120ms ease;
}
.default-btn--pressed {
  opacity: 0.9;
  transform: scale(0.98);
  box-shadow: 0 6rpx 16rpx rgba(10, 132, 255, 0.18);
}
.is-disabled {
  opacity: 0.48;
  box-shadow: none;
}
</style>
