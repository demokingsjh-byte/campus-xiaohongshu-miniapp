<script lang="ts" setup>
import { PRIVACY_POLICY_VERSION } from '@/utils/privacy';

type PolicyType = 'privacy' | 'agreement' | 'community' | 'permissions';

interface PolicySection {
  title: string
  paragraphs?: string[]
  items?: string[]
  alert?: string
}

interface PolicyDocument {
  title: string
  eyebrow: string
  summary: string
  sections: PolicySection[]
}

const type = ref<PolicyType>('privacy');

const documents: Record<PolicyType, PolicyDocument> = {
  privacy: {
    title: '云点校园隐私政策',
    eyebrow: `版本 ${PRIVACY_POLICY_VERSION}`,
    summary: '我们坚持最小必要、目的明确和公开透明。游客可以直接浏览公开内容；只有你主动同意并点击登录后，我们才会创建校园账号。',
    sections: [
      {
        title: '1. 我们是谁与适用范围',
        paragraphs: [
          '本政策适用于“云点校园”微信小程序提供的校园社区、二手交易、互助、拼车、探店、失物招领和社团活动服务。运营者主体信息以微信小程序“关于—主体信息”页面公示为准。',
          '服务接口域名为 huanwoshidai.com.cn。你可以通过“我的—帮助与反馈”提交隐私与个人信息权利请求。',
        ],
        alert: '正式提审前，运营者仍需在微信公众平台补齐并核验主体全称、注册地址、联系电话、邮箱及《小程序用户隐私保护指引》。',
      },
      {
        title: '2. 我们处理哪些信息',
        items: [
          '账号登录：你主动点击微信登录后，我们使用临时登录凭证换取 OpenID，用于识别账号和保持登录状态；不会获取你的微信密码。',
          '校园资料：你主动填写或选择的头像、昵称、学校、校区、年级，以及可选的性别信息，用于展示身份和匹配同校内容。',
          '你发布的内容：文字、图片、价格、地点描述、标签、可见范围和你主动填写的联系方式，用于发布、展示和促成校园互动。',
          '互动信息：发布、点赞、收藏、浏览和删除记录，用于实现相应功能、展示计数和保障账号安全。',
          '运行与安全日志：请求时间、网络地址、接口结果及必要的异常日志，用于防攻击、排障、审计和维护服务安全。',
        ],
      },
      {
        title: '3. 权限与调用时机',
        items: [
          '头像和昵称：仅在你点击选择头像或填写昵称时使用；不授权不影响游客浏览。',
          '相册或相机：仅在你主动为头像或发布内容选图时调用，并只处理你选中的图片。',
          '网络：用于登录、加载内容、上传图片和提交互动。',
          '目前不申请通讯录、精确位置、麦克风、蓝牙或后台定位权限，也不以一次授权捆绑无关功能。',
        ],
      },
      {
        title: '4. 使用、共享与公开展示',
        paragraphs: [
          '我们仅为实现你选择的功能、安全保障和依法履责使用信息，不出售个人信息，不将信息用于与你的请求无关的营销。',
          '昵称、头像、学校/校区和你选择公开的内容会按可见范围展示给其他用户。联系方式属于高风险信息，发布前请确认必要性并避免填写身份证号、校园卡号、住址、银行卡等敏感信息。',
          '除依法配合监管、保护人身财产安全或取得你的单独同意外，我们不会向无关第三方提供个人信息。微信平台基础能力依其规则处理必要信息。',
        ],
      },
      {
        title: '5. 保存与安全',
        paragraphs: [
          '账号资料和发布内容通常保存至你删除内容或注销账号；本地搜索记录和发布草稿保存在当前设备，可在“设置与隐私”随时清理。',
          '注销后，账号标识和资料会停止使用并删除或匿名化；法律法规要求留存的安全日志仅在规定期限和目的内保存，到期后删除或匿名化。',
          '我们采用 HTTPS 传输、访问控制、最小权限和日志审计等措施。互联网服务无法保证绝对安全，如发生可能影响你权益的事件，我们将依法处置和通知。',
        ],
      },
      {
        title: '6. 你的权利',
        items: [
          '查阅和更正：在“我的—编辑资料”查看或修改头像、昵称和校园资料。',
          '删除：删除自己的发布，或在设置页清理本地草稿与搜索记录。',
          '撤回同意：在设置页撤回隐私同意并退出登录；撤回不影响此前基于同意完成的处理。',
          '注销账号：在“设置与隐私—账号与安全”发起注销；注销将删除或匿名化账号资料、发布和互动，且不可恢复。',
          '投诉与解释：通过“我的—帮助与反馈”提交请求。我们会核验身份并在法律规定期限内处理。',
        ],
      },
      {
        title: '7. 未成年人保护与政策更新',
        paragraphs: [
          '云点校园主要面向大学生。未满十四周岁的用户请勿自行注册或发布内容；确需使用时应由监护人阅读规则并依法提供同意。',
          '如处理目的、信息种类或权限发生实质变化，我们会更新版本并重新取得必要同意。你拒绝更新后的非必要处理，不影响使用与其无关的基础浏览功能。',
        ],
      },
    ],
  },
  agreement: {
    title: '云点校园用户协议',
    eyebrow: '使用服务前请仔细阅读',
    summary: '这份协议说明账号使用、内容发布、校园交易和平台治理的基本边界。你可以不同意并继续以游客身份浏览公开内容。',
    sections: [
      { title: '1. 服务与账号', items: ['你应使用真实、合法的信息完成校园资料，不得冒用他人身份或批量注册账号。', '账号仅限本人使用。请妥善保管设备和登录状态，发现异常及时退出并反馈。', '游客可浏览公开内容；发布、点赞、收藏、评论、联系发布者等功能需要登录。'] },
      { title: '2. 内容责任', items: ['你对上传的文字、图片、价格、联系方式及其他信息的真实性、合法性和必要授权负责。', '不得发布违法违规、诈骗引流、侵权盗图、造谣攻击、色情低俗、危险品交易、考试作弊或侵犯隐私的内容。', '发现违规内容可通过内容详情页举报或“帮助与反馈”提交线索。平台可依规则采取限流、下架、删除、暂停功能或封禁账号等措施。'] },
      { title: '3. 校园交易与互助', items: ['平台提供信息发布与沟通工具，不是商品或服务的交易相对方，也不代替双方验货、付款或履约。', '建议校内公共场所当面核验，不提前支付大额定金，不点击不明链接，不向陌生人提供验证码、身份证、银行卡或校园卡信息。', '拼车、活动和线下互助应优先考虑人身安全；遇到紧急情况请立即联系学校或公安机关。'] },
      { title: '4. 知识产权与许可', paragraphs: ['你保留原创内容权利，并授予平台在提供服务所必需范围内进行存储、展示、适配和传播的非独占许可。你删除内容或注销账号后，该许可原则上终止，但依法留存、他人已合法分享或争议处理所必需的情形除外。'] },
      { title: '5. 服务变更与责任边界', paragraphs: ['我们会尽力保障服务连续与数据安全，但可能因维护、网络故障、不可抗力或监管要求暂停部分功能。法律允许范围内，各方按过错承担责任；本条不排除法律规定不得限制的责任。'] },
      { title: '6. 终止与争议', paragraphs: ['你可停止使用、退出登录或注销账号。协议的订立、履行和解释适用中华人民共和国法律；发生争议时优先协商，协商不成的，依法向有管辖权的人民法院提起诉讼。'] },
    ],
  },
  community: {
    title: '校园社区发布规范',
    eyebrow: '真实 · 友善 · 安全',
    summary: '发布前请确认内容真实、授权完整、分类准确。保护自己，也尊重每一位同学。',
    sections: [
      { title: '鼓励发布', items: ['真实闲置、校园互助、合规拼车、探店体验、失物招领和社团活动。', '清晰标题、实拍图片、合理价格、准确时间地点和必要的物品瑕疵说明。', '友善讨论、真实反馈和对他人有帮助的经验。'] },
      { title: '禁止发布', items: ['违法违禁品、烟草药品、账号证件、管制器具、盗版侵权和不明来源物品。', '刷单兼职、博彩借贷、传销诈骗、站外恶意引流、虚假中奖或索取验证码。', '色情低俗、仇恨歧视、辱骂骚扰、造谣、人肉搜索、未经同意公开他人照片或联系方式。', '代考、替课、论文代写、伪造证明、破解校园系统等破坏教学和校园管理秩序的行为。'] },
      { title: '隐私与交易安全', items: ['图片发布前遮挡人脸、门牌、快递单、学号、校园卡号和证件号码。', '联系方式尽量在确认需求后私下提供；不要在正文公开身份证、住址、银行卡、验证码等敏感信息。', '二手交易优先校内公共场所当面验货；价格明显异常、要求提前转账或跳转陌生平台时立即停止。'] },
      { title: '分类补充规则', items: ['拼车：说明时间、起终点、费用分摊与车型，不发布非法营运信息。', '失物招领：证明物品所有权时避免公开完整特征，证件类优先交学校保卫处。', '社团活动：说明主办方、时间地点和收费情况，不冒用学校或社团官方名义。', '探店：标注真实体验和商业合作，不发布虚假评价或未声明的广告。'] },
      { title: '违规处理', paragraphs: ['平台可结合严重程度采取提醒修改、限制展示、下架删除、限制发布、暂停或终止账号等措施；涉嫌违法犯罪或紧急人身风险的，依法配合有关机关处理。对处理结果有异议，可通过“帮助与反馈”申诉。'] },
    ],
  },
  permissions: {
    title: '权限与个人信息清单',
    eyebrow: '按需调用 · 用完即停',
    summary: '云点校园不申请后台定位、通讯录、麦克风或蓝牙。你可以拒绝非必要权限，公开浏览仍可继续使用。',
    sections: [
      { title: '微信登录标识', items: ['触发：你点击“微信一键登录”。', '用途：换取 OpenID，识别同一账号并保持登录。', '拒绝影响：不能发布和互动，但仍可浏览公开内容。'] },
      { title: '头像与昵称', items: ['触发：你点击“选择微信头像”或填写昵称。', '用途：完善校园资料并在社区展示身份。', '控制：可在“我的—编辑资料”更换。'] },
      { title: '选中的照片或视频信息', items: ['触发：选择头像、发布图片或调用相机拍摄。', '用途：上传你选中的图片；不会扫描未选择的相册内容。', '拒绝影响：无法上传图片，其他不依赖图片的功能仍可使用。'] },
      { title: '本地存储', items: ['内容：登录凭证、隐私同意版本、发布草稿、搜索记录和校园选择。', '用途：保持登录、恢复草稿、改善本机使用体验。', '控制：可在“设置与隐私”清理本地数据或退出登录。'] },
      { title: '当前未申请', items: ['精确位置或后台定位、通讯录、麦克风、蓝牙、日历、运动健康、剪贴板自动读取。', '如未来新增确有必要的权限，我们会先更新说明，并在具体场景中单独请求。'] },
    ],
  },
};

