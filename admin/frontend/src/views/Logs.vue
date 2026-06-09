<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>交易日志</span>
          <el-button v-if="isSuperAdmin" type="primary" @click="showAddDialog">新增记录</el-button>
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
        <el-form-item label="类型">
          <el-select v-model="filters.type" clearable placeholder="全部类型" style="width: 140px">
            <el-option label="消耗" value="CONSUME" />
            <el-option label="转出" value="OUT" />
            <el-option label="转入" value="IN" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="用户/转入/转出手机号" style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="店铺">
          <el-input v-model="filters.shopName" clearable placeholder="输入店铺名称" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="getLogTypeTag(row.type)">{{ getLogTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额">
          <template #default="{ row }">
            <span :style="{ color: getAmountColor(row.type), fontWeight: 600 }">
              {{ row.type === 'IN' ? '+' : '-' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="platform" label="关联平台" />
        <el-table-column prop="shopName" label="关联店铺" />
        <el-table-column label="关联用户" min-width="150">
          <template #default="{ row }">
            {{ formatAssociatedUser(row) }}
          </template>
        </el-table-column>
        <el-table-column label="渠道" min-width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ channelText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fromPhone" label="转出方" />
        <el-table-column prop="toPhone" label="接收方" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createdAt" label="时间" />
        <el-table-column v-if="isSuperAdmin" label="操作" width="100">
          <template #default="{ row }">
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

    <el-dialog v-model="dialogVisible" title="新增交易记录" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="消耗" value="CONSUME" />
            <el-option label="转出" value="OUT" />
            <el-option label="转入" value="IN" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="所属用户" prop="userId">
          <el-select v-model="form.userId" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联平台">
          <el-input v-model="form.platform" />
        </el-form-item>
        <el-form-item label="关联店铺">
          <el-input v-model="form.shopName" />
        </el-form-item>
        <el-form-item label="转出方手机号">
          <el-input v-model="form.fromPhone" />
        </el-form-item>
        <el-form-item label="接收方手机号">
          <el-input v-model="form.toPhone" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
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
import { channelFilterParam, formatChannelLabel, useAdminSession } from '../utils/adminSession'

const logs = ref([])
const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const filters = ref({
  type: '',
  phone: '',
  shopName: '',
  channelId: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const form = ref({ type: '', amount: 0, userId: '', platform: '', shopName: '', fromPhone: '', toPhone: '', remark: '' })
const { channels, isSuperAdmin, fetchChannels, channelText } = useAdminSession()

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }]
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const result = await request.get('/logs', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        type: filters.value.type || undefined,
        phone: filters.value.phone || undefined,
        shopName: filters.value.shopName || undefined,
        channelId: channelFilterParam(isSuperAdmin.value, filters.value.channelId)
      }
    })
    logs.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const fetchUsers = async () => {
  users.value = await request.get('/users')
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchLogs()
}

const resetFilters = () => {
  filters.value = {
    type: '',
    phone: '',
    shopName: '',
    channelId: ''
  }
  pagination.value.page = 1
  fetchLogs()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchLogs()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchLogs()
}

const getLogTypeTag = (type) => {
  const map = { CONSUME: 'info', OUT: 'warning', IN: 'success' }
  return map[type] || 'info'
}

const getLogTypeText = (type) => {
  const map = { CONSUME: '消耗', OUT: '转出', IN: '转入' }
  return map[type] || type
}

const getAmountColor = (type) => {
  const map = { CONSUME: '#0284c7', OUT: '#d97706', IN: '#059669' }
  return map[type] || '#1e293b'
}

const formatAssociatedUser = (row) => {
  return row.userName || row.userPhone || row.userId || '-'
}

const showAddDialog = () => {
  if (!isSuperAdmin.value) return
  form.value = { type: '', amount: 0, userId: '', platform: '', shopName: '', fromPhone: '', toPhone: '', remark: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    await request.post('/logs', form.value)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchLogs()
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该记录吗？', '提示', { type: 'warning' })
    await request.delete(`/logs/${row.id}`)
    ElMessage.success('删除成功')
    fetchLogs()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(() => {
  fetchChannels().finally(() => {
    fetchLogs()
    fetchUsers()
  })
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
</style>
