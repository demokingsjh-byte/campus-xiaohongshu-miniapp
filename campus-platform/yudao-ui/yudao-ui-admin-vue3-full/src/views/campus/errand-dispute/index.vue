<template>
  <div class="dispute-page">
    <section class="hero">
      <div><span>ERRAND DISPUTE</span><h1>代办申诉仲裁</h1><p>申诉期间赏金保持冻结，裁决后才结算给接单人或原路退回发布人。</p></div>
      <el-button :loading="loading" @click="getList"><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button>
    </section>
    <ContentWrap>
      <el-form :model="query" :inline="true" label-width="72px">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable class="!w-260px" placeholder="订单号、任务或双方昵称" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable class="!w-160px">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户ID"><el-input-number v-model="query.tenantId" :min="1" controls-position="right" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap>
      <el-table v-loading="loading" :data="list" row-key="orderId">
        <el-table-column label="订单 / 任务" min-width="250">
          <template #default="{ row }"><div class="order-cell"><strong>{{ row.title }}</strong><small>{{ row.orderNo }}</small><span>赏金 ¥{{ money(row.amount) }}</span></div></template>
        </el-table-column>
        <el-table-column label="发布人" min-width="140"><template #default="{ row }">{{ row.publisherName || row.publisherId }}<br/><small>{{ row.publisherMobile || '-' }}</small></template></el-table-column>
        <el-table-column label="接单人" min-width="140"><template #default="{ row }">{{ row.helperName || row.helperId }}<br/><small>{{ row.helperMobile || '-' }}</small></template></el-table-column>
        <el-table-column label="申诉原因" min-width="260" show-overflow-tooltip prop="disputeReason" />
        <el-table-column label="状态" width="150" align="center"><template #default="{ row }"><el-tag :type="tagType(row.disputeStatus)">{{ statusText(row.disputeStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="申诉时间" width="170"><template #default="{ row }">{{ time(row.disputedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="row.disputeStatus === 1" v-hasPermi="['campus:errand-dispute:resolve']" link type="warning" @click="openResolve(row)">裁决</el-button></template>
        </el-table-column>
      </el-table>
      <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="代办申诉详情" size="650px">
      <div v-if="detail" class="detail">
        <el-alert :title="statusText(detail.disputeStatus)" :type="detail.disputeStatus === 1 ? 'warning' : 'info'" :closable="false" show-icon />
        <section><h3>{{ detail.title }}</h3><p>订单 {{ detail.orderNo }} · 赏金 ¥{{ money(detail.amount) }}</p></section>
        <section><h3>接单人完成说明</h3><p>{{ detail.completionNote || '未填写' }}</p><div class="images"><el-image v-for="url in images(detail.completionImagesJson)" :key="url" :src="url" :preview-src-list="images(detail.completionImagesJson)" fit="cover" /></div></section>
        <section><h3>发布人申诉原因</h3><p>{{ detail.disputeReason }}</p><div class="images"><el-image v-for="url in images(detail.disputeImagesJson)" :key="url" :src="url" :preview-src-list="images(detail.disputeImagesJson)" fit="cover" /></div></section>
        <section><h3>任务沟通记录</h3><div v-if="detail.messages?.length" class="messages"><p v-for="item in detail.messages" :key="item.id"><strong>{{ item.senderName || item.senderId }}</strong><span>{{ time(item.createTime) }}</span><br/>{{ item.content }}</p></div><p v-else>暂无站内沟通记录</p></section>
        <section v-if="detail.disputeResolution"><h3>裁决说明</h3><p>{{ detail.disputeResolution }}</p></section>
        <div v-if="detail.disputeStatus === 1" class="actions"><el-button type="primary" @click="openResolve(detail)">进行裁决</el-button></div>
      </div>
    </el-drawer>

    <el-dialog v-model="resolveVisible" title="代办申诉裁决" width="560px">
      <el-alert title="裁决不可撤销：接单人胜诉会立即结算收益；发布人胜诉会提交微信原路退款。" type="warning" :closable="false" />
      <el-radio-group v-model="resolveResult" class="result-group">
        <el-radio-button :value="2">接单人胜诉 · 结算赏金</el-radio-button>
        <el-radio-button :value="3">发布人胜诉 · 原路退款</el-radio-button>
      </el-radio-group>
      <el-input v-model="resolution" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写依据和处理说明" />
      <template #footer><el-button @click="resolveVisible = false">取消</el-button><el-button type="primary" :loading="resolving" @click="submitResolve">确认裁决</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { CampusErrandDisputeItem, CampusErrandDisputeQuery } from '@/api/campus/errand-dispute'
