<script lang="ts" setup>
import StatePanel from '@/components/StatePanel/index.vue';
import { campusRegions, campusTenants, getDefaultTenant } from '@/mock/campus';
import { useTenantStore } from '@/stores/modules/tenant';

const activeRegionId = ref(1);
const keyword = ref('');
const tenantStore = useTenantStore();
const services = [
  { icon: '♻', label: '二手市场', note: '386件在售' },
  { icon: '🙌', label: '校园互助', note: '48条求助' },
  { icon: '🚕', label: '拼车出行', note: '周末热门' },
  { icon: '🥤', label: '校园探店', note: '真实评价' },
  { icon: '🔎', label: '失物招领', note: '今日12条' },
  { icon: '🎉', label: '社团活动', note: '本周28场' },
];
const currentTenant = computed(() => tenantStore.currentTenant || getDefaultTenant());
const currentRegion = computed(() => campusRegions.find(item => item.id === activeRegionId.value));
const campuses = computed(() => campusTenants.filter((item) => {
  const matchesRegion = !currentRegion.value || item.areaName === currentRegion.value.name;
  const matchesKeyword = !keyword.value.trim() || `${item.name}${item.areaName}`.includes(keyword.value.trim());
  return matchesRegion && matchesKeyword;
}));
function selectCampus(campus: typeof campusTenants[number]) {
  tenantStore.selectTenant(campus);
  uni.showToast({ title: `已切换到${campus.name}`, icon: 'success' });
  setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 600);
}
function regionLabel(name: string) {
  return name.replace('杭州大学城', '大学城').replace('滨江高校圈', '滨江').replace('下沙生活区', '下沙');
}
</script>

