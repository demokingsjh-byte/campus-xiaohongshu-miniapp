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
  border: 1rpx solid rgba(10, 126, 103, 0.12);
  border-radius: 22rpx;
  color: #fff;
  background: #10967a;
  box-shadow: 0 10rpx 22rpx rgba(15, 133, 108, 0.18);
  font-size: 28rpx;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0.5rpx;
  transition: transform 120ms ease, opacity 120ms ease;
}
.default-btn--pressed {
  opacity: 0.9;
  transform: scale(0.98);
}
.is-disabled {
  opacity: 0.48;
  box-shadow: none;
}
</style>
