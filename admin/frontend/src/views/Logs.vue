<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>交易日志</span>
          <el-button type="primary" @click="showAddDialog">新增记录</el-button>
        </div>
      </template>

      <el-radio-group v-model="filterType" @change="handleFilter" style="margin-bottom: 16px;">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="CONSUME">消耗</el-radio-button>
        <el-radio-button label="OUT">转出</el-radio-button>
        <el-radio-button label="IN">转入</el-radio-button>
      </el-radio-group>

      <el-table :data="filteredLogs" v-loading="loading" style="width: 100%">
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
        <el-table-column prop="fromPhone" label="转出方" />
        <el-table-column prop="toPhone" label="接收方" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createdAt" label="时间" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const logs = ref([])
const users = ref([])
const loading = ref(false)
const filterType = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const form = ref({ type: '', amount: 0, userId: '', platform: '', shopName: '', fromPhone: '', toPhone: '', remark: '' })

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }]
}

const filteredLogs = computed(() => {
  if (!filterType.value) return logs.value
  return logs.value.filter(l => l.type === filterType.value)
})

const fetchLogs = async () => {
  loading.value = true
  try {
    logs.value = await request.get('/logs')
    users.value = await request.get('/users')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  // 前端过滤，无需额外请求
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

const showAddDialog = () => {
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

onMounted(fetchLogs)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