<template>
  <view class="yd-page region-page">
    <view class="current-card yd-card">
      <view class="current-label">
        当前校园
      </view>
      <view class="current-main">
        <view>
          <view class="current-name">
            {{ currentTenant.name }}
          </view><view class="current-meta">
            {{ currentTenant.areaName }} · 已认证校园
          </view>
        </view><view class="verified">
          ✓ 同校
        </view>
      </view>
      <view class="campus-weather">
        校园热度 89% <text>·</text> 26℃ 多云 <text>·</text> 1.8km
      </view>
    </view>

    <view class="search-box">
      <view class="search-symbol" /><input v-model="keyword" placeholder="搜索学校或校区">
    </view>

    <view class="section-head">
      <view class="yd-section-title">
        校园服务
      </view><view class="yd-section-subtitle">
        同校优先，附近也能看
      </view>
    </view>
    <view class="service-grid">
      <view v-for="service in services" :key="service.label" class="service-card yd-card" @click="uni.switchTab({ url: '/pages/index/index' })">
        <view class="service-icon">
          {{ service.label.slice(0, 1) }}
        </view><view class="service-label">
          {{ service.label }}
        </view><view class="service-note">
          {{ service.note }}
        </view>
      </view>
    </view>

    <view class="section-head">
      <view class="yd-section-title">
        附近校园
      </view><view class="region-tabs">
        <text v-for="region in campusRegions" :key="region.id" :class="{ active: activeRegionId === region.id }" @click="activeRegionId = region.id">
          {{ regionLabel(region.name) }}
        </text>
      </view>
    </view>
    <StatePanel v-if="!campuses.length" title="这个区域暂未开通校园" description="可以切换其他区域，或搜索已开通的学校和校区。" />
    <view v-else class="campus-list">
      <view v-for="(campus, index) in campuses" :key="campus.id" class="campus-row yd-card" @click="selectCampus(campus)">
        <view class="school-logo" :class="`logo-${index}`">
          {{ campus.name.slice(0, 1) }}
        </view>
        <view class="school-main">
          <view class="school-name">
            {{ campus.name }}
          </view><view class="school-meta">
            {{ campus.areaName }} · {{ index + 1 }}.{{ index + 2 }}km
          </view><view class="school-slogan">
            {{ campus.slogan }}
          </view>
        </view>
        <view class="enter">
          进入 ›
        </view>
      </view>
    </view>

    <view class="activity-card yd-card">
      <view class="activity-tag">
        本周活动
      </view><view class="activity-title">
        高校草坪音乐夜
      </view><view class="activity-meta">
        周六 19:00 · 北操场 · 236人想去
      </view><button @click="uni.navigateTo({ url: '/pages/detail/index?id=1006' })">
        查看活动
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.region-page {
  padding-top: 24rpx;
  background: #f7f7f3;
}
.current-card {
  overflow: hidden;
  padding: 28rpx;
  border: 0;
  color: #fff;
  background: #174f48;
  box-shadow: 0 12rpx 30rpx rgba(23, 79, 72, 0.16);
}
.current-label {
  color: rgba(255, 255, 255, 0.64);
  font-size: 23rpx;
}
.current-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}
.current-name {
  font-size: 38rpx;
  font-weight: 900;
}
.current-meta {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 23rpx;
}
.verified {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  color: #174f48;
  background: #d7f0e9;
  font-size: 22rpx;
  font-weight: 700;
}
.campus-weather {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}
.campus-weather text {
  margin: 0 8rpx;
  color: rgba(255, 255, 255, 0.3);
}
.search-box {
  display: flex;
  align-items: center;
  height: 76rpx;
  margin-top: 22rpx;
  padding: 0 22rpx;
  border-radius: 20rpx;
  border: 1rpx solid #e5e5df;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgba(31, 56, 49, 0.03);
}
.search-symbol { position: relative; width: 21rpx; height: 21rpx; border: 3rpx solid #6f7c77; border-radius: 50%; }
.search-symbol::after { position: absolute; right: -9rpx; bottom: -6rpx; width: 11rpx; height: 3rpx; border-radius: 6rpx; background: #6f7c77; content: ''; transform: rotate(45deg); }
.search-box input {
  flex: 1;
  margin-left: 12rpx;
  font-size: 25rpx;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 34rpx 4rpx 18rpx;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
}
.service-card {
  padding: 20rpx 8rpx;
  border-radius: 22rpx;
  text-align: center;
}
.service-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54rpx;
  height: 54rpx;
  margin: 0 auto;
  border-radius: 18rpx;
  color: #fff;
  background: #1f675f;
  font-size: 22rpx;
  font-weight: 800;
}
.service-card:nth-child(2) .service-icon { background: #e68658; }
.service-card:nth-child(3) .service-icon { background: #3c7f91; }
.service-card:nth-child(4) .service-icon { background: #b27155; }
.service-card:nth-child(5) .service-icon { background: #536c67; }
.service-card:nth-child(6) .service-icon { background: #d6675a; }
.service-label {
  margin-top: 10rpx;
  font-size: 25rpx;
  font-weight: 700;
}
.service-note {
  margin-top: 5rpx;
  color: #929c98;
  font-size: 19rpx;
}
.region-tabs {
  display: flex;
  gap: 16rpx;
  color: #8c9692;
  font-size: 22rpx;
}
.region-tabs .active {
  color: #16a085;
  font-weight: 700;
}
.campus-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.campus-row {
  display: flex;
  align-items: center;
  padding: 22rpx;
}
.school-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  color: #fff;
  background: #16a085;
  font-size: 30rpx;
  font-weight: 900;
}
.logo-1 {
  background: #ee8b55;
}
.logo-2 {
  background: #4b8791;
}
.school-main {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}
.school-name {
  font-size: 28rpx;
  font-weight: 800;
}
.school-meta,
.school-slogan {
  overflow: hidden;
  margin-top: 5rpx;
  color: #808b87;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.enter {
  color: #16a085;
  font-size: 23rpx;
}
.activity-card {
  position: relative;
  overflow: hidden;
  margin-top: 22rpx;
  padding: 28rpx;
  background: #174f48;
  color: #fff;
}
.activity-tag {
  display: inline-block;
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  color: #174f48;
  background: #d7f0e9;
  font-size: 20rpx;
}
.activity-title {
  margin-top: 18rpx;
  font-size: 34rpx;
  font-weight: 900;
}
.activity-meta {
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}
.activity-card button {
  width: 170rpx;
  margin-top: 22rpx;
  border-radius: 999rpx;
  color: #174f48;
  background: #fff;
  font-size: 23rpx;
}
</style>
