<script lang="ts" setup>
import { campusPublishTypes, getDefaultTenant } from '@/mock/campus';
import { uploadCampusPostImage } from '@/services/api/file';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';

const activeType = ref('idle');
const images = ref<string[]>([]);
const submitting = ref(false);
const showSuccess = ref(false);
const createdPostId = ref<number | null>(null);
const publishedSummary = ref<{ typeTitle: string, location: string, visibleRange: string } | null>(null);
const agreed = ref(true);
const errors = reactive<Record<string, string>>({});
const userStore = useUserStore();
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
const currentTenant = computed(() => tenantStore.currentTenant || getDefaultTenant());
const schoolName = computed(() => currentTenant.value.name);
const locations = computed(() => [`${schoolName.value} · 校内`, `${schoolName.value} · 宿舍区`, `${schoolName.value} · 校门口`]);
const visibleRanges = ['仅本校可见', '同城高校可见', '所有人可见'];
const form = reactive({
  title: '',
  price: '',
  originalPrice: '',
  content: '',
  location: locations.value[0],
  tags: [] as string[],
  contact: '',
  tradeMode: '校内自提',
  visibleRange: visibleRanges[0],
  anonymous: false,
});
watch(schoolName, () => {
  if (!form.location.startsWith(schoolName.value))
    form.location = locations.value[0];
});

const typeDetails: Record<string, { eyebrow: string, hint: string, placeholder: string, tags: string[], modes: string[] }> = {
  idle: { eyebrow: '闲置交易', hint: '真实图片和成色描述能更快成交', placeholder: '品牌、型号、成色、购买时间、瑕疵和取货方式...', tags: ['宿舍自提', '可小刀', '九成新', '毕业出', '校内交易'], modes: ['校内自提', '送到宿舍', '快递邮寄'] },
  help: { eyebrow: '同学互助', hint: '把时间、地点和具体需求写清楚', placeholder: '需要什么帮助、截止时间、地点和答谢方式...', tags: ['急', '有偿', '奶茶答谢', '今天', '女生优先'], modes: ['线上回应', '当面帮助'] },
  ride: { eyebrow: '拼车出行', hint: '请注明时间、路线、人数和行李情况', placeholder: '出发时间、上车点、目的地、空位和行李要求...', tags: ['高铁站', '火车站', '女生优先', '可带行李', '周末'], modes: ['费用均摊', '司机接单'] },
  shop: { eyebrow: '校园探店', hint: '实拍、价格和真实体验最有参考价值', placeholder: '推荐菜品、价格、排队情况、位置和真实感受...', tags: ['学生折扣', '人均30', '东门', '不踩雷', '适合聚餐'], modes: ['到店消费', '外卖可点'] },
  lost: { eyebrow: '失物招领', hint: '避免公开证件号码等敏感信息', placeholder: '丢失或捡到的物品、时间、地点和辨认特征...', tags: ['急', '校园卡', '图书馆', '已交服务台', '求扩散'], modes: ['失物寻找', '物品招领'] },
  club: { eyebrow: '社团活动', hint: '活动时间、地点和报名方式要完整', placeholder: '活动主题、时间地点、适合人群、名额和报名方式...', tags: ['社团活动', '零基础', '免费', '周末', '招募中'], modes: ['线上报名', '现场参与'] },
};

const currentType = computed(() => campusPublishTypes.find(item => item.key === activeType.value)!);
const currentDetail = computed(() => typeDetails[activeType.value]);
const showPrice = computed(() => ['idle', 'ride', 'shop'].includes(activeType.value));
const requiresImage = computed(() => ['idle', 'shop', 'lost'].includes(activeType.value));
const completionScore = computed(() => {
  let score = 10;
  if (images.value.length)
    score += 30;
  if (form.title.trim().length >= 4)
    score += 20;
  if (form.content.trim().length >= 10)
    score += 25;
  if (form.tags.length)
    score += 10;
  if (form.contact.trim())
    score += 5;
  return Math.min(score, 100);
});

