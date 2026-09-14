<template>
  <div class="esp32-log-page">
    <section class="log-hero">
      <div>
        <span>ESP32 ASSISTANT OBSERVABILITY</span>
        <h1>ESP32 助手链路日志</h1>
        <p>从 WebSocket 采集、模型推理到语音回传，按设备查看每一轮请求的分段耗时。</p>
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
        <div><h2>请求明细</h2><p>耗时均为毫秒；空值表示该阶段尚未开始或上游未返回统计。</p></div>
        <el-tag effect="plain">仅保留链路元数据</el-tag>
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

    <el-drawer v-model="detailVisible" title="链路耗时详情" size="560px">
      <div v-if="detail" class="detail-content">
        <div class="detail-heading">
          <el-tag :type="statusTag(detail.status)" effect="light" round>{{ statusText(detail.status) }}</el-tag>
          <h2>{{ detail.deviceId }}</h2>
          <p>{{ detail.requestId }} · {{ formatTime(detail.createTime) }}</p>
        </div>
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
import {
  getCampusEsp32Log,
  getCampusEsp32LogPage,
  getCampusEsp32LogSummary,
  type CampusEsp32Log,
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
const detail = ref<CampusEsp32Log>()

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

const openDetail = async (row: CampusEsp32Log) => {
  detailVisible.value = true
  detail.value = await getCampusEsp32Log(row.id)
}

const formatMs = (value?: number) => value == null ? '-' : `${Math.max(0, Number(value))} ms`
const formatBytes = (value?: number) => {
  const bytes = Number(value || 0)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
const formatTime = (value?: string) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
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
.identity-cell { display: grid; min-width: 0; gap: 4px; }
.identity-cell strong, .identity-cell small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.identity-cell small { color: #8b9895; }
.total-ms { color: #0f766e; }
.detail-content { display: grid; gap: 18px; }
.detail-heading h2 { margin: 12px 0 7px; }
.detail-heading p { margin: 0; color: #7b8985; word-break: break-all; }
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
@media (width <= 700px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } .log-hero { padding: 22px; } .log-hero h1 { font-size: 22px; } }
</style>
