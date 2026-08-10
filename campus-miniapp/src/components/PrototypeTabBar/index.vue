<script lang="ts" setup>
type TabKey = 'home' | 'publish' | 'mine';

const props = defineProps<{
  active: TabKey
}>();
const emit = defineEmits<{
  reselect: [tab: TabKey]
}>();

// Keep tabbar assets as runtime paths so WeChat DevTools hot reload cannot
// mix them up when the shared static-asset module is regenerated.
const tabbarAssets = {
  curve: '/static/images/home-prototype/tabbar-curve.png',
  homeActive: '/static/images/home-prototype/home-active.png',
  homeInactive: '/static/images/home-prototype/home-inactive.png',
  publish: '/static/images/home-prototype/publish.png',
  mineActive: '/static/images/home-prototype/mine-active.png',
  mineInactive: '/static/images/home-prototype/mine-inactive.png',
};

function switchTo(tab: TabKey) {
  if (tab === props.active) {
    emit('reselect', tab);
    return;
  }
  const routes: Record<TabKey, string> = {
    home: '/pages/index/index',
    publish: '/pages/publish/index',
    mine: '/pages/about/index',
  };
  uni.reLaunch({ url: routes[tab] });
}
</script>

<template>
  <view class="prototype-tabbar">
    <image
      class="prototype-tabbar__curve"
      :src="tabbarAssets.curve" mode="scaleToFill"
    />
    <view class="prototype-tabbar__item" role="button" aria-label="首页" @click="switchTo('home')">
      <image v-if="active === 'home'" class="prototype-tabbar__icon" :src="tabbarAssets.homeActive" mode="aspectFit" />
      <image v-else class="prototype-tabbar__icon" :src="tabbarAssets.homeInactive" mode="aspectFit" />
      <text class="prototype-tabbar__label">
        首页
      </text>
    </view>
    <view class="prototype-tabbar__publish" role="button" aria-label="发布" @click="switchTo('publish')">
      <image class="prototype-tabbar__publish-image" :src="tabbarAssets.publish" mode="aspectFit" />
    </view>
    <view class="prototype-tabbar__item" role="button" aria-label="我的" @click="switchTo('mine')">
      <image v-if="active === 'mine'" class="prototype-tabbar__icon" :src="tabbarAssets.mineActive" mode="aspectFit" />
      <image v-else class="prototype-tabbar__icon" :src="tabbarAssets.mineInactive" mode="aspectFit" />
      <text class="prototype-tabbar__label">
        我的
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.prototype-tabbar {
  position: fixed;
  z-index: 60;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  height: calc(124rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  border-radius: 38rpx 38rpx 0 0;
  background: #fff;
  box-shadow: 0 -8rpx 24rpx rgba(48, 60, 53, 0.035);
  box-sizing: border-box;
}

.prototype-tabbar__curve {
  position: absolute;
  z-index: 0;
  left: 50%;
  top: -27rpx;
  display: block;
  width: 174rpx;
  height: 28rpx;
  transform: translateX(-50%);
}

.prototype-tabbar__item,
.prototype-tabbar__publish {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 0 0 33.3333%;
  align-items: center;
  justify-content: center;
  height: 124rpx;
  flex-direction: column;
  box-sizing: border-box;
  color: #171a18;
  font-size: 25rpx;
  font-weight: 500;
}

.prototype-tabbar__label {
  line-height: 32rpx;
}

.prototype-tabbar__icon {
  display: block;
  width: 56rpx;
  height: 56rpx;
  margin-bottom: 4rpx;
}

.prototype-tabbar__publish {
  z-index: 2;
}

.prototype-tabbar__publish-image {
  position: absolute;
  left: 50%;
  top: -18rpx;
  display: block;
  width: 108rpx;
  height: 108rpx;
  transform: translateX(-50%);
}
</style>
