<template>
  <div class="order-center">
    <section class="order-hero">
      <div>
        <span class="hero-kicker">CAMPUS TRADE</span>
        <h1>订单中心</h1>
        <p>集中查看校园二手订单、微信支付结果与退款进度</p>
      </div>
      <el-button class="hero-refresh" :loading="loading" @click="refreshAll">
        <Icon icon="ep:refresh" class="mr-5px" />刷新数据
      </el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-card">
        <span class="metric-icon" :style="{ '--metric-color': item.color }">
          <Icon :icon="item.icon" :size="22" />
        </span>
        <div>
          <small>{{ item.label }}</small>
          <strong>{{ item.value }}</strong>
          <p>{{ item.note }}</p>
        </div>
      </article>
    </section>

    <section class="glass-panel filter-panel">
      <el-form :model="queryParams" :inline="true" label-width="74px">
        <el-form-item label="订单号">
          <el-input
            v-model="queryParams.orderNo"
            clearable
            class="!w-220px"
            placeholder="输入商户订单号"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            clearable
            class="!w-220px"
            placeholder="商品、买家、卖家、手机号"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="queryParams.status" clearable class="!w-140px" placeholder="全部">
            <el-option v-for="item in orderStatuses" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="退款状态">
          <el-select v-model="queryParams.refundStatus" clearable class="!w-150px" placeholder="全部">
            <el-option v-for="item in refundStatuses" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="createTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            class="!w-360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetQuery">
            <Icon icon="ep:refresh-left" class="mr-5px" />重置
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="glass-panel table-panel">
      <div class="panel-title">
        <div>
          <h2>订单记录</h2>
          <p>退款按钮仅对已付款或已完成订单开放，并且始终原路全额退回</p>
        </div>
        <el-tag effect="plain" round>共 {{ total }} 笔</el-tag>
      </div>

      <el-table v-loading="loading" :data="list" class="order-table" row-key="id">
        <el-table-column label="商品" min-width="250">
          <template #default="{ row }">
            <div class="product-cell">
              <el-image :src="row.coverImage" fit="cover" class="product-cover">
                <template #error><Icon icon="ep:picture" :size="24" /></template>
              </el-image>
              <div>
                <strong>{{ row.title || '商品信息已下架' }}</strong>
                <span>{{ row.schoolName || '校园' }} · {{ row.campusName || '校区' }}</span>
                <small>{{ row.orderNo }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="买家 / 卖家" min-width="180">
          <template #default="{ row }">
            <div class="people-cell">
              <span><b>买</b>{{ row.buyerName || `用户 ${row.buyerId}` }}</span>
              <span><b class="seller">卖</b>{{ row.sellerName || `用户 ${row.sellerId}` }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实付金额" width="118" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="108" align="center">
          <template #default="{ row }">
            <el-tag :type="orderTag(row.status)" effect="light" round>{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款状态" width="125" align="center">
          <template #default="{ row }">
            <el-tag :type="refundTag(row.refundStatus)" effect="plain" round>
              {{ row.refundStatusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付 / 下单时间" min-width="175">
          <template #default="{ row }">
            <div class="time-cell">
              <span>{{ formatTime(row.paidAt) }}</span>
              <small>下单 {{ formatTime(row.createTime) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="185" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.refundStatus === 1"
              v-hasPermi="['campus:trade-order:refund']"
              link
              type="warning"
              :loading="syncingId === row.id"
              @click="handleSyncRefund(row)"
            >
              同步退款
            </el-button>
            <el-button
              v-else-if="canRefund(row)"
              v-hasPermi="['campus:trade-order:refund']"
              link
              type="danger"
              @click="openRefund(row)"
            >
              退款
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </section>

    <el-drawer v-model="detailVisible" title="订单详情" size="580px">
      <div v-if="detail" class="detail-sheet">
        <div class="detail-product">
          <el-image :src="detail.coverImage" fit="cover" />
          <div>
            <h3>{{ detail.title }}</h3>
            <span class="money">¥{{ money(detail.amount) }}</span>
            <p>{{ detail.schoolName }} · {{ detail.campusName }} · {{ detail.location || '校内交易' }}</p>
          </div>
        </div>

        <div class="detail-section">
          <h4>订单与支付</h4>
          <InfoRow label="订单号" :value="detail.orderNo" copyable />
          <InfoRow label="订单状态" :value="detail.statusText" />
          <InfoRow label="微信交易号" :value="detail.wxTransactionId || '-'" copyable />
          <InfoRow label="微信交易状态" :value="detail.wechatTradeState || '-'" />
          <InfoRow label="支付时间" :value="formatTime(detail.paidAt)" />
          <InfoRow label="创建时间" :value="formatTime(detail.createTime)" />
        </div>

        <div class="detail-section two-columns">
          <div>
            <h4>买家</h4>
            <p>{{ detail.buyerName || '-' }}</p>
            <small>{{ detail.buyerMobile || '未留手机号' }}</small>
          </div>
          <div>
            <h4>卖家</h4>
            <p>{{ detail.sellerName || '-' }}</p>
            <small>{{ detail.sellerContact || detail.sellerMobile || '未留联系方式' }}</small>
          </div>
        </div>

        <div v-if="detail.refundStatus > 0" class="detail-section refund-detail">
          <h4>退款信息</h4>
          <InfoRow label="退款状态" :value="detail.refundStatusText" />
          <InfoRow label="商户退款单号" :value="detail.refundNo || '-'" copyable />
          <InfoRow label="微信退款单号" :value="detail.wxRefundId || '-'" copyable />
          <InfoRow label="退款原因" :value="detail.refundReason || '-'" />
          <InfoRow label="操作人" :value="detail.refundOperator || '-'" />
          <InfoRow label="退款成功时间" :value="formatTime(detail.refundedAt)" />
          <el-alert
            v-if="detail.refundError"
            :title="detail.refundError"
            type="error"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="refundVisible" title="确认原路全额退款" width="520px">
      <div v-if="refundOrder" class="refund-dialog">
        <el-alert
          title="退款将直接提交至微信支付，成功后不可撤销，请确认已与买卖双方沟通。"
          type="warning"
          :closable="false"
          show-icon
        />
        <div class="refund-order-line">
          <span>{{ refundOrder.title }}</span>
          <strong>¥{{ money(refundOrder.amount) }}</strong>
        </div>
        <el-form label-position="top">
          <el-form-item label="退款原因（将提交给微信支付）" required>
            <el-input
              v-model="refundReason"
              type="textarea"
              maxlength="80"
              show-word-limit
              :rows="4"
              placeholder="例如：买卖双方协商一致，取消本次交易"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="refundVisible = false">暂不退款</el-button>
        <el-button type="danger" :loading="refundLoading" @click="submitRefund">
          确认退款 ¥{{ money(refundOrder?.amount) }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  getCampusTradeOrder,
  getCampusTradeOrderPage,
  getCampusTradeOrderSummary,
  refundCampusTradeOrder,
  syncCampusTradeRefund,
  type CampusTradeOrder,
  type CampusTradeOrderQuery,
  type CampusTradeOrderSummary
} from '@/api/campus/order'
import { formatDate } from '@/utils/formatTime'
import InfoRow from './InfoRow.vue'

defineOptions({ name: 'CampusTradeOrder' })

const message = useMessage()
const loading = ref(false)
const list = ref<CampusTradeOrder[]>([])
const total = ref(0)
const summary = ref<CampusTradeOrderSummary>({
  totalCount: 0,
  waitingCount: 0,
  paidCount: 0,
  refundedCount: 0,
  paidAmount: 0,
  refundedAmount: 0
})
const queryParams = reactive<CampusTradeOrderQuery>({ pageNo: 1, pageSize: 20 })
const createTimeRange = ref<string[]>([])
const detailVisible = ref(false)
const detail = ref<CampusTradeOrder>()
const refundVisible = ref(false)
const refundOrder = ref<CampusTradeOrder>()
const refundReason = ref('')
const refundLoading = ref(false)
const syncingId = ref<number>()

const orderStatuses = [
  { label: '待付款', value: 0 },
  { label: '已付款', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已关闭', value: 3 },
  { label: '已退款', value: 4 }
]
const refundStatuses = [
  { label: '未退款', value: 0 },
  { label: '处理中', value: 1 },
  { label: '退款成功', value: 2 },
  { label: '退款失败', value: 3 }
]

const metrics = computed(() => [
  {
    label: '订单总量',
    value: Number(summary.value.totalCount || 0).toLocaleString(),
    note: '全部校园交易记录',
    icon: 'ep:tickets',
    color: '#2563eb'
  },
  {
    label: '待付款',
    value: Number(summary.value.waitingCount || 0).toLocaleString(),
    note: '等待买家完成支付',
    icon: 'ep:clock',
    color: '#f59e0b'
  },
  {
    label: '已支付订单',
    value: Number(summary.value.paidCount || 0).toLocaleString(),
    note: `累计实付 ¥${money(summary.value.paidAmount)}`,
    icon: 'ep:circle-check',
    color: '#0cab7c'
  },
  {
    label: '已退款',
    value: Number(summary.value.refundedCount || 0).toLocaleString(),
    note: `累计退款 ¥${money(summary.value.refundedAmount)}`,
    icon: 'ep:refresh-left',
    color: '#ef6464'
  }
])

const getList = async () => {
  loading.value = true
  try {
    queryParams.createTimeStart = createTimeRange.value?.[0]
    queryParams.createTimeEnd = createTimeRange.value?.[1]
    const data = await getCampusTradeOrderPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const getSummary = async () => {
  summary.value = await getCampusTradeOrderSummary(queryParams.tenantId)
}

const refreshAll = async () => {
  await Promise.all([getList(), getSummary()])
}

const handleQuery = () => {
  queryParams.pageNo = 1
  void refreshAll()
}

const resetQuery = () => {
  Object.assign(queryParams, { pageNo: 1, pageSize: 20 })
  delete queryParams.orderNo
  delete queryParams.keyword
  delete queryParams.status
  delete queryParams.refundStatus
  delete queryParams.tenantId
  delete queryParams.createTimeStart
  delete queryParams.createTimeEnd
  createTimeRange.value = []
  void refreshAll()
}

const openDetail = async (row: CampusTradeOrder) => {
  detailVisible.value = true
  detail.value = await getCampusTradeOrder(row.id)
}

const openRefund = (row: CampusTradeOrder) => {
  refundOrder.value = row
  refundReason.value = ''
  refundVisible.value = true
}

const submitRefund = async () => {
  if (!refundOrder.value) return
  if (!refundReason.value.trim()) {
    message.warning('请填写退款原因')
    return
  }
  await message.confirm(
    `确认将订单 ${refundOrder.value.orderNo} 的 ¥${money(refundOrder.value.amount)} 原路退回吗？`
  )
  refundLoading.value = true
  try {
    const result = await refundCampusTradeOrder({
      orderId: refundOrder.value.id,
      reason: refundReason.value.trim()
    })
    message.success(result.refundStatus === 2 ? '退款已成功' : '退款申请已提交，请稍后同步状态')
    refundVisible.value = false
    await refreshAll()
  } finally {
    refundLoading.value = false
  }
}

const handleSyncRefund = async (row: CampusTradeOrder) => {
  syncingId.value = row.id
  try {
    const result = await syncCampusTradeRefund(row.id)
    if (result.refundStatus === 2) message.success('退款已成功')
    else if (result.refundStatus === 3) message.error(result.refundError || '退款失败')
    else message.info('微信仍在处理退款')
    await refreshAll()
  } finally {
    syncingId.value = undefined
  }
}

const canRefund = (row: CampusTradeOrder) =>
  (row.status === 1 || row.status === 2) && row.refundStatus !== 2

const money = (value?: number) => Number(value || 0).toFixed(2)
const formatTime = (value?: string) => (value ? formatDate(value) : '-')
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
const orderTagMap: Record<number, TagType> = {
  0: 'warning',
  1: 'success',
  2: 'success',
  3: 'info',
  4: 'danger'
}
const refundTagMap: Record<number, TagType> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger'
}
const orderTag = (status: number): TagType =>
  orderTagMap[status] || 'info'
const refundTag = (status: number): TagType =>
  refundTagMap[status] || 'info'

onMounted(() => void refreshAll())
</script>

<style lang="scss" scoped>
.order-center {
  min-height: calc(100vh - 84px);
  padding: 20px;
  background:
    radial-gradient(circle at 8% 0%, rgb(13 184 137 / 13%), transparent 28%),
    radial-gradient(circle at 95% 12%, rgb(37 99 235 / 10%), transparent 24%),
    #f4f7f7;
}

.order-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 27px 31px;
  margin-bottom: 16px;
  color: #fff;
  background: linear-gradient(120deg, #075f53 0%, #0b9276 52%, #22b790 100%);
  border-radius: 22px;
  box-shadow: 0 18px 42px rgb(4 111 89 / 20%);

  h1 {
    margin: 3px 0 5px;
    font-size: 28px;
  }

  p {
    margin: 0;
    color: rgb(255 255 255 / 76%);
  }
}

.hero-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: rgb(255 255 255 / 60%);
}

.hero-refresh {
  color: #fff;
  background: rgb(255 255 255 / 14%);
  border-color: rgb(255 255 255 / 28%);
  backdrop-filter: blur(12px);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card,
.glass-panel {
  background: rgb(255 255 255 / 86%);
  border: 1px solid rgb(255 255 255 / 90%);
  box-shadow: 0 10px 28px rgb(32 77 68 / 7%);
  backdrop-filter: blur(18px);
}

.metric-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px;
  border-radius: 18px;

  small,
  strong,
  p {
    display: block;
  }

  small {
    color: #7a8b88;
  }

  strong {
    margin: 3px 0;
    font-size: 24px;
    color: #16342e;
  }

  p {
    margin: 0;
    font-size: 11px;
    color: #94a19f;
  }
}

.metric-icon {
  display: flex;
  width: 46px;
  height: 46px;
  color: var(--metric-color);
  background: color-mix(in srgb, var(--metric-color) 11%, white);
  border-radius: 14px;
  flex: none;
  align-items: center;
  justify-content: center;
}

.glass-panel {
  padding: 20px;
  border-radius: 20px;
}

.filter-panel {
  padding-bottom: 2px;
  margin-bottom: 16px;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 0 0 4px;
    font-size: 18px;
    color: #17342f;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #8b9a98;
  }
}

.order-table {
  --el-table-border-color: #edf2f1;
  --el-table-header-bg-color: #f6faf9;
  --el-table-row-hover-bg-color: #f1faf7;
}

.product-cell {
  display: flex;
  gap: 11px;
  align-items: center;
  min-width: 0;

  > div {
    min-width: 0;
  }

  strong,
  span,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #203b36;
  }

  span {
    margin: 3px 0;
    font-size: 11px;
    color: #82918e;
  }

  small {
    font-size: 10px;
    color: #a1adaa;
  }
}

.product-cover {
  display: flex;
  width: 58px;
  height: 58px;
  color: #9eb0ac;
  background: #eef4f2;
  border-radius: 12px;
  flex: none;
  align-items: center;
  justify-content: center;
}

.people-cell,
.time-cell {
  display: grid;
  gap: 5px;

  span {
    color: #3a514d;
  }

  small {
    color: #9aa7a4;
  }
}

.people-cell b {
  display: inline-flex;
  width: 20px;
  height: 20px;
  margin-right: 7px;
  font-size: 10px;
  color: #0a8c70;
  background: #e8f7f2;
  border-radius: 7px;
  align-items: center;
  justify-content: center;

  &.seller {
    color: #596cdb;
    background: #eef0ff;
  }
}

.money {
  font-weight: 700;
  color: #e65c4d;
}

.detail-sheet {
  display: grid;
  gap: 14px;
}

.detail-product,
.detail-section {
  padding: 18px;
  background: #f7faf9;
  border: 1px solid #e9f0ee;
  border-radius: 16px;
}

.detail-product {
  display: flex;
  gap: 14px;

  .el-image {
    width: 92px;
    height: 92px;
    border-radius: 14px;
  }

  h3 {
    margin: 3px 0 8px;
  }

  p {
    font-size: 12px;
    color: #84938f;
  }
}

.detail-section h4 {
  margin: 0 0 13px;
  color: #27433e;
}

.two-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;

  p {
    margin: 0 0 6px;
  }

  small {
    color: #81908d;
  }
}

.refund-detail {
  background: #fff8f7;
  border-color: #f5dfda;
}

:deep(.info-row) {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed #e4ebe9;

  > span {
    color: #82908d;
  }

  > div {
    max-width: 68%;
    text-align: right;
  }

  strong {
    font-weight: 500;
    color: #334b46;
    word-break: break-all;
  }
}

:deep(.copy-link) {
  padding: 0;
  margin-left: 7px;
  color: #0b9879;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.refund-dialog {
  display: grid;
  gap: 18px;
}

.refund-order-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px;
  background: #f7faf9;
  border-radius: 12px;
}

@media (width <= 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 720px) {
  .order-center {
    padding: 12px;
  }

  .order-hero {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
