<template>
  <div class="esp32-log-page">
    <section class="log-hero">
      <div>
        <span>ESP32 ASSISTANT OBSERVABILITY</span>
        <h1>ESP32 助手链路日志</h1>
        <p>按设备查看每轮对话的用户图片、提问、模型回答，以及完整链路耗时。</p>
      </div>
      <el-button :loading="loading" plain @click="refreshAll">
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
      <el-form :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="设备编号">
          <el-input v-model="queryParams.deviceId" clearable class="!w-190px" placeholder="按设备编号筛选" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="请求编号">
          <el-input v-model="queryParams.requestId" clearable class="!w-220px" placeholder="按 request_id 筛选" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="链路状态">
          <el-select v-model="queryParams.status" clearable class="!w-155px" placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生时间">
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
        <div><h2>请求明细</h2><p>点击详情查看完整对话与原图；耗时均为毫秒。</p></div>
        <el-tag effect="plain">对话内容与链路耗时</el-tag>
      </div>
      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column label="发生时间" width="170" fixed="left">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="设备 / 请求" min-width="220">
          <template #default="{ row }">
            <div class="identity-cell"><strong>{{ row.deviceId }}</strong><small>{{ row.requestId }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="115" align="center">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" effect="light" round>{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="用户提问" min-width="230">
          <template #default="{ row }">
            <div class="conversation-summary" :class="{ 'empty-copy': !row.questionText }">{{ row.questionText || questionPlaceholder(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="模型回答" min-width="260">
          <template #default="{ row }">
            <div class="conversation-summary" :class="{ 'empty-copy': !row.answerText }">{{ row.answerText || answerPlaceholder(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="采集" width="90" align="right"><template #default="{ row }">{{ formatMs(row.captureMs) }}</template></el-table-column>
        <el-table-column label="提交模型" width="105" align="right"><template #default="{ row }">{{ formatMs(row.submitMs) }}</template></el-table-column>
        <el-table-column label="ASR" width="90" align="right"><template #default="{ row }">{{ formatMs(row.asrMs) }}</template></el-table-column>
        <el-table-column label="模型首 token" width="120" align="right"><template #default="{ row }">{{ formatMs(row.modelFirstTokenMs) }}</template></el-table-column>
        <el-table-column label="模型总耗时" width="110" align="right"><template #default="{ row }">{{ formatMs(row.modelTotalMs) }}</template></el-table-column>
        <el-table-column label="TTS 首包" width="100" align="right"><template #default="{ row }">{{ formatMs(row.ttsFirstAudioMs) }}</template></el-table-column>
        <el-table-column label="TTS 输出" width="100" align="right"><template #default="{ row }">{{ formatMs(row.ttsAudioMs) }}</template></el-table-column>
        <el-table-column label="总耗时" width="105" align="right">
          <template #default="{ row }"><strong class="total-ms">{{ formatMs(row.totalMs) }}</strong></template>
        </el-table-column>
        <el-table-column label="音频 / 图片" width="120" align="center">
          <template #default="{ row }">{{ formatBytes(row.audioBytes) }} / {{ row.imageCount || 0 }} 张</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
        :total="total" @pagination="getList"
      />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="对话与链路详情" size="min(760px, 100vw)" destroy-on-close>
      <template #header>
        <div class="drawer-heading">
          <h2>对话与链路详情</h2>
          <el-button :loading="detailLoading" :disabled="detailId == null" plain size="small" @click="reloadDetail">
            <Icon icon="ep:refresh" class="mr-5px" />刷新详情
          </el-button>
        </div>
      </template>
      <div v-if="detailLoading" class="detail-loading"><el-skeleton :rows="8" animated /></div>
      <el-result v-else-if="detailError" icon="warning" title="详情加载失败" sub-title="请检查网络后重试。">
        <template #extra><el-button type="primary" @click="reloadDetail">重新加载</el-button></template>
      </el-result>
      <div v-else-if="detail" class="detail-content">
        <div class="detail-heading">
          <el-tag :type="statusTag(detail.status)" effect="light" round>{{ statusText(detail.status) }}</el-tag>
          <h2>{{ detail.deviceId }}</h2>
          <p>{{ detail.requestId }} · {{ formatTime(detail.createTime) }}</p>
        </div>
        <el-alert
          v-if="!detail.contentRecorded"
          title="这条历史记录仅保存了耗时，未留存图片、提问和回答，无法补回。更新后的新对话会记录这些内容。"
          type="info" :closable="false" show-icon
        />
        <section class="conversation-section">
          <div class="section-heading">
            <h3>用户提问</h3>
            <el-tag v-if="detail.contentRecorded && detail.asrStatus" :type="asrTag(detail.asrStatus)" size="small" effect="plain">{{ asrText(detail.asrStatus) }}</el-tag>
          </div>
          <p v-if="detail.questionText" class="conversation-text">{{ detail.questionText }}</p>
          <p v-else class="empty-copy">{{ questionPlaceholder(detail) }}</p>
          <small v-if="detail.asrStatus === 'PENDING'" class="content-note">转写异步完成，点击“刷新详情”可查看最新结果。</small>
        </section>
        <section class="conversation-section">
          <div class="section-heading"><h3>用户图片</h3><small>{{ detail.storedImageCount || 0 }} 张已留存 / {{ detail.imageCount || 0 }} 张上传</small></div>
          <div v-if="detailImages.length" class="image-grid">
            <figure v-for="item in detailImages" :key="item.id" class="image-card">
              <div v-if="item.loading" v-loading="true" class="image-placeholder" />
              <el-image
                v-else-if="item.url && !item.failed"
                :src="item.url" :preview-src-list="previewImages" :initial-index="previewImages.indexOf(item.url)"
                fit="cover" preview-teleported class="user-image" @error="item.failed = true"
              />
              <div v-else class="image-placeholder image-error">
                <Icon icon="ep:picture" :size="28" />
                <span>图片加载失败</span>
                <el-button link type="primary" @click="retryImage(item)">重新加载</el-button>
              </div>
              <figcaption>图片 {{ item.imageIndex + 1 }} · {{ formatBytes(item.sizeBytes) }}</figcaption>
            </figure>
          </div>
          <p v-else class="empty-copy">{{ imagePlaceholder(detail) }}</p>
          <small v-if="detailImages.length" class="content-note">点击图片查看原图。</small>
          <small v-if="detail.contentRecorded && detail.storedImageCount < detail.imageCount" class="content-note">部分上传图片未留存，当前展示已保存的图片。</small>
        </section>
        <section class="conversation-section answer-section">
          <div class="section-heading"><h3>模型回答</h3></div>
          <p v-if="detail.answerText" class="conversation-text">{{ detail.answerText }}</p>
          <p v-else class="empty-copy">{{ answerPlaceholder(detail) }}</p>
          <small v-if="detail.answerText && ['INTERRUPTED', 'DISCONNECTED', 'FAILED'].includes(detail.status)" class="content-note">本轮链路未正常完成，以上为已收到的回答内容。</small>
        </section>
        <div class="section-heading"><h3>链路耗时</h3><small>空值表示该阶段未返回统计</small></div>
        <section class="stage-list">
          <div v-for="stage in detailStages" :key="stage.label" class="stage-row">
            <span class="stage-dot" :class="{ empty: stage.value == null }" />
            <div><strong>{{ stage.label }}</strong><small>{{ stage.note }}</small></div>
            <b>{{ formatMs(stage.value) }}</b>
          </div>
        </section>
        <section class="meta-grid">
          <div><small>WebSocket 会话</small><strong>{{ detail.sessionId || '-' }}</strong></div>
          <div><small>客户端 IP</small><strong>{{ detail.clientIp || '-' }}</strong></div>
          <div><small>音频大小</small><strong>{{ formatBytes(detail.audioBytes) }}</strong></div>
          <div><small>图片数量</small><strong>{{ detail.imageCount || 0 }} 张</strong></div>
        </section>
        <el-alert
          v-if="detail.errorCode || detail.errorMessage"
          :title="`${detail.errorCode || '链路异常'}${detail.errorMessage ? `：${detail.errorMessage}` : ''}`"
          type="warning" :closable="false" show-icon
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import {
  getCampusEsp32Log,
  getCampusEsp32LogImage,
  getCampusEsp32LogPage,
  getCampusEsp32LogSummary,
  type CampusEsp32Log,
  type CampusEsp32LogDetail,
  type CampusEsp32LogImage,
  type CampusEsp32LogQuery,
  type CampusEsp32LogStatus,
  type CampusEsp32LogSummary
} from '@/api/campus/esp32-log'

defineOptions({ name: 'CampusEsp32Log' })

const loading = ref(false)
const list = ref<CampusEsp32Log[]>([])
const total = ref(0)
const summary = ref<CampusEsp32LogSummary>({ totalCount: 0, completedCount: 0, failedCount: 0 })
const queryParams = reactive<CampusEsp32LogQuery>({ pageNo: 1, pageSize: 20 })
const createTimeRange = ref<string[]>([])
const detailVisible = ref(false)
const detail = ref<CampusEsp32LogDetail>()
const detailLoading = ref(false)
const detailError = ref(false)
const detailId = ref<number | null>(null)
const detailGeneration = ref(0)

type DetailImage = CampusEsp32LogImage & {
  url?: string
  loading: boolean
  failed: boolean
}

const detailImages = ref<DetailImage[]>([])
const imageObjectUrls = new Set<string>()
const previewImages = computed(() => detailImages.value
  .filter((item) => item.url && !item.failed)
  .map((item) => item.url as string))

const statusOptions: Array<{ label: string; value: CampusEsp32LogStatus }> = [
  { label: '采集中', value: 'CAPTURING' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '模型完成', value: 'MODEL_DONE' },
  { label: '语音回传', value: 'SPEAKING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已忽略', value: 'IGNORED' },
  { label: '已中断', value: 'INTERRUPTED' },
  { label: '失败', value: 'FAILED' },
  { label: '连接断开', value: 'DISCONNECTED' }
]

const metrics = computed(() => [
  { label: '请求总数', value: Number(summary.value.totalCount || 0), icon: 'ep:data-line', color: '#2563eb' },
  { label: '已完成', value: Number(summary.value.completedCount || 0), icon: 'ep:circle-check', color: '#10b981' },
  { label: '失败 / 中断', value: Number(summary.value.failedCount || 0), icon: 'ep:warning', color: '#ef4444' },
  { label: '平均总耗时', value: formatMs(summary.value.averageTotalMs), icon: 'ep:timer', color: '#8b5cf6' },
  { label: '平均模型耗时', value: formatMs(summary.value.averageModelMs), icon: 'ep:cpu', color: '#f59e0b' }
])

const detailStages = computed(() => {
  if (!detail.value) return []
  return [
    { label: '音频采集', value: detail.value.captureMs, note: 'turn_start → turn_commit' },
    { label: '提交模型', value: detail.value.submitMs, note: '网关发送 → 上游接收' },
    { label: 'ASR 转写', value: detail.value.asrMs, note: '异步记录，不阻塞回答' },
    { label: '模型首 token', value: detail.value.modelFirstTokenMs, note: '上游模型统计' },
    { label: '模型完成', value: detail.value.modelTotalMs, note: '上游模型统计' },
    { label: 'TTS 首包', value: detail.value.ttsFirstAudioMs, note: '本轮开始 → 首个音频包' },
    { label: 'TTS 输出', value: detail.value.ttsAudioMs, note: '首包 → 音频发送完成' },
    { label: '本轮总耗时', value: detail.value.totalMs, note: 'turn_start → turn_done' }
  ]
})

const syncTimeParams = () => {
  queryParams.createTimeStart = createTimeRange.value?.[0]
  queryParams.createTimeEnd = createTimeRange.value?.[1]
}

const getList = async () => {
  loading.value = true
  try {
    syncTimeParams()
    const data = await getCampusEsp32LogPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const getSummary = async () => {
  syncTimeParams()
  summary.value = await getCampusEsp32LogSummary(queryParams)
}

const refreshAll = async () => Promise.all([getList(), getSummary()])
const handleQuery = () => { queryParams.pageNo = 1; void refreshAll() }

const resetQuery = () => {
  Object.assign(queryParams, { pageNo: 1, pageSize: 20 })
  delete queryParams.deviceId
  delete queryParams.requestId
  delete queryParams.status
  delete queryParams.createTimeStart
  delete queryParams.createTimeEnd
  createTimeRange.value = []
  void refreshAll()
}

const revokeImageUrls = () => {
  imageObjectUrls.forEach((url) => URL.revokeObjectURL(url))
  imageObjectUrls.clear()
  detailImages.value = []
}

const loadImage = async (item: DetailImage, generation: number) => {
  item.loading = true
  item.failed = false
  try {
    const response = await getCampusEsp32LogImage(item.id)
    if (generation !== detailGeneration.value) return
    // request.download returns the Axios response at runtime; keep a small fallback
    // so this remains compatible with a response interceptor that returns Blob.
    const payload = (response as any)?.data ?? response
    const blob = payload instanceof Blob
      ? payload
      : new Blob([payload], { type: item.mimeType || 'image/jpeg' })
    item.url = URL.createObjectURL(blob)
    imageObjectUrls.add(item.url)
  } catch {
    if (generation === detailGeneration.value) item.failed = true
  } finally {
    if (generation === detailGeneration.value) item.loading = false
  }
}

const fetchDetail = async (id: number) => {
  const generation = ++detailGeneration.value
  detailLoading.value = true
  detailError.value = false
  detail.value = undefined
  revokeImageUrls()
  try {
    const data = await getCampusEsp32Log(id)
    if (detailId.value !== id || generation !== detailGeneration.value) return
    detail.value = data
    detailImages.value = (data.images || []).map((item) => ({ ...item, loading: true, failed: false }))
    await Promise.all(detailImages.value.map((item) => loadImage(item, generation)))
  } catch {
    if (detailId.value === id && generation === detailGeneration.value) {
      detail.value = undefined
      detailError.value = true
      revokeImageUrls()
    }
  } finally {
    if (detailId.value === id && generation === detailGeneration.value) detailLoading.value = false
  }
}

const openDetail = (row: CampusEsp32Log) => {
  detailId.value = row.id
  detailVisible.value = true
  void fetchDetail(row.id)
}

const reloadDetail = () => {
  if (detailId.value != null) void fetchDetail(detailId.value)
}

const retryImage = (item: DetailImage) => {
  if (item.url) {
    URL.revokeObjectURL(item.url)
    imageObjectUrls.delete(item.url)
    item.url = undefined
  }
  void loadImage(item, detailGeneration.value)
}

watch(detailVisible, (visible) => {
  if (!visible) {
    detailGeneration.value++
    detailId.value = null
    detail.value = undefined
    detailError.value = false
    detailLoading.value = false
    revokeImageUrls()
  }
})

onBeforeUnmount(() => {
  detailGeneration.value++
  revokeImageUrls()
})

const questionPlaceholder = (row: CampusEsp32Log) => {
  if (row.contentRecorded === false) return '历史记录未留存提问'
  if (row.asrStatus === 'PENDING') return '正在转写语音…'
  if (row.asrStatus === 'FAILED') return '语音转写失败'
  if (row.asrStatus === 'DISABLED') return '未启用语音转写'
  return '暂无用户提问'
}

const answerPlaceholder = (row: CampusEsp32Log) => {
  if (row.contentRecorded === false) return '历史记录未留存回答'
  if (row.status === 'IGNORED') return '本轮未提交模型'
  if (['FAILED', 'DISCONNECTED', 'INTERRUPTED'].includes(row.status)) return '本轮未生成完整回答'
  return '暂无模型回答'
}

const imagePlaceholder = (row: CampusEsp32Log) => {
  if (row.contentRecorded === false) return '历史记录未留存图片'
  if (row.imageCount > 0 && row.storedImageCount === 0) return '图片正在保存，刷新详情后查看'
  return '本轮未上传图片'
}

type AsrTagType = 'success' | 'warning' | 'danger' | 'info'
const asrTag = (status: CampusEsp32Log['asrStatus']): AsrTagType => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

const asrText = (status: CampusEsp32Log['asrStatus']) => {
  if (status === 'SUCCESS') return '已转写'
  if (status === 'FAILED') return '转写失败'
  if (status === 'PENDING') return '转写中'
  if (status === 'DISABLED') return '未启用'
  return '未开始'
}

const formatMs = (value?: number) => value == null ? '-' : `${Math.max(0, Number(value))} ms`
const formatBytes = (value?: number) => {
  const bytes = Number(value || 0)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
const formatTime = (value?: string | number) => {
  if (value == null || value === '') return '-'
  const normalizedValue = typeof value === 'number' && value < 10_000_000_000
    ? value * 1000
    : value
  return formatDate(normalizedValue) || '-'
}
const statusText = (status: CampusEsp32LogStatus) => statusOptions.find((item) => item.value === status)?.label || status || '未知'
type TagType = 'success' | 'warning' | 'danger' | 'info'
const statusTag = (status: CampusEsp32LogStatus): TagType => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'DISCONNECTED') return 'danger'
  if (status === 'INTERRUPTED' || status === 'IGNORED') return 'warning'
  return 'info'
}

onMounted(() => void refreshAll())
</script>

<style lang="scss" scoped>
.esp32-log-page { min-height: calc(100vh - 84px); padding: 20px; background: #f4f7f7; }
.log-hero { display: flex; align-items: center; justify-content: space-between; padding: 28px 32px; margin-bottom: 16px; color: #fff; background: linear-gradient(120deg, #0f766e, #2563eb); border-radius: 20px; }
.log-hero span { font-size: 10px; letter-spacing: .18em; opacity: .7; }
.log-hero h1 { margin: 5px 0; font-size: 28px; }
.log-hero p { margin: 0; opacity: .8; }
.metric-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin-bottom: 16px; }
.metric-grid article { display: flex; gap: 14px; align-items: center; padding: 18px; background: #fff; border-radius: 16px; box-shadow: 0 8px 24px rgb(39 52 48 / 6%); }
.metric-grid small, .metric-grid strong { display: block; }
.metric-grid small { color: #83908d; }
.metric-grid strong { margin-top: 3px; font-size: 22px; color: #213c36; }
.table-title { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.table-title h2 { margin: 0 0 4px; font-size: 18px; }
.table-title p { margin: 0; color: #8b9895; }
.conversation-summary { overflow: hidden; color: #334e49; text-overflow: ellipsis; white-space: nowrap; }
.empty-copy { color: #9aa8a4 !important; }
.identity-cell { display: grid; min-width: 0; gap: 4px; }
.identity-cell strong, .identity-cell small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.identity-cell small { color: #8b9895; }
.total-ms { color: #0f766e; }
.detail-content { display: grid; gap: 18px; }
.drawer-heading { display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 18px; }
.drawer-heading h2 { margin: 0; font-size: 18px; }
.detail-loading { padding: 12px 4px; }
.detail-heading h2 { margin: 12px 0 7px; }
.detail-heading p { margin: 0; color: #7b8985; word-break: break-all; }
.conversation-section { display: grid; gap: 10px; padding: 16px; background: #f7faf9; border: 1px solid #e5eeeb; border-radius: 15px; }
.conversation-section p { margin: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading h3 { margin: 0; font-size: 15px; color: #213c36; }
.section-heading small { color: #8a9794; }
.conversation-text { white-space: pre-wrap; word-break: break-word; line-height: 1.7; color: #253c38; }
.content-note { color: #8a9794; line-height: 1.5; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.image-card { overflow: hidden; margin: 0; background: #fff; border: 1px solid #e5eeeb; border-radius: 12px; }
.image-card figcaption { padding: 8px 10px; color: #7b8985; font-size: 12px; }
.user-image, .image-placeholder { display: flex; align-items: center; justify-content: center; width: 100%; height: 150px; }
.user-image { cursor: zoom-in; }
.image-placeholder { gap: 6px; flex-direction: column; color: #9aa8a4; background: #f1f5f3; }
.image-error { font-size: 12px; }
.answer-section { background: linear-gradient(145deg, #f7faf9, #f1f7ff); }
.stage-list, .meta-grid { padding: 8px 16px; background: #f7faf9; border-radius: 15px; }
.stage-row { display: grid; grid-template-columns: 16px 1fr auto; gap: 10px; align-items: center; padding: 11px 0; border-bottom: 1px solid #e5eeeb; }
.stage-row:last-child { border-bottom: 0; }
.stage-row div { display: grid; gap: 3px; }
.stage-row small, .meta-grid small { color: #8a9794; }
.stage-row b { color: #0f766e; }
.stage-dot { width: 9px; height: 9px; background: #10b981; border-radius: 50%; }
.stage-dot.empty { background: #cbd5d1; }
.meta-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.meta-grid div { display: grid; gap: 5px; min-width: 0; }
.meta-grid strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (width <= 1200px) { .metric-grid { grid-template-columns: repeat(3, 1fr); } }
@media (width <= 700px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } .log-hero { padding: 22px; } .log-hero h1 { font-size: 22px; } .image-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .user-image, .image-placeholder { height: 120px; } }
</style>
