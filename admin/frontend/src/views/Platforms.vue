<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>支持平台</span>
          <div>
            <el-button @click="fetchPlatforms">刷新</el-button>
            <el-button type="primary" @click="showAddDialog">新增平台</el-button>
          </div>
        </div>
      </template>

      <el-table :data="platforms" v-loading="loading" style="width: 100%">
        <el-table-column label="图标" width="90">
          <template #default="{ row }">
            <img
              class="platform-icon"
              :class="{ unavailable: !row.available }"
              :src="row.iconUrl"
              :alt="row.name"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="平台名称" min-width="140" />
        <el-table-column prop="id" label="平台标识" min-width="120" />
        <el-table-column prop="packageName" label="应用包名" min-width="240" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column prop="available" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.available ? 'success' : 'info'">
              {{ row.available ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑平台' : '新增平台'" width="620px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="平台标识" prop="id">
          <el-input v-model="form.id" placeholder="如 jd、meituan" />
        </el-form-item>
        <el-form-item label="平台名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="form.packageName" placeholder="对应手机上的平台 App 包名" />
        </el-form-item>
        <el-form-item label="图标地址">
          <el-input v-model="form.iconUrl" />
        </el-form-item>
        <el-form-item label="上传图标">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="handleIconChange"
          >
            <el-button :loading="uploading">选择并上传</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="可用">
          <el-switch v-model="form.available" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
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

const platforms = ref([])
const loading = ref(false)
const uploading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = ref({
  id: '',
  name: '',
  packageName: '',
  iconUrl: '',
  available: false,
  sortOrder: 0
})

const rules = {
  id: [{ required: true, message: '请输入平台标识', trigger: 'blur' }],
  name: [{ required: true, message: '请输入平台名称', trigger: 'blur' }]
}

const fetchPlatforms = async () => {
  loading.value = true
  try {
    platforms.value = await request.get('/platforms')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.value = {
    id: '',
    name: '',
    packageName: '',
    iconUrl: '',
    available: false,
    sortOrder: 0
  }
}

const showAddDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleIconChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  const data = new FormData()
  data.append('file', uploadFile.raw)
  uploading.value = true
  try {
    const res = await request.post('/files/platform-icons', data)
    form.value.iconUrl = res.url
    ElMessage.success('图标上传成功')
  } finally {
    uploading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    await request.put(`/platforms/${form.value.dbId}`, form.value)
    ElMessage.success('更新成功')
  } else {
    await request.post('/platforms', form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchPlatforms()
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该平台吗？', '提示', { type: 'warning' })
    await request.delete(`/platforms/${row.dbId}`)
    ElMessage.success('删除成功')
    fetchPlatforms()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(fetchPlatforms)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.platform-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  display: block;
}

.platform-icon.unavailable {
  filter: grayscale(100%);
  opacity: 0.45;
}
</style>
