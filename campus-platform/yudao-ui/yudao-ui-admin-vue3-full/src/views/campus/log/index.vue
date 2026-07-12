<template>
  <div class="campus-log-page">
    <div class="log-hero">
      <div>
        <div class="log-title">校园数据日志</div>
        <div class="log-subtitle">区域、学校、校区、商品和学生资料的新增、编辑、删除都会在这里留痕</div>
      </div>
      <div class="log-badge"><Icon icon="ep:document-checked" /> 操作可追溯</div>
    </div>

    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="操作类型" prop="subType">
          <el-select v-model="queryParams.subType" clearable class="!w-180px" placeholder="全部操作">
            <el-option label="新增数据" value="新增数据" />
            <el-option label="编辑数据" value="编辑数据" />
            <el-option label="删除数据" value="删除数据" />
            <el-option label="批量删除" value="批量删除" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作内容" prop="action">
          <el-input v-model="queryParams.action" clearable class="!w-240px" placeholder="资源、编号或内容" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="业务编号" prop="bizId">
          <el-input v-model="queryParams.bizId" clearable class="!w-180px" placeholder="数据编号" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="操作时间" prop="createTime">
          <el-date-picker
            v-model="queryParams.createTime"
            class="!w-320px"
            type="daterange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
          <el-button v-hasPermi="['system:operate-log:export']" plain type="success" :loading="exportLoading" @click="handleExport">
            <Icon icon="ep:download" class="mr-5px" />导出
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="日志编号" align="center" prop="id" width="100" />
        <el-table-column label="操作人" align="center" prop="userName" width="120" />
        <el-table-column label="操作类型" align="center" prop="subType" width="120">
          <template #default="scope">
            <el-tag :type="tagType(scope.row.subType)" effect="light">{{ scope.row.subType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作内容" prop="action" min-width="320" show-overflow-tooltip />
        <el-table-column label="业务编号" align="center" prop="bizId" width="120" />
        <el-table-column label="操作 IP" align="center" prop="userIp" width="130" />
        <el-table-column label="操作时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
        <el-table-column label="操作" align="center" fixed="right" width="80">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </ContentWrap>

    <OperateLogDetail ref="detailRef" />
  </div>
</template>

<script setup lang="ts">
import * as OperateLogApi from '@/api/system/operatelog'
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import OperateLogDetail from '@/views/system/operatelog/OperateLogDetail.vue'
import { useRoute } from 'vue-router'

defineOptions({ name: 'CampusDataLog' })

const message = useMessage()
const route = useRoute()
const loading = ref(false)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<OperateLogApi.OperateLogVO[]>([])
const queryFormRef = ref()
const detailRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  type: '校园运营',
  subType: undefined as string | undefined,
  action: undefined as string | undefined,
  bizId: undefined as string | undefined,
  createTime: [] as string[]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await OperateLogApi.getOperateLogPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.type = '校园运营'
  handleQuery()
}

const openDetail = (row: OperateLogApi.OperateLogVO) => detailRef.value?.open(row)

const tagType = (subType: string): 'danger' | 'success' | 'warning' => {
  if (subType?.includes('删除')) return 'danger'
  if (subType?.includes('新增')) return 'success'
  return 'warning'
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await OperateLogApi.exportOperateLog(queryParams)
    download.excel(data, '校园数据日志.xls')
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  if (route.query.bizId) queryParams.bizId = String(route.query.bizId)
  getList()
})
</script>

<style scoped>
.campus-log-page { min-height: 100%; }
.log-hero { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 24px 28px; border-radius: 14px; color: #fff; background: #175d53; box-shadow: 0 10px 26px rgb(23 93 83 / 16%); }
.log-title { font-size: 22px; font-weight: 800; }
.log-subtitle { margin-top: 7px; color: rgb(255 255 255 / 68%); font-size: 13px; }
.log-badge { display: flex; align-items: center; gap: 6px; padding: 8px 13px; border-radius: 999px; color: #175d53; background: #d8f0e9; font-size: 13px; font-weight: 700; }
</style>
