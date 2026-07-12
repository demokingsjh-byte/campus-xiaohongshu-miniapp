<script lang="ts" setup>
import { uploadCampusAvatar } from '@/services/api/file';
import { useUserStore } from '@/stores/modules/user';

const step = ref<'login' | 'profile' | 'done'>('login');
const editing = ref(false);
const agreed = ref(true);
const loading = ref(false);
const avatarUploading = ref(false);
const userStore = useUserStore();
const campusOptions = ['下沙校区', '临平校区', '延安路校区'];
const gradeOptions = ['2026级', '2025级', '2024级', '2023级', '2022级及以前', '研究生'];
const genderOptions = ['不公开', '男', '女'];
const form = reactive({ avatar: '', nickname: '', schoolName: '浙江理工大学', campusName: '下沙校区', grade: '2023级', gender: '不公开', roleType: 'student' });

function fillForm() {
  const user = userStore.userInfo;
  if (!user)
    return;
  form.avatar = user.avatar || '';
  form.nickname = user.nickname === '校园体验用户' && !editing.value ? '' : (user.nickname || '');
  form.schoolName = user.schoolName || form.schoolName;
  form.campusName = user.campusName || form.campusName;
  form.grade = user.grade || form.grade;
  form.gender = user.gender || form.gender;
}

onLoad(async (query) => {
  editing.value = query?.mode === 'edit';
  if (!editing.value)
    return;
  if (!uni.getStorageSync('yd-demo-login')) {
    editing.value = false;
    return;
  }
  step.value = 'profile';
  if (!userStore.userInfo) {
    try {
      await userStore.initUserInfo();
    } catch {}
  }
  fillForm();
});

async function loginDemo() {
  if (!agreed.value) { uni.showToast({ title: '请先同意用户协议与隐私政策', icon: 'none' }); return; }
  loading.value = true;
  try {
    await userStore.silentLogin({ tenantId: 1 });
    fillForm();
    step.value = 'profile';
  } catch {
    uni.showToast({ title: '微信登录失败，请稍后重试', icon: 'none' });
  } finally {
    loading.value = false;
  }
}
async function chooseAvatar(event: any) {
  const avatarUrl = event?.detail?.avatarUrl;
  if (!avatarUrl)
    return;
  avatarUploading.value = true;
  try {
    form.avatar = await uploadCampusAvatar(avatarUrl);
    uni.showToast({ title: '头像已获取', icon: 'success' });
  } catch {
    uni.showToast({ title: '头像上传失败，请重试', icon: 'none' });
  } finally {
    avatarUploading.value = false;
  }
}
async function finishProfile() {
  if (!form.avatar) { uni.showToast({ title: '请选择微信头像', icon: 'none' }); return; }
  if (!form.nickname || !form.schoolName || !form.campusName) { uni.showToast({ title: '请完善昵称、学校和校区', icon: 'none' }); return; }
  loading.value = true;
  try {
    await userStore.updateProfile(form);
    uni.setStorageSync('yd-demo-login', '1');
    step.value = 'done';
  } catch {
    uni.showToast({ title: '资料保存失败，请稍后重试', icon: 'none' });
  } finally {
    loading.value = false;
  }
}
function selectOption(key: 'campusName' | 'grade' | 'gender', options: string[], event: any) { form[key] = options[Number(event.detail.value)]; }
function finish() {
  setTimeout(() => {
    if (editing.value)
      uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/about/index' }) });
    else
      uni.switchTab({ url: '/pages/about/index' });
  }, 500);
}
</script>

