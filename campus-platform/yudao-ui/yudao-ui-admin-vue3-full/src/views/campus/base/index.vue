<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" label-width="80px">
      <el-form-item v-if="meta.searchKey" :label="meta.searchLabel">
        <el-input
          v-model="queryParams[meta.searchKey]"
          clearable
          class="!w-240px"
          :placeholder="`请输入${meta.searchLabel}`"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item v-for="filter in meta.filters || []" :key="filter.prop" :label="filter.label">
        <el-select
          v-if="filter.options"
          v-model="queryParams[filter.prop]"
          clearable
          class="!w-180px"
          :placeholder="`请选择${filter.label}`"
        >
          <el-option
            v-for="option in filter.options"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input-number
          v-else-if="filter.type === 'number'"
          v-model="queryParams[filter.prop]"
          controls-position="right"
          class="!w-180px"
          :min="0"
        />
        <el-input
          v-else
          v-model="queryParams[filter.prop]"
          clearable
          class="!w-180px"
          :placeholder="`请输入${filter.label}`"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item v-if="meta.statusKey" label="状态">
        <el-select v-model="queryParams[meta.statusKey]" clearable class="!w-160px">
          <el-option
            v-for="option in meta.statusOptions || defaultStatusOptions"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button v-if="meta.allowCreate !== false" type="primary" plain @click="openForm('create')">
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="90" />
      <el-table-column v-if="resource === 'miniapp-user'" label="微信头像" align="center" width="90">
        <template #default="scope">
          <el-avatar :size="42" :src="scope.row.avatar">
            {{ String(scope.row.nickname || '学').slice(0, 1) }}
          </el-avatar>
        </template>
      </el-table-column>
      <el-table-column
        v-for="column in meta.columns"
        :key="column.prop"
        :label="column.label"
        :prop="column.prop"
        align="center"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="创建时间" align="center" prop="create_time" width="180" />
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openForm('update', scope.row.id)">编辑</el-button>
          <el-button link type="info" @click="openLog(scope.row.id)">日志</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form :model="formData" label-width="120px">
      <el-form-item v-for="field in meta.fields" :key="field.prop" :label="field.label">
        <el-select v-if="field.options" v-model="formData[field.prop]" class="!w-240px">
          <el-option v-for="option in field.options" :key="String(option.value)" :label="option.label" :value="option.value" />
        </el-select>
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="formData[field.prop]"
          class="!w-240px"
          :min="0"
        />
        <el-input-number
          v-else-if="field.type === 'decimal'"
          v-model="formData[field.prop]"
          class="!w-240px"
          :precision="2"
          :min="0"
        />
        <el-switch v-else-if="field.type === 'boolean'" v-model="formData[field.prop]" />
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="formData[field.prop]"
          type="textarea"
          :rows="3"
        />
        <el-input v-else v-model="formData[field.prop]" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { createCampus, deleteCampus, getCampus, getCampusPage, updateCampus } from '@/api/campus/base'
import { useRoute, useRouter } from 'vue-router'

defineOptions({ name: 'CampusBase' })

type FieldType = 'text' | 'textarea' | 'number' | 'decimal' | 'boolean'

interface SelectOption {
  label: string
  value: string | number
}

interface FieldMeta {
  label: string
  prop: string
  type?: FieldType
  defaultValue?: any
  options?: SelectOption[]
}

