<script lang="ts" setup>
import type { CampusTradeMessage } from '@/services/api/content';
import { getCampusTradeMessages, sendCampusTradeMessage } from '@/services/api/content';

const props = defineProps<{
  orderId: number
  currentUserId?: number
  participantName?: string
  readonly?: boolean
  scene?: 'trade' | 'errand'
}>();

const messages = ref<CampusTradeMessage[]>([]);
const draft = ref('');
const loading = ref(true);
const sending = ref(false);
const loadFailed = ref(false);
const isErrand = computed(() => props.scene === 'errand');
const lastMessageAnchor = computed(() => messages.value.length
  ? `trade-message-${messages.value[messages.value.length - 1].id}`
  : '');
let pollingTimer: ReturnType<typeof setInterval> | null = null;

watch(() => props.orderId, () => void loadMessages(), { immediate: true });

onMounted(() => {
  pollingTimer = setInterval(() => void loadMessages(true), 5000);
});

onUnmounted(() => {
  if (pollingTimer)
    clearInterval(pollingTimer);
});

async function loadMessages(silent = false) {
  if (!props.orderId)
    return;
  if (!silent)
    loading.value = true;
  try {
    messages.value = await getCampusTradeMessages(props.orderId) || [];
    loadFailed.value = false;
  } catch {
    if (!silent)
      loadFailed.value = true;
  } finally {
    if (!silent)
      loading.value = false;
  }
}

async function sendMessage() {
  const content = draft.value.trim();
  if (!content || sending.value || props.readonly)
    return;
  sending.value = true;
  try {
    const created = await sendCampusTradeMessage(props.orderId, content);
    messages.value.push(created);
    draft.value = '';
    loadFailed.value = false;
  } catch (error: any) {
    const raw = String(error?.message || error?.errMsg || '消息发送失败，请稍后重试');
    const matched = raw.match(/请求错误\[\d+\]：(.+)$/);
    uni.showToast({ title: (matched?.[1] || raw).slice(0, 60), icon: 'none' });
  } finally {
    sending.value = false;
  }
}

function isMine(message: CampusTradeMessage) {
  return Number(message.senderId) === Number(props.currentUserId);
}

function formatTime(value?: string | number[]) {
  if (!value)
    return '';
  const date = Array.isArray(value)
    ? new Date(value[0], Number(value[1]) - 1, value[2], value[3] || 0, value[4] || 0)
    : new Date(value);
  if (Number.isNaN(date.getTime()))
    return '';
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
}
</script>

<template>
  <view class="trade-chat-panel">
    <view class="chat-head">
      <view>
        <text class="chat-title">{{ isErrand ? '任务对话' : '交易对话' }}</text>
        <text class="chat-participant">与 {{ participantName || '交易对方' }} {{ isErrand ? '沟通取件、交付和完成情况' : '沟通验货和交付' }}</text>
      </view>
      <text class="chat-safe">敏感词防护已开启</text>
    </view>

    <view v-if="loading" class="chat-state">会话加载中…</view>
    <view v-else-if="loadFailed" class="chat-state chat-retry" @click="loadMessages()">会话加载失败，点击重试</view>
    <scroll-view
      v-else class="message-list" scroll-y :scroll-into-view="lastMessageAnchor"
      :show-scrollbar="false" scroll-with-animation
    >
      <view v-if="!messages.length" class="empty-chat">
        {{ isErrand ? '接单已确认，可以在这里沟通取件地点、办理要求和交付时间。' : '支付已完成，可以在这里和对方确认取货时间、地点及商品情况。' }}
      </view>
      <view
        v-for="message in messages" :id="`trade-message-${message.id}`" :key="message.id"
        class="message-row" :class="{ mine: isMine(message) }"
      >
        <text class="sender-name">{{ isMine(message) ? '我' : (message.senderName || participantName || '交易对方') }}</text>
        <view class="message-bubble">{{ message.content }}</view>
        <text class="message-time">{{ formatTime(message.createTime) }}</text>
      </view>
    </scroll-view>

    <view v-if="readonly" class="readonly-tip">当前订单状态不可继续发送消息，历史会话仍可查看。</view>
    <view v-else class="chat-composer">
      <textarea
        v-model="draft" class="chat-input" maxlength="500" auto-height
        :placeholder="isErrand ? '输入任务消息，请勿发送验证码、密码等敏感信息' : '输入交易消息，请勿发送验证码、密码等敏感信息'"
        :disabled="sending" @confirm="sendMessage"
      />
      <button class="send-button" :disabled="!draft.trim() || sending" @click="sendMessage">
        {{ sending ? '发送中' : '发送' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.trade-chat-panel {
  margin-bottom: 32rpx;
  padding: 28rpx 24rpx 24rpx;
  border-radius: 32rpx;
  background: #fff;
}

.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.chat-title,
.chat-participant {
  display: block;
}

.chat-title {
  color: #202321;
  font-size: 32rpx;
  font-weight: 600;
}

.chat-participant {
  margin-top: 8rpx;
  color: #8b918d;
  font-size: 23rpx;
}

.chat-safe {
  flex: 0 0 auto;
  padding: 7rpx 12rpx;
  border-radius: 12rpx;
  color: #168c65;
  background: #ecf9f4;
  font-size: 20rpx;
}

.message-list {
  height: 430rpx;
  margin-top: 24rpx;
  padding: 22rpx;
  box-sizing: border-box;
  border-radius: 22rpx;
  background: #f5f7f6;
}

.chat-state,
.empty-chat {
  padding: 70rpx 24rpx;
  color: #969c98;
  font-size: 24rpx;
  line-height: 1.6;
  text-align: center;
}

.chat-retry {
  color: #168c65;
}

.message-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
  flex-direction: column;
}

.message-row.mine {
  align-items: flex-end;
}

.sender-name,
.message-time {
  color: #9aa09c;
  font-size: 20rpx;
}

.message-bubble {
  max-width: 78%;
  margin: 7rpx 0;
  padding: 18rpx 22rpx;
  border-radius: 8rpx 22rpx 22rpx 22rpx;
  color: #303532;
  background: #fff;
  font-size: 27rpx;
  line-height: 1.55;
  word-break: break-all;
}

.message-row.mine .message-bubble {
  border-radius: 22rpx 8rpx 22rpx 22rpx;
  background: #dff8d2;
}

.chat-composer {
  display: flex;
  align-items: flex-end;
  margin-top: 20rpx;
  gap: 16rpx;
}

.chat-input {
  min-height: 74rpx;
  max-height: 180rpx;
  padding: 17rpx 20rpx;
  flex: 1;
  box-sizing: border-box;
  border-radius: 20rpx;
  color: #202321;
  background: #f3f5f4;
  font-size: 26rpx;
  line-height: 40rpx;
}

.send-button {
  width: 124rpx;
  height: 74rpx;
  margin: 0;
  padding: 0;
  border-radius: 20rpx;
  color: #17200f;
  background: #95f51f;
  font-size: 26rpx;
  line-height: 74rpx;
}

.send-button::after {
  border: 0;
}

.send-button[disabled] {
  opacity: 0.45;
}

.readonly-tip {
  margin-top: 20rpx;
  color: #969c98;
  font-size: 23rpx;
  text-align: center;
}
</style>
