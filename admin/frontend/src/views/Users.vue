<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <el-button type="primary" @click="showAddDialog">新增用户</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="filters.username" clearable placeholder="输入用户名" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="输入手机号" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.role" clearable placeholder="全部角色" style="width: 140px">
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
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apkChannel" label="渠道标识" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.apkChannel || 'main' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="computeBalance" label="算力余额" />
        <el-table-column prop="nonTransferableComputeBalance" label="不可转赠" />
        <el-table-column prop="shopCount" label="店铺数" />
        <el-table-column prop="createdAt" label="创建时间" />
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

    <!-- 新增/编辑弹窗 -->
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
            <el-option label="用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="算力余额">
          <el-input-number v-model="form.computeBalance" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不可转赠">
          <el-input-number v-model="form.nonTransferableComputeBalance" :min="0" :max="form.computeBalance || 0" style="width: 100%" />
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

const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const filters = ref({
  username: '',
  phone: '',
  role: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const form = ref({ username: '', phone: '', password: '', role: 'USER', computeBalance: 0, nonTransferableComputeBalance: 0 })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const result = await request.get('/users', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        username: filters.value.username || undefined,
        phone: filters.value.phone || undefined,
        role: filters.value.role || undefined
      }
    })
    users.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
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
    role: ''
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
  isEdit.value = false
  form.value = { username: '', phone: '', password: '', role: 'USER', computeBalance: 0, nonTransferableComputeBalance: 0 }
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
      await request.put(`/users/${form.value.id}`, form.value)
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

onMounted(fetchUsers)
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
