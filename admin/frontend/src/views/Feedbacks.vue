<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>问题反馈</span>
          <el-button @click="fetchFeedbacks">刷新</el-button>
        </div>
      </template>

      <el-table :data="feedbacks" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userPhone" label="用户手机号" width="140" />
        <el-table-column prop="content" label="反馈内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="图片" width="180">
          <template #default="{ row }">
            <el-space wrap>
              <el-link
                v-for="url in imageList(row.imageUrls)"
                :key="url"
                type="primary"
                @click="openPrivateFile(url)"
              >
                查看
              </el-link>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="日志" width="120">
          <template #default="{ row }">
            <el-link v-if="row.logUrl" type="primary" @click="downloadPrivateFile(row.logUrl)">下载ZIP</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="160">
          <template #default="{ row }">
            <el-space wrap>
              <el-link
                v-for="url in imageList(row.attachmentUrls)"
                :key="url"
                type="primary"
                @click="downloadPrivateFile(url)"
              >
                下载
              </el-link>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-select v-model="row.status" size="small" @change="updateStatus(row)" style="width: 110px">
              <el-option label="待处理" value="PENDING" />
              <el-option label="处理中" value="PROCESSING" />
              <el-option label="已解决" value="RESOLVED" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const feedbacks = ref([])
const loading = ref(false)

const fetchFeedbacks = async () => {
  loading.value = true
  try {
    feedbacks.value = await request.get('/feedbacks')
  } finally {
    loading.value = false
  }
}

const imageList = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value
  return String(value).split(',').filter(Boolean)
}

const updateStatus = async (row) => {
  await request.put(`/feedbacks/${row.id}/status`, { status: row.status })
  ElMessage.success('状态已更新')
}

const fetchPrivateBlob = async (url) => {
  const token = localStorage.getItem('admin_token')
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error(`文件下载失败: ${response.status}`)
  }
  return response.blob()
}

const openPrivateFile = async (url) => {
  try {
    const blob = await fetchPrivateBlob(url)
    window.open(URL.createObjectURL(blob), '_blank')
  } catch (e) {
    ElMessage.error(e.message || '文件打开失败')
  }
}

const downloadPrivateFile = async (url) => {
  try {
    const blob = await fetchPrivateBlob(url)
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = url.split('/').pop() || 'download'
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch (e) {
    ElMessage.error(e.message || '文件下载失败')
  }
}

onMounted(fetchFeedbacks)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
