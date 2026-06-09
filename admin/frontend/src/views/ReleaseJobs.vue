<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>发布任务</span>
          <el-button type="primary" @click="showCreateDialog">新建发布任务</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="渠道">
          <el-select v-model="filters.channelId" clearable filterable placeholder="全部渠道" style="width: 200px">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="formatChannelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="等待中" value="PENDING" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="jobs" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="渠道" min-width="150">
          <template #default="{ row }">
            {{ channelText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="170">
          <template #default="{ row }">
            <el-progress :percentage="normalizeProgress(row.progress)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="分支" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.sourceReleaseBranch || row.sourceReleaseRef || '-' }} -> {{ row.channelReleaseBranch || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="主 APK" min-width="150">
          <template #default="{ row }">
            {{ row.appVersionName || row.versionName || '-' }} / {{ row.appVersionCode || row.versionCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="引擎" min-width="150">
          <template #default="{ row }">
            {{ row.engineVersionName || '-' }} / {{ row.engineVersionCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt || row.finishedAt || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-button type="warning" link :disabled="!isFailed(row.status)" @click="retryJob(row)">重试</el-button>
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

    <el-dialog v-model="dialogVisible" title="新建发布任务" width="760px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="140px">
        <el-form-item label="发布渠道" prop="channelId">
          <el-select v-model="form.channelId" filterable placeholder="选择 main 或渠道" style="width: 100%" @change="onChannelChange">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="formatChannelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="源发布分支" prop="sourceReleaseBranch">
            <el-input v-model="form.sourceReleaseBranch" placeholder="release/1.0.0" />
          </el-form-item>
          <el-form-item label="渠道发布分支" prop="channelReleaseBranch">
            <el-input v-model="form.channelReleaseBranch" placeholder="release/channel/main/1.0.0" />
          </el-form-item>
          <el-form-item label="主 APK 版本名" prop="appVersionName">
            <el-input v-model="form.appVersionName" placeholder="1.0.0" />
          </el-form-item>
          <el-form-item label="主 APK 版本号" prop="appVersionCode">
            <el-input-number v-model="form.appVersionCode" :min="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="引擎版本名" prop="engineVersionName">
            <el-input v-model="form.engineVersionName" placeholder="1.0.0" />
          </el-form-item>
          <el-form-item label="引擎版本号" prop="engineVersionCode">
            <el-input-number v-model="form.engineVersionCode" :min="1" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="公告内容" prop="announcementContent">
          <el-input
            v-model="form.announcementContent"
            type="textarea"
            :rows="6"
            maxlength="4000"
            show-word-limit
            placeholder="发布成功后用于新版本发布公告"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitJob">提交发布</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="发布任务详情" size="520px">
      <div v-if="detailJob" class="detail-panel">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务ID">{{ detailJob.id }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ channelText(detailJob) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailJob.status)">{{ statusLabel(detailJob.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            <el-progress :percentage="normalizeProgress(detailJob.progress)" :stroke-width="8" />
          </el-descriptions-item>
          <el-descriptions-item label="源分支">{{ detailJob.sourceReleaseBranch || detailJob.sourceReleaseRef || '-' }}</el-descriptions-item>
          <el-descriptions-item label="渠道分支">{{ detailJob.channelReleaseBranch || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主 APK">{{ detailJob.appVersionName || detailJob.versionName || '-' }} / {{ detailJob.appVersionCode || detailJob.versionCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="引擎">{{ detailJob.engineVersionName || '-' }} / {{ detailJob.engineVersionCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailJob.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(detailJob.finishedAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button @click="refreshJobDetail(detailJob.id)">刷新</el-button>
          <el-button type="warning" :disabled="!isFailed(detailJob.status)" @click="retryJob(detailJob)">失败重试</el-button>
        </div>

        <div class="log-box">
          <div class="log-title">日志</div>
          <pre>{{ detailJob.logExcerpt || detailJob.log || detailJob.errorMessage || '暂无日志' }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { channelFilterParam, formatChannelLabel, formatDateTime, useAdminSession } from '../utils/adminSession'

const { channels, isSuperAdmin, fetchChannels, channelText } = useAdminSession()

const jobs = ref([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailJob = ref(null)
const formRef = ref()
const filters = ref({
  channelId: '',
  status: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

const emptyForm = () => ({
  channelId: '',
  sourceReleaseBranch: '',
  channelReleaseBranch: '',
  appVersionName: '',
  appVersionCode: 1,
  engineVersionName: '',
  engineVersionCode: 1,
  announcementContent: ''
})

const form = ref(emptyForm())

const rules = {
  channelId: [{ required: true, message: '请选择发布渠道', trigger: 'change' }],
  sourceReleaseBranch: [{ required: true, message: '请输入源发布分支', trigger: 'blur' }],
  channelReleaseBranch: [{ required: true, message: '请输入渠道发布分支', trigger: 'blur' }],
  appVersionName: [{ required: true, message: '请输入主 APK 版本名', trigger: 'blur' }],
  appVersionCode: [{ required: true, message: '请输入主 APK 版本号', trigger: 'blur' }],
  engineVersionName: [{ required: true, message: '请输入引擎版本名', trigger: 'blur' }],
  engineVersionCode: [{ required: true, message: '请输入引擎版本号', trigger: 'blur' }],
  announcementContent: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

const fetchJobs = async () => {
  if (!isSuperAdmin.value) return
  loading.value = true
  try {
    const result = await request.get('/release-jobs', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        channelId: channelFilterParam(isSuperAdmin.value, filters.value.channelId),
        status: filters.value.status || undefined
      }
    })
    jobs.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchJobs()
}

const resetFilters = () => {
  filters.value = { channelId: '', status: '' }
  pagination.value.page = 1
  fetchJobs()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchJobs()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchJobs()
}

const statusLabel = (status) => {
  const map = {
    PENDING: '等待中',
    RUNNING: '运行中',
    SUCCESS: '成功',
    COMPLETED: '成功',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return map[String(status || '').toUpperCase()] || status || '-'
}

const statusTag = (status) => {
  const map = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[String(status || '').toUpperCase()] || 'info'
}

const isFailed = (status) => String(status || '').toUpperCase() === 'FAILED'

const normalizeProgress = (value) => {
  const parsed = Number(value ?? 0)
  if (!Number.isFinite(parsed)) return 0
  return Math.max(0, Math.min(100, Math.round(parsed)))
}

const selectedChannel = () => {
  return channels.value.find(item => Number(item.id) === Number(form.value.channelId)) || null
}

const onChannelChange = () => {
  const channel = selectedChannel()
  if (!channel) return
  const code = channel.code || 'main'
  if (!form.value.channelReleaseBranch) {
    form.value.channelReleaseBranch = code === 'main'
      ? form.value.sourceReleaseBranch
      : `release/channel/${code}/${form.value.appVersionName || 'version'}`
  }
}

const showCreateDialog = () => {
  form.value = emptyForm()
  const mainChannel = channels.value.find(item => item.code === 'main')
  if (mainChannel) {
    form.value.channelId = mainChannel.id
  }
  dialogVisible.value = true
}

const submitJob = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await request.post('/release-jobs', { ...form.value })
    ElMessage.success('发布任务已提交')
    dialogVisible.value = false
    fetchJobs()
  } finally {
    submitting.value = false
  }
}

const showDetail = (row) => {
  detailJob.value = { ...row }
  detailVisible.value = true
}

const refreshJobDetail = async (id) => {
  if (!id) return
  detailJob.value = await request.get(`/release-jobs/${id}`)
  fetchJobs()
}

const retryJob = async (row) => {
  try {
    await ElMessageBox.confirm(`确定重试发布任务 #${row.id} 吗？`, '提示', { type: 'warning' })
    await request.post(`/release-jobs/${row.id}/retry`)
    ElMessage.success('已提交重试')
    if (detailVisible.value) {
      await refreshJobDetail(row.id)
    } else {
      fetchJobs()
    }
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(async () => {
  await fetchChannels()
  fetchJobs()
})
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.log-box {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: hidden;
}

.log-title {
  padding: 10px 12px;
  border-bottom: 1px solid #334155;
  color: #cbd5e1;
  font-size: 13px;
}

.log-box pre {
  min-height: 220px;
  max-height: 52vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
