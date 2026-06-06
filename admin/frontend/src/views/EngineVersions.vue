<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>引擎版本</span>
          <el-button type="primary" @click="showAddDialog">新增版本</el-button>
        </div>
      </template>

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
const form = ref({ versionCode: 1, versionName: '', apkUrl: '', checksum: '', changelog: '', available: true })

const rules = {
  versionCode: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  apkUrl: [{ required: true, message: '请输入APK地址', trigger: 'blur' }]
}

const fetchVersions = async () => {
  loading.value = true
  try {
    versions.value = await request.get('/engine-versions')
  } finally {
    loading.value = false
  }
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
    const res = await request.post('/files/engine-packages', data)
    form.value.apkUrl = res.url
    ElMessage.success('APK上传成功')
  } finally {
    uploading.value = false
  }
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
</style>
