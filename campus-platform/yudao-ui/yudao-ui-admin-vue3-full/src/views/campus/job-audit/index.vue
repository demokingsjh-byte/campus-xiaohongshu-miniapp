<template>
  <div class="job-audit-page">
    <section class="audit-hero">
      <div>
        <span>JOB CONTENT REVIEW</span>
        <h1>兼职信息审核</h1>
        <p>兼职发布后默认不公开，只有人工审核通过后才会进入小程序信息流。</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">
        <Icon icon="ep:refresh" class="mr-5px" />刷新
      </el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.label">
        <Icon :icon="item.icon" :size="22" :color="item.color" />
        <div><small>{{ item.label }}</small><strong>{{ item.value }}</strong></div>
      </article>
    </section>

    <ContentWrap>
      <el-form :model="queryParams" :inline="true" label-width="76px">
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword" clearable class="!w-230px"
            placeholder="标题、发布者或联系方式" @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="queryParams.status" clearable class="!w-140px" placeholder="全部">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="学校">
          <el-input v-model="queryParams.schoolName" clearable class="!w-160px" />
        </el-form-item>
        <el-form-item label="校区">
          <el-input v-model="queryParams.campusName" clearable class="!w-160px" />
        </el-form-item>
        <el-form-item label="租户ID">
          <el-input-number v-model="queryParams.tenantId" :min="1" controls-position="right" class="!w-130px" />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="createTimeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间" end-placeholder="结束时间" class="!w-360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh-left" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <div class="table-title">
        <div><h2>审核队列</h2><p>待审核内容按发布时间从早到晚排列</p></div>
        <el-tag type="warning" effect="plain">待审核 {{ summary.pendingCount || 0 }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column label="兼职信息" min-width="280">
          <template #default="{ row }">
            <div class="job-cell">
              <el-image :src="firstImage(row.imagesJson)" fit="cover">
                <template #error><Icon icon="ep:briefcase" :size="24" /></template>
              </el-image>
              <div>
                <strong>{{ row.title }}</strong>
                <span>{{ row.location || '未填写地点' }} · {{ row.tradeMode || '方式待沟通' }}</span>
                <small>{{ row.price ? `¥${row.price}` : '薪资面议' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布者" min-width="150">
          <template #default="{ row }">
            <div class="publisher-cell">
              <span>{{ row.publisherName || `用户 ${row.userId}` }}</span>
              <small>{{ row.contact || row.publisherMobile || '未留联系方式' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学校 / 校区" min-width="160">
          <template #default="{ row }">
            <div class="publisher-cell"><span>{{ row.schoolName }}</span><small>{{ row.campusName }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light" round>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 0">
              <el-button
                v-hasPermi="['campus:job-audit:review']" link type="success"
                :loading="reviewingId === row.id" @click="approve(row)"
              >通过</el-button>
              <el-button
                v-hasPermi="['campus:job-audit:review']" link type="danger" @click="openReject(row)"
              >驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
        :total="total" @pagination="getList"
      />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="兼职信息详情" size="620px">
      <div v-if="detail" class="detail-content">
        <div class="detail-heading">
          <el-tag :type="statusTag(detail.status)">{{ statusText(detail.status) }}</el-tag>
          <h2>{{ detail.title }}</h2>
          <p>{{ detail.schoolName }} · {{ detail.campusName }} · {{ detail.location || '未填写地点' }}</p>
        </div>
        <div v-if="images(detail.imagesJson).length" class="image-list">
          <el-image
            v-for="image in images(detail.imagesJson)" :key="image" :src="image"
            :preview-src-list="images(detail.imagesJson)" fit="cover"
          />
        </div>
        <section><h3>兼职说明</h3><p class="description">{{ detail.content }}</p></section>
        <section class="detail-grid">
          <div><small>薪资</small><strong>{{ detail.price ? `¥${detail.price}` : '面议' }}</strong></div>
          <div><small>参与方式</small><strong>{{ detail.tradeMode || '-' }}</strong></div>
          <div><small>发布者</small><strong>{{ detail.publisherName || `用户 ${detail.userId}` }}</strong></div>
          <div><small>联系方式</small><strong>{{ detail.contact || detail.publisherMobile || '-' }}</strong></div>
        </section>
        <el-alert
          v-if="detail.auditReason" :title="detail.auditReason"
          :type="detail.status === 2 ? 'error' : 'info'" :closable="false" show-icon
        />
        <div v-if="detail.status === 0" class="drawer-actions">
          <el-button type="danger" plain @click="openReject(detail)">驳回</el-button>
          <el-button type="success" :loading="reviewingId === detail.id" @click="approve(detail)">审核通过</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="rejectVisible" title="驳回兼职信息" width="520px">
      <el-alert title="驳回后该信息不会在小程序展示，发布者仍可在自己的发布记录中查看原因。" type="warning" :closable="false" />
      <el-input
        v-model="rejectReason" class="reject-input" type="textarea" :rows="4" maxlength="200"
        show-word-limit placeholder="请填写明确的驳回原因"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="reviewingId === rejectTarget?.id" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type {
  CampusJobAuditItem,
  CampusJobAuditQuery,
  CampusJobAuditSummary
} from '@/api/campus/job-audit'
import {
  getCampusJobAudit,
  getCampusJobAuditPage,
  getCampusJobAuditSummary,
  reviewCampusJob
} from '@/api/campus/job-audit'

defineOptions({ name: 'CampusJobAudit' })

const message = useMessage()
const loading = ref(false)
const reviewingId = ref<number>()
const list = ref<CampusJobAuditItem[]>([])
const total = ref(0)
const summary = ref<CampusJobAuditSummary>({ totalCount: 0, pendingCount: 0, approvedCount: 0, rejectedCount: 0 })
const queryParams = reactive<CampusJobAuditQuery>({ pageNo: 1, pageSize: 20, status: 0 })
const createTimeRange = ref<string[]>([])
const detailVisible = ref(false)
const detail = ref<CampusJobAuditItem>()
const rejectVisible = ref(false)
const rejectTarget = ref<CampusJobAuditItem>()
const rejectReason = ref('')

const statusOptions = [
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已驳回/下架', value: 2 }
]

const metrics = computed(() => [
  { label: '全部兼职', value: Number(summary.value.totalCount || 0), icon: 'ep:briefcase', color: '#2563eb' },
  { label: '待审核', value: Number(summary.value.pendingCount || 0), icon: 'ep:clock', color: '#f59e0b' },
  { label: '已通过', value: Number(summary.value.approvedCount || 0), icon: 'ep:circle-check', color: '#10b981' },
  { label: '已驳回/下架', value: Number(summary.value.rejectedCount || 0), icon: 'ep:circle-close', color: '#ef4444' }
])

const getList = async () => {
  loading.value = true
  try {
    queryParams.createTimeStart = createTimeRange.value?.[0]
    queryParams.createTimeEnd = createTimeRange.value?.[1]
    const data = await getCampusJobAuditPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const getSummary = async () => {
  summary.value = await getCampusJobAuditSummary(queryParams.tenantId)
}

const refreshAll = async () => Promise.all([getList(), getSummary()])

const handleQuery = () => {
  queryParams.pageNo = 1
  void refreshAll()
}

const resetQuery = () => {
  Object.assign(queryParams, { pageNo: 1, pageSize: 20, status: 0 })
  delete queryParams.keyword
  delete queryParams.tenantId
  delete queryParams.schoolName
  delete queryParams.campusName
  delete queryParams.createTimeStart
  delete queryParams.createTimeEnd
  createTimeRange.value = []
  void refreshAll()
}

const openDetail = async (row: CampusJobAuditItem) => {
  detailVisible.value = true
  detail.value = await getCampusJobAudit(row.id)
}

const approve = async (row: CampusJobAuditItem) => {
  await message.confirm(`确认通过“${row.title}”吗？通过后将立即在小程序展示。`)
  reviewingId.value = row.id
  try {
    await reviewCampusJob({ id: row.id, approved: true })
    message.success('审核通过，兼职信息已发布')
    detailVisible.value = false
    await refreshAll()
  } finally {
    reviewingId.value = undefined
  }
}

const openReject = (row: CampusJobAuditItem) => {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const submitReject = async () => {
  if (!rejectTarget.value) return
  if (!rejectReason.value.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  reviewingId.value = rejectTarget.value.id
  try {
    await reviewCampusJob({ id: rejectTarget.value.id, approved: false, reason: rejectReason.value.trim() })
    message.success('已驳回，该信息不会公开展示')
    rejectVisible.value = false
    detailVisible.value = false
    await refreshAll()
  } finally {
    reviewingId.value = undefined
  }
}

const images = (value?: string) => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}
const firstImage = (value?: string) => images(value)[0] || ''
const statusText = (status: number) => statusOptions.find((item) => item.value === status)?.label || '未知'
type TagType = 'success' | 'warning' | 'danger' | 'info'
const statusTag = (status: number): TagType => ({ 0: 'warning', 1: 'success', 2: 'danger' })[status] as TagType || 'info'
const formatTime = (value?: string) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'

onMounted(() => void refreshAll())
</script>

<style lang="scss" scoped>
.job-audit-page { min-height: calc(100vh - 84px); padding: 20px; background: #f4f7f7; }
.audit-hero { display: flex; align-items: center; justify-content: space-between; padding: 28px 32px; margin-bottom: 16px; color: #fff; background: linear-gradient(120deg, #5b3ca7, #8462d7); border-radius: 20px; }
.audit-hero span { font-size: 10px; letter-spacing: .18em; opacity: .65; }
.audit-hero h1 { margin: 5px 0; font-size: 28px; }
.audit-hero p { margin: 0; opacity: .78; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.metric-grid article { display: flex; gap: 14px; align-items: center; padding: 18px; background: #fff; border-radius: 16px; box-shadow: 0 8px 24px rgb(39 52 48 / 6%); }
.metric-grid small, .metric-grid strong { display: block; }
.metric-grid small { color: #83908d; }
.metric-grid strong { margin-top: 3px; font-size: 24px; color: #213c36; }
.table-title { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.table-title h2 { margin: 0 0 4px; font-size: 18px; }
.table-title p { margin: 0; color: #8b9895; }
.job-cell { display: flex; gap: 12px; align-items: center; }
.job-cell .el-image { width: 64px; height: 64px; flex: none; color: #a1aca9; background: #eef2f1; border-radius: 12px; }
.job-cell div, .publisher-cell { display: grid; min-width: 0; gap: 5px; }
.job-cell strong, .job-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.job-cell span, .job-cell small, .publisher-cell small { color: #8a9794; }
.job-cell small { color: #e46354; }
.detail-content { display: grid; gap: 18px; }
.detail-heading h2 { margin: 12px 0 7px; }
.detail-heading p, .description { color: #63736f; line-height: 1.8; white-space: pre-wrap; }
.image-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.image-list .el-image { width: 100%; aspect-ratio: 1; border-radius: 12px; }
.detail-content section { padding: 18px; background: #f7faf9; border-radius: 15px; }
.detail-content h3 { margin: 0 0 10px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }
.detail-grid small, .detail-grid strong { display: block; }
.detail-grid small { margin-bottom: 6px; color: #8a9794; }
.drawer-actions { display: flex; justify-content: flex-end; padding-top: 8px; }
.reject-input { margin-top: 18px; }
@media (width <= 900px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
