<script lang="ts" setup>
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
// onLoad((query) => {
//   console.log('log onLoad query', query);
// });
</script>

<template>
  <view class="utility-page text-center">
    <view class="mt-36 text-center">
      登录后访问log
    </view>
    <image class="utility-avatar my-4 h-8 w-8" :src="userStore.userInfo?.avatar" mode="aspectFit" lazy-load="false" binderror="" bindload="" />
    <view>{{ userStore.userInfo?.nickname }}</view>
  </view>
</template>

<style lang="scss" scoped>
.utility-page {
  min-height: 100vh;
  padding: 120rpx var(--page-gutter);
  color: var(--color-text);
  background: var(--color-page);
}
.utility-page > view,
.utility-page > image {
  position: relative;
  z-index: 1;
}
.utility-avatar {
  overflow: hidden;
  border: 4rpx solid #fff;
  border-radius: 50%;
  background: var(--color-primary-soft);
  box-shadow: var(--shadow-card);
}
</style>
