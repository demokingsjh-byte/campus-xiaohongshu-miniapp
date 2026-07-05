<script lang="ts" setup>
import { campusPublishTypes, campusTenants, getDefaultTenant } from '@/mock/campus';

const activeType = ref(campusPublishTypes[0].key);
const currentTenant = ref(getDefaultTenant());
const form = reactive({
  title: '',
  price: '',
  content: '',
  contact: '',
});

function switchCampus() {
  uni.showActionSheet({
    itemList: campusTenants.map(item => item.name),
    success: ({ tapIndex }) => currentTenant.value = campusTenants[tapIndex],
  });
}

function submit() {
  if (!form.title || !form.content) {
    uni.showToast({ title: '请先填写标题和内容', icon: 'none' });
    return;
  }
  uni.showToast({ title: '已保存为模拟发布', icon: 'success' });
  form.title = '';
  form.price = '';
  form.content = '';
  form.contact = '';
}
</script>

<template>
  <view class="page">
    <view class="campus-bar" @click="switchCampus">
      <text>发布到：{{ currentTenant.name }}</text>
      <text class="change">切换校区</text>
    </view>

    <view class="type-grid">
      <view
        v-for="item in campusPublishTypes"
        :key="item.key"
        class="type-card"
        :class="{ active: activeType === item.key }"
        @click="activeType = item.key"
      >
        <view class="type-title">{{ item.title }}</view>
        <view class="type-desc">{{ item.desc }}</view>
      </view>
    </view>

    <view class="form-card">
      <input v-model="form.title" class="input" placeholder="标题，例如：出一台九成新台灯" />
      <input v-model="form.price" class="input" placeholder="价格，可选" type="digit" />
      <textarea v-model="form.content" class="textarea" placeholder="描述一下物品、求助内容、出发时间或探店体验" />
      <input v-model="form.contact" class="input" placeholder="联系方式，可选" />
      <button class="submit-btn" @click="submit">发布到校区</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f7f8fa;
}

.campus-bar {
  display: flex;
  justify-content: space-between;
  padding: 24rpx;
  border-radius: 24rpx;
  color: #111827;
  background: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.change {
  color: #2563eb;
  font-size: 24rpx;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 22rpx;
}

.type-card {
  padding: 24rpx;
  border-radius: 24rpx;
  background: #fff;
}

.type-card.active {
  color: #fff;
  background: #111827;
}

.type-title {
  font-size: 30rpx;
  font-weight: 800;
}

.type-desc {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 23rpx;
  line-height: 1.35;
}

.type-card.active .type-desc {
  color: rgba(255, 255, 255, .72);
}

.form-card {
  margin-top: 22rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: #fff;
}

.input,
.textarea {
  box-sizing: border-box;
  width: 100%;
  margin-bottom: 18rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  background: #f8fafc;
  color: #111827;
  font-size: 28rpx;
}

.textarea {
  height: 220rpx;
}

.submit-btn {
  margin-top: 6rpx;
  color: #fff;
  background: #2563eb;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 800;
}
</style>
