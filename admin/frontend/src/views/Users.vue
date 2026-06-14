<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <el-button v-if="canMutate" type="primary" @click="showAddDialog">新增用户</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item v-if="isSuperAdmin" label="渠道">
          <el-select v-model="filters.channelId" clearable filterable placeholder="全部渠道" style="width: 190px">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="formatChannelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="filters.username" clearable placeholder="输入用户名" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="输入手机号" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.role" clearable placeholder="全部角色" style="width: 150px">
            <el-option label="超管" value="SUPER_ADMIN" />
            <el-option label="渠道管理员" value="CHANNEL" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.userType" clearable placeholder="全部类型" style="width: 140px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="users" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="渠道" min-width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ channelText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apkChannel" label="渠道标识" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.apkChannel || 'main' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="computeBalance" label="算力余额" />
        <el-table-column prop="nonTransferableComputeBalance" label="不可转赠" />
        <el-table-column prop="phoneMinutesBalance" label="话费余额(分钟)" />
        <el-table-column label="订阅状态" min-width="170">
          <template #default="{ row }">
            <div class="subscription-cell">
              <el-tag :type="isSubscriptionActive(row) ? 'success' : 'info'" size="small">
                {{ subscriptionPlanLabel(row.subscriptionPlan) }}
              </el-tag>
              <span class="subscription-expiry">{{ subscriptionText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="shopCount" label="店铺数" />
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column v-if="canMutate" label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="渠道管理员" value="CHANNEL" />
            <el-option label="超管" value="SUPER_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="算力余额">
          <el-input-number v-model="form.computeBalance" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不可转赠">
          <el-input-number v-model="form.nonTransferableComputeBalance" :min="0" :max="form.computeBalance || 0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="话费余额">
          <el-input-number v-model="form.phoneMinutesBalance" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="订阅套餐">
          <el-select v-model="subscriptionForm.plan" style="width: 100%" :disabled="!isEdit" @change="subscriptionTouched = true">
            <el-option label="关闭订阅" value="NONE" />
            <el-option label="体验订阅" value="TRIAL" disabled />
            <el-option label="月度订阅" value="MONTHLY" />
            <el-option label="季度订阅" value="QUARTERLY" />
            <el-option label="年度订阅" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isEdit" label="到期时间">
          <span>{{ form.subscriptionExpiresAt ? formatDateTime(form.subscriptionExpiresAt) : '未开通' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { channelFilterParam, formatChannelLabel, normalizeRole, useAdminSession } from '../utils/adminSession'

const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const originalSubscriptionPlan = ref('NONE')
const subscriptionForm = ref({ plan: 'NONE' })
const subscriptionTouched = ref(false)
const filters = ref({
  username: '',
  phone: '',
  role: '',
  userType: '',
  channelId: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const emptyUserForm = () => ({
  username: '',
  phone: '',
  password: '',
  role: 'USER',
  computeBalance: 0,
  nonTransferableComputeBalance: 0,
  phoneMinutesBalance: 0,
  subscriptionPlan: 'NONE'
})

const form = ref(emptyUserForm())

const { channels, isSuperAdmin, fetchChannels, channelText } = useAdminSession()
const canMutate = isSuperAdmin

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const adminRoles = ['SUPER_ADMIN', 'CHANNEL']

const selectedTypeRoles = () => {
  if (filters.value.userType === 'ADMIN') return adminRoles
  if (filters.value.userType === 'USER') return ['USER']
  return null
}

const hasConflictingRoleAndType = () => {
  const typeRoles = selectedTypeRoles()
  return Boolean(filters.value.role && typeRoles && !typeRoles.includes(filters.value.role))
}

const commonQueryParams = (overrides = {}) => ({
  page: pagination.value.page,
  size: pagination.value.size,
  username: filters.value.username || undefined,
  phone: filters.value.phone || undefined,
  channelId: channelFilterParam(isSuperAdmin.value, filters.value.channelId),
  ...overrides
})

const applyPagedResult = (result) => {
  users.value = result.list || result.content || []
  pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
}

const applyAdminTypeResult = async () => {
  const adminResults = await Promise.all(adminRoles.map(role =>
    request.get('/users', {
      params: commonQueryParams({ page: 1, size: 100, role })
    })
  ))
  const merged = adminResults
    .flatMap(result => result.list || result.content || [])
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
  const start = (pagination.value.page - 1) * pagination.value.size
  users.value = merged.slice(start, start + pagination.value.size)
  pagination.value.total = merged.length
}

const fetchUsers = async () => {
  loading.value = true
  try {
    if (hasConflictingRoleAndType()) {
      users.value = []
      pagination.value.total = 0
      return
    }
    if (!filters.value.role && filters.value.userType === 'ADMIN') {
      await applyAdminTypeResult()
      return
    }
    const result = await request.get('/users', {
      params: commonQueryParams({
        role: filters.value.role || (filters.value.userType === 'USER' ? 'USER' : undefined),
        type: filters.value.userType && filters.value.userType !== 'ADMIN' ? filters.value.userType : undefined
      })
    })
    applyPagedResult(result)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchUsers()
}

const resetFilters = () => {
  filters.value = {
    username: '',
    phone: '',
    role: '',
    userType: '',
    channelId: ''
  }
  pagination.value.page = 1
  fetchUsers()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchUsers()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchUsers()
}

const showAddDialog = () => {
  if (!canMutate.value) return
  isEdit.value = false
  form.value = emptyUserForm()
  originalSubscriptionPlan.value = 'NONE'
  subscriptionForm.value = { plan: 'NONE' }
  subscriptionTouched.value = false
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  if (!canMutate.value) return
  isEdit.value = true
  form.value = { ...row, phoneMinutesBalance: row.phoneMinutesBalance || 0 }
  const plan = normalizeSubscriptionPlan(row.subscriptionPlan)
  originalSubscriptionPlan.value = plan
  subscriptionForm.value = { plan: isSubscriptionActive(row) ? plan : 'NONE' }
  subscriptionTouched.value = false
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    if (isEdit.value) {
      await request.put(`/users/${form.value.id}`, form.value)
      if (subscriptionTouched.value && subscriptionForm.value.plan !== originalSubscriptionPlan.value) {
        await request.put(`/users/${form.value.id}/subscription`, subscriptionForm.value)
      }
      ElMessage.success('更新成功')
    } else {
      await request.post('/users', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchUsers()
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该用户吗？', '提示', { type: 'warning' })
    await request.delete(`/users/${row.id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const normalizeSubscriptionPlan = (plan) => {
  const normalized = String(plan || 'NONE').toUpperCase()
  return ['TRIAL', 'MONTHLY', 'QUARTERLY', 'YEARLY'].includes(normalized) ? normalized : 'NONE'
}

const subscriptionPlanLabel = (plan) => {
  switch (normalizeSubscriptionPlan(plan)) {
    case 'TRIAL':
      return '体验订阅'
    case 'MONTHLY':
      return '月度订阅'
    case 'QUARTERLY':
      return '季度订阅'
    case 'YEARLY':
      return '年度订阅'
    default:
      return '普通用户'
  }
}

const isSubscriptionActive = (row) => {
  if (row.subscriptionActive === true) return true
  if (!row.subscriptionExpiresAt) return false
  return new Date(row.subscriptionExpiresAt).getTime() > Date.now()
}

const subscriptionText = (row) => {
  if (isSubscriptionActive(row)) {
    return `到期 ${formatDateTime(row.subscriptionExpiresAt)}`
  }
  if (normalizeSubscriptionPlan(row.subscriptionPlan) !== 'NONE') {
    return '已到期'
  }
  return '未开通'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const roleLabel = (role) => {
  const normalized = normalizeRole(role)
  const map = { SUPER_ADMIN: '超管', CHANNEL: '渠道管理员', USER: '普通用户' }
  return map[normalized] || role || '-'
}

const roleTag = (role) => {
  const normalized = normalizeRole(role)
  const map = { SUPER_ADMIN: 'danger', CHANNEL: 'warning', USER: 'info' }
  return map[normalized] || 'info'
}

onMounted(() => {
  fetchChannels()
  fetchUsers()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin-bottom: 16px;
  padding: 12px 12px 0;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.subscription-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.subscription-expiry {
  color: #64748b;
  font-size: 12px;
}
</style>