const document = computed(() => documents[type.value]);

onLoad((query) => {
  const nextType = query?.type as PolicyType;
  if (nextType && documents[nextType])
    type.value = nextType;
  uni.setNavigationBarTitle({ title: document.value.title });
});
</script>

<template>
  <view class="policy-page">
    <view class="hero">
      <view class="hero-icon">
        安
      </view>
      <view class="eyebrow">
        {{ document.eyebrow }}
      </view>
      <view class="hero-title">
        {{ document.title }}
      </view>
      <view class="hero-summary">
        {{ document.summary }}
      </view>
    </view>

    <view v-for="section in document.sections" :key="section.title" class="policy-card">
      <view class="section-title">
        {{ section.title }}
      </view>
      <view v-for="paragraph in section.paragraphs" :key="paragraph" class="paragraph">
        {{ paragraph }}
      </view>
      <view v-for="item in section.items" :key="item" class="policy-item">
        <view class="dot" />
        <text>{{ item }}</text>
      </view>
      <view v-if="section.alert" class="policy-alert">
        <view>!</view><text>{{ section.alert }}</text>
      </view>
    </view>

    <view class="policy-footer">
      最近更新：2026 年 7 月 13 日 · 生效日期：2026 年 7 月 13 日
    </view>
  </view>
</template>

