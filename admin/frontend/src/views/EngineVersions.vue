<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>引擎版本</span>
          <el-button type="primary" @click="showAddDialog">新增版本</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="版本号">
          <el-input v-model="filters.versionCode" clearable placeholder="输入版本号" style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="版本名称">
          <el-input v-model="filters.versionName" clearable placeholder="输入版本名称" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="可用">
          <el-select v-model="filters.available" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="可用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="versions" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="versionCode" label="版本号" width="100" />
        <el-table-column prop="versionName" label="版本名称" width="140" />
        <el-table-column prop="apkUrl" label="APK地址" min-width="280" show-overflow-tooltip />
        <el-table-column prop="changelog" label="更新日志" min-width="220" show-overflow-tooltip />
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="available" label="可用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.available ? 'success' : 'info'">{{ row.available ? '可用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑版本' : '新增版本'" width="620px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="版本号" prop="versionCode">
          <el-input-number v-model="form.versionCode" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="版本名称" prop="versionName">
          <el-input v-model="form.versionName" />
        </el-form-item>
        <el-form-item label="APK地址" prop="apkUrl">
          <el-input v-model="form.apkUrl" />
        </el-form-item>
        <el-form-item label="上传APK">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".apk,application/vnd.android.package-archive"
            :on-change="handleApkChange"
          >
            <el-button :loading="uploading">选择并上传</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="校验值">
          <el-input v-model="form.checksum" />
        </el-form-item>
        <el-form-item label="更新日志">
          <el-input
            v-model="form.changelog"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="请输入本次引擎升级的更新内容"
          />
        </el-form-item>
        <el-form-item label="可用">
          <el-switch v-model="form.available" />
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

const versions = ref([])
const loading = ref(false)
const uploading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const filters = ref({
  versionCode: '',
  versionName: '',
  available: null
})
const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})
const form = ref({ versionCode: 1, versionName: '', apkUrl: '', checksum: '', changelog: '', available: true })

const rules = {
  versionCode: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  apkUrl: [{ required: true, message: '请输入APK地址', trigger: 'blur' }]
}

const fetchVersions = async () => {
  loading.value = true
  try {
    const result = await request.get('/engine-versions', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        versionCode: normalizeVersionCode(filters.value.versionCode),
        versionName: filters.value.versionName || undefined,
        available: normalizeBooleanFilter(filters.value.available)
      }
    })
    versions.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchVersions()
}

const resetFilters = () => {
  filters.value = {
    versionCode: '',
    versionName: '',
    available: null
  }
  pagination.value.page = 1
  fetchVersions()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchVersions()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchVersions()
}

const normalizeVersionCode = (value) => {
  if (value === null || value === undefined || value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : undefined
}

const normalizeBooleanFilter = (value) => {
  return value === true || value === false ? value : undefined
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { versionCode: 1, versionName: '', apkUrl: '', checksum: '', changelog: '', available: true }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row, changelog: row.changelog || '' }
  dialogVisible.value = true
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const handleApkChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  const data = new FormData()
  data.append('file', uploadFile.raw)
  uploading.value = true
  try {
    const checksum = await sha256(uploadFile.raw)
    const res = await request.post('/files/engine-packages', data)
    form.value.apkUrl = res.url
    form.value.checksum = checksum
    ElMessage.success('APK上传成功')
  } finally {
    uploading.value = false
  }
}

const sha256 = async (file) => {
  const buffer = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, '0')).join('')
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    await request.put(`/engine-versions/${form.value.id}`, form.value)
    ElMessage.success('更新成功')
  } else {
    await request.post('/engine-versions', form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchVersions()
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该版本吗？', '提示', { type: 'warning' })
    await request.delete(`/engine-versions/${row.id}`)
    ElMessage.success('删除成功')
    fetchVersions()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(fetchVersions)
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