<template>
  <view class="login-page">
    <view class="login-status" /><view class="back" @click="uni.navigateBack()">
      ‹
    </view>
    <view v-if="step === 'login'" class="login-content">
      <view class="logo">
        云
      </view><view class="title">
        欢迎来到云点校园
      </view><view class="subtitle">
        同校同学的真实生活社区
      </view>
      <view class="feature-list">
        <view><text>♻</text><span><b>闲置好物，就近交易</b><i>同校自提更放心</i></span></view><view><text>🙌</text><span><b>校园互助，及时回应</b><i>问题总有同学懂</i></span></view><view><text>🎉</text><span><b>活动与新鲜事</b><i>不错过校园每个瞬间</i></span></view>
      </view>
      <button class="wechat-btn" :loading="loading" @click="loginDemo">
        微信一键登录
      </button>
      <view class="privacy" @click="agreed = !agreed">
        <view :class="{ checked: agreed }">
          {{ agreed ? '✓' : '' }}
        </view><text>已阅读并同意《用户协议》和《隐私政策》</text>
      </view>
    </view>

    <view v-else-if="step === 'profile'" class="profile-content">
      <view class="step-note">
        {{ editing ? '编辑资料' : '第 2 步 / 2' }}
      </view><view class="title left">
        {{ editing ? '修改校园资料' : '完善校园资料' }}
      </view><view class="subtitle left">
        资料仅用于匹配同校内容，可随时修改
      </view>
      <button class="avatar-upload" open-type="chooseAvatar" :loading="avatarUploading" @chooseavatar="chooseAvatar">
        <image v-if="form.avatar" :src="form.avatar" mode="aspectFill" />
        <view v-else>云</view><text>{{ form.avatar ? '更换微信头像' : '选择微信头像' }}</text>
      </button>
      <view class="profile-form">
        <label>昵称<input v-model="form.nickname" type="nickname" placeholder="请输入微信昵称"></label>
        <label>学校<view class="picker">{{ form.schoolName }}</view></label>
        <picker :range="campusOptions" @change="selectOption('campusName', campusOptions, $event)"><label>校区<view class="picker">{{ form.campusName }} <text>›</text></view></label></picker>
        <picker :range="gradeOptions" @change="selectOption('grade', gradeOptions, $event)"><label>年级<view class="picker">{{ form.grade }} <text>›</text></view></label></picker>
        <picker :range="genderOptions" @change="selectOption('gender', genderOptions, $event)"><label>性别（选填）<view class="picker">{{ form.gender }} <text>›</text></view></label></picker>
      </view>
      <button class="wechat-btn" :loading="loading" @click="finishProfile">
        {{ editing ? '保存修改' : '完成并进入校园' }}
      </button>
    </view>

    <view v-else class="done-content">
      <view class="done-icon">
        ✓
      </view><view class="title">
        {{ editing ? '资料已更新' : '资料已完善' }}
      </view><view class="subtitle">
        正在带你进入浙江理工大学校园社区
      </view><button class="wechat-btn" @click="finish">
        {{ editing ? '返回我的' : '开始逛校园' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  padding: 0 44rpx 44rpx;
  background: #faf8f3;
}
.login-status {
  height: calc(28rpx + env(safe-area-inset-top));
}
.back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66rpx;
  height: 66rpx;
  border-radius: 22rpx;
  background: #fff;
  font-size: 46rpx;
}
.login-content,
.done-content {
  padding-top: 62rpx;
  text-align: center;
}
.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 118rpx;
  height: 118rpx;
  margin: 0 auto;
  border-radius: 38rpx;
  color: #fff;
  background: #16a085;
  box-shadow: 0 18rpx 34rpx rgba(22, 160, 133, 0.2);
  font-size: 48rpx;
  font-weight: 900;
}
.title {
  margin-top: 30rpx;
  font-size: 42rpx;
  font-weight: 900;
}
.title.left,
.subtitle.left {
  text-align: left;
}
.subtitle {
  margin-top: 10rpx;
  color: #7f8985;
  font-size: 25rpx;
}
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 58rpx;
  text-align: left;
}
.feature-list > view {
  display: flex;
  align-items: center;
  padding: 22rpx;
  border-radius: 22rpx;
  background: #fff;
}
.feature-list > view > text {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  background: #e8f5f1;
  font-size: 34rpx;
}
.feature-list span {
  display: flex;
  flex-direction: column;
  margin-left: 18rpx;
}
.feature-list b {
  font-size: 26rpx;
}
.feature-list i {
  margin-top: 5rpx;
  color: #8e9894;
  font-size: 21rpx;
  font-style: normal;
}
.wechat-btn {
  width: 100%;
  height: 90rpx;
  margin-top: 48rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #16a085;
  font-size: 29rpx;
  font-weight: 800;
}
.privacy {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 22rpx;
  color: #8b9591;
  font-size: 20rpx;
}
.privacy > view {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28rpx;
  height: 28rpx;
  margin-right: 8rpx;
  border: 2rpx solid #b6bfbb;
  border-radius: 8rpx;
}
.privacy .checked {
  border-color: #16a085;
  color: #fff;
  background: #16a085;
}
.profile-content {
  padding-top: 22rpx;
}
.step-note {
  color: #16a085;
  font-size: 22rpx;
  font-weight: 700;
}
.avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 32rpx 0;
  color: #16a085;
  font-size: 21rpx;
  line-height: 1.4;
  background: transparent;
}
.avatar-upload > view,
.avatar-upload > image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 110rpx;
  height: 110rpx;
  margin-bottom: 10rpx;
  border-radius: 50%;
  color: #174f48;
  background: #dcefe9;
  font-size: 38rpx;
  font-weight: 900;
}
.avatar-upload > image { display: block; }
.profile-form {
  overflow: hidden;
  border-radius: 24rpx;
  background: #fff;
}
.profile-form label {
  display: flex;
  align-items: center;
  min-height: 94rpx;
  padding: 0 24rpx;
  border-bottom: 1rpx solid #efebe5;
  font-size: 25rpx;
  font-weight: 700;
}
.profile-form input,
.picker {
  flex: 1;
  margin-left: 30rpx;
  color: #5f6b67;
  font-size: 25rpx;
  font-weight: 400;
  text-align: right;
}
.picker {
  display: flex;
  justify-content: flex-end;
  gap: 10rpx;
}
.skip {
  margin-top: 12rpx;
  color: #7c8782;
  background: transparent;
  font-size: 24rpx;
}
.done-content {
  padding-top: 180rpx;
}
.done-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  margin: 0 auto;
  border-radius: 40rpx;
  color: #fff;
  background: #16a085;
  font-size: 54rpx;
  font-weight: 900;
}
</style>
