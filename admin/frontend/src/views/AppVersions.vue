<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>主 APK 版本</span>
          <el-button type="primary" @click="showAddDialog">新增版本</el-button>
        </div>
      </template>

      <el-table :data="versions" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="versionCode" label="版本号" width="100" />
        <el-table-column prop="versionName" label="版本名称" width="150" />
        <el-table-column label="APK地址" min-width="280">
          <template #default="{ row }">
            <el-link type="primary" :href="resolveDownloadUrl(row.apkUrl)" target="_blank">
              {{ row.apkUrl }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="changelog" label="更新日志" min-width="220" show-overflow-tooltip />
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="published" label="发布" width="90">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑版本' : '新增版本'" width="660px">
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
        <el-form-item label="文件大小">
          <el-input-number v-model="form.fileSize" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="更新日志">
          <el-input
            v-model="form.changelog"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="请输入本次主 APK 发布的更新内容"
          />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const versions = ref([])
const loading = ref(false)
const uploading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const emptyForm = () => ({
  versionCode: 1,
  versionName: '',
  apkUrl: '',
  checksum: '',
  fileSize: 0,
  changelog: '',
  published: true
})

const form = ref(emptyForm())

const rules = {
  versionCode: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  apkUrl: [{ required: true, message: '请输入APK地址', trigger: 'blur' }]
}

const fetchVersions = async () => {
  loading.value = true
  try {
    versions.value = await request.get('/app-versions')
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = emptyForm()
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row, changelog: row.changelog || '', fileSize: row.fileSize || 0 }
  dialogVisible.value = true
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatFileSize = (value) => {
  if (!value) return '-'
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

const resolveDownloadUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return url
}

const handleApkChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  const data = new FormData()
  data.append('file', uploadFile.raw)
  uploading.value = true
  try {
    const checksum = await sha256(uploadFile.raw)
    const res = await request.post('/files/app-packages', data)
    form.value.apkUrl = res.url
    form.value.checksum = checksum
    form.value.fileSize = uploadFile.raw.size
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
    await request.put(`/app-versions/${form.value.id}`, form.value)
    ElMessage.success('更新成功')
  } else {
    await request.post('/app-versions', form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchVersions()
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该版本吗？', '提示', { type: 'warning' })
    await request.delete(`/app-versions/${row.id}`)
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
</style>
