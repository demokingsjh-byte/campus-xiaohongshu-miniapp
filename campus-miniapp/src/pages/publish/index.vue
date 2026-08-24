<script lang="ts" setup>
import PrototypeTabBar from '@/components/PrototypeTabBar/index.vue';
import { campusPublishTypes, getDefaultTenant } from '@/mock/campus';
import { uploadCampusPostImage } from '@/services/api/file';
import { useCampusContentStore, useTenantStore } from '@/stores/modules/tenant';
import { useUserStore } from '@/stores/modules/user';
import { resolveCampusAvatar } from '@/utils/avatar';
import { openPolicyPage } from '@/utils/privacy';

const activeType = ref('idle');
const images = ref<string[]>([]);
const customTag = ref('');
const choosingImages = ref(false);
const submitting = ref(false);
const showSuccess = ref(false);
const createdPostId = ref<number | null>(null);
const createdPostStatus = ref<number>(1);
const publishedSummary = ref<{ typeTitle: string, location: string, visibleRange: string } | null>(null);
const agreed = ref(true);
const errors = reactive<Record<string, string>>({});
const userStore = useUserStore();
const tenantStore = useTenantStore();
const contentStore = useCampusContentStore();
const currentTenant = computed(() => tenantStore.currentTenant || getDefaultTenant());
const schoolName = computed(() => currentTenant.value.name);
const publisherAvatar = computed(() => resolveCampusAvatar(userStore.userInfo?.avatar));
const publisherName = computed(() => userStore.userInfo?.nickname || '未登录用户');
const publisherCampus = computed(() => {
  if (!userStore.loggedIn)
    return '登录后内容会展示你的头像与昵称';
  return [userStore.userInfo?.schoolName, userStore.userInfo?.campusName].filter(Boolean).join(' · ') || '完善校园资料后再发布';
});
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
  idle: { eyebrow: '闲置交易', hint: '真实图片和成色描述能更快成交', placeholder: '品牌、型号、成色、购买时间、瑕疵和取货方式', tags: ['宿舍自提', '可小刀', '九成新', '全新未拆', '毕业出', '校内交易'], modes: ['校内自提', '送到宿舍', '快递邮寄'] },
  help: { eyebrow: '同学互助', hint: '把时间、地点和具体需求写清楚', placeholder: '需要什么帮助、截止时间、地点和答谢方式...', tags: ['急', '有偿', '奶茶答谢', '今天', '女生优先'], modes: ['线上回应', '当面帮助'] },
  ride: { eyebrow: '拼车出行', hint: '请注明时间、路线、人数和行李情况', placeholder: '出发时间、上车点、目的地、空位和行李要求...', tags: ['高铁站', '火车站', '女生优先', '可带行李', '周末'], modes: ['费用均摊', '司机接单'] },
  shop: { eyebrow: '校园探店', hint: '实拍、价格和真实体验最有参考价值', placeholder: '推荐菜品、价格、排队情况、位置和真实感受...', tags: ['学生折扣', '人均30', '东门', '不踩雷', '适合聚餐'], modes: ['到店消费', '外卖可点'] },
  lost: { eyebrow: '失物招领', hint: '避免公开证件号码等敏感信息', placeholder: '丢失或捡到的物品、时间、地点和辨认特征...', tags: ['急', '校园卡', '图书馆', '已交服务台', '求扩散'], modes: ['失物寻找', '物品招领'] },
  club: { eyebrow: '社团活动', hint: '活动时间、地点和报名方式要完整', placeholder: '活动主题、时间地点、适合人群、名额和报名方式...', tags: ['社团活动', '零基础', '免费', '周末', '招募中'], modes: ['线上报名', '现场参与'] },
  confession: { eyebrow: '校园表白', hint: '请尊重他人隐私，不要发布联系方式或敏感信息', placeholder: '写下你想对 TA 说的话，可以从一次相遇、一句感谢开始...', tags: ['匿名', '暗恋', '感谢', '毕业季', '求回应'], modes: ['仅表白墙展示'] },
  job: { eyebrow: '兼职信息', hint: '请写清工作内容、结算方式和安全注意事项', placeholder: '岗位职责、工作时间、薪资结算、地点和报名要求...', tags: ['校内兼职', '周末', '日结', '时间灵活', '学生优先'], modes: ['线上申请', '到店面试'] },
};
const typeIcons: Record<string, string> = {
  idle: '/static/images/home-prototype/category-idle.png',
  help: '/static/images/home-prototype/category-errand.png',
  ride: '/static/icons/publish/ride.svg',
  shop: '/static/images/home-prototype/category-groupbuy.png',
  lost: '/static/icons/publish/lost.svg',
  club: '/static/images/home-prototype/category-fun.png',
  confession: '/static/images/home-prototype/category-confession.png',
  job: '/static/images/home-prototype/category-job.png',
};

