<script lang="ts" setup>
import { campusTenants, getDefaultTenant } from '@/mock/campus';
import { uploadCampusAvatar } from '@/services/api/file';
import { useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import { hasCurrentPrivacyConsent, openPolicyPage, recordPrivacyConsent } from '@/utils/privacy';

const step = ref<'login' | 'profile' | 'done'>('login');
const editing = ref(false);
const agreed = ref(hasCurrentPrivacyConsent());
const loading = ref(false);
const loginError = ref('');
const avatarUploading = ref(false);
const phoneBinding = ref(false);
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
const mobileBound = computed(() => Boolean(userStore.userInfo?.mobileBound || userStore.userInfo?.mobile));
const mobileLabel = computed(() => {
  const mobile = userStore.userInfo?.mobile || '';
  return mobile ? `${mobile.slice(0, 3)}****${mobile.slice(-4)}` : '用于账号安全与必要联系';
});
const navigationTitle = computed(() => {
  if (editing.value)
    return '编辑资料';
  if (step.value === 'profile')
    return '完善校园资料';
  if (step.value === 'done')
    return '资料已完成';
  return '微信登录';
});

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

function syncTenantFromProfile() {
  const matchedTenant = campusTenants.find(item => item.name === userStore.userInfo?.schoolName);
  if (matchedTenant)
    tenantStore.selectTenant(matchedTenant);
}

function returnToPreviousPage(delay = 0) {
  setTimeout(() => {
    uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/about/index' }) });
  }, delay);
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
    recordPrivacyConsent();
    const success = await userStore.silentLogin({ tenantId: initialTenant.id });
    if (!success)
      throw new Error('未完成微信登录');
    fillForm();
    if (userStore.profileCompleted && mobileBound.value) {
      syncTenantFromProfile();
      uni.showToast({ title: '登录成功', icon: 'success' });
      returnToPreviousPage(450);
      return;
    }
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
async function bindPhone(event: any) {
  const phoneCode = event?.detail?.code;
  if (!phoneCode) {
    const denied = /deny|cancel/i.test(event?.detail?.errMsg || '');
    uni.showToast({ title: denied ? '你已取消手机号授权' : '未获取到手机号授权信息', icon: 'none' });
    return;
  }
  phoneBinding.value = true;
  try {
    await userStore.bindPhone(phoneCode);
    uni.showToast({ title: '手机号已绑定', icon: 'success' });
  } catch {
    uni.showToast({ title: '手机号绑定失败，请重试', icon: 'none' });
  } finally {
    phoneBinding.value = false;
  }
}
async function finishProfile() {
  if (!form.avatar) {
    uni.showToast({ title: '请选择微信头像', icon: 'none' });
    return;
  }
  if (!mobileBound.value) {
    uni.showToast({ title: '请先授权微信手机号', icon: 'none' });
    return;
  }
  if (!form.nickname || !form.schoolName || !form.campusName) {
    uni.showToast({ title: '请完善昵称、学校和校区', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await userStore.updateProfile(form);
    syncTenantFromProfile();
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
  returnToPreviousPage(500);
}

function openPolicy(type: 'privacy' | 'agreement') {
  openPolicyPage(type);
}
</script>

<template>
  <view class="login-page">
    <view class="login-status" /><view class="login-nav">
      <view class="back" @click="uni.navigateBack()">
        ‹
      </view><text>{{ navigationTitle }}</text>
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
      <view class="privacy">
        <view class="privacy-check" :class="{ checked: agreed }" @click="agreed = !agreed">
          {{ agreed ? '✓' : '' }}
        </view><text @click="agreed = !agreed">
          我已阅读并同意
        </text><text class="privacy-link" @click.stop="openPolicy('agreement')">
          《用户协议》
        </text><text>和</text><text class="privacy-link" @click.stop="openPolicy('privacy')">
          《隐私政策》
        </text>
      </view>
      <view class="guest-tip">
        <text @click="uni.navigateBack()">
          暂不登录，先逛逛校园
        </text><text>游客可以浏览公开内容 ›</text>
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
        </view><text>{{ form.avatar ? '更换微信头像' : '点击选择微信头像' }}</text>
      </button>
      <button class="phone-bind" open-type="getPhoneNumber" :loading="phoneBinding" @getphonenumber="bindPhone">
        <view class="phone-icon">
          手
        </view><view class="phone-copy">
          <text>{{ mobileBound ? '微信手机号已绑定' : '授权微信手机号' }}</text><text>{{ mobileLabel }}</text>
        </view><text class="phone-state">
          {{ mobileBound ? '已完成' : '去授权' }}
        </text>
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
  padding: 0 38rpx 44rpx;
  background: var(--yd-paper);
}
.login-status {
  height: calc(28rpx + env(safe-area-inset-top));
}
.back {
  position: absolute;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 14rpx;
  background: var(--yd-card);
  font-size: 46rpx;
}
.login-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 84rpx;
  color: var(--yd-ink);
  font-size: 30rpx;
  font-weight: 800;
}
.login-content,
.done-content {
  padding-top: 20rpx;
  text-align: center;
}
.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96rpx;
  height: 96rpx;
  margin: 0 auto;
  border-radius: 32rpx 32rpx 32rpx 9rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 10rpx 12rpx 0 #c9dfd6;
  font-size: 42rpx;
  font-weight: 900;
}
.title {
  margin-top: 24rpx;
  font-size: 38rpx;
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
  gap: 0;
  margin-top: 38rpx;
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 18rpx 44rpx rgba(33, 50, 86, 0.085);
  text-align: left;
}
.feature-list > view {
  display: flex;
  align-items: center;
  padding: 16rpx 20rpx;
  border: 0;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.1);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.feature-list > view:last-child {
  border-bottom: 0;
}
.feature-list > view > text {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60rpx;
  height: 60rpx;
  border-radius: 14rpx 14rpx 14rpx 4rpx;
  background: var(--yd-mint);
  font-size: 29rpx;
}
.feature-list span {
  display: flex;
  flex-direction: column;
  margin-left: 18rpx;
}
.feature-list b {
  font-size: 24rpx;
}
.feature-list i {
  margin-top: 5rpx;
  color: #8e9894;
  font-size: 19rpx;
  font-style: normal;
}
.wechat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: var(--yd-control-large);
  margin-top: 28rpx;
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
.privacy-check {
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
.privacy-link {
  color: var(--yd-green);
  font-weight: 650;
}
.guest-tip {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  flex-direction: column;
  color: #a0a7b2;
  font-size: 19rpx;
  line-height: 1.5;
  text-align: center;
}
.guest-tip text:first-child {
  color: #52615c;
  font-size: 22rpx;
  font-weight: 700;
}
.guest-tip text:last-child {
  margin-top: 4rpx;
  font-size: 18rpx;
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
  margin: 24rpx 0 16rpx;
  padding: 0;
  color: var(--yd-green);
  font-size: 21rpx;
  line-height: 1.4;
  background: transparent;
}
.phone-bind {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 104rpx;
  margin: 0 0 18rpx;
  padding: 14rpx 18rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 20rpx;
  color: var(--yd-ink);
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 12rpx 30rpx rgba(33, 50, 86, 0.07);
  line-height: 1.35;
  text-align: left;
}
.phone-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 58rpx;
  height: 58rpx;
  border-radius: 18rpx;
  color: var(--yd-green-dark);
  background: rgba(10, 132, 255, 0.1);
  font-size: 21rpx;
  font-weight: 800;
}
.phone-copy {
  display: flex;
  min-width: 0;
  margin-left: 14rpx;
  flex: 1;
  flex-direction: column;
}
.phone-copy text:first-child {
  font-size: 24rpx;
  font-weight: 750;
}
.phone-copy text:last-child {
  margin-top: 4rpx;
  color: #8d9693;
  font-size: 19rpx;
}
.phone-state {
  flex: 0 0 auto;
  color: var(--yd-green);
  font-size: 20rpx;
  font-weight: 700;
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
.feature-list > view {
  border-radius: 0;
}
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
