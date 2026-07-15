<template>
  <div class="analytics-page" v-loading="loading">
    <section class="analytics-hero">
      <div>
        <div class="hero-kicker">MINI PROGRAM INSIGHTS</div>
        <h1>小程序数据看板</h1>
        <p>实时观察校园用户、内容发布与交易转化，在线人数按最近心跳动态计算。</p>
      </div>
      <div class="hero-actions">
        <el-select
          v-model="query.tenantId"
          clearable
          class="campus-select"
          placeholder="全部校区"
          @change="loadData"
        >
          <el-option
            v-for="campus in overview.campuses"
            :key="campus.tenantId"
            :label="campus.name"
            :value="campus.tenantId"
          />
        </el-select>
        <el-select v-model="query.days" class="days-select" @change="loadData">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
        </el-select>
        <el-button :loading="loading" class="refresh-button" @click="loadData">
          <Icon icon="ep:refresh" class="mr-5px" />刷新
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="card in metricCards" :key="card.key" class="metric-card">
        <div class="metric-icon" :style="{ '--metric-color': card.color }">
          <Icon :icon="card.icon" :size="22" />
        </div>
        <div class="metric-label">{{ card.label }}</div>
        <div class="metric-value" :class="{ money: card.money }">{{ card.value }}</div>
        <div class="metric-note" :class="card.tone">
          <Icon :icon="card.noteIcon" :size="14" />
          <span>{{ card.note }}</span>
        </div>
      </article>
    </section>

    <section class="chart-grid">
      <div class="glass-panel trend-panel">
        <div class="panel-header">
          <div>
            <h2>运营趋势</h2>
            <p>新增、活跃、发布、订单与实付营收</p>
          </div>
          <span class="live-dot"><i></i>每 60 秒自动刷新</span>
        </div>
        <Echart :options="trendOptions" :height="350" />
      </div>

      <div class="glass-panel event-panel">
        <div class="panel-header">
          <div>
            <h2>今日行为排行</h2>
            <p>了解学生主要使用路径</p>
          </div>
        </div>
        <Echart v-if="overview.topEvents.length" :options="eventOptions" :height="280" />
        <el-empty v-else description="今天还没有行为数据" :image-size="92" />
      </div>
    </section>

    <section class="bottom-grid">
      <div class="glass-panel page-panel">
        <div class="panel-header">
          <div>
            <h2>热门页面</h2>
            <p>今日页面浏览分布</p>
          </div>
        </div>
        <div v-if="overview.topPages.length" class="page-rank-list">
          <div v-for="(item, index) in overview.topPages" :key="item.name" class="page-rank-item">
            <span class="rank-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="rank-main">
              <strong>{{ formatPage(item.name) }}</strong>
              <span>{{ item.name }}</span>
            </div>
            <b>{{ formatNumber(item.count) }}</b>
          </div>
        </div>
        <el-empty v-else description="暂无页面浏览数据" :image-size="82" />
      </div>

      <div class="glass-panel recent-panel">
        <div class="panel-header">
          <div>
            <h2>最近动态</h2>
            <p>最新 20 条有效埋点事件</p>
          </div>
          <span class="updated-at">更新于 {{ updatedAt }}</span>
        </div>
        <el-table :data="overview.recentEvents" height="330" class="event-table">
          <el-table-column label="用户" min-width="120">
            <template #default="scope">
              <div class="user-cell">
                <span>{{ String(scope.row.nickname || '学').slice(0, 1) }}</span>
                <div>
                  <strong>{{ scope.row.nickname }}</strong>
                  <small>ID {{ scope.row.userId }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="行为" min-width="110">
            <template #default="scope">
              <el-tag effect="plain" round>{{ formatEvent(scope.row.eventName) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="页面" min-width="170" show-overflow-tooltip>
            <template #default="scope">{{ formatPage(scope.row.pagePath) }}</template>
          </el-table-column>
          <el-table-column label="时间" prop="createTime" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import type { CampusAnalyticsOverview, CampusAnalyticsSummary } from '@/api/campus/analytics'
import { getCampusAnalyticsOverview } from '@/api/campus/analytics'

defineOptions({ name: 'CampusAnalytics' })

const emptySummary = (): CampusAnalyticsSummary => ({
  totalUsers: 0,
  registeredUsers: 0,
  onlineUsers: 0,
  activeUsers: 0,
  postCount: 0,
  orderCount: 0,
  paidOrderCount: 0,
  revenueAmount: 0,
  eventCount: 0
})

const emptyOverview = (): CampusAnalyticsOverview => ({
  today: emptySummary(),
  yesterday: emptySummary(),
  trend: [],
  topEvents: [],
  topPages: [],
  recentEvents: [],
  campuses: [],
  onlineWindowMinutes: 5,
  serverTime: ''
})

const message = useMessage()
const loading = ref(false)
const overview = ref<CampusAnalyticsOverview>(emptyOverview())
const query = reactive<{ tenantId?: number; days: number }>({ days: 7 })
let refreshTimer: ReturnType<typeof setInterval> | undefined

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
const formatMoney = (value: number) =>
  new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(
    Number(value || 0)
  )

const compareText = (current: number, previous: number) => {
  const delta = Number(current || 0) - Number(previous || 0)
  if (delta === 0) return { text: '与昨日持平', tone: 'neutral', icon: 'ep:minus' }
  return {
    text: `较昨日${delta > 0 ? '增加' : '减少'} ${formatNumber(Math.abs(delta))}`,
    tone: delta > 0 ? 'positive' : 'negative',
    icon: delta > 0 ? 'ep:top-right' : 'ep:bottom-right'
  }
}

const metricCards = computed(() => {
  const today = overview.value.today
  const yesterday = overview.value.yesterday
  const activeCompare = compareText(today.activeUsers, yesterday.activeUsers)
  const postCompare = compareText(today.postCount, yesterday.postCount)
  const revenueCompare = compareText(today.revenueAmount, yesterday.revenueAmount)
  return [
    {
      key: 'users',
      label: '注册用户',
      value: formatNumber(today.totalUsers),
      note: `今日新增 ${formatNumber(today.registeredUsers)}`,
      noteIcon: 'ep:user-filled',
      tone: 'positive',
      icon: 'ep:user',
      color: '#1677ff'
    },
    {
      key: 'online',
      label: '当前在线',
      value: formatNumber(today.onlineUsers),
      note: `${overview.value.onlineWindowMinutes} 分钟内有前台心跳`,
      noteIcon: 'ep:connection',
      tone: 'positive',
      icon: 'ep:monitor',
      color: '#14b8a6'
    },
    {
      key: 'active',
      label: '今日活跃',
      value: formatNumber(today.activeUsers),
      note: activeCompare.text,
      noteIcon: activeCompare.icon,
      tone: activeCompare.tone,
      icon: 'ep:data-line',
      color: '#6366f1'
    },
    {
      key: 'posts',
      label: '今日发布',
      value: formatNumber(today.postCount),
      note: postCompare.text,
      noteIcon: postCompare.icon,
      tone: postCompare.tone,
      icon: 'ep:document-add',
      color: '#f59e0b'
    },
    {
      key: 'orders',
      label: '今日订单',
      value: formatNumber(today.orderCount),
      note: `已支付 ${formatNumber(today.paidOrderCount)} 笔`,
      noteIcon: 'ep:circle-check',
      tone: 'positive',
      icon: 'ep:tickets',
      color: '#8b5cf6'
    },
    {
      key: 'revenue',
      label: '今日营收',
      value: `¥${formatMoney(today.revenueAmount)}`,
      note: revenueCompare.text,
      noteIcon: revenueCompare.icon,
      tone: revenueCompare.tone,
      icon: 'ep:wallet',
      color: '#f97368',
      money: true
    }
  ]
})

const trendOptions = computed<EChartsOption>(() => ({
  color: ['#1677ff', '#6366f1', '#f59e0b', '#8b5cf6', '#f97368'],
  tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,.96)', borderColor: '#dbeafe' },
  legend: { top: 4, right: 8, itemWidth: 10, itemHeight: 10 },
  grid: { left: 18, right: 24, top: 54, bottom: 12, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: overview.value.trend.map((item) => item.date.slice(5)),
    axisLine: { lineStyle: { color: '#dbe3ef' } },
    axisTick: { show: false }
  },
  yAxis: [
    {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf1f7', type: 'dashed' } }
    },
    {
      type: 'value',
      name: '营收（元）',
      splitLine: { show: false },
      axisLabel: { formatter: '¥{value}' }
    }
  ],
  series: [
    {
      name: '新增用户',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      data: overview.value.trend.map((item) => item.registeredUsers),
      areaStyle: { color: 'rgba(22,119,255,.08)' }
    },
    {
      name: '活跃用户',
      type: 'line',
      smooth: true,
      data: overview.value.trend.map((item) => item.activeUsers)
    },
    {
      name: '发布',
      type: 'line',
      smooth: true,
      data: overview.value.trend.map((item) => item.postCount)
    },
    {
      name: '订单',
      type: 'line',
      smooth: true,
      data: overview.value.trend.map((item) => item.orderCount)
    },
    {
      name: '营收',
      type: 'bar',
      yAxisIndex: 1,
      barMaxWidth: 20,
      itemStyle: { borderRadius: [5, 5, 0, 0] },
      data: overview.value.trend.map((item) => Number(item.revenueAmount || 0))
    }
  ]
}))

const eventOptions = computed<EChartsOption>(() => {
  const list = [...overview.value.topEvents].reverse()
  return {
    color: ['#1677ff'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 24, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f7' } } },
    yAxis: {
      type: 'category',
      data: list.map((item) => formatEvent(item.name)),
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        data: list.map((item) => item.count),
        itemStyle: {
          borderRadius: 8,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#72b5ff' },
              { offset: 1, color: '#1677ff' }
            ]
          }
        }
      }
    ]
  }
})

