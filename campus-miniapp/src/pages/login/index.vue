<script lang="ts" setup>
import { campusTenants, getDefaultTenant } from '@/mock/campus';
import { uploadCampusAvatar } from '@/services/api/file';
import { useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const step = ref<'login' | 'profile' | 'done'>('login');
const editing = ref(false);
const agreed = ref(true);
const loading = ref(false);
const loginError = ref('');
const avatarUploading = ref(false);
const userStore = useUserStore();
const tenantStore = useTenantStore();
const gradeOptions = ['2026级', '2025级', '2024级', '2023级', '2022级及以前', '研究生'];
const genderOptions = ['不公开', '男', '女'];
const initialTenant = tenantStore.currentTenant && campusTenants.some(item => item.id === tenantStore.tenantId)
  ? tenantStore.currentTenant
  : getDefaultTenant();
const form = reactive({ avatar: '', nickname: '', schoolName: initialTenant.name, campusName: initialTenant.name === '吉首大学' ? '吉首校区' : '主校区', grade: '2023级', gender: '不公开', roleType: 'student' });
const schoolOptions = campusTenants.map(item => item.name);
const campusOptions = computed(() => form.schoolName === '吉首大学' ? ['吉首校区'] : ['主校区']);

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
  if (!userStore.userInfo) {
    try {
      await userStore.initUserInfo();
    } catch {
      editing.value = false;
      return;
    }
  }
  step.value = 'profile';
  fillForm();
});

