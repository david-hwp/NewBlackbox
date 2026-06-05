<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>店铺列表</span>
          <el-button type="primary" @click="showAddDialog">新增店铺</el-button>
        </div>
      </template>

      <el-table :data="shops" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="shopName" label="店铺名称" />
        <el-table-column prop="shopId" label="店铺ID" />
        <el-table-column prop="cloneInstanceId" label="唯一标识" min-width="220">
          <template #default="{ row }">
            <el-text v-if="row.cloneInstanceId" class="mono" truncated>{{ row.cloneInstanceId }}</el-text>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="关联用户" min-width="150">
          <template #default="{ row }">
            {{ row.userName || row.userPhone || row.userId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="platformName" label="平台" />
        <el-table-column prop="remainingDays" label="剩余天数">
          <template #default="{ row }">
            <el-tag :type="getDaysType(row.remainingDays)">{{ row.remainingDays }}天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="autoRenew" label="自动续时">
          <template #default="{ row }">
            <el-tag :type="row.autoRenew ? 'success' : 'info'">
              {{ row.autoRenew ? '已开启' : '未开启' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑店铺' : '新增店铺'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="店铺名称" prop="shopName">
          <el-input v-model="form.shopName" />
        </el-form-item>
        <el-form-item label="店铺ID" prop="shopId">
          <el-input v-model="form.shopId" />
        </el-form-item>
        <el-form-item label="所属用户" prop="userId">
          <el-select v-model="form.userId" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" style="width: 100%" @change="onPlatformChange">
            <el-option
              v-for="platform in platforms"
              :key="platform.id"
              :label="platform.name"
              :value="platform.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="form.packageName" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="唯一标识">
          <el-input v-model="form.cloneInstanceId" disabled placeholder="由APK创建分身后自动上报" />
        </el-form-item>
        <el-form-item label="剩余天数">
          <el-input-number v-model="form.remainingDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="自动续时">
          <el-switch v-model="form.autoRenew" />
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

const shops = ref([])
const users = ref([])
const platforms = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = ref({
  shopName: '',
  shopId: '',
  userId: '',
  platform: '',
  platformName: '',
  remainingDays: 0,
  autoRenew: false,
  packageName: '',
  cloneInstanceId: ''
})

const rules = {
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  shopId: [{ required: true, message: '请输入店铺ID', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择所属用户', trigger: 'change' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }]
}

const fetchShops = async () => {
  loading.value = true
  try {
    const [shopList, userList, platformList] = await Promise.all([
      request.get('/shops'),
      request.get('/users'),
      request.get('/platforms')
    ])
    shops.value = shopList
    users.value = userList
    platforms.value = platformList
  } finally {
    loading.value = false
  }
}

const onPlatformChange = (val) => {
  const platform = platforms.value.find(item => item.id === val)
  form.value.platformName = platform?.name || val
  form.value.packageName = platform?.packageName || form.value.packageName
}

const getDaysType = (days) => {
  if (days <= 3) return 'danger'
  if (days <= 7) return 'warning'
  return 'success'
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { shopName: '', shopId: '', userId: '', platform: '', platformName: '', remainingDays: 0, autoRenew: false, packageName: '', cloneInstanceId: '' }
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

  try {
    if (isEdit.value) {
      await request.put(`/shops/${form.value.id}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/shops', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchShops()
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该店铺吗？', '提示', { type: 'warning' })
    await request.delete(`/shops/${row.id}`)
    ElMessage.success('删除成功')
    fetchShops()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(fetchShops)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  max-width: 210px;
}
</style>
