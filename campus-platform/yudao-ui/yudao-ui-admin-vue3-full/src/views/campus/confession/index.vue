<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="表白内容">
        <el-input
          v-model="queryParams.title"
          clearable
          class="!w-240px"
          placeholder="请输入标题或表白对象"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学校">
        <el-input
          v-model="queryParams.school_name"
          clearable
          class="!w-180px"
          placeholder="请输入学校"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable class="!w-160px" placeholder="请选择状态">
          <el-option label="审核中" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已下架" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="90" />
      <el-table-column label="表白对象/标题" prop="title" min-width="200" show-overflow-tooltip />
      <el-table-column label="正文" prop="content" min-width="260" show-overflow-tooltip />
      <el-table-column label="发布用户" align="center" prop="user_id" width="100" />
      <el-table-column label="学校" align="center" prop="school_name" width="150" show-overflow-tooltip />
      <el-table-column label="校区" align="center" prop="campus_name" width="130" show-overflow-tooltip />
      <el-table-column label="点赞" align="center" prop="like_count" width="80" />
      <el-table-column label="评论" align="center" prop="comment_count" width="80" />
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusMeta(scope.row.status).type">
            {{ statusMeta(scope.row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" align="center" prop="create_time" width="180" />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row)">管理</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <el-dialog v-model="dialogVisible" title="管理表白内容" width="680px">
    <el-form :model="formData" label-width="90px">
      <el-form-item label="标题">
        <el-input v-model="formData.title" />
      </el-form-item>
      <el-form-item label="正文">
        <el-input v-model="formData.content" type="textarea" :rows="6" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formData.status">
          <el-radio-button :value="0">审核中</el-radio-button>
          <el-radio-button :value="1">发布</el-radio-button>
          <el-radio-button :value="2">下架</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="submitEdit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { deleteCampus, getCampusPage, updateCampus } from '@/api/campus/base'

defineOptions({ name: 'CampusConfession' })

const message = useMessage()
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const list = ref<Record<string, any>[]>([])
const total = ref(0)
const formData = ref<Record<string, any>>({})
const queryParams = reactive<Record<string, any>>({
  pageNo: 1,
  pageSize: 10,
  channel: '表白',
  title: undefined,
  school_name: undefined,
  status: undefined
})

const statusMeta = (status: number) => {
  if (status === 1) return { label: '已发布', type: 'success' as const }
  if (status === 2) return { label: '已下架', type: 'info' as const }
  return { label: '审核中', type: 'warning' as const }
}

const getList = async () => {
  loading.value = true
  try {
    const data = await getCampusPage('post', queryParams as PageParam)
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
  queryParams.title = undefined
  queryParams.school_name = undefined
  queryParams.status = undefined
  queryParams.channel = '表白'
  handleQuery()
}

const openEdit = (row: Record<string, any>) => {
  formData.value = {
    id: row.id,
    title: row.title,
    content: row.content,
    status: row.status
  }
  dialogVisible.value = true
}

const submitEdit = async () => {
  submitLoading.value = true
  try {
    await updateCampus('post', formData.value)
    message.success('保存成功')
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (id: number) => {
  await message.delConfirm()
  await deleteCampus('post', id)
  message.success('删除成功')
  await getList()
}

onMounted(getList)
</script>
