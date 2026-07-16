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
  min-height: var(--yd-control-regular);
  padding: 0 32rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.2);
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 8rpx 22rpx rgba(16, 167, 121, 0.24);
  font-size: 27rpx;
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
  box-shadow: 0 4rpx 14rpx rgba(16, 167, 121, 0.18);
}
.is-disabled {
  opacity: 0.48;
  box-shadow: none;
}
</style>
