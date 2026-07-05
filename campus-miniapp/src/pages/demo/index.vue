<script lang="ts" setup>
import { campusRegions, campusTenants } from '@/mock/campus';

const activeRegionId = ref(campusRegions[0].id);

const campuses = computed(() => campusTenants.filter(item => {
  const region = campusRegions.find(region => region.id === activeRegionId.value);
  return region ? item.areaName === region.name : true;
}));

function selectCampus(campus: typeof campusTenants[number]) {
  uni.showToast({ title: `已切换到${campus.name}`, icon: 'none' });
}
</script>

<template>
  <view class="page">
    <view class="header">
      <view class="title">区域与校区</view>
      <view class="subtitle">每个校区绑定一个代理人，后续分账分润都围绕校区租户展开。</view>
    </view>

    <view class="region-list">
      <view
        v-for="region in campusRegions"
        :key="region.id"
        class="region-card"
        :class="{ active: activeRegionId === region.id }"
        @click="activeRegionId = region.id"
      >
        <view class="region-name">{{ region.name }}</view>
        <view class="region-meta">{{ region.city }} · {{ region.campusCount }} 个校区</view>
        <view class="agent">区域代理 {{ region.agentName }}</view>
      </view>
    </view>

    <view class="section-title">校区租户</view>
    <view class="campus-list">
      <view v-for="campus in campuses" :key="campus.id" class="campus-card">
        <view>
          <view class="campus-name">{{ campus.name }}</view>
          <view class="campus-desc">{{ campus.slogan }}</view>
          <view class="campus-agent">代理人：{{ campus.agentName }} · 邀请码 {{ campus.inviteCode }}</view>
        </view>
        <button class="select-btn" size="mini" @click="selectCampus(campus)">进入</button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f7f8fa;
}

.header {
  padding: 28rpx;
  border-radius: 28rpx;
  background: #fff;
}

.title {
  color: #111827;
  font-size: 42rpx;
  font-weight: 800;
}

.subtitle {
  margin-top: 12rpx;
  color: #64748b;
  font-size: 26rpx;
  line-height: 1.5;
}

.region-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 22rpx;
}

.region-card,
.campus-card {
  padding: 24rpx;
  border-radius: 24rpx;
  background: #fff;
}

.region-card.active {
  outline: 4rpx solid #2563eb;
}

.region-name,
.campus-name {
  color: #111827;
  font-size: 32rpx;
  font-weight: 800;
}

.region-meta,
.campus-desc,
.campus-agent,
.agent {
  margin-top: 10rpx;
  color: #64748b;
  font-size: 24rpx;
}

.agent {
  color: #2563eb;
}

.section-title {
  margin: 32rpx 0 18rpx;
  color: #111827;
  font-size: 30rpx;
  font-weight: 800;
}

.campus-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.campus-card {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  align-items: center;
}

.select-btn {
  flex: 0 0 auto;
  margin: 0;
  color: #fff;
  background: #111827;
  border-radius: 999rpx;
}
</style>