async function loginDemo() {
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议与隐私政策', icon: 'none' });
    return;
  }
  loginError.value = '';
  loading.value = true;
  try {
    const success = await userStore.silentLogin({ tenantId: initialTenant.id });
    if (!success)
      throw new Error('未完成微信登录');
    fillForm();
    step.value = 'profile';
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error || '');
    if (/domain|合法域名|url not in domain list|request:fail/i.test(message)) {
      loginError.value = '当前网络域名未完成微信配置，请联系管理员';
    } else if (/login:fail|登录凭证|未返回 code|appid/i.test(message)) {
      loginError.value = '微信登录凭证获取失败，请重新编译后再试';
    } else if (/系统异常|\[500\]/.test(message)) {
      loginError.value = '登录服务暂时繁忙，请稍后再试';
    } else {
      loginError.value = '微信登录失败，请检查网络后重试';
    }
    uni.showToast({ title: loginError.value, icon: 'none', duration: 3000 });
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
  if (!form.avatar) {
    uni.showToast({ title: '请选择微信头像', icon: 'none' });
    return;
  }
  if (!form.nickname || !form.schoolName || !form.campusName) {
    uni.showToast({ title: '请完善昵称、学校和校区', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await userStore.updateProfile(form);
    const matchedTenant = campusTenants.find(item => item.name === form.schoolName);
    if (matchedTenant)
      tenantStore.selectTenant(matchedTenant);
    step.value = 'done';
  } catch {
    uni.showToast({ title: '资料保存失败，请稍后重试', icon: 'none' });
  } finally {
    loading.value = false;
  }
}
function selectOption(key: 'campusName' | 'grade' | 'gender', options: string[], event: any) {
  form[key] = options[Number(event.detail.value)];
}
function selectSchool(event: any) {
  form.schoolName = schoolOptions[Number(event.detail.value)];
  form.campusName = form.schoolName === '吉首大学' ? '吉首校区' : '主校区';
}
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
        <view><text>♻</text><span><b>闲置好物，就近交易</b><i>同校自提更放心</i></span></view>
        <view><text>🙌</text><span><b>校园互助，及时回应</b><i>问题总有同学懂</i></span></view>
        <view><text>🎉</text><span><b>活动与新鲜事</b><i>不错过校园每个瞬间</i></span></view>
      </view>
      <button class="wechat-btn" :disabled="loading" @click="loginDemo">
        <view class="button-inner">
          <view v-if="loading" class="button-spinner" />
          <view v-else class="wechat-symbol">
            微
          </view>
          <text>{{ loading ? '正在安全登录…' : '微信一键登录' }}</text>
        </view>
      </button>
      <view v-if="loginError" class="login-error" @click="loginDemo">
        <text class="login-error-icon">
          !
        </text><text>
          {{ loginError }}
        </text><text class="retry">
          重试
        </text>
      </view>
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
        <view v-else>
          云
        </view><text>{{ form.avatar ? '更换微信头像' : '选择微信头像' }}</text>
      </button>
      <view class="profile-form">
        <label>昵称<input v-model="form.nickname" type="nickname" placeholder="请输入微信昵称"></label>
        <picker :range="schoolOptions" @change="selectSchool">
          <label>学校<view class="picker">{{ form.schoolName }} <text>›</text></view></label>
        </picker>
        <picker :range="campusOptions" @change="selectOption('campusName', campusOptions, $event)">
          <label>校区<view class="picker">{{ form.campusName }} <text>›</text></view></label>
        </picker>
        <picker :range="gradeOptions" @change="selectOption('grade', gradeOptions, $event)">
          <label>年级<view class="picker">{{ form.grade }} <text>›</text></view></label>
        </picker>
        <picker :range="genderOptions" @change="selectOption('gender', genderOptions, $event)">
          <label>性别（选填）<view class="picker">{{ form.gender }} <text>›</text></view></label>
        </picker>
      </view>
      <button class="wechat-btn" :disabled="loading" @click="finishProfile">
        <view class="button-inner">
          <view v-if="loading" class="button-spinner" />
          <text>{{ loading ? '正在保存…' : (editing ? '保存修改' : '完成并进入校园') }}</text>
        </view>
      </button>
    </view>

    <view v-else class="done-content">
      <view class="done-icon">
        ✓
      </view><view class="title">
        {{ editing ? '资料已更新' : '资料已完善' }}
      </view><view class="subtitle">
        正在带你进入{{ form.schoolName }}校园社区
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
  background: var(--yd-paper);
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
  border: 1rpx solid var(--yd-line);
  border-radius: 14rpx;
  background: var(--yd-card);
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
  border-radius: 32rpx 32rpx 32rpx 9rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 10rpx 12rpx 0 #c9dfd6;
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
  border: 1rpx solid var(--yd-line);
  border-radius: 16rpx;
  background: var(--yd-card);
  box-shadow: 5rpx 6rpx 0 rgba(75, 59, 44, 0.035);
}
.feature-list > view > text {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 14rpx 14rpx 14rpx 4rpx;
  background: var(--yd-mint);
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: var(--yd-control-large);
  margin-top: 40rpx;
  padding: 0 34rpx;
  border: 1rpx solid rgba(10, 132, 255, 0.16);
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 14rpx 32rpx rgba(10, 132, 255, 0.25);
  font-size: 28rpx;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0.6rpx;
}
.wechat-btn:active {
  opacity: 0.9;
  transform: scale(0.985);
}
.wechat-btn[disabled] {
  opacity: 0.72;
  box-shadow: 0 8rpx 18rpx rgba(10, 132, 255, 0.16);
}
.button-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
}
.wechat-symbol {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38rpx;
  height: 38rpx;
  border-radius: 13rpx;
  color: var(--yd-green);
  background: var(--yd-card);
  font-size: 20rpx;
  font-weight: 800;
  line-height: 1;
}
.button-spinner {
  width: 28rpx;
  height: 28rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.38);
  border-top-color: #fff;
  border-radius: 50%;
  animation: button-spin 0.8s linear infinite;
}
@keyframes button-spin {
  to {
    transform: rotate(360deg);
  }
}
.login-error {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-height: 64rpx;
  margin-top: 18rpx;
  padding: 12rpx 16rpx;
  border: 1rpx solid rgba(227, 101, 85, 0.18);
  border-radius: 16rpx;
  color: #8f463e;
  background: var(--yd-coral-soft);
  font-size: 22rpx;
  line-height: 1.4;
  text-align: left;
}
.login-error-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  color: #fff;
  background: #e36555;
  font-size: 19rpx;
  font-weight: 800;
}
.login-error .retry {
  flex: 0 0 auto;
  margin-left: auto;
  color: #c44f42;
  font-weight: 700;
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
  border-color: var(--yd-green);
  color: #fff;
  background: var(--yd-green);
}
.profile-content {
  padding-top: 22rpx;
}
.step-note {
  color: var(--yd-coral);
  font-size: 22rpx;
  font-weight: 700;
}
.avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 150rpx;
  margin: 32rpx 0;
  padding: 0;
  color: var(--yd-green);
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
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-size: 38rpx;
  font-weight: 900;
}
.avatar-upload > image {
  display: block;
}
.profile-form {
  overflow: hidden;
  border: 1rpx solid var(--yd-line);
  border-radius: 18rpx;
  background: var(--yd-card);
  box-shadow: 0 5rpx 0 rgba(75, 59, 44, 0.035);
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
  border-radius: 32rpx 32rpx 32rpx 9rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 10rpx 12rpx 0 #c9dfd6;
  font-size: 54rpx;
  font-weight: 900;
}

/* Apple-inspired glass theme */
.back,
.feature-list > view,
.profile-form,
.login-error {
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 18rpx 44rpx rgba(33, 50, 86, 0.085);
  backdrop-filter: blur(28rpx) saturate(155%);
  -webkit-backdrop-filter: blur(28rpx) saturate(155%);
}
.logo,
.done-icon {
  border-radius: 30rpx;
  background: var(--yd-green);
  box-shadow: 0 18rpx 38rpx rgba(10, 132, 255, 0.24);
}
.wechat-btn {
  border: 1rpx solid rgba(255, 255, 255, 0.32);
  border-radius: var(--yd-control-radius);
  background: var(--yd-green);
  box-shadow: 0 14rpx 32rpx rgba(10, 132, 255, 0.25);
}
.feature-list > view,
.profile-form {
  border-radius: 24rpx;
}
.feature-list > view > text,
.avatar-upload > view,
.picker,
.profile-form input {
  background: rgba(118, 118, 128, 0.08);
}
</style>