interface PageMeta {
  title: string
  searchKey?: string
  searchLabel?: string
  statusKey?: string
  statusOptions?: SelectOption[]
  allowCreate?: boolean
  filters?: FieldMeta[]
  defaultQuery?: Record<string, any>
  columns: FieldMeta[]
  fields: FieldMeta[]
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const defaultStatusOptions: SelectOption[] = [
  { label: '开启/待审核', value: 0 },
  { label: '运营/生效', value: 1 },
  { label: '暂停/下架', value: 2 },
  { label: '关闭/终止', value: 3 }
]

const metas: Record<string, PageMeta> = {
  region: {
    title: '区域管理',
    searchKey: 'name',
    searchLabel: '区域名称',
    statusKey: 'status',
    columns: [
      { label: '区域名称', prop: 'name' },
      { label: '省份', prop: 'province' },
      { label: '城市', prop: 'city' },
      { label: '区县', prop: 'district' },
      { label: '状态', prop: 'status' }
    ],
    fields: [
      { label: '区域名称', prop: 'name' },
      { label: '省份', prop: 'province' },
      { label: '城市', prop: 'city' },
      { label: '区县', prop: 'district' },
      { label: '状态', prop: 'status', type: 'number' },
      { label: '排序', prop: 'sort', type: 'number' }
    ]
  },
  school: {
    title: '学校资料',
    searchKey: 'name',
    searchLabel: '学校名称',
    statusKey: 'status',
    columns: [
      { label: '学校名称', prop: 'name' },
      { label: '省份', prop: 'province' },
      { label: '城市', prop: 'city' },
      { label: '区县', prop: 'district' },
      { label: '状态', prop: 'status' }
    ],
    fields: [
      { label: '学校名称', prop: 'name' },
      { label: '省份', prop: 'province' },
      { label: '城市', prop: 'city' },
      { label: '区县', prop: 'district' },
      { label: 'Logo', prop: 'logo_url' },
      { label: '状态', prop: 'status', type: 'number' }
    ]
  },
  'tenant-profile': {
    title: '校区租户',
    searchKey: 'display_name',
    searchLabel: '展示名称',
    statusKey: 'status',
    columns: [
      { label: '展示名称', prop: 'display_name' },
      { label: '学校', prop: 'school_name' },
      { label: '校区', prop: 'campus_name' },
      { label: '城市', prop: 'city' },
      { label: '代理用户', prop: 'agent_user_id' },
      { label: '状态', prop: 'status' }
    ],
    fields: [
      { label: '系统租户ID', prop: 'system_tenant_id', type: 'number' },
      { label: '区域ID', prop: 'region_id', type: 'number' },
      { label: '学校ID', prop: 'school_id', type: 'number' },
      { label: '学校名称', prop: 'school_name' },
      { label: '校区名称', prop: 'campus_name' },
      { label: '展示名称', prop: 'display_name' },
      { label: '省份', prop: 'province' },
      { label: '城市', prop: 'city' },
      { label: '区县', prop: 'district' },
      { label: '地址', prop: 'address' },
      { label: '代理用户ID', prop: 'agent_user_id', type: 'number' },
      { label: '启用分润', prop: 'commission_enabled', type: 'boolean' },
      { label: '状态', prop: 'status', type: 'number' },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ]
  },
  agent: {
    title: '校区代理',
    searchKey: 'invite_code',
    searchLabel: '邀请码',
    statusKey: 'status',
    columns: [
      { label: '租户ID', prop: 'system_tenant_id' },
      { label: '用户ID', prop: 'user_id' },
      { label: '等级', prop: 'agent_level' },
      { label: '分润比例', prop: 'commission_rate' },
      { label: '邀请码', prop: 'invite_code' },
      { label: '状态', prop: 'status' }
    ],
    fields: [
      { label: '系统租户ID', prop: 'system_tenant_id', type: 'number' },
      { label: '用户ID', prop: 'user_id', type: 'number' },
      { label: '代理等级', prop: 'agent_level', type: 'number' },
      { label: '状态', prop: 'status', type: 'number' },
      { label: '分润比例', prop: 'commission_rate', type: 'decimal' },
      { label: '邀请码', prop: 'invite_code' },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ]
  },
  product: {
    title: '商品管理',
    searchKey: 'title',
    searchLabel: '商品标题',
    statusKey: 'status',
    columns: [
      { label: '标题', prop: 'title' },
      { label: '发布用户', prop: 'user_id' },
      { label: '分类', prop: 'category_id' },
      { label: '价格', prop: 'price' },
      { label: '状态', prop: 'status' },
      { label: '地点', prop: 'location' }
    ],
    fields: [
      { label: '发布用户ID', prop: 'user_id', type: 'number' },
      { label: '分类ID', prop: 'category_id', type: 'number' },
      { label: '标题', prop: 'title' },
      { label: '描述', prop: 'description', type: 'textarea' },
      { label: '价格', prop: 'price', type: 'decimal' },
      { label: '图片JSON', prop: 'images', type: 'textarea', defaultValue: '[]' },
      { label: '状态', prop: 'status', type: 'number' },
      { label: '审核原因', prop: 'audit_reason' },
      { label: '地点', prop: 'location' },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ]
  },
  post: {
    title: '内容管理',
    searchKey: 'title',
    searchLabel: '内容标题',
    statusKey: 'status',
    allowCreate: false,
    filters: [
      { label: '用户ID', prop: 'user_id', type: 'number' },
      { label: '学校', prop: 'school_name' },
      { label: '校区', prop: 'campus_name' },
      { label: '频道', prop: 'channel', options: [
        { label: '二手', value: '二手' },
        { label: '互助', value: '互助' },
        { label: '拼车', value: '拼车' },
        { label: '探店', value: '探店' },
        { label: '失物', value: '失物' },
        { label: '社团', value: '社团' }
      ] },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ],
    columns: [
      { label: '标题', prop: 'title' },
      { label: '发布用户', prop: 'user_id' },
      { label: '学校', prop: 'school_name' },
      { label: '校区', prop: 'campus_name' },
      { label: '频道', prop: 'channel' },
      { label: '价格', prop: 'price' },
      { label: '点赞', prop: 'like_count' },
      { label: '收藏', prop: 'collect_count' },
      { label: '状态', prop: 'status' }
    ],
    fields: [
      { label: '标题', prop: 'title' },
      { label: '正文', prop: 'content', type: 'textarea' },
      { label: '价格', prop: 'price', type: 'decimal' },
      { label: '原价', prop: 'original_price', type: 'decimal' },
      { label: '位置', prop: 'location' },
      { label: '交易/参与方式', prop: 'trade_mode' },
      { label: '可见范围', prop: 'visible_range' },
      { label: '状态', prop: 'status', options: [
        { label: '审核中', value: 0 },
        { label: '已发布', value: 1 },
        { label: '已下架', value: 2 }
      ] },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ]
  },
  'post-report': {
    title: '举报处理',
    searchKey: 'reason',
    searchLabel: '举报原因',
    statusKey: 'status',
    statusOptions: [
      { label: '待处理', value: 0 },
      { label: '已处理', value: 1 },
      { label: '已驳回', value: 2 }
    ],
    allowCreate: false,
    filters: [
      { label: '帖子ID', prop: 'post_id', type: 'number' },
      { label: '举报用户', prop: 'reporter_user_id', type: 'number' },
      { label: '校区租户ID', prop: 'tenant_id', type: 'number' }
    ],
    columns: [
      { label: '帖子ID', prop: 'post_id' },
      { label: '举报用户', prop: 'reporter_user_id' },
      { label: '原因', prop: 'reason' },
      { label: '补充说明', prop: 'detail' },
      { label: '处理状态', prop: 'status' },
      { label: '处理说明', prop: 'result_note' }
    ],
    fields: [
      { label: '处理状态', prop: 'status', options: [
        { label: '待处理', value: 0 },
        { label: '已处理', value: 1 },
        { label: '已驳回', value: 2 }
      ] },
      { label: '处理说明', prop: 'result_note', type: 'textarea' }
    ]
  },
  'miniapp-user': {
    title: '学生用户',
    searchKey: 'nickname',
    searchLabel: '微信昵称',
    allowCreate: false,
    defaultQuery: { role_type: 'student' },
    filters: [
      { label: '手机号', prop: 'mobile' },
      { label: '学校', prop: 'school_name' },
      { label: '校区', prop: 'campus_name' },
      { label: '年级', prop: 'grade' },
      { label: '身份', prop: 'role_type', options: [
        { label: '学生', value: 'student' },
        { label: '商家', value: 'merchant' },
        { label: '代理', value: 'agent' }
      ] },
      { label: '性别', prop: 'gender', options: [
        { label: '不公开', value: '不公开' },
        { label: '男', value: '男' },
        { label: '女', value: '女' }
      ] },
      { label: '校区租户ID', prop: 'tenant_id', type: 'number' }
    ],
    columns: [
      { label: 'OpenID', prop: 'openid' },
      { label: '微信昵称', prop: 'nickname' },
      { label: '手机号', prop: 'mobile' },
      { label: '学校', prop: 'school_name' },
      { label: '校区', prop: 'campus_name' },
      { label: '年级', prop: 'grade' },
      { label: '性别', prop: 'gender' },
      { label: '身份', prop: 'role_type' },
      { label: '租户ID', prop: 'tenant_id' },
      { label: '最近登录', prop: 'last_login_time' }
    ],
    fields: [
      { label: '昵称', prop: 'nickname' },
      { label: '头像', prop: 'avatar' },
      { label: '手机号', prop: 'mobile' },
      { label: '国家区号', prop: 'phone_country_code' },
      { label: '学校名称', prop: 'school_name' },
      { label: '校区名称', prop: 'campus_name' },
      { label: '年级', prop: 'grade' },
      { label: '性别', prop: 'gender', options: [
        { label: '不公开', value: '不公开' },
        { label: '男', value: '男' },
        { label: '女', value: '女' }
      ] },
      { label: '身份类型', prop: 'role_type', options: [
        { label: '学生', value: 'student' },
        { label: '商家', value: 'merchant' },
        { label: '代理', value: 'agent' }
      ] },
      { label: '入口 scene', prop: 'source_scene' },
      { label: '邀请人ID', prop: 'inviter_user_id', type: 'number' },
      { label: '租户ID', prop: 'tenant_id', type: 'number' }
    ]
  }
}

const resource = computed(() => String(route.path.split('/').filter(Boolean).pop() || 'region'))
const meta = computed(() => metas[resource.value] || metas.region)
const loading = ref(false)
const submitLoading = ref(false)
const list = ref<Record<string, any>[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formType = ref<'create' | 'update'>('create')
const formData = ref<Record<string, any>>({})
const queryParams = reactive<Record<string, any>>({
  pageNo: 1,
  pageSize: 10,
  ...(meta.value.defaultQuery || {})
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getCampusPage(resource.value, queryParams as PageParam)
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
  Object.keys(queryParams).forEach((key) => {
    if (!['pageNo', 'pageSize'].includes(key)) delete queryParams[key]
  })
  Object.assign(queryParams, meta.value.defaultQuery || {})
  handleQuery()
}

const openForm = async (type: 'create' | 'update', id?: number) => {
  formType.value = type
  dialogTitle.value = `${type === 'create' ? '新增' : '编辑'}${meta.value.title}`
  formData.value = {}
  for (const field of meta.value.fields) {
    if (field.defaultValue !== undefined) formData.value[field.prop] = field.defaultValue
    else if (field.type === 'number' || field.type === 'decimal') formData.value[field.prop] = 0
    else if (field.type === 'boolean') formData.value[field.prop] = true
    else formData.value[field.prop] = ''
  }
  if (type === 'update' && id) {
    formData.value = await getCampus(resource.value, id)
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  submitLoading.value = true
  try {
    if (formType.value === 'create') await createCampus(resource.value, formData.value)
    else await updateCampus(resource.value, formData.value)
    message.success('保存成功')
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (id: number) => {
  await message.delConfirm()
  await deleteCampus(resource.value, id)
  message.success('删除成功')
  await getList()
}

const openLog = (id: number) => {
  router.push({ path: '/campus/data-log', query: { bizId: String(id) } })
}

watch(resource, () => {
  resetQuery()
})

onMounted(() => {
  getList()
})
</script>