const selectableTypeKeys = ['idle', 'help', 'club', 'job', 'confession', 'shop'];
const selectablePublishTypes = computed(() => selectableTypeKeys
  .map(key => campusPublishTypes.find(item => item.key === key))
  .filter((item): item is typeof campusPublishTypes[number] => Boolean(item)));
const selectableTypeTitles: Record<string, string> = {
  idle: '二手闲置',
  help: '代拿代办',
  club: '校园趣事',
  job: '兼职信息',
  confession: '表白墙',
  shop: '商家团购',
};
function selectableTypeTitle(key: string) {
  return selectableTypeTitles[key] || campusPublishTypes.find(item => item.key === key)?.title || key;
}

const currentType = computed(() => {
  const item = campusPublishTypes.find(type => type.key === activeType.value)!;
  return { ...item, title: selectableTypeTitle(activeType.value) };
});
const currentDetail = computed(() => typeDetails[activeType.value]);
const displayTags = computed(() => Array.from(new Set([
  ...form.tags,
  ...currentDetail.value.tags,
])));
const showPrice = computed(() => ['idle', 'help', 'ride', 'shop', 'job'].includes(activeType.value));
const isConfession = computed(() => activeType.value === 'confession');

onLoad(() => {
  const draft = uni.getStorageSync('campus-publish-draft');
  if (!draft || typeof draft !== 'object')
    return;
  const draftType = selectableTypeKeys.includes(draft.activeType) ? draft.activeType : 'idle';
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

onShow(async () => {
  const requestedType = uni.getStorageSync('campus-publish-active-type');
  if (typeof requestedType === 'string' && campusPublishTypes.some(item => item.key === requestedType)) {
    chooseType(requestedType);
    uni.removeStorageSync('campus-publish-active-type');
  }
  if (!userStore.loggedIn)
    return;
  try {
    await userStore.getUserInfo();
    if (!form.contact && userStore.userInfo?.mobile)
      form.contact = userStore.userInfo.mobile;
  } catch {
    // 发布表单仍可继续编辑，提交时会再校验登录状态。
  }
});

function openPublisherProfile() {
  if (!userStore.loggedIn) {
    uni.navigateTo({ url: '/pages/login/index' });
    return;
  }
  uni.navigateTo({ url: '/pages/login/index?mode=edit' });
}

function chooseType(key: string) {
  activeType.value = key;
  form.tags = [];
  customTag.value = '';
  form.tradeMode = typeDetails[key].modes[0];
  Object.keys(errors).forEach(keyName => errors[keyName] = '');
}
function handleChooseImageFailure(error: { errMsg?: string }) {
  const message = error.errMsg || '';
  if (/cancel/i.test(message))
    return;

  if (/privacy|scope is not declared/i.test(message)) {
    uni.showModal({
      title: '暂时无法选择图片',
      content: '微信隐私保护指引尚未声明“选中的照片或视频信息”，请在小程序后台完成声明并生效后重试。',
      showCancel: false,
      confirmText: '知道了',
    });
    return;
  }

  if (/auth deny|permission|authorize/i.test(message)) {
    uni.showModal({
      title: '需要相册或相机权限',
      content: '请在微信右上角“···”的设置中允许使用相册或相机，然后重新选择图片。',
      showCancel: false,
      confirmText: '知道了',
    });
    return;
  }

  uni.showToast({ title: '图片选择失败，请重试', icon: 'none' });
}

function appendSelectedImages(paths: string[]) {
  const availableCount = 9 - images.value.length;
  const selectedPaths = paths.filter(Boolean).slice(0, availableCount);
  if (!selectedPaths.length) {
    uni.showToast({ title: '没有获取到可用图片', icon: 'none' });
    return;
  }
  images.value.push(...selectedPaths);
  errors.images = '';
}

function addImage() {
  if (images.value.length >= 9 || choosingImages.value)
    return;

  choosingImages.value = true;
  const count = 9 - images.value.length;
  const complete = () => {
    choosingImages.value = false;
  };

  uni.chooseImage({
    count,
    sourceType: ['album', 'camera'],
    sizeType: ['compressed'],
    success: result => appendSelectedImages(result.tempFilePaths || []),
    fail: handleChooseImageFailure,
    complete,
  });
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
function addCustomTag() {
  const tag = customTag.value
    .replace(/^#+/, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 10);
  if (!tag) {
    uni.showToast({ title: '请输入标签内容', icon: 'none' });
    return;
  }
  if (form.tags.includes(tag)) {
    customTag.value = '';
    uni.showToast({ title: '这个标签已经添加', icon: 'none' });
    return;
  }
  if (form.tags.length >= 3) {
    uni.showToast({ title: '最多添加 3 个标签', icon: 'none' });
    return;
  }
  form.tags.push(tag);
  customTag.value = '';
}
function selectFrom(key: 'location' | 'visibleRange', options: string[], event: any) {
  form[key] = options[Number(event.detail.value)];
}
function validate() {
  errors.images = '';
  errors.title = isConfession.value
    ? (form.title.trim().length >= 2 ? '' : '请填写表白对象或称呼')
    : (form.title.trim().length >= 4 ? '' : '标题至少填写 4 个字');
  errors.content = form.content.trim().length >= 10 ? '' : '详细描述至少填写 10 个字';
  errors.price = showPrice.value && form.price && Number(form.price) <= 0 ? '价格需要大于 0' : '';
  errors.agreement = agreed.value ? '' : '请先同意社区发布规范';
  return !Object.values(errors).some(Boolean);
}

watch([() => form.title, isConfession], ([value, confession]) => {
  const minimum = confession ? 2 : 4;
  if (errors.title && value.trim().length >= minimum)
    errors.title = '';
});

watch(() => form.content, (value) => {
  if (errors.content && value.trim().length >= 10)
    errors.content = '';
});
function normalizeConfessionTitle(value: string) {
  const target = value.replace(/^TO[：:]?\s*/i, '').trim();
  return `TO：${target}`;
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
      title: isConfession.value ? normalizeConfessionTitle(form.title) : form.title.trim(),
      content: form.content.trim(),
      price: form.price.trim() || undefined,
      originalPrice: form.originalPrice.trim() || undefined,
      location: isConfession.value ? `${schoolName.value} · 表白墙` : form.location,
      tradeMode: form.tradeMode,
      visibleRange: form.visibleRange,
      contact: form.contact.trim() || undefined,
      tags: [...form.tags],
      images: uploadedImages,
      anonymous: form.anonymous,
    });
    createdPostId.value = created.id;
    createdPostStatus.value = created.status ?? 1;
    publishedSummary.value = {
      typeTitle: currentType.value.title,
      location: form.location,
      visibleRange: form.visibleRange,
    };
    clearEditor();
    showSuccess.value = true;
  } catch (error) {
    const message = error instanceof Error ? error.message.replace(/^.*：/, '') : '请检查网络后重试';
    const backendNotUpdated = /不支持的发布类型/.test(message);
    uni.showModal({
      title: backendNotUpdated ? '服务器版本未更新' : '发布失败，内容未保存',
      content: backendNotUpdated
        ? '该发布类型已加入小程序，但服务器仍在运行旧版本。请部署最新后端并重启服务后再试。'
        : (message || '请检查网络后重试，当前填写内容仍为你保留。'),
      showCancel: false,
      confirmText: '知道了',
    });
  } finally {
    submitting.value = false;
  }
}
function viewPublished() {
  if (!createdPostId.value) {
    uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  showSuccess.value = false;
  uni.navigateTo({ url: `/pages/detail/index?id=${createdPostId.value}&mine=1` });
}
function reset() {
  showSuccess.value = false;
  createdPostId.value = null;
  createdPostStatus.value = 1;
  publishedSummary.value = null;
  clearEditor();
}
</script>

<template>
  <view class="publish-page safe-bottom">
    <view class="publisher-card card-block" @click="openPublisherProfile">
      <image class="publisher-avatar" :src="publisherAvatar" mode="aspectFill" />
      <view class="publisher-copy">
        <view class="publisher-line">
          <text>{{ publisherName }}</text><text>{{ userStore.loggedIn ? '当前发布者' : '去登录' }}</text>
        </view>
        <text>{{ form.anonymous ? '匿名发布时不会展示头像和昵称' : publisherCampus }}</text>
      </view>
      <text class="publisher-arrow">
        ›
      </text>
    </view>
    <view class="type-card card-block">
      <view class="block-head">
        <text>内容分类</text><text>选择最合适的一项</text>
      </view>
      <scroll-view scroll-x class="type-scroll" :show-scrollbar="false">
        <view class="type-track">
          <view v-for="item in selectablePublishTypes" :key="item.key" class="type-item" :class="{ active: activeType === item.key }" @click="chooseType(item.key)">
            <view class="type-symbol-wrap">
              <image class="type-symbol" :src="typeIcons[item.key]" mode="aspectFit" />
            </view>
            <text class="type-title">
              {{ selectableTypeTitle(item.key) }}
            </text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="content-card card-block">
      <view class="block-head media-head">
        <text>添加图片</text><text>{{ images.length }}/9 首图为封面图(可拖拽图片更改封面)</text>
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
        <view v-if="images.length < 9" class="add-image" :class="{ 'wide-add': !images.length, 'disabled': choosingImages }" @click="addImage">
          <view class="upload-image-symbol">
            <image src="/static/icons/ui/comment-image.svg" mode="aspectFit" />
            <text>+</text>
          </view>
          <view class="add-image-copy">
            <text>{{ choosingImages ? '正在打开相册…' : '添加真实图片' }}</text>
            <text>拍全景、细节和真实瑕疵</text>
          </view>
        </view>
      </view>
      <view v-if="errors.images" class="error">
        {{ errors.images }}
      </view>

      <view class="editor-divider" />
      <view class="title-editor">
        <input
          v-model="form.title" maxlength="30" :class="{ invalid: errors.title }"
          :placeholder="isConfession ? '例如：TO：图书馆三楼遇到的你' : '填写标题更容易被看到'"
          placeholder-style="font-family: PingFang SC; font-weight: 400; font-size: 30.77rpx; color: #C6CDCE; line-height: 38.46rpx;"
        >
        <text>{{ form.title.length }}/30</text>
      </view>
      <view v-if="errors.title" class="error">
        {{ errors.title }}
      </view>
      <view class="editor-divider compact-divider" />
      <textarea
        v-model="form.content" maxlength="500" class="content-editor" :class="{ invalid: errors.content }"
        :placeholder="currentDetail.placeholder"
        placeholder-style="font-family: PingFang SC; font-weight: 400; font-size: 23.08rpx; color: #C6CDCE; line-height: 26.92rpx;"
      />
      <view class="content-tools">
        <text>{{ currentDetail.hint }}</text><text>{{ form.content.length }}/500</text>
      </view>
      <view v-if="errors.content" class="error">
        {{ errors.content }}
      </view>

      <view class="editor-divider" />
      <view class="block-head tag-head">
        <text>添加标签</text><text>最多可选3个标签</text>
      </view>
      <view class="custom-tag-entry">
        <image class="custom-tag-hash" src="/static/icons/publish/tag-hot.svg" mode="aspectFit" />
        <input
          v-model="customTag" maxlength="10" confirm-type="done"
          placeholder="输入自定义标签" @confirm="addCustomTag"
        >
        <text class="custom-tag-action" @click="addCustomTag">添加</text>
      </view>
      <view class="tag-scroll">
        <view class="tag-track">
          <view v-for="tag in displayTags" :key="tag" class="tag-chip" :class="{ active: form.tags.includes(tag) }" @click="toggleTag(tag)">
            <image class="tag-hash" src="/static/icons/publish/tag-hot.svg" mode="aspectFit" /><text>{{ tag }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showPrice" class="trade-card card-block">
      <view class="block-head">
        <text>{{ activeType === 'ride' ? '费用信息' : '交易信息' }}</text><text>价格可面议 ◯</text>
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
      <picker v-if="!isConfession" :range="locations" @change="selectFrom('location', locations, $event)">
        <view class="setting-row">
          <view class="setting-icon">
            <image src="/static/icons/ui/location.svg" mode="aspectFit" />
          </view>
          <view class="setting-main">
            <text>所在位置</text><text>{{ form.location }}</text>
          </view><text class="arrow">
            ›
          </text>
        </view>
      </picker>
      <picker :range="visibleRanges" @change="selectFrom('visibleRange', visibleRanges, $event)">
        <view class="setting-row">
          <view class="setting-icon">
            <image src="/static/icons/ui/eye.svg" mode="aspectFit" />
          </view>
          <view class="setting-main">
            <text>谁可以看</text><text>{{ form.visibleRange }}</text>
          </view><text class="arrow">
            ›
          </text>
        </view>
      </picker>
      <view v-if="!isConfession" class="setting-row contact-row">
        <view class="setting-icon">
          <image src="/static/icons/ui/contact.svg" mode="aspectFit" />
        </view>
        <view class="setting-main">
          <text>联系方式</text><input v-model="form.contact" placeholder="请输入联系方式">
        </view>
      </view>
      <view class="setting-row last-row">
        <view class="setting-icon">
          <image src="/static/icons/ui/anonymous.svg" mode="aspectFit" />
        </view>
        <view class="setting-main">
          <text>匿名发布</text>
        </view>
        <switch :checked="form.anonymous" color="#10a779" @change="form.anonymous = $event.detail.value" />
      </view>
    </view>

    <view class="community-note">
      <view class="check" :class="{ checked: agreed }" @click="agreed = !agreed">
        {{ agreed ? '✓' : '' }}
      </view>
      <view class="community-copy">
        <text>发布即代表同意</text><text class="community-link" @click="openPolicyPage('community')">
          《云点校园社区发布规范》
        </text><text>，请勿泄露身份证、校园卡号等敏感信息。</text>
      </view>
    </view>
    <view v-if="errors.agreement" class="error agreement-error">
      {{ errors.agreement }}
    </view>
    <view class="bottom-spacer" />

    <view class="submit-bar">
      <button class="draft-btn" @click="saveDraft">
        存草稿
      </button>
      <button class="publish-btn" :disabled="submitting" @click="submit">
        <view v-if="submitting" class="submit-spinner" /><text>
          {{ submitting ? '正在发布…' : `发布${currentType.title}` }}
        </text>
      </button>
    </view>

    <PrototypeTabBar active="publish" @reselect="submit" />

    <view v-if="showSuccess" class="success-mask">
      <view class="success-card">
        <view class="success-mark">
          <i />
        </view>
        <view class="success-title">
          {{ createdPostStatus === 1 ? '发布成功' : '已提交审核' }}
        </view>
        <view class="success-desc">
          {{ createdPostStatus === 1
            ? `内容已发布到${schoolName}，新的回应会通过消息通知你。`
            : '文字和图片正在进行安全审核，通过后会自动展示给同校同学。' }}
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
  padding: 12rpx 20rpx 0;
  background: var(--yd-paper);
}
.card-block {
  margin-bottom: 16rpx;
  padding: 24rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8rpx 24rpx rgba(35, 52, 88, 0.045);
}
.publisher-card {
  display: flex;
  align-items: center;
  min-height: 112rpx;
  padding: 18rpx 22rpx;
  background: var(--color-glass-strong);
}
.publisher-avatar {
  width: 76rpx;
  height: 76rpx;
  flex: 0 0 auto;
  border: 3rpx solid #fff;
  border-radius: 50%;
  background: #e7f3ff;
  box-shadow: 0 8rpx 20rpx rgba(16, 167, 121, 0.18);
}
.publisher-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  margin-left: 18rpx;
}
.publisher-line {
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.publisher-line text:first-child {
  overflow: hidden;
  color: var(--color-text);
  font-size: 26rpx;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.publisher-line text:last-child {
  flex: 0 0 auto;
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  color: #0877df;
  background: var(--color-primary-soft);
  font-size: 18rpx;
  font-weight: 700;
}
.publisher-copy > text {
  overflow: hidden;
  margin-top: 7rpx;
  color: #84908b;
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.publisher-arrow {
  margin-left: 12rpx;
  color: #a0aaa5;
  font-size: 36rpx;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.block-head > text:first-child {
  color: var(--color-text);
  font-size: 27rpx;
  font-weight: 800;
}
.block-head > text:last-child {
  color: #9aa29f;
  font-size: 19rpx;
}
.type-card {
  padding: 22rpx 0 20rpx;
}
.type-card .block-head {
  margin-bottom: 16rpx;
  padding: 0 24rpx;
}
.type-scroll {
  width: 100%;
  white-space: nowrap;
}
.type-track {
  display: inline-flex;
  gap: 12rpx;
  padding: 0 24rpx 4rpx;
}
.type-item {
  display: flex;
  align-items: center;
  min-height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--yd-line);
  border-radius: 999rpx;
  color: var(--yd-muted);
  background: rgba(118, 118, 128, 0.06);
}
.type-symbol {
  width: 32rpx;
  height: 32rpx;
}
.type-title {
  margin-left: 12rpx;
  color: var(--yd-ink);
  font-size: 22rpx;
  font-weight: 650;
}
.type-item.active {
  border-color: transparent;
  color: #181b19;
  background: transparent;
}
.type-item.active .type-title {
  color: #1f1f1f;
  font-weight: 500;
}
.media-head {
  margin-bottom: 18rpx;
}
.uploader-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
}
.image-item,
.add-image {
  position: relative;
  height: 190rpx;
  overflow: hidden;
  border-radius: 15rpx;
}
.add-image.disabled {
  opacity: 0.62;
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
  border: 2rpx dashed rgba(16, 167, 121, 0.34);
  color: #64748b;
  background: rgba(239, 246, 255, 0.52);
  font-size: 20rpx;
}
.add-image.wide-add {
  grid-column: 1 / -1;
  height: 156rpx;
  flex-direction: row;
  gap: 24rpx;
}
.add-image-copy {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
}
.add-image-copy text:first-child {
  color: #33413d;
  font-size: 22rpx;
  font-weight: 750;
}
.add-image-copy text:last-child {
  margin-top: var(--yd-copy-gap);
  color: #a0aaa6;
  font-size: 17rpx;
}
.camera-icon {
  position: relative;
  width: 47rpx;
  height: 35rpx;
  margin-bottom: 0;
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
  margin: 28rpx 0;
  background: rgba(60, 60, 67, 0.1);
}
.compact-divider {
  margin: 14rpx 0 20rpx;
}
.title-editor {
  display: flex;
  align-items: center;
}
.title-editor input {
  flex: 1;
  height: 76rpx;
  color: var(--color-text);
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
  height: 240rpx;
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
  border-color: rgba(16, 167, 121, 0.36);
  color: var(--yd-green-dark);
  background: var(--color-primary-soft);
  font-weight: 700;
}
.price-row {
  display: flex;
  align-items: flex-end;
  gap: 26rpx;
  padding: 5rpx 0 22rpx;
  border-bottom: 1rpx solid var(--color-divider);
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
  background: var(--color-surface-subtle);
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
  color: var(--color-text-secondary);
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
  border-color: rgba(16, 167, 121, 0.36);
  color: var(--yd-green-dark);
  background: var(--color-primary-soft);
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
  min-height: 108rpx;
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
  width: 60rpx;
  height: 60rpx;
  margin-right: var(--yd-icon-gap);
  border-radius: 16rpx;
  background: var(--color-primary-soft);
}
.setting-icon image {
  width: 36rpx;
  height: 36rpx;
}
.setting-main {
  display: flex;
  flex: 1;
  flex-direction: column;
}
.setting-main > text:first-child {
  color: var(--color-text-secondary);
  font-size: 24rpx;
  font-weight: 700;
}
.setting-main > text:last-child,
.setting-main input {
  margin-top: var(--yd-copy-gap);
  color: #87918d;
  font-size: 20rpx;
}
.arrow {
  color: #a2aaa7;
  font-size: 33rpx;
  transition: transform 0.2s ease;
}
.arrow.expanded {
  transform: rotate(90deg);
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
.community-copy {
  flex: 1;
}
.community-link {
  color: var(--yd-green);
  font-weight: 650;
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
  height: 142rpx;
}
.submit-bar {
  position: fixed;
  z-index: 20;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: 166rpx minmax(0, 1fr);
  gap: 16rpx;
  padding: 12rpx 24rpx calc(12rpx + env(safe-area-inset-bottom));
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
.draft-btn {
  border: 1rpx solid rgba(60, 60, 67, 0.14);
  color: var(--yd-ink);
  background: rgba(255, 255, 255, 0.72);
}
.publish-btn {
  gap: 10rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 12rpx 28rpx rgba(16, 167, 121, 0.26);
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
  color: var(--color-text);
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

/* 蓝湖原型：发布页布局覆盖 */
.publish-page {
  min-height: 100vh;
  padding: 26rpx 26rpx 0;
  color: #1e201f;
  background: #f4f4f4;
  box-sizing: border-box;
}

.publisher-card {
  display: none;
}

.card-block {
  margin-bottom: 20rpx;
  padding: 40rpx;
  border: 0;
  border-radius: 30rpx;
  background: #fff;
  box-shadow: none;
}

.block-head {
  margin-bottom: 24rpx;
}

.block-head > text:first-child {
  color: #1e201f;
  font-size: 32rpx;
  font-weight: 600;
}

.block-head > text:last-child {
  color: #929593;
  font-size: 24rpx;
}

.type-card {
  padding: 24rpx 0 22rpx;
}

.type-card .block-head {
  margin-bottom: 20rpx;
  padding: 0 40rpx;
}

.type-track {
  min-width: 100%;
  padding: 0 24rpx;
  gap: 4rpx;
  box-sizing: border-box;
}

.type-item {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-start;
  width: 124rpx;
  min-height: 124rpx;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  flex-direction: column;
}

.type-symbol-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84.62rpx;
  height: 84.62rpx;
  border: 1rpx solid transparent;
  border-radius: 34rpx;
  transition:
    background 0.18s ease,
    transform 0.18s ease;
}

.type-symbol {
  width: 84.62rpx;
  height: 84.62rpx;
  filter: none;
}

.type-title {
  width: 123.08rpx;
  height: 30.77rpx;
  margin: 15.38rpx 0 0;
  color: #646464;
  font-family: 'PingFang SC', sans-serif;
  font-size: 26.92rpx;
  font-weight: 500;
  line-height: 30.77rpx;
  text-align: center;
  white-space: nowrap;
}

.type-item.active {
  border: 0;
  color: #181b19;
  background: transparent;
}

.type-item.active .type-symbol-wrap {
  border-color: rgba(255, 255, 255, 0.82);
  background: linear-gradient(145deg, rgba(211, 255, 195, 0.9), rgba(239, 255, 231, 0.55));
  box-shadow: 0 10rpx 24rpx rgba(94, 223, 87, 0.14);
  transform: translateY(-2rpx);
}

.type-item.active .type-title {
  color: #1f1f1f;
  font-weight: 500;
}

.content-card {
  padding: 40rpx;
}

.uploader-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.image-item,
.add-image {
  flex: 0 0 auto;
  width: 142rpx;
  height: 142rpx;
  border-radius: 20rpx;
}

.add-image {
  border: 0;
  color: #929694;
  background: #f7f7f7;
}

.add-image.wide-add {
  width: 142rpx;
  height: 142rpx;
  flex-direction: column;
  gap: 7rpx;
}

.add-image-copy {
  align-items: center;
  text-align: center;
}

.add-image-copy text:first-child {
  color: #929694;
  font-size: 22rpx;
  font-weight: 500;
}

.add-image-copy text:last-child {
  display: none;
}

.remove-image {
  top: -16rpx;
  right: -16rpx;
  width: 42rpx;
  height: 42rpx;
  border: 5rpx solid #fff;
  color: #fff;
  background: #ff4c55;
  font-size: 31rpx;
  line-height: 38rpx;
  box-sizing: border-box;
}

.editor-divider {
  margin: 30rpx 0 24rpx;
  background: #f3f3f3;
}

.title-editor input {
  color: #202321;
  font-size: 30rpx;
  font-weight: 600;
}

.content-editor {
  min-height: 210rpx;
  color: #303331;
  font-size: 26rpx;
  line-height: 1.65;
}

.content-tools {
  margin-top: 8rpx;
  color: #a0a3a1;
  font-size: 22rpx;
}

.tag-chip {
  min-height: 56rpx;
  padding: 0 24rpx;
  border: 0;
  border-radius: 28rpx;
  color: #969a97;
  background: #f7f7f7;
  font-size: 24rpx;
  line-height: 56rpx;
}

.tag-chip.active {
  color: #203015;
  background: #97f820;
}

.tag-head > text:last-child {
  color: #ff4d55;
  font-weight: 500;
}

.tag-scroll {
  width: 100%;
  white-space: normal;
}

.tag-track {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx 16rpx;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.custom-tag-entry {
  display: flex;
  align-items: center;
  width: 100%;
  height: 64rpx;
  margin-bottom: 16rpx;
  padding: 0 18rpx;
  border: 1rpx solid #ededed;
  border-radius: 20rpx;
  background: #f7f7f7;
  box-shadow: none;
  box-sizing: border-box;
}

.custom-tag-hash {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  color: #fff;
  background: #222522;
  font-size: 19rpx;
  line-height: 30rpx;
}

.custom-tag-entry input {
  flex: 1;
  height: 64rpx;
  margin-left: 12rpx;
  color: #1f1f1f;
  font-size: 24rpx;
  line-height: 64rpx;
}

.custom-tag-action {
  margin-left: 16rpx;
  color: #58b900;
  font-size: 24rpx;
  font-weight: 500;
}

.tag-hash {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26rpx;
  height: 26rpx;
  margin-right: 8rpx;
  border-radius: 50%;
  color: #fff;
  background: #222522;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 26rpx;
}

.mode-label {
  display: none;
}

.trade-card .price-main,
.original-price {
  border: 0;
  border-radius: 18rpx;
  background: #f8f8f8;
}

.price-main > text,
.price-main input {
  color: #ff4d55;
}

.mode-list > view {
  border-color: #eceeec;
  color: #303330;
  background: #fff;
}

.mode-list > view.active {
  border-color: #ffe8c6;
  color: #ff8e16;
  background: #fff7ea;
}

.setting-card {
  padding: 4rpx 40rpx;
}

.setting-row {
  min-height: 96rpx;
  border-bottom-color: #f1f2f1;
}

.setting-icon {
  display: none;
}

.setting-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-left: 0;
  flex-direction: row;
}

.setting-main > text:first-child {
  color: #1e211f;
  font-size: 28rpx;
  font-weight: 550;
}

.setting-main > text:last-child,
.setting-main input {
  flex: 1;
  min-width: 0;
  margin-top: 0;
  margin-left: 24rpx;
  color: #959996;
  font-size: 25rpx;
  text-align: right;
}

.setting-main input {
  height: 72rpx;
}

.setting-row switch {
  margin-right: -10rpx;
  transform: scale(0.72);
  transform-origin: right center;
}

.community-note,
.agreement-error,
.submit-bar {
  display: none;
}

.bottom-spacer {
  height: calc(190rpx + env(safe-area-inset-bottom));
}

.success-mask {
  z-index: 100;
  display: block;
  padding: 0 32rpx;
  background: linear-gradient(180deg, #e9fff0 0%, #f4f4f4 26%, #f4f4f4 100%);
  backdrop-filter: none;
  box-sizing: border-box;
}

.success-card {
  width: 100%;
  padding: 310rpx 0 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  box-sizing: border-box;
}

/* 蓝湖稿为纯色平面卡片，发布页不使用投影或模糊阴影。 */
.publish-page .card-block,
.publish-page button,
.publish-page image {
  box-shadow: none;
}

/* 灰色填充统一改为白底，只用轻边框区分可操作区域。 */
.publish-page .type-card,
.publish-page .content-card,
.publish-page .trade-card,
.publish-page .setting-card,
.publish-page .add-image,
.publish-page .custom-tag-entry,
.publish-page .tag-chip,
.publish-page .trade-card .price-main,
.publish-page .original-price {
  background: #fff;
}

.publish-page .add-image,
.publish-page .custom-tag-entry,
.publish-page .tag-chip,
.publish-page .trade-card .price-main,
.publish-page .original-price {
  border: 1rpx solid #eeeeee;
}

.publish-page .tag-chip.active {
  border-color: #97f820;
  background: #97f820;
}

.publish-page .mode-list > view.active {
  border-color: #ffe8c6;
  background: #fff7ea;
}

.media-head > text:last-child {
  flex: 0 0 auto;
  font-size: 20rpx;
  white-space: nowrap;
}

.title-editor > text {
  display: none;
}

/* 微信原生 input/textarea 会带默认灰底，设计稿要求编辑区域为纯白。 */
.publish-page .title-editor,
.publish-page .title-editor input,
.publish-page .content-editor,
.publish-page textarea {
  background: #fff !important;
  background-color: #fff !important;
}

.publish-page .editor-divider {
  background: #fff;
}

/* 自定义标签、交易信息和联系方式区域保持纯白。 */
.publish-page .custom-tag-entry,
.publish-page .custom-tag-entry input,
.publish-page .trade-card,
.publish-page .price-row,
.publish-page .price-main,
.publish-page .price-main input,
.publish-page .original-price,
.publish-page .original-price input,
.publish-page .setting-card,
.publish-page .contact-row,
.publish-page .contact-row .setting-main,
.publish-page .contact-row input {
  background: #fff !important;
  background-color: #fff !important;
}

/* 蓝湖明确参数：内容编辑区横向位置与宽度。 */
.publish-page {
  padding-right: 30.77rpx;
  padding-left: 30.77rpx;
}

.content-card {
  padding-right: 23.08rpx;
  padding-left: 23.08rpx;
}

.upload-image-symbol {
  position: relative;
  width: 48rpx;
  height: 48rpx;
}

.upload-image-symbol image {
  width: 48rpx;
  height: 48rpx;
}

.upload-image-symbol text {
  position: absolute;
  right: -2rpx;
  bottom: -1rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18rpx;
  height: 18rpx;
  border-radius: 3rpx;
  color: #8b8b8b;
  background: #fff;
  font-size: 18rpx;
  line-height: 18rpx;
}

.title-editor {
  width: 642.31rpx;
  height: 38.46rpx;
  min-height: 38.46rpx;
}

.title-editor input {
  width: 642.31rpx;
  height: 38.46rpx;
  min-height: 38.46rpx;
  padding: 0;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 400;
  line-height: 38.46rpx;
  text-align: left;
}

.compact-divider {
  height: 0;
  margin: 23.08rpx 0 0;
  background: transparent;
}

.content-editor {
  width: 642.31rpx;
  padding: 0;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 26.92rpx;
  text-align: left;
}

.content-tools {
  justify-content: flex-end;
}

.content-tools > text:first-child {
  display: none;
}

/* 蓝湖明确参数：图片说明文字。 */
.media-head {
  height: 38.46rpx;
  min-height: 38.46rpx;
}

.media-head > text:last-child {
  width: 409.62rpx;
  height: 38.46rpx;
  color: #8b8b8b;
  font-family: "PingFang SC", sans-serif;
  font-size: 23.08rpx;
  font-weight: 400;
  line-height: 38.46rpx;
  text-align: left;
}

/* 蓝湖明确参数：添加标签标题。 */
.tag-head > text:first-child {
  width: 446.15rpx;
  height: 38.46rpx;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 400;
  line-height: 38.46rpx;
  text-align: left;
}

.custom-tag-hash,
.tag-hash {
  flex: 0 0 auto;
  border-radius: 0;
  background: transparent;
}

.custom-tag-hash {
  width: 30rpx;
  height: 30rpx;
}

.tag-hash {
  width: 32rpx;
  height: 32rpx;
}

/* 蓝湖明确参数：发布设置四行。 */
.setting-card {
  padding-right: 23.08rpx;
  padding-left: 23.08rpx;
}

.setting-row {
  width: 100%;
  height: 103.85rpx;
  min-height: 103.85rpx;
  padding-left: 0;
  box-sizing: border-box;
}

.setting-main > text:first-child {
  height: 42.31rpx;
  color: #1d1b18;
  font-family: "PingFang SC", sans-serif;
  font-size: 30.77rpx;
  font-weight: 500;
  line-height: 42.31rpx;
  text-align: left;
}

.setting-row.last-row .setting-main,
.setting-row.last-row .setting-main > text:first-child {
  margin-left: 0;
  padding-left: 0;
}

:deep(.prototype-tabbar) {
  box-shadow: none;
}

.success-mark {
  display: inline-flex;
  width: 42rpx;
  height: 42rpx;
  margin: 0 17rpx 0 0;
  border-radius: 50%;
  background: #93ef22;
  vertical-align: middle;
}

.success-mark i {
  top: 12rpx;
  left: 10rpx;
  width: 19rpx;
  height: 10rpx;
  border-width: 0 0 4rpx 4rpx;
  border-color: #1c2a13;
  border-style: solid;
}

.success-title {
  display: inline-block;
  margin: 0;
  color: #1e201f;
  font-size: 42rpx;
  font-weight: 600;
  vertical-align: middle;
}

.success-desc {
  margin-top: 32rpx;
  color: #999d9a;
  font-size: 28rpx;
}

.success-summary {
  display: none;
}

.view-content {
  width: auto;
  min-width: 210rpx;
  height: 66rpx;
  margin: 32rpx auto 0;
  padding: 0 30rpx;
  border: 2rpx solid #dedfdd;
  border-radius: 30rpx;
  color: #202220;
  background: transparent;
  font-size: 27rpx;
  font-weight: 500;
  line-height: 62rpx;
}

.view-content::after {
  border: 0;
}

.again {
  width: auto;
  margin: 12rpx auto 0;
  color: #8f9390;
}
</style>