import { getCampusErrandDispute, getCampusErrandDisputePage, resolveCampusErrandDispute } from '@/api/campus/errand-dispute'

defineOptions({ name: 'CampusErrandDispute' })
const message = useMessage()
const loading = ref(false)
const resolving = ref(false)
const list = ref<CampusErrandDisputeItem[]>([])
const total = ref(0)
const query = reactive<CampusErrandDisputeQuery>({ pageNo: 1, pageSize: 20, status: 1 })
const detailVisible = ref(false)
const detail = ref<CampusErrandDisputeItem>()
const resolveVisible = ref(false)
const resolveTarget = ref<CampusErrandDisputeItem>()
const resolveResult = ref<2 | 3>(2)
const resolution = ref('')
const statusOptions = [{ label: '待平台处理', value: 1 }, { label: '接单人胜诉', value: 2 }, { label: '发布人胜诉', value: 3 }]

const getList = async () => { loading.value = true; try { const data = await getCampusErrandDisputePage(query); list.value = data.list; total.value = data.total } finally { loading.value = false } }
const search = () => { query.pageNo = 1; void getList() }
const reset = () => { Object.assign(query, { pageNo: 1, pageSize: 20, status: 1 }); delete query.keyword; delete query.tenantId; void getList() }
const openDetail = async (row: CampusErrandDisputeItem) => { detailVisible.value = true; detail.value = await getCampusErrandDispute(row.orderId) }
const openResolve = (row: CampusErrandDisputeItem) => { resolveTarget.value = row; resolveResult.value = 2; resolution.value = ''; resolveVisible.value = true }
const submitResolve = async () => {
  if (!resolveTarget.value || !resolution.value.trim()) { message.warning('请填写裁决说明'); return }
  const label = resolveResult.value === 2 ? '结算给接单人' : '原路退回发布人'
  await message.confirm(`确认${label}吗？该操作不可撤销。`)
  resolving.value = true
  try { await resolveCampusErrandDispute({ orderId: resolveTarget.value.orderId, result: resolveResult.value, resolution: resolution.value.trim() }); message.success('裁决已生效'); resolveVisible.value = false; detailVisible.value = false; await getList() } finally { resolving.value = false }
}
const images = (json?: string) => { try { const data = JSON.parse(json || '[]'); return Array.isArray(data) ? data : [] } catch { return [] } }
const statusText = (status: number) => statusOptions.find((item) => item.value === status)?.label || '无申诉'
type TagType = 'warning' | 'success' | 'danger' | 'info'
const tagType = (status: number): TagType => ({ 1: 'warning', 2: 'success', 3: 'danger' })[status] as TagType || 'info'
const money = (value?: number) => Number(value || 0).toFixed(2)
const time = (value?: string) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
onMounted(() => void getList())
</script>

<style lang="scss" scoped>
.dispute-page { min-height: calc(100vh - 84px); padding: 20px; background: #f4f7f7; }
.hero { display: flex; align-items: center; justify-content: space-between; padding: 28px 32px; margin-bottom: 16px; color: #fff; background: linear-gradient(120deg, #9b4c32, #d0784f); border-radius: 20px; }
.hero span { font-size: 10px; letter-spacing: .18em; opacity: .7; }.hero h1 { margin: 5px 0; font-size: 28px; }.hero p { margin: 0; opacity: .82; }
.order-cell { display: grid; gap: 5px; }.order-cell small, td small { color: #87938f; }.order-cell span { color: #dc6249; font-weight: 700; }
.detail { display: grid; gap: 16px; }.detail section { padding: 18px; background: #f7faf9; border-radius: 14px; }.detail h3 { margin: 0 0 9px; }.detail p { margin: 0; line-height: 1.7; white-space: pre-wrap; }
.images { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 12px; }.images .el-image { width: 100%; aspect-ratio: 1; border-radius: 10px; }.actions { display: flex; justify-content: flex-end; }
.messages { display: grid; gap: 10px; }.messages p { padding: 10px 12px; background: #fff; border-radius: 9px; }.messages span { margin-left: 10px; color: #8a9794; font-size: 12px; }
.result-group { display: flex; margin: 18px 0; }.result-group .el-radio-button { flex: 1; }
</style>
