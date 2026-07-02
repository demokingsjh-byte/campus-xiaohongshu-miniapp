<script setup lang="ts">
import { getRandomIcon } from '@/utils/character';
import { platform } from '@/utils/platform';

const logo = getRandomIcon();
const appTitle = 'uniapp-vue3';
</script>

<template>
  <view class="flex flex-col items-center gap-y-2 pt-36">
    <image :src="logo" class="h-8 w-8" alt="" mode="widthFix" />
    <view class="text-xl font-semibold text-red">
      {{ appTitle }}
    </view>
    <view class="text-red">
      当前平台：{{ platform }}
    </view>
  </view>
</template>

<style lang="scss">
</style>
