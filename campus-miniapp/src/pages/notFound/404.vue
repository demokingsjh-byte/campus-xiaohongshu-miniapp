<script lang="ts" setup>
import BasicButton from '@/components/BasicButton/index.vue';
import { onLoad } from '@dcloudio/uni-app';

const go = ref<string>('');
const router = useRouter();
const redirect = ref<string>('');
onLoad((query) => {
  go.value = query?.go || '';
  redirect.value = query?.redirect || '';
});

/**
 * 返回首页
 */
function backHome() {
  router.pushTab(redirect.value);
}
</script>

<template>
  <view class="not-found-page w-screen flex flex-col items-center">
    <image class="not-found-art" mode="widthFix" src="/static/svg/weep.svg" />
    <view class="not-found-title">
      <text>{{ go }} 页面找不到了~</text>
    </view>
    <BasicButton @click="backHome">
      返回首页
    </BasicButton>
  </view>
</template>

<style lang="scss" scoped>
.not-found-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 140rpx var(--page-gutter) 60rpx;
  color: var(--color-text);
  background: var(--color-page);
}
.not-found-art {
  width: 300rpx;
  padding: 24rpx;
  border-radius: var(--radius-xl);
  background: var(--color-glass-strong);
  box-shadow: var(--shadow-card);
}
.not-found-title {
  margin: 36rpx 0 32rpx;
  color: var(--color-text-secondary);
  font-size: var(--font-size-body);
}
</style>
