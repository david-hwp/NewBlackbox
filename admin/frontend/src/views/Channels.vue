<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>渠道管理</span>
          <el-button type="primary" @click="showAddDialog">新增渠道</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="渠道名称/code/应用名"
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="pagedChannels" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="渠道名称" min-width="130" />
        <el-table-column prop="code" label="Code" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="isDisabled(row) ? 'info' : 'success'">
              {{ isDisabled(row) ? '禁用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主 APK" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.appDisplayName || '-' }} / {{ row.appApplicationId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="引擎 APK" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.engineDisplayName || '-' }} / {{ row.engineApplicationId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="注册赠送" width="110">
          <template #default="{ row }">
            {{ row.registerBonusCompute ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="管理员" width="110">
          <template #default="{ row }">
            {{ row.adminUserId ? `#${row.adminUserId}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button :type="isDisabled(row) ? 'success' : 'warning'" link @click="toggleStatus(row)">
              {{ isDisabled(row) ? '启用' : '禁用' }}
            </el-button>
            <el-button type="danger" link :disabled="row.code === 'main'" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑渠道' : '新增渠道'" width="760px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px">
        <div class="form-grid">
          <el-form-item label="渠道名称" prop="name">
            <el-input v-model="form.name" maxlength="128" show-word-limit />
          </el-form-item>
          <el-form-item label="Code" prop="code">
            <el-input v-model="form.code" :disabled="isEdit" maxlength="64" placeholder="英文小写、数字、-、_" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="注册赠送" prop="registerBonusCompute">
            <el-input-number v-model="form.registerBonusCompute" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="主 APK 名称">
            <el-input v-model="form.appDisplayName" maxlength="128" />
          </el-form-item>
          <el-form-item label="主 APK 包名" prop="appApplicationId">
            <el-input v-model="form.appApplicationId" maxlength="128" />
          </el-form-item>
          <el-form-item label="引擎名称">
            <el-input v-model="form.engineDisplayName" maxlength="128" />
          </el-form-item>
          <el-form-item label="引擎包名" prop="engineApplicationId">
            <el-input v-model="form.engineApplicationId" maxlength="128" />
          </el-form-item>
        </div>

        <el-form-item label="引擎通知标题">
          <el-input v-model="form.engineNotificationTitle" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="引擎通知文案">
          <el-input v-model="form.engineNotificationText" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="渠道管理员">
          <el-select
            v-model="form.adminUserId"
            clearable
            filterable
            remote
            reserve-keyword
            :remote-method="searchAdminCandidates"
            :loading="candidateLoading"
            placeholder="搜索用户名或手机号"
            style="width: 100%"
          >
            <el-option
              v-for="candidate in adminCandidates"
              :key="candidate.id"
              :label="candidateLabel(candidate)"
              :value="candidate.id"
            />
          </el-select>
          <div class="form-help">保存后会将所选用户绑定为该渠道管理员；清空不会解绑已绑定管理员。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { formatDateTime, isChannelDisabled } from '../utils/adminSession'

const channels = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const candidateLoading = ref(false)
const adminCandidates = ref([])
const filters = ref({
  keyword: '',
  status: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

const emptyForm = () => ({
  id: null,
  name: '',
  code: '',
  status: 'ACTIVE',
  appDisplayName: '',
  appApplicationId: 'com.zhirang.zhanghaoguanjia',
  engineDisplayName: '',
  engineApplicationId: 'com.zhirang.zhanghaoguanjia.engine',
  engineNotificationTitle: '',
  engineNotificationText: '',
  registerBonusCompute: 3,
  adminUserId: null,
  remark: ''
})

const form = ref(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入渠道 Code', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9_-]{0,63}$/, message: 'Code 仅支持小写字母、数字、-、_', trigger: 'blur' }
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  appApplicationId: [{ required: true, message: '请输入主 APK 包名', trigger: 'blur' }],
  engineApplicationId: [{ required: true, message: '请输入引擎包名', trigger: 'blur' }]
}

const filteredChannels = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  const status = filters.value.status
  return channels.value.filter((channel) => {
    const matchStatus = !status || String(channel.status || '').toUpperCase() === status
    const haystack = [
      channel.name,
      channel.code,
      channel.appDisplayName,
      channel.appApplicationId,
      channel.engineDisplayName,
      channel.engineApplicationId,
      channel.adminUserId
    ].filter(Boolean).join(' ').toLowerCase()
    const matchKeyword = !keyword || haystack.includes(keyword)
    return matchStatus && matchKeyword
  })
})

const pagedChannels = computed(() => {
  pagination.value.total = filteredChannels.value.length
  const start = (pagination.value.page - 1) * pagination.value.size
  return filteredChannels.value.slice(start, start + pagination.value.size)
})

const fetchChannels = async () => {
  loading.value = true
  try {
    channels.value = await request.get('/channels')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
}

const resetFilters = () => {
  filters.value = { keyword: '', status: '' }
  pagination.value.page = 1
}

const handlePageChange = (page) => {
  pagination.value.page = page
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
}

const isDisabled = (row) => isChannelDisabled(row)

const candidateLabel = (candidate) => {
  const parts = [candidate.username || `#${candidate.id}`, candidate.phone].filter(Boolean)
  return parts.join(' / ')
}

const searchAdminCandidates = async (keyword = '') => {
  candidateLoading.value = true
  try {
    adminCandidates.value = await request.get('/channels/admin-candidates', {
      params: { keyword: keyword || undefined }
    })
  } finally {
    candidateLoading.value = false
  }
}

const ensureAdminOption = (row) => {
  if (!row.adminUserId) return
  if (!adminCandidates.value.some(item => Number(item.id) === Number(row.adminUserId))) {
    adminCandidates.value = [
      { id: row.adminUserId, username: `已绑定管理员 #${row.adminUserId}`, phone: '' },
      ...adminCandidates.value
    ]
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = emptyForm()
  adminCandidates.value = []
  dialogVisible.value = true
  searchAdminCandidates()
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = {
    ...emptyForm(),
    ...row,
    status: String(row.status || 'ACTIVE').toUpperCase(),
    registerBonusCompute: row.registerBonusCompute ?? 0,
    adminUserId: row.adminUserId || null
  }
  ensureAdminOption(row)
  dialogVisible.value = true
}

const submitChannel = async () => {
  const payload = { ...form.value }
  if (isEdit.value) {
    return request.put(`/channels/${payload.id}`, payload)
  }
  return request.post('/channels', payload)
}

const bindAdminIfNeeded = async (channel) => {
  if (!form.value.adminUserId || !channel?.id) return
  await request.post(`/channels/${channel.id}/admin`, { userId: form.value.adminUserId })
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const channel = await submitChannel()
    await bindAdminIfNeeded(channel)
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchChannels()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row) => {
  const nextStatus = isDisabled(row) ? 'ACTIVE' : 'DISABLED'
  const action = nextStatus === 'ACTIVE' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}渠道「${row.name || row.code}」吗？`, '提示', { type: 'warning' })
    await request.put(`/channels/${row.id}`, { ...row, status: nextStatus })
    ElMessage.success(`${action}成功`)
    fetchChannels()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除渠道「${row.name || row.code}」吗？删除后不可在列表中查看。`, '提示', { type: 'warning' })
    await request.delete(`/channels/${row.id}`)
    ElMessage.success('删除成功')
    fetchChannels()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(fetchChannels)
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.form-help {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
