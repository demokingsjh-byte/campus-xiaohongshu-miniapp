<script lang="ts" setup>
import { campusPublishTypes } from '@/mock/campus';

const activeType = ref('idle');
const images = ref<string[]>([]);
const submitting = ref(false);
const showSuccess = ref(false);
const agreed = ref(true);
const errors = reactive<Record<string, string>>({});
const form = reactive({ title: '', price: '', content: '', location: '浙江理工大学 · 生活二区', tags: [] as string[], contact: '', anonymous: false });
const tagOptions = ['宿舍自提', '可小刀', '急', '女生优先', '校内交易'];
const showPrice = computed(() => ['idle', 'ride', 'shop'].includes(activeType.value));
const currentType = computed(() => campusPublishTypes.find(item => item.key === activeType.value)!);

function addImage() {
  if (images.value.length >= 9)
    return;
  uni.chooseImage({ count: 9 - images.value.length, success: res => images.value.push(...res.tempFilePaths) });
}
function removeImage(index: number) { images.value.splice(index, 1); }
function toggleTag(tag: string) { const index = form.tags.indexOf(tag); index >= 0 ? form.tags.splice(index, 1) : form.tags.push(tag); }
function validate() {
  errors.title = form.title.trim() ? '' : '请填写一个清楚的标题';
  errors.content = form.content.trim().length >= 10 ? '' : '内容至少写 10 个字，让同学更容易了解';
  errors.price = showPrice.value && form.price && Number(form.price) <= 0 ? '价格需要大于 0' : '';
  errors.agreement = agreed.value ? '' : '请先同意社区发布规范';
  return !Object.values(errors).some(Boolean);
}
function saveDraft() { uni.setStorageSync('campus-publish-draft', { ...form, activeType: activeType.value }); uni.showToast({ title: '草稿已保存', icon: 'none' }); }
function submit() {
  if (!validate()) { uni.showToast({ title: '还有内容需要完善', icon: 'none' }); return; }
  submitting.value = true;
  setTimeout(() => { submitting.value = false; showSuccess.value = true; }, 900);
}
function reset() { showSuccess.value = false; Object.assign(form, { title: '', price: '', content: '', location: '浙江理工大学 · 生活二区', tags: [], contact: '', anonymous: false }); images.value = []; }
</script>

