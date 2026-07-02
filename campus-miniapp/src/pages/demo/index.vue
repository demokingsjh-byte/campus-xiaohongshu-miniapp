<script lang="ts" setup>
import Iconify from '@/components/Iconify/index.vue';

const demo = ref('Demo');
</script>

<template>
  <view class="flex flex-col items-center justify-center pt-36 text-lg font-medium">
    {{ demo }}
    <view class="mt-30px">
      iconify 组件
    </view>
    <view class="mt-30rpx center flex flex-row gap-10px">
      <Iconify icon="i-mdi-account-box" size="64" color="blue" />
      <Iconify icon="i-mdi-account-box" size="48" color="orange" />
      <Iconify icon="i-mdi-account-box" size="32" class="text-green-400" />
      <Iconify icon="i-mdi-account-box" class="text-red-400" />
      <Iconify icon="i-svg-spinners-12-dots-scale-rotate" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
</style>
