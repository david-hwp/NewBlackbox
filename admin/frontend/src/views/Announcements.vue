<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>公告列表</span>
          <el-button type="primary" @click="showAddDialog">发布公告</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="filters.title" clearable placeholder="输入公告标题" style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" clearable placeholder="全部类型" style="width: 150px">
            <el-option label="普通公告" value="NORMAL" />
            <el-option label="版本发布" value="APP_RELEASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.published" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="已发布" :value="true" />
            <el-option label="草稿" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="announcements" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === 'APP_RELEASE' ? 'success' : 'info'">
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="published" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'">
              {{ row.published ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="180">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公告' : '发布公告'" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            :disabled="form.type === APP_RELEASE_TYPE"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="普通公告" value="NORMAL" />
            <el-option label="版本发布" value="APP_RELEASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-form-item label="发布">
          <el-switch v-model="form.published" />
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
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const announcements = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const APP_RELEASE_TYPE = 'APP_RELEASE'
const APP_RELEASE_TITLE = '新版本发布'
const filters = ref({
  title: '',
  type: '',
  published: null
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const form = ref({ title: '', content: '', type: 'NORMAL', published: true })

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const typeLabel = (type) => {
  if (type === APP_RELEASE_TYPE) return '版本发布'
  return '普通公告'
}

watch(
  () => form.value.type,
  (type) => {
    if (type === APP_RELEASE_TYPE) {
      form.value.title = APP_RELEASE_TITLE
    }
  }
)

const fetchAnnouncements = async () => {
  loading.value = true
  try {
    const result = await request.get('/announcements', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        title: filters.value.title || undefined,
        type: filters.value.type || undefined,
        published: normalizeBooleanFilter(filters.value.published)
      }
    })
    announcements.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchAnnouncements()
}

const resetFilters = () => {
  filters.value = {
    title: '',
    type: '',
    published: null
  }
  pagination.value.page = 1
  fetchAnnouncements()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchAnnouncements()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchAnnouncements()
}

const normalizeBooleanFilter = (value) => {
  return value === true || value === false ? value : undefined
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { title: '', content: '', type: 'NORMAL', published: true }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row, type: row.type || 'NORMAL' }
  if (form.value.type === APP_RELEASE_TYPE) {
    form.value.title = APP_RELEASE_TITLE
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (form.value.type === APP_RELEASE_TYPE) {
    form.value.title = APP_RELEASE_TITLE
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    await request.put(`/announcements/${form.value.id}`, form.value)
    ElMessage.success('更新成功')
  } else {
    await request.post('/announcements', form.value)
    ElMessage.success('发布成功')
  }
  dialogVisible.value = false
  fetchAnnouncements()
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该公告吗？', '提示', { type: 'warning' })
    await request.delete(`/announcements/${row.id}`)
    ElMessage.success('删除成功')
    fetchAnnouncements()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(fetchAnnouncements)
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
