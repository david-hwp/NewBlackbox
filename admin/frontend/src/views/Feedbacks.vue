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
      </el-table>
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
        <el-button :icon="ZoomOut" @click="zoomImage(-0.25)">缩小</el-button>
        <el-button :icon="ZoomIn" @click="zoomImage(0.25)">放大</el-button>
        <el-button @click="resetZoom">原始</el-button>
        <el-button type="primary" :icon="Download" @click="downloadPreviewImage">保存到本地</el-button>
      </div>
      <div class="preview-stage">
        <img
          v-if="preview.objectUrl"
          :src="preview.objectUrl"
          class="preview-image"
          :style="{ transform: `scale(${preview.scale})` }"
          alt=""
        />
        <el-empty v-else description="图片加载中" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Download, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import request from '../utils/request'
import { fetchFileBlob, getObjectUrl, getPreferredImageObjectUrl } from '../utils/files'

const feedbacks = ref([])
const loading = ref(false)
const thumbnailUrls = reactive({})
const objectUrlCache = new Map()
const preview = reactive({
  visible: false,
  urls: [],
  index: 0,
  objectUrl: '',
  scale: 1
})

const fetchFeedbacks = async () => {
  loading.value = true
  try {
    feedbacks.value = await request.get('/feedbacks')
    await loadThumbnails(feedbacks.value)
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
  preview.scale = 1
  preview.visible = true
  await loadPreviewImage()
}

const loadPreviewImage = async () => {
  preview.objectUrl = ''
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

const zoomImage = (delta) => {
  const nextScale = Number((preview.scale + delta).toFixed(2))
  preview.scale = Math.max(0.25, Math.min(4, nextScale))
}

const resetZoom = () => {
  preview.scale = 1
}

const closeImagePreview = () => {
  preview.urls = []
  preview.index = 0
  preview.objectUrl = ''
  preview.scale = 1
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
    preview.scale = 1
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
  display: grid;
  place-items: center;
  background: #f5f7fa;
  border-radius: 6px;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  transform-origin: center;
  transition: transform 120ms ease;
}
</style>