<template>
  <view class="yd-page publish-page safe-bottom">
    <view class="publish-intro">
      <view>
        <view class="intro-title">分享此刻的校园生活</view>
        <view class="intro-desc">真实、清楚的内容更容易获得回应</view>
      </view>
      <view class="school-chip">浙理工 <text>⌄</text></view>
    </view>

    <view class="type-scroll">
      <view class="type-list">
        <view v-for="item in campusPublishTypes" :key="item.key" class="type-item" :class="{ active: activeType === item.key }" @click="activeType = item.key">
          <view class="type-icon">{{ item.title.slice(0, 1) }}</view><text>{{ item.title }}</text>
        </view>
      </view>
    </view>

    <view class="form-card yd-card">
      <view class="form-section-head">
        <text>内容信息</text><text>必填项请认真填写</text>
      </view>
      <view class="field-label">
        上传图片 <text>最多 9 张，首图会成为封面</text>
      </view>
      <view class="uploader">
        <view v-for="(image, index) in images" :key="image" class="image-wrap">
          <image :src="image" mode="aspectFill" /><text @click="removeImage(index)">
            ×
          </text>
        </view>
        <view v-if="images.length < 9" class="add-image" @click="addImage">
          <text class="plus">
            ＋
          </text><text>添加实拍</text>
        </view>
      </view>

      <view class="field-group">
        <view class="field-label">
          标题 <text>{{ form.title.length }}/30</text>
        </view><input v-model="form.title" maxlength="30" class="field-input" :class="{ invalid: errors.title }" :placeholder="`${currentType.title}，一句话说清楚`"><view v-if="errors.title" class="error">
          {{ errors.title }}
        </view>
      </view>
      <view class="field-group">
        <view class="field-label">
          详细描述 <text>{{ form.content.length }}/500</text>
        </view><textarea v-model="form.content" maxlength="500" class="field-textarea" :class="{ invalid: errors.content }" placeholder="说说具体情况、时间、成色或注意事项，真实描述更容易获得回应…" /><view v-if="errors.content" class="error">
          {{ errors.content }}
        </view>
      </view>

      <view v-if="showPrice" class="field-group price-group">
        <view class="field-label">
          {{ activeType === 'ride' ? '人均费用' : '价格' }} <text>可不填</text>
        </view><view class="price-input">
          <text>¥</text><input v-model="form.price" type="digit" placeholder="0.00">
        </view><view v-if="errors.price" class="error">
          {{ errors.price }}
        </view>
      </view>

      <view class="field-group">
        <view class="field-label">
          标签 <text>最多选择 3 个</text>
        </view><view class="tag-list">
          <view v-for="tag in tagOptions" :key="tag" class="tag-chip" :class="{ active: form.tags.includes(tag) }" @click="form.tags.length < 3 || form.tags.includes(tag) ? toggleTag(tag) : null">
            # {{ tag }}
          </view>
        </view>
      </view>

      <view class="setting-row">
        <view>
          <view class="setting-title">
            <i class="location-mark" />校园位置
          </view><view class="setting-value">
            {{ form.location }}
          </view>
        </view><text>›</text>
      </view>
      <view class="setting-row">
        <view>
          <view class="setting-title">
            联系方式
          </view><view class="setting-value">
            仅回应后双方可见
          </view>
        </view><text>›</text>
      </view>
      <view class="setting-row">
        <view>
          <view class="setting-title">
            匿名发布
          </view><view class="setting-value">
            昵称将显示为“同校同学”
          </view>
        </view><switch :checked="form.anonymous" color="#16A085" @change="form.anonymous = $event.detail.value" />
      </view>
    </view>

    <view class="agreement" @click="agreed = !agreed">
      <view class="check" :class="{ checked: agreed }">
        {{ agreed ? '✓' : '' }}
      </view><text>我已阅读并同意《云点校园社区发布规范》</text>
    </view>
    <view v-if="errors.agreement" class="error agreement-error">
      {{ errors.agreement }}
    </view>
    <view class="submit-bar">
      <button class="draft-btn" @click="saveDraft">
        存草稿
      </button><button class="publish-btn" :loading="submitting" @click="submit">
        {{ submitting ? '发布中…' : '确认发布' }}
      </button>
    </view>

    <view v-if="showSuccess" class="success-mask">
      <view class="success-card">
        <view class="success-icon">
          ✓
        </view><view class="success-title">
          发布成功
        </view><view class="success-desc">
          内容正在同校社区展示，新的回应会通过消息通知你。
        </view><button class="yd-primary-btn" @click="uni.switchTab({ url: '/pages/index/index' })">
          查看内容
        </button><button class="again" @click="reset">
          再发一条
        </button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.publish-page {
  padding-top: 12rpx;
  background: #f7f7f3;
}
.publish-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 2rpx 22rpx;
}
.intro-title { color: #18201e; font-size: 32rpx; font-weight: 850; }
.intro-desc { margin-top: 6rpx; color: #7f8a86; font-size: 21rpx; }
.school-chip { padding: 13rpx 17rpx; border: 1rpx solid #dedfd9; border-radius: 999rpx; color: #0f766e; background: #fff; font-size: 21rpx; font-weight: 700; }
.school-chip text { margin-left: 4rpx; }
.type-scroll {
  margin-bottom: 20rpx;
}
.type-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}
.type-item {
  position: relative;
  display: flex;
  align-items: center;
  height: 82rpx;
  padding: 0 12rpx;
  border: 1rpx solid #e5e5df;
  border-radius: 18rpx;
  color: #596762;
  background: #fff;
  font-size: 20rpx;
}
.type-item.active {
  border-color: #16a085;
  color: #0f766e;
  background: #e8f5f1;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx #16a085;
}
.type-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 42rpx;
  height: 42rpx;
  margin-right: 9rpx;
  border-radius: 13rpx;
  color: #fff;
  background: #253a35;
  font-size: 20rpx;
  font-weight: 800;
}
.type-item.active .type-icon { background: #16a085; }
.form-card {
  padding: 25rpx 24rpx 8rpx;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 22rpx rgba(26, 49, 43, 0.04);
}
.form-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 25rpx; padding-bottom: 20rpx; border-bottom: 1rpx solid #efede7; }
.form-section-head text:first-child { color: #18201e; font-size: 29rpx; font-weight: 800; }
.form-section-head text:last-child { color: #a0a9a5; font-size: 19rpx; }
.field-group {
  margin-top: 28rpx;
}
.field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
  font-size: 25rpx;
  font-weight: 750;
}
.field-label text {
  color: #9aa39f;
  font-size: 20rpx;
  font-weight: 400;
}
.uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.image-wrap,
.add-image {
  position: relative;
  width: 150rpx;
  height: 150rpx;
  overflow: hidden;
  border-radius: 20rpx;
}
.image-wrap image {
  width: 100%;
  height: 100%;
}
.image-wrap > text {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
}
.add-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2rpx dashed #b9c7c2;
  color: #89938f;
  background: #f5faf8;
  font-size: 21rpx;
}
.plus {
  color: #16a085;
  font-size: 46rpx;
}
.field-input,
.field-textarea {
  width: 100%;
  border: 2rpx solid #ebeae4;
  border-radius: 18rpx;
  background: #fafaf7;
  font-size: 26rpx;
}
.field-input {
  height: 84rpx;
  padding: 0 22rpx;
}
.field-textarea {
  height: 230rpx;
  padding: 22rpx;
}
.invalid {
  border-color: #ff6b5e;
  background: #fff8f6;
}
.error {
  margin-top: 8rpx;
  color: #ff6b5e;
  font-size: 21rpx;
}
.price-input {
  display: flex;
  align-items: center;
  height: 84rpx;
  padding: 0 22rpx;
  border-radius: 18rpx;
  color: #ff6b5e;
  border: 2rpx solid #ffddd7;
  background: #fff8f6;
  font-size: 30rpx;
  font-weight: 800;
}
.price-input input {
  flex: 1;
  margin-left: 8rpx;
  color: #ff6b5e;
  font-size: 32rpx;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.tag-chip {
  padding: 11rpx 17rpx;
  border: 1rpx solid #e5e5df;
  border-radius: 999rpx;
  color: #75807c;
  background: #fff;
  font-size: 22rpx;
}
.tag-chip.active {
  border-color: #b8dfd6;
  color: #0f766e;
  background: #dff1ec;
  font-weight: 700;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 23rpx 2rpx;
  border-top: 1rpx solid #eeeae3;
}
.tag-list + .setting-row {
  margin-top: 28rpx;
}
.setting-title {
  display: flex;
  align-items: center;
  font-size: 26rpx;
  font-weight: 700;
}
.location-mark { display: inline-block; width: 13rpx; height: 13rpx; margin-right: 12rpx; border: 4rpx solid #16a085; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); }
.setting-value {
  margin-top: 6rpx;
  color: #929c98;
  font-size: 21rpx;
}
.setting-row > text {
  color: #9ba39f;
  font-size: 34rpx;
}
.agreement {
  display: flex;
  align-items: center;
  margin: 22rpx 8rpx 0;
  color: #7b8581;
  font-size: 21rpx;
}
.check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32rpx;
  height: 32rpx;
  margin-right: 10rpx;
  border: 2rpx solid #bac1bd;
  border-radius: 9rpx;
}
.check.checked {
  border-color: #16a085;
  color: #fff;
  background: #16a085;
}
.agreement-error {
  margin-left: 8rpx;
}
.submit-bar {
  position: sticky;
  z-index: 8;
  bottom: calc(18rpx + env(safe-area-inset-bottom));
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 16rpx;
  margin: 22rpx -4rpx 0;
  padding: 12rpx;
  border: 1rpx solid rgba(229, 229, 223, 0.9);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12rpx 34rpx rgba(26, 49, 43, 0.12);
}
.submit-bar button {
  height: 88rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 800;
}
.draft-btn {
  color: #0f766e;
  background: #eef6f3;
}
.publish-btn {
  color: #fff;
  background: #118b79;
}
.success-mask {
  position: fixed;
  z-index: 20;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 44rpx;
  background: rgba(24, 32, 30, 0.48);
}
.success-card {
  width: 100%;
  padding: 46rpx 36rpx 32rpx;
  border-radius: 32rpx;
  background: #fff;
  text-align: center;
}
.success-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100rpx;
  height: 100rpx;
  margin: 0 auto;
  border-radius: 34rpx;
  color: #fff;
  background: #16a085;
  font-size: 46rpx;
  font-weight: 900;
}
.success-title {
  margin-top: 24rpx;
  font-size: 36rpx;
  font-weight: 900;
}
.success-desc {
  margin: 12rpx 0 28rpx;
  color: #77827e;
  font-size: 24rpx;
  line-height: 1.55;
}
.again {
  margin-top: 12rpx;
  color: #0f766e;
  background: transparent;
  font-size: 25rpx;
}
</style>