onLoad(() => {
  const draft = uni.getStorageSync('campus-publish-draft');
  if (!draft || typeof draft !== 'object')
    return;
  const draftType = campusPublishTypes.some(item => item.key === draft.activeType) ? draft.activeType : 'idle';
  activeType.value = draftType;
  Object.assign(form, {
    title: draft.title || '',
    price: draft.price || '',
    originalPrice: draft.originalPrice || '',
    content: draft.content || '',
    location: locations.value.includes(draft.location) ? draft.location : locations.value[0],
    tags: Array.isArray(draft.tags) ? draft.tags.slice(0, 3) : [],
    contact: draft.contact || '',
    tradeMode: typeDetails[draftType].modes.includes(draft.tradeMode) ? draft.tradeMode : typeDetails[draftType].modes[0],
    visibleRange: visibleRanges.includes(draft.visibleRange) ? draft.visibleRange : visibleRanges[0],
    anonymous: Boolean(draft.anonymous),
  });
  images.value = Array.isArray(draft.images) ? draft.images.slice(0, 9) : [];
  uni.showToast({ title: '已恢复上次草稿', icon: 'none' });
});

function chooseType(key: string) {
  activeType.value = key;
  form.tags = [];
  form.tradeMode = typeDetails[key].modes[0];
  Object.keys(errors).forEach(keyName => errors[keyName] = '');
}
function addImage() {
  if (images.value.length >= 9)
    return;
  uni.chooseImage({ count: 9 - images.value.length, sizeType: ['compressed'], success: res => images.value.push(...res.tempFilePaths) });
}
function removeImage(index: number) {
  images.value.splice(index, 1);
}
function setCover(index: number) {
  if (index === 0)
    return;
  const selected = images.value[index];
  images.value.splice(index, 1);
  images.value.unshift(selected);
  uni.showToast({ title: '已设为封面', icon: 'none' });
}
function toggleTag(tag: string) {
  const index = form.tags.indexOf(tag);
  if (index >= 0)
    form.tags.splice(index, 1);
  else if (form.tags.length < 3)
    form.tags.push(tag);
  else
    uni.showToast({ title: '最多选择 3 个标签', icon: 'none' });
}
function selectFrom(key: 'location' | 'visibleRange', options: string[], event: any) {
  form[key] = options[Number(event.detail.value)];
}
function validate() {
  errors.images = requiresImage.value && !images.value.length ? '请至少上传 1 张真实图片' : '';
  errors.title = form.title.trim().length >= 4 ? '' : '标题至少填写 4 个字';
  errors.content = form.content.trim().length >= 10 ? '' : '详细描述至少填写 10 个字';
  errors.price = showPrice.value && form.price && Number(form.price) <= 0 ? '价格需要大于 0' : '';
  errors.agreement = agreed.value ? '' : '请先同意社区发布规范';
  return !Object.values(errors).some(Boolean);
}
function preview() {
  if (!form.title.trim()) {
    uni.showToast({ title: '先写一个标题再预览', icon: 'none' });
    return;
  }
  uni.showModal({ title: '发布预览', content: `${currentType.value.title}\n${form.title}\n${form.price ? `¥${form.price}` : '免费 / 面议'} · ${form.location}`, showCancel: false });
}
function saveDraft() {
  uni.setStorageSync('campus-publish-draft', { ...form, images: images.value, activeType: activeType.value });
  uni.showToast({ title: '草稿已保存', icon: 'none' });
}
function clearEditor() {
  activeType.value = 'idle';
  Object.assign(form, {
    title: '',
    price: '',
    originalPrice: '',
    content: '',
    location: locations.value[0],
    tags: [],
    contact: '',
    tradeMode: typeDetails.idle.modes[0],
    visibleRange: visibleRanges[0],
    anonymous: false,
  });
  images.value = [];
  agreed.value = true;
  Object.keys(errors).forEach(key => errors[key] = '');
  uni.removeStorageSync('campus-publish-draft');
}
async function submit() {
  if (submitting.value)
    return;
  if (!userStore.loggedIn) {
    uni.showModal({
      title: '登录后才能发布',
      content: '登录并完善校园资料后，内容会优先展示给同校同学。',
      confirmText: '去登录',
      success: res => res.confirm && uni.navigateTo({ url: '/pages/login/index' }),
    });
    return;
  }
  if (!userStore.userInfo?.schoolName || !userStore.userInfo?.campusName) {
    uni.showModal({
      title: '先完善校园资料',
      content: '学校和校区会用于确认内容归属，完善后即可发布。',
      confirmText: '去完善',
      success: res => res.confirm && uni.navigateTo({ url: '/pages/login/index?mode=edit' }),
    });
    return;
  }
  if (!validate()) {
    uni.showToast({ title: '还有必填内容未完成', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const uploadedImages = await Promise.all(images.value.map((image) => {
      if (/^https?:\/\//i.test(image))
        return image;
      return uploadCampusPostImage(image);
    }));
    const created = await contentStore.publishPost({
      type: activeType.value,
      title: form.title.trim(),
      content: form.content.trim(),
      price: form.price.trim() || undefined,
      originalPrice: form.originalPrice.trim() || undefined,
      location: form.location,
      tradeMode: form.tradeMode,
      visibleRange: form.visibleRange,
      contact: form.contact.trim() || undefined,
      tags: [...form.tags],
      images: uploadedImages,
      anonymous: form.anonymous,
    });
    createdPostId.value = created.id;
    publishedSummary.value = {
      typeTitle: currentType.value.title,
      location: form.location,
      visibleRange: form.visibleRange,
    };
    clearEditor();
    showSuccess.value = true;
  } catch (error) {
    const message = error instanceof Error ? error.message.replace(/^.*：/, '') : '请检查网络后重试';
    uni.showModal({
      title: '发布失败，内容未保存',
      content: message || '请检查网络后重试，当前填写内容仍为你保留。',
      showCancel: false,
      confirmText: '知道了',
    });
  } finally {
    submitting.value = false;
  }
}
function viewPublished() {
  if (!createdPostId.value) {
    uni.switchTab({ url: '/pages/index/index' });
    return;
  }
  showSuccess.value = false;
  uni.navigateTo({ url: `/pages/detail/index?id=${createdPostId.value}` });
}
function reset() {
  showSuccess.value = false;
  createdPostId.value = null;
  publishedSummary.value = null;
  clearEditor();
}
</script>

<template>
  <view class="publish-page safe-bottom">
    <view class="publish-head">
      <view>
        <view class="head-title">
          发布到校园
        </view>
        <view class="head-subtitle">
          分享真实信息，和同校同学更快连接
        </view>
      </view>
      <view class="draft-entry" @click="saveDraft">
        <view class="draft-icon" /><text>草稿</text>
      </view>
    </view>
    <view class="quality-card">
      <view class="quality-copy">
        <text>让同校同学一眼想点开</text><text>实拍清楚、标题具体，通常能获得更多回应</text>
      </view>
      <view class="quality-score">
        {{ completionScore }}%
      </view>
      <view class="quality-track">
        <view :style="{ width: `${completionScore}%` }" />
      </view>
    </view>

    <view class="type-card card-block">
      <view class="block-head">
        <text>选择发布类型</text><text>发布后不可修改</text>
      </view>
      <view class="type-track">
        <view v-for="item in campusPublishTypes" :key="item.key" class="type-item" :class="{ active: activeType === item.key }" @click="chooseType(item.key)">
          <view class="type-symbol">
            {{ item.icon }}
          </view>
          <view class="type-copy">
            <text>{{ item.title }}</text><text>{{ item.desc }}</text>
          </view>
          <view class="type-check">
            ✓
          </view>
        </view>
      </view>
      <view class="type-helper">
        <text>{{ currentDetail.eyebrow }}</text><view>{{ currentDetail.hint }}</view>
      </view>
    </view>

    <view class="content-card card-block">
      <view class="block-head media-head">
        <text>图片</text><text>{{ images.length }}/9 · 首图作为封面</text>
      </view>
      <view class="uploader-grid">
        <view v-for="(image, index) in images" :key="image" class="image-item" @click="setCover(index)">
          <image :src="image" mode="aspectFill" />
          <text v-if="index === 0" class="cover-badge">
            封面
          </text>
          <text class="remove-image" @click.stop="removeImage(index)">
            ×
          </text>
        </view>
        <view v-if="images.length < 9" class="add-image" :class="{ 'wide-add': !images.length }" @click="addImage">
          <view class="camera-icon">
            <i />
          </view>
          <text>添加实拍</text>
          <text>支持图片</text>
        </view>
      </view>
      <view class="photo-tips">
        <text>封面建议</text><text>拍全景</text><text>拍细节</text><text>如实拍瑕疵</text>
      </view>
      <view v-if="errors.images" class="error">
        {{ errors.images }}
      </view>

      <view class="editor-divider" />
      <view class="title-editor">
        <input v-model="form.title" maxlength="30" :class="{ invalid: errors.title }" :placeholder="`${currentType.title}，一句话说明重点`">
        <text>{{ form.title.length }}/30</text>
      </view>
      <view v-if="errors.title" class="error">
        {{ errors.title }}
      </view>
      <view class="editor-divider" />
      <textarea v-model="form.content" maxlength="500" class="content-editor" :class="{ invalid: errors.content }" :placeholder="currentDetail.placeholder" />
      <view class="content-tools">
        <text>真实描述更容易获得回应</text><text>{{ form.content.length }}/500</text>
      </view>
      <view v-if="errors.content" class="error">
        {{ errors.content }}
      </view>

      <view class="editor-divider" />
      <view class="block-head tag-head">
        <text>添加标签</text><text>最多选 3 个</text>
      </view>
      <scroll-view scroll-x class="tag-scroll" :show-scrollbar="false">
        <view class="tag-track">
          <view v-for="tag in currentDetail.tags" :key="tag" class="tag-chip" :class="{ active: form.tags.includes(tag) }" @click="toggleTag(tag)">
            # {{ tag }}
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="showPrice" class="trade-card card-block">
      <view class="block-head">
        <text>{{ activeType === 'ride' ? '费用信息' : '交易信息' }}</text><text>价格可面议</text>
      </view>
      <view class="price-row">
        <view class="price-main">
          <text>¥</text><input v-model="form.price" type="digit" placeholder="0.00">
        </view>
        <view v-if="activeType === 'idle'" class="original-price">
          <text>原价</text><input v-model="form.originalPrice" type="digit" placeholder="选填">
        </view>
      </view>
      <view v-if="errors.price" class="error">
        {{ errors.price }}
      </view>
      <view class="mode-label">
        {{ activeType === 'ride' ? '费用方式' : '交付方式' }}
      </view>
      <view class="mode-list">
        <view v-for="mode in currentDetail.modes" :key="mode" :class="{ active: form.tradeMode === mode }" @click="form.tradeMode = mode">
          <i />{{ mode }}
        </view>
      </view>
    </view>

    <view class="setting-card card-block">
      <picker :range="locations" @change="selectFrom('location', locations, $event)">
        <view class="setting-row">
          <view class="setting-icon pin-icon">
            <i />
          </view>
          <view class="setting-main">
            <text>发布位置</text><text>{{ form.location }}</text>
          </view><text class="arrow">
            ›
          </text>
        </view>
      </picker>
      <picker :range="visibleRanges" @change="selectFrom('visibleRange', visibleRanges, $event)">
        <view class="setting-row">
          <view class="setting-icon eye-icon">
            <i />
          </view>
          <view class="setting-main">
            <text>谁可以看</text><text>{{ form.visibleRange }}</text>
          </view><text class="arrow">
            ›
          </text>
        </view>
      </picker>
      <view class="setting-row contact-row">
        <view class="setting-icon contact-icon">
          联
        </view>
        <view class="setting-main">
          <text>联系方式</text><input v-model="form.contact" placeholder="选填，仅回应后可见">
        </view>
      </view>
      <view class="setting-row last-row">
        <view class="setting-icon anonymous-icon">
          匿
        </view>
        <view class="setting-main">
          <text>匿名发布</text><text>昵称将显示为“同校同学”</text>
        </view>
        <switch :checked="form.anonymous" color="#0a84ff" @change="form.anonymous = $event.detail.value" />
      </view>
    </view>

    <view class="community-note">
      <view class="check" :class="{ checked: agreed }" @click="agreed = !agreed">
        {{ agreed ? '✓' : '' }}
      </view>
      <text>发布即代表同意《云点校园社区发布规范》，请勿泄露身份证、校园卡号等敏感信息。</text>
    </view>
    <view v-if="errors.agreement" class="error agreement-error">
      {{ errors.agreement }}
    </view>
    <view class="bottom-spacer" />

    <view class="submit-bar">
      <button class="preview-btn" @click="preview">
        <view class="preview-icon" /><text>预览</text>
      </button>
      <button class="publish-btn" :disabled="submitting" @click="submit">
        <view v-if="submitting" class="submit-spinner" /><text>
          {{ submitting ? '正在发布…' : `发布${currentType.title}` }}
        </text>
      </button>
    </view>

    <view v-if="showSuccess" class="success-mask">
      <view class="success-card">
        <view class="success-mark">
          <i />
        </view>
        <view class="success-title">
          发布成功
        </view>
        <view class="success-desc">
          内容已进入「{{ publishedSummary?.typeTitle || '校园' }}」分区，新的回应会通过消息通知你。
        </view>
        <view class="success-summary">
          <text>{{ publishedSummary?.location }}</text><text>{{ publishedSummary?.visibleRange }}</text>
        </view>
        <button class="view-content" @click="viewPublished">
          查看刚刚发布的内容
        </button>
        <button class="again" @click="reset">
          继续发布
        </button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.publish-page {
  min-height: 100vh;
  padding: 18rpx 22rpx 0;
  background: var(--yd-paper);
}
.publish-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 4rpx 22rpx;
}
.head-title {
  color: var(--yd-ink);
  font-size: 38rpx;
  font-weight: 900;
  letter-spacing: -1rpx;
}
.head-subtitle {
  margin-top: 6rpx;
  color: var(--yd-muted);
  font-size: 21rpx;
}
.draft-entry {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 16rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 999rpx;
  color: var(--yd-green-dark);
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 10rpx 28rpx rgba(30, 49, 86, 0.08);
  backdrop-filter: blur(22rpx);
  -webkit-backdrop-filter: blur(22rpx);
  font-size: 21rpx;
}
.draft-icon {
  width: 18rpx;
  height: 22rpx;
  border: 3rpx solid #61706b;
  border-radius: 3rpx;
}
.draft-icon::after {
  display: block;
  width: 10rpx;
  height: 3rpx;
  margin: 5rpx auto;
  background: #61706b;
  box-shadow: 0 7rpx #61706b;
  content: '';
}
.quality-card {
  position: relative;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8rpx 18rpx;
  margin-bottom: 18rpx;
  padding: 22rpx 24rpx;
  overflow: hidden;
  border: 1rpx solid rgba(10, 132, 255, 0.16);
  border-radius: 26rpx;
  color: var(--yd-ink);
  background: rgba(255, 255, 255, 0.58);
  box-shadow: 0 18rpx 44rpx rgba(42, 65, 106, 0.08);
  backdrop-filter: blur(28rpx) saturate(150%);
  -webkit-backdrop-filter: blur(28rpx) saturate(150%);
}
.quality-copy {
  display: flex;
  flex-direction: column;
}
.quality-copy text:first-child {
  font-size: 26rpx;
  font-weight: 900;
}
.quality-copy text:last-child {
  margin-top: 6rpx;
  color: var(--yd-muted);
  font-size: 18rpx;
}
.quality-score {
  align-self: start;
  padding: 7rpx 11rpx;
  border-radius: 999rpx;
  color: #fff;
  background: var(--yd-green-dark);
  font-size: 20rpx;
  font-weight: 900;
}
.quality-track {
  grid-column: 1 / -1;
  height: 8rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: rgba(10, 132, 255, 0.12);
}
.quality-track view {
  height: 100%;
  border-radius: inherit;
  background: var(--yd-green);
  transition: width 0.25s ease;
}
.card-block {
  margin-bottom: 18rpx;
  padding: 24rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 26rpx;
  background: var(--yd-card);
  box-shadow: 0 18rpx 46rpx rgba(35, 52, 88, 0.085);
  backdrop-filter: blur(28rpx) saturate(145%);
  -webkit-backdrop-filter: blur(28rpx) saturate(145%);
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.block-head > text:first-child {
  color: #1c2623;
  font-size: 27rpx;
  font-weight: 800;
}
.block-head > text:last-child {
  color: #9aa29f;
  font-size: 19rpx;
}
.type-card {
  padding: 24rpx;
}
.type-track {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
}
.type-item {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 104rpx;
  padding: 15rpx 14rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 16rpx;
  color: var(--yd-muted);
  background: rgba(247, 248, 251, 0.74);
  font-size: 20rpx;
}
.type-symbol {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 52rpx;
  height: 52rpx;
  border-radius: 16rpx;
  color: var(--yd-ink);
  background: rgba(118, 118, 128, 0.1);
  font-size: 27rpx;
  font-weight: 800;
}
.type-copy {
  display: flex;
  min-width: 0;
  margin-left: 11rpx;
  flex-direction: column;
}
.type-copy text:first-child {
  color: var(--yd-ink);
  font-size: 22rpx;
  font-weight: 800;
}
.type-copy text:last-child {
  overflow: hidden;
  margin-top: 4rpx;
  color: #929b97;
  font-size: 16rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.type-check {
  position: absolute;
  top: 7rpx;
  right: 8rpx;
  display: none;
  align-items: center;
  justify-content: center;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  color: #fff;
  background: var(--yd-green);
  font-size: 16rpx;
}
.type-item.active {
  border-color: rgba(10, 132, 255, 0.42);
  color: var(--yd-green-dark);
  background: var(--yd-mint);
  font-weight: 700;
}
.type-item.active .type-symbol {
  background: rgba(255, 255, 255, 0.88);
}
.type-item.active .type-check {
  display: flex;
}
.type-helper {
  display: flex;
  align-items: center;
  margin: 17rpx 0 0;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  color: var(--yd-muted);
  background: rgba(118, 118, 128, 0.08);
  font-size: 20rpx;
}
.type-helper > text {
  flex: 0 0 auto;
  margin-right: 14rpx;
  padding-right: 14rpx;
  border-right: 1rpx solid rgba(60, 60, 67, 0.12);
  color: var(--yd-green-dark);
  font-weight: 700;
}
.media-head {
  margin-bottom: 15rpx;
}
.uploader-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}
.image-item,
.add-image {
  position: relative;
  height: 190rpx;
  overflow: hidden;
  border-radius: 15rpx;
}
.image-item image {
  width: 100%;
  height: 100%;
}
.cover-badge {
  position: absolute;
  left: 8rpx;
  bottom: 8rpx;
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  color: #fff;
  background: rgba(18, 28, 25, 0.7);
  font-size: 17rpx;
}
.remove-image {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  color: #fff;
  background: rgba(18, 28, 25, 0.68);
  font-size: 26rpx;
}
.add-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2rpx dashed rgba(10, 132, 255, 0.32);
  color: #64748b;
  background: rgba(239, 246, 255, 0.52);
  font-size: 20rpx;
}
.add-image.wide-add {
  grid-column: 1 / -1;
  height: 236rpx;
}
.add-image > text:last-child {
  margin-top: 3rpx;
  color: #a0aaa6;
  font-size: 17rpx;
}
.photo-tips {
  display: flex;
  align-items: center;
  gap: 9rpx;
  margin-top: 14rpx;
  color: #72807b;
  font-size: 18rpx;
}
.photo-tips text:first-child {
  color: #26332f;
  font-weight: 800;
}
.photo-tips text:not(:first-child) {
  padding: 6rpx 10rpx;
  border-radius: 999rpx;
  background: rgba(118, 118, 128, 0.08);
}
.camera-icon {
  position: relative;
  width: 47rpx;
  height: 35rpx;
  margin-bottom: 12rpx;
  border: 4rpx solid var(--yd-green);
  border-radius: 10rpx;
}
.camera-icon::before {
  position: absolute;
  top: -12rpx;
  left: 11rpx;
  width: 18rpx;
  height: 10rpx;
  border-radius: 5rpx 5rpx 0 0;
  background: var(--yd-green);
  content: '';
}
.camera-icon i {
  position: absolute;
  top: 7rpx;
  left: 13rpx;
  width: 13rpx;
  height: 13rpx;
  border: 4rpx solid var(--yd-green);
  border-radius: 50%;
}
.editor-divider {
  height: 1rpx;
  margin: 25rpx 0;
  background: rgba(60, 60, 67, 0.1);
}
.title-editor {
  display: flex;
  align-items: center;
}
.title-editor input {
  flex: 1;
  height: 66rpx;
  color: #17201d;
  font-size: 31rpx;
  font-weight: 700;
}
.title-editor > text {
  margin-left: 12rpx;
  color: #a5ada9;
  font-size: 18rpx;
}
.content-editor {
  width: 100%;
  height: 220rpx;
  color: #34403c;
  font-size: 25rpx;
  line-height: 1.65;
}
.content-tools {
  display: flex;
  justify-content: space-between;
  margin-top: 8rpx;
  color: #a0a8a5;
  font-size: 18rpx;
}
.invalid {
  color: #d95549 !important;
}
.error {
  margin-top: 9rpx;
  color: #e45f52;
  font-size: 20rpx;
}
.tag-head {
  margin-bottom: 14rpx;
}
.tag-scroll {
  width: 100%;
  white-space: nowrap;
}
.tag-track {
  display: flex;
  gap: 10rpx;
}
.tag-chip {
  flex: 0 0 auto;
  padding: 11rpx 16rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.12);
  border-radius: 999rpx;
  color: #68736f;
  background: rgba(118, 118, 128, 0.08);
  font-size: 20rpx;
}
.tag-chip.active {
  border-color: rgba(10, 132, 255, 0.35);
  color: var(--yd-green-dark);
  background: rgba(10, 132, 255, 0.1);
  font-weight: 700;
}
.price-row {
  display: flex;
  align-items: flex-end;
  gap: 26rpx;
  padding: 5rpx 0 22rpx;
  border-bottom: 1rpx solid #eeece7;
}
.price-main {
  display: flex;
  flex: 1;
  align-items: baseline;
  color: #ff695b;
}
.price-main > text {
  margin-right: 8rpx;
  font-size: 28rpx;
  font-weight: 800;
}
.price-main input {
  flex: 1;
  height: 66rpx;
  color: #ff695b;
  font-size: 44rpx;
  font-weight: 900;
}
.original-price {
  display: flex;
  flex: 0 0 190rpx;
  align-items: center;
  height: 60rpx;
  padding: 0 16rpx;
  border-radius: 14rpx;
  background: #f5f4f0;
}
.original-price text {
  margin-right: 10rpx;
  color: #818b87;
  font-size: 19rpx;
}
.original-price input {
  flex: 1;
  font-size: 22rpx;
}
.mode-label {
  margin-top: 21rpx;
  color: #303b37;
  font-size: 23rpx;
  font-weight: 700;
}
.mode-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 13rpx;
}
.mode-list > view {
  display: flex;
  align-items: center;
  padding: 12rpx 17rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.12);
  border-radius: 15rpx;
  color: #64706b;
  font-size: 20rpx;
}
.mode-list i {
  width: 13rpx;
  height: 13rpx;
  margin-right: 9rpx;
  border: 2rpx solid #b3bbb8;
  border-radius: 50%;
}
.mode-list .active {
  border-color: rgba(10, 132, 255, 0.35);
  color: var(--yd-green-dark);
  background: rgba(10, 132, 255, 0.1);
}
.mode-list .active i {
  border: 4rpx solid var(--yd-green);
}
.setting-card {
  padding-top: 0;
  padding-bottom: 0;
}
.setting-row {
  display: flex;
  align-items: center;
  min-height: 104rpx;
  border-bottom: 1rpx solid rgba(60, 60, 67, 0.1);
}
.last-row {
  border-bottom: 0;
}
.setting-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  margin-right: 17rpx;
  border-radius: 16rpx;
  color: var(--yd-green-dark);
  background: rgba(10, 132, 255, 0.1);
  font-size: 20rpx;
  font-weight: 800;
}
.pin-icon i {
  width: 14rpx;
  height: 14rpx;
  border: 4rpx solid var(--yd-green);
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
}
.eye-icon i {
  width: 25rpx;
  height: 16rpx;
  border: 3rpx solid var(--yd-green);
  border-radius: 50%;
}
.setting-main {
  display: flex;
  flex: 1;
  flex-direction: column;
}
.setting-main > text:first-child {
  color: #25302c;
  font-size: 24rpx;
  font-weight: 700;
}
.setting-main > text:last-child,
.setting-main input {
  margin-top: 5rpx;
  color: #87918d;
  font-size: 20rpx;
}
.arrow {
  color: #a2aaa7;
  font-size: 33rpx;
}
.contact-row input {
  width: 100%;
}
.community-note {
  display: flex;
  align-items: flex-start;
  padding: 4rpx 8rpx;
  color: #8a9490;
  font-size: 19rpx;
  line-height: 1.5;
}
.check {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 29rpx;
  height: 29rpx;
  margin: 1rpx 10rpx 0 0;
  border: 2rpx solid #b9c0bd;
  border-radius: 8rpx;
}
.check.checked {
  border-color: var(--yd-green);
  color: #fff;
  background: var(--yd-green);
}
.agreement-error {
  margin-left: 8rpx;
}
.bottom-spacer {
  height: 164rpx;
}
.submit-bar {
  position: fixed;
  z-index: 20;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: 190rpx minmax(0, 1fr);
  gap: 14rpx;
  padding: 14rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid rgba(255, 255, 255, 0.68);
  background: rgba(246, 248, 252, 0.76);
  box-shadow: 0 -12rpx 34rpx rgba(31, 47, 80, 0.09);
  backdrop-filter: blur(34rpx) saturate(165%);
  -webkit-backdrop-filter: blur(34rpx) saturate(165%);
}
.submit-bar button {
  display: flex;
  width: 100%;
  min-width: 0;
  margin: 0;
  padding: 0 20rpx;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  height: var(--yd-control-large);
  border-radius: var(--yd-control-radius);
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}
.preview-btn {
  gap: 10rpx;
  border: 1rpx solid rgba(60, 60, 67, 0.14);
  color: var(--yd-green-dark);
  background: rgba(255, 255, 255, 0.72);
}
.preview-icon {
  width: 27rpx;
  height: 17rpx;
  border: 3rpx solid var(--yd-green);
  border-radius: 50%;
}
.preview-icon::after {
  display: block;
  width: 7rpx;
  height: 7rpx;
  margin: 2rpx auto;
  border-radius: 50%;
  background: var(--yd-green);
  content: '';
}
.publish-btn {
  gap: 10rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 12rpx 28rpx rgba(10, 132, 255, 0.26);
}
.publish-btn[disabled] {
  color: rgba(255, 255, 255, 0.86);
  background: #8ebfee;
}
.submit-spinner {
  width: 26rpx;
  height: 26rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.38);
  border-top-color: #fff;
  border-radius: 50%;
  animation: submit-spin 0.8s linear infinite;
}
@keyframes submit-spin {
  to {
    transform: rotate(360deg);
  }
}
.success-mask {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 44rpx;
  background: rgba(20, 25, 36, 0.42);
  backdrop-filter: blur(18rpx);
  -webkit-backdrop-filter: blur(18rpx);
}
.success-card {
  width: 100%;
  padding: 48rpx 34rpx 30rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 34rpx 80rpx rgba(23, 38, 70, 0.18);
  backdrop-filter: blur(36rpx) saturate(160%);
  -webkit-backdrop-filter: blur(36rpx) saturate(160%);
  text-align: center;
}
.success-mark {
  position: relative;
  width: 104rpx;
  height: 104rpx;
  margin: 0 auto;
  border-radius: 34rpx;
  background: var(--yd-green);
}
.success-mark i {
  position: absolute;
  top: 32rpx;
  left: 27rpx;
  width: 45rpx;
  height: 23rpx;
  border-bottom: 8rpx solid #fff;
  border-left: 8rpx solid #fff;
  transform: rotate(-45deg);
}
.success-title {
  margin-top: 24rpx;
  color: #19231f;
  font-size: 36rpx;
  font-weight: 900;
}
.success-desc {
  margin-top: 10rpx;
  color: #75807c;
  font-size: 23rpx;
  line-height: 1.55;
}
.success-summary {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin: 24rpx 0;
  padding: 18rpx;
  border-radius: 18rpx;
  color: #56706a;
  background: rgba(118, 118, 128, 0.08);
  font-size: 20rpx;
}
.view-content {
  width: 100%;
  height: var(--yd-control-large);
  border-radius: var(--yd-control-radius);
  color: #fff;
  background: var(--yd-green);
  font-size: 26rpx;
  font-weight: 800;
}
.again {
  width: 100%;
  height: 64rpx;
  margin-top: 8rpx;
  border-radius: 16rpx;
  color: var(--yd-green-dark);
  background: transparent;
  font-size: 22rpx;
}
</style>