const eventNames: Record<string, string> = {
  APP_SHOW: '进入小程序',
  APP_HIDE: '离开小程序',
  PAGE_VIEW: '浏览页面'
}

const pageNames: Record<string, string> = {
  'pages/index/index': '逛校园',
  'pages/publish/index': '发布内容',
  'pages/about/index': '个人中心',
  'pages/detail/index': '内容详情',
  'pages/login/index': '登录与资料',
  'pages/messages/index': '消息通知',
  'pages/search/index': '搜索结果'
}

const formatEvent = (name: string) => eventNames[name] || name || '未知行为'
const formatPage = (path: string) => pageNames[path] || path || '未知页面'
const formatDateTime = (value: string) => {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '-')
}
const updatedAt = computed(() => formatDateTime(overview.value.serverTime))

const loadData = async () => {
  loading.value = true
  try {
    overview.value = await getCampusAnalyticsOverview(query)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '数据看板加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
  refreshTimer = setInterval(() => void loadData(), 60_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.analytics-page {
  min-height: calc(100vh - 84px);
  padding: 20px;
  background:
    radial-gradient(circle at 5% 0%, rgb(78 146 255 / 14%), transparent 28%),
    radial-gradient(circle at 95% 10%, rgb(121 99 255 / 12%), transparent 24%), #f5f7fb;
}

.analytics-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26px 30px;
  margin-bottom: 16px;
  color: #f8fbff;
  background: linear-gradient(120deg, #0f59c7 0%, #357ff0 54%, #6e83f7 100%);
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 22px;
  box-shadow: 0 18px 42px rgb(34 91 176 / 20%);

  h1 {
    margin: 3px 0 6px;
    font-size: 27px;
    line-height: 1.3;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: rgb(255 255 255 / 78%);
  }
}

.hero-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: rgb(255 255 255 / 62%);
}

.hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.campus-select {
  width: 200px;
}

.days-select {
  width: 112px;
}

.hero-actions :deep(.el-select__wrapper),
.refresh-button {
  color: #fff;
  background: rgb(255 255 255 / 14%);
  border-color: rgb(255 255 255 / 25%);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 14%);
  backdrop-filter: blur(14px);
}

.hero-actions :deep(.el-select__selected-item),
.hero-actions :deep(.el-select__placeholder) {
  color: #fff;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card,
.glass-panel {
  background: rgb(255 255 255 / 82%);
  border: 1px solid rgb(255 255 255 / 85%);
  box-shadow: 0 10px 28px rgb(38 69 117 / 8%);
  backdrop-filter: blur(18px);
}

.metric-card {
  min-width: 0;
  padding: 18px;
  border-radius: 18px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: 16px;
  color: var(--metric-color);
  background: color-mix(in srgb, var(--metric-color) 12%, white);
  border-radius: 13px;
}

.metric-label {
  font-size: 13px;
  color: #748094;
}

.metric-value {
  margin: 7px 0 8px;
  overflow: hidden;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.03em;
  color: #152033;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.money {
    font-size: 24px;
  }
}

.metric-note {
  display: flex;
  min-height: 18px;
  font-size: 11px;
  color: #8090a5;
  gap: 4px;
  align-items: center;

  &.positive {
    color: #07966d;
  }

  &.negative {
    color: #e45555;
  }
}

.chart-grid,
.bottom-grid {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-grid {
  grid-template-columns: minmax(0, 2fr) minmax(340px, 1fr);
}

.bottom-grid {
  grid-template-columns: minmax(300px, 0.8fr) minmax(0, 2.2fr);
}

.glass-panel {
  min-width: 0;
  padding: 20px;
  border-radius: 20px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;

  h2 {
    margin: 0 0 4px;
    font-size: 17px;
    color: #1a2538;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #8a96a8;
  }
}

.live-dot {
  display: flex;
  font-size: 11px;
  color: #738197;
  gap: 7px;
  align-items: center;

  i {
    width: 7px;
    height: 7px;
    background: #1fc69a;
    border-radius: 50%;
    box-shadow: 0 0 0 5px rgb(31 198 154 / 12%);
  }
}

.page-rank-list {
  display: grid;
  gap: 3px;
}

.page-rank-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 8px;
  border-bottom: 1px solid #edf1f6;

  > b {
    margin-left: auto;
    font-size: 15px;
    color: #172235;
  }
}

.rank-number {
  font-size: 11px;
  font-weight: 700;
  color: #9aa6b6;
}

.rank-main {
  min-width: 0;

  strong,
  span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    font-size: 13px;
    color: #344054;
  }

  span {
    max-width: 220px;
    margin-top: 3px;
    font-size: 10px;
    color: #9aa5b4;
  }
}

.updated-at {
  font-size: 11px;
  color: #9aa5b4;
}

.event-table {
  --el-table-border-color: #edf1f6;
  --el-table-header-bg-color: #f7f9fc;
  --el-table-row-hover-bg-color: #f4f8ff;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.user-cell {
  display: flex;
  gap: 9px;
  align-items: center;

  > span {
    display: flex;
    width: 30px;
    height: 30px;
    font-size: 12px;
    font-weight: 700;
    color: #2568c9;
    background: #e8f2ff;
    border-radius: 10px;
    flex: none;
    align-items: center;
    justify-content: center;
  }

  strong,
  small {
    display: block;
    line-height: 1.35;
  }

  strong {
    font-size: 12px;
    color: #344054;
  }

  small {
    font-size: 9px;
    color: #9aa5b4;
  }
}

@media (width <= 1500px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 1000px) {
  .analytics-hero,
  .hero-actions {
    align-items: stretch;
  }

  .analytics-hero {
    flex-direction: column;
    gap: 18px;
  }

  .chart-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 720px) {
  .analytics-page {
    padding: 12px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-actions {
    flex-direction: column;
  }

  .campus-select,
  .days-select {
    width: 100%;
  }
}
</style>
