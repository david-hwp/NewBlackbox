<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>系统参数</span>
          <el-button type="primary" @click="showAddDialog">新增参数</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="中文名称/编码/说明"
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="filters.channelId" clearable placeholder="全部渠道" style="width: 180px">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="channelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="pagedParameters" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="中文名称" min-width="180" />
        <el-table-column prop="code" label="编码" min-width="240" show-overflow-tooltip />
        <el-table-column prop="value" label="值" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="渠道" min-width="150">
          <template #default="{ row }">
            {{ channelName(row.channelId) }}
          </template>
        </el-table-column>
        <el-table-column label="是否内置" width="100">
          <template #default="{ row }">
            <el-tag :type="isBuiltin(row) ? 'warning' : 'info'" size="small">
              {{ isBuiltin(row) ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button
              type="danger"
              link
              :disabled="isBuiltin(row)"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑系统参数' : '新增系统参数'" width="620px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="渠道" prop="channelId">
          <el-select v-model="form.channelId" placeholder="选择渠道" style="width: 100%">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="channelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="中文名称" prop="name">
          <el-input v-model="form.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" maxlength="128" placeholder="如 app.menu.gift_compute.label" />
        </el-form-item>
        <el-form-item label="值" prop="value">
          <el-input v-model="form.value" type="textarea" :rows="3" maxlength="1024" show-word-limit />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" show-word-limit />
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
import { formatDateTime } from '../utils/adminSession'

const parameters = ref([])
const channels = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const filters = ref({
  keyword: '',
  channelId: null
})

const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

const emptyForm = () => ({
  id: null,
  channelId: null,
  name: '',
  code: '',
  value: '',
  description: ''
})

const form = ref(emptyForm())

const rules = {
  channelId: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  name: [{ required: true, message: '请输入中文名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

const pagedParameters = computed(() => {
  pagination.value.total = parameters.value.length
  const start = (pagination.value.page - 1) * pagination.value.size
  return parameters.value.slice(start, start + pagination.value.size)
})

const channelLabel = (channel) => `${channel.name || '-'} / ${channel.code || '-'}`

const channelName = (channelId) => {
  const channel = channels.value.find(item => item.id === channelId)
  return channel ? channelLabel(channel) : `#${channelId || '-'}`
}

const isBuiltin = (row) => row?.builtin === 1 || row?.builtin === true

const fetchChannels = async () => {
  channels.value = await request.get('/channels')
}

const fetchParameters = async () => {
  loading.value = true
  try {
    parameters.value = await request.get('/system-parameters', {
      params: {
        keyword: filters.value.keyword || undefined,
        channelId: filters.value.channelId || undefined
      }
    })
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchParameters()
}

const resetFilters = () => {
  filters.value = {
    keyword: '',
    channelId: null
  }
  pagination.value.page = 1
  fetchParameters()
}

const handlePageChange = (page) => {
  pagination.value.page = page
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = {
    ...emptyForm(),
    channelId: filters.value.channelId || channels.value[0]?.id || null
  }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/system-parameters/${form.value.id}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/system-parameters', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchParameters()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  if (isBuiltin(row)) {
    ElMessage.warning('系统内置参数不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除参数 ${row.code} 吗？`, '提示', { type: 'warning' })
    await request.delete(`/system-parameters/${row.id}`)
    ElMessage.success('删除成功')
    fetchParameters()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(async () => {
  await fetchChannels()
  await fetchParameters()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filter-bar {
  margin-bottom: 16px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