<style lang="scss" scoped>
.policy-page {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(56rpx + env(safe-area-inset-bottom));
  color: #1d1d1f;
  background: var(--yd-paper);
}
.hero {
  padding: 38rpx 32rpx 34rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.84);
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 22rpx 56rpx rgba(31, 45, 70, 0.09);
  backdrop-filter: blur(30rpx) saturate(150%);
}
.hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: 23rpx;
  color: #fff;
  background: var(--yd-green);
  box-shadow: 0 12rpx 28rpx rgba(10, 132, 255, 0.22);
  font-size: 30rpx;
  font-weight: 800;
}
.eyebrow {
  margin-top: 28rpx;
  color: var(--yd-green);
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.hero-title {
  margin-top: 10rpx;
  font-size: 39rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
}
.hero-summary {
  margin-top: 18rpx;
  color: #666a73;
  font-size: 25rpx;
  line-height: 1.72;
}
.policy-card {
  margin-top: 20rpx;
  padding: 28rpx 28rpx 30rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.8);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 15rpx 40rpx rgba(31, 45, 70, 0.065);
  backdrop-filter: blur(26rpx) saturate(145%);
}
.section-title {
  margin-bottom: 18rpx;
  font-size: 29rpx;
  font-weight: 750;
}
.paragraph,
.policy-item {
  color: #5f636b;
  font-size: 24rpx;
  line-height: 1.8;
}
.paragraph + .paragraph,
.policy-item + .policy-item,
.paragraph + .policy-item,
.policy-item + .paragraph {
  margin-top: 12rpx;
}
.policy-item {
  display: flex;
  align-items: flex-start;
}
.policy-item text {
  flex: 1;
}
.dot {
  flex: 0 0 auto;
  width: 9rpx;
  height: 9rpx;
  margin: 17rpx 16rpx 0 2rpx;
  border-radius: 50%;
  background: #64a9ef;
}
.policy-alert {
  display: flex;
  gap: 14rpx;
  margin-top: 22rpx;
  padding: 20rpx;
  border: 1rpx solid rgba(255, 159, 10, 0.18);
  border-radius: 18rpx;
  color: #785719;
  background: rgba(255, 159, 10, 0.09);
  font-size: 22rpx;
  line-height: 1.6;
}
.policy-alert view {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 30rpx;
  height: 30rpx;
  margin-top: 2rpx;
  border-radius: 50%;
  color: #fff;
  background: #ff9f0a;
  font-size: 20rpx;
  font-weight: 800;
}
.policy-footer {
  padding: 38rpx 16rpx 8rpx;
  color: #969aa2;
  font-size: 20rpx;
  line-height: 1.6;
  text-align: center;
}
</style>
