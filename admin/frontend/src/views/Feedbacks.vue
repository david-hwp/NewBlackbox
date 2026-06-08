<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>问题反馈</span>
          <el-button @click="fetchFeedbacks">刷新</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="手机号">
          <el-input v-model="filters.userPhone" clearable placeholder="输入用户手机号" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="filters.content" clearable placeholder="反馈/日志/设备关键词" style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="feedbacks" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userPhone" label="用户手机号" width="140" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag :type="row.source === 'ENGINE_LOG' ? 'warning' : 'success'" size="small">
              {{ row.source || 'APP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="logCaption" label="日志说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="deviceInfo" label="设备信息" min-width="220" show-overflow-tooltip />
        <el-table-column label="图片" width="220">
          <template #default="{ row }">
            <div v-if="imageList(row.imageUrls).length" class="thumb-list">
              <button
                v-for="(url, index) in imageList(row.imageUrls)"
                :key="url"
                class="thumb-button"
                type="button"
                @click="openImagePreview(imageList(row.imageUrls), index)"
              >
                <img v-if="thumbnailUrls[url]" :src="thumbnailUrls[url]" alt="" class="thumb-image" />
                <span v-else class="thumb-loading">图</span>
              </button>
            </div>
            <span v-else>-</span>
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
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
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

    <el-dialog
      v-model="preview.visible"
      title="图片预览"
      width="82vw"
      class="feedback-image-dialog"
      @closed="closeImagePreview"
    >
      <div class="preview-toolbar">
        <el-button :icon="ArrowLeft" :disabled="preview.index <= 0" @click="showPrevImage">上一张</el-button>
        <span class="preview-count">{{ preview.index + 1 }} / {{ preview.urls.length }}</span>
        <el-button :icon="ArrowRight" :disabled="preview.index >= preview.urls.length - 1" @click="showNextImage">下一张</el-button>
        <el-divider direction="vertical" />
        <el-button :icon="ZoomOut" @click="zoomImage(-1)">缩小</el-button>
        <el-button :icon="ZoomIn" @click="zoomImage(1)">放大</el-button>
        <el-button :icon="Refresh" @click="resetZoom">重置</el-button>
        <el-button type="primary" :icon="Download" @click="downloadPreviewImage">保存到本地</el-button>
      </div>
      <div ref="previewStageRef" class="preview-stage">
        <div v-if="preview.objectUrl" class="preview-image-wrap">
          <img
            :src="preview.objectUrl"
            class="preview-image"
            :style="previewImageStyle"
            alt=""
            @load="handlePreviewImageLoad"
          />
        </div>
        <div v-else class="preview-empty">
          <el-empty description="图片加载中" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Download, Refresh, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import request from '../utils/request'
import { fetchFileBlob, getObjectUrl, getPreferredImageObjectUrl } from '../utils/files'

const feedbacks = ref([])
const loading = ref(false)
const thumbnailUrls = reactive({})
const objectUrlCache = new Map()
const previewStageRef = ref(null)
const filters = ref({
  userPhone: '',
  status: '',
  content: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const preview = reactive({
  visible: false,
  urls: [],
  index: 0,
  objectUrl: '',
  scale: 1,
  fitScale: 1,
  naturalWidth: 0,
  naturalHeight: 0
})

const previewImageStyle = computed(() => {
  if (!preview.naturalWidth || !preview.naturalHeight) {
    return {}
  }
  return {
    width: `${Math.max(1, Math.round(preview.naturalWidth * preview.scale))}px`,
    height: `${Math.max(1, Math.round(preview.naturalHeight * preview.scale))}px`
  }
})

const fetchFeedbacks = async () => {
  loading.value = true
  try {
    const result = await request.get('/feedbacks', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        userPhone: filters.value.userPhone || undefined,
        status: filters.value.status || undefined,
        content: filters.value.content || undefined
      }
    })
    feedbacks.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
    await loadThumbnails(feedbacks.value)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchFeedbacks()
}

const resetFilters = () => {
  filters.value = {
    userPhone: '',
    status: '',
    content: ''
  }
  pagination.value.page = 1
  fetchFeedbacks()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchFeedbacks()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchFeedbacks()
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

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该反馈吗？', '提示', { type: 'warning' })
    await request.delete(`/feedbacks/${row.id}`)
    ElMessage.success('删除成功')
    fetchFeedbacks()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const loadThumbnails = async (rows) => {
  const urls = Array.from(new Set(rows.flatMap(row => imageList(row.imageUrls))))
  await Promise.all(urls.map(async (url) => {
    if (thumbnailUrls[url]) return
    try {
      thumbnailUrls[url] = await getPreferredImageObjectUrl(url, objectUrlCache)
    } catch (e) {
      console.warn('thumbnail load failed', e)
    }
  }))
}

const openImagePreview = async (urls, index) => {
  preview.urls = urls
  preview.index = index
  resetPreviewSizing()
  preview.visible = true
  await loadPreviewImage()
}

const loadPreviewImage = async () => {
  preview.objectUrl = ''
  resetPreviewSizing()
  const url = preview.urls[preview.index]
  if (!url) return
  try {
    preview.objectUrl = await getObjectUrl(url, objectUrlCache)
  } catch (e) {
    ElMessage.error(e.message || '图片加载失败')
  }
}

const showPrevImage = () => {
  if (preview.index <= 0) return
  preview.index -= 1
}

const showNextImage = () => {
  if (preview.index >= preview.urls.length - 1) return
  preview.index += 1
}

const resetPreviewSizing = () => {
  preview.scale = 1
  preview.fitScale = 1
  preview.naturalWidth = 0
  preview.naturalHeight = 0
}

const anchorPreviewToTop = () => {
  requestAnimationFrame(() => {
    const stage = previewStageRef.value
    if (!stage) return
    stage.scrollTop = 0
    stage.scrollLeft = Math.max(0, (stage.scrollWidth - stage.clientWidth) / 2)
  })
}

const fitImageToStage = async () => {
  await nextTick()
  const stage = previewStageRef.value
  if (!stage || !preview.naturalWidth || !preview.naturalHeight) return
  const availableWidth = Math.max(1, stage.clientWidth - 32)
  const availableHeight = Math.max(1, stage.clientHeight - 32)
  const nextScale = Math.min(
    availableWidth / preview.naturalWidth,
    availableHeight / preview.naturalHeight
  )
  preview.fitScale = Number(Math.max(0.05, nextScale).toFixed(4))
  preview.scale = preview.fitScale
  await nextTick()
  anchorPreviewToTop()
}

const handlePreviewImageLoad = async (event) => {
  const image = event.target
  preview.naturalWidth = image.naturalWidth || 0
  preview.naturalHeight = image.naturalHeight || 0
  await fitImageToStage()
}

const zoomImage = async (direction) => {
  if (!preview.naturalWidth || !preview.naturalHeight) return
  const factor = direction > 0 ? 1.25 : 0.8
  const minScale = Math.max(0.05, preview.fitScale * 0.25)
  const maxScale = Math.max(4, preview.fitScale * 8)
  const nextScale = Number((preview.scale * factor).toFixed(4))
  preview.scale = Math.max(minScale, Math.min(maxScale, nextScale))
  await nextTick()
  anchorPreviewToTop()
}

const resetZoom = () => {
  fitImageToStage()
}

const closeImagePreview = () => {
  preview.urls = []
  preview.index = 0
  preview.objectUrl = ''
  resetPreviewSizing()
}

const downloadPrivateFile = async (url) => {
  try {
    const blob = await fetchFileBlob(url)
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

const downloadPreviewImage = () => {
  const url = preview.urls[preview.index]
  if (url) {
    downloadPrivateFile(url)
  }
}

watch(() => preview.index, () => {
  if (preview.visible) {
    loadPreviewImage()
  }
})

onBeforeUnmount(() => {
  objectUrlCache.forEach(objectUrl => URL.revokeObjectURL(objectUrl))
  objectUrlCache.clear()
})

onMounted(fetchFeedbacks)
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

.thumb-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.thumb-button {
  width: 46px;
  height: 46px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  padding: 0;
  cursor: pointer;
  overflow: hidden;
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-loading {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.preview-count {
  color: var(--el-text-color-secondary);
  min-width: 56px;
  text-align: center;
}

.preview-stage {
  height: 70vh;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
}

.preview-image-wrap {
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  width: max-content;
  min-width: 100%;
  min-height: 100%;
  padding: 16px;
}

.preview-image {
  display: block;
  max-width: none;
  max-height: none;
  object-fit: contain;
  transition: width 120ms ease, height 120ms ease;
}

.preview-empty {
  display: grid;
  min-height: 100%;
  place-items: center;
}
</style>
