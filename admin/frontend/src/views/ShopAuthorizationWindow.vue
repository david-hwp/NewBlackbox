<template>
  <div class="authorization-window">
    <header class="authorization-header">
      <div>
        <h1>店铺授权</h1>
        <p>{{ shopTitle }}</p>
      </div>
      <div class="header-actions">
        <el-tag v-if="shop?.shopAuthorizationStatus" size="small" :type="statusType(shop.shopAuthorizationStatus)">
          {{ statusText(shop.shopAuthorizationStatus) }}
        </el-tag>
        <el-button :loading="preparing" @click="prepareRemoteWindow">刷新</el-button>
      </div>
    </header>

    <main ref="frameHost" class="authorization-frame-host">
      <iframe
        v-if="streamUrl && !preparing && !loadFailed"
        :key="streamFrameKey"
        class="authorization-frame"
        :src="streamUrl"
        allow="clipboard-read; clipboard-write"
      />
      <div v-else class="authorization-state">
        <el-icon v-if="preparing" class="loading-icon" :size="28"><Loading /></el-icon>
        <h2>{{ stateTitle }}</h2>
        <p>{{ stateDescription }}</p>
        <el-button v-if="loadFailed" type="primary" @click="prepareRemoteWindow">重新连接</el-button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import request from '../utils/request'

const route = useRoute()
const shopId = computed(() => route.params.id)
const frameHost = ref(null)
const shop = ref(null)
const streamUrl = ref('')
const streamFrameKey = ref(0)
const preparing = ref(false)
const loadFailed = ref(false)
let resizeTimer = null

const shopTitle = computed(() => {
  if (!shop.value) return '正在读取店铺信息'
  return [shop.value.platformName, shop.value.shopName].filter(Boolean).join(' / ') || `店铺 ${shop.value.id}`
})

const stateTitle = computed(() => {
  if (loadFailed.value) return '授权窗口连接失败'
  return '正在连接授权窗口'
})

const stateDescription = computed(() => {
  if (loadFailed.value) return '远端浏览器没有准备好，请稍后重试。'
  return '正在根据当前浏览器窗口准备远端画面。'
})

const statusText = (status) => {
  const normalized = String(status || 'UNAUTHORIZED').toUpperCase()
  if (normalized === 'AUTHORIZED') return '已授权'
  if (normalized === 'AUTHORIZING') return '授权中'
  if (normalized === 'FAILED') return '授权失败'
  if (normalized === 'UNKNOWN') return '待确认'
  return '未授权'
}

const statusType = (status) => {
  const normalized = String(status || 'UNAUTHORIZED').toUpperCase()
  if (normalized === 'AUTHORIZED') return 'success'
  if (normalized === 'AUTHORIZING') return 'warning'
  if (normalized === 'FAILED') return 'danger'
  if (normalized === 'UNKNOWN') return 'warning'
  return 'info'
}

const viewportParams = () => {
  const rect = frameHost.value?.getBoundingClientRect()
  const width = Math.max(320, Math.round(rect?.width || window.innerWidth || 720))
  const height = Math.max(420, Math.round(rect?.height || (window.innerHeight - 68) || 900))
  return {
    width,
    height,
    renderScale: 1,
    scale: 1
  }
}

const loadShop = async () => {
  shop.value = await request.get(`/shops/${shopId.value}`)
}

const prepareRemoteWindow = async () => {
  if (!shopId.value || preparing.value) return
  preparing.value = true
  loadFailed.value = false
  try {
    await nextTick()
    const result = await request.get(`/shops/${shopId.value}/authorization/open-url`, {
      params: viewportParams(),
      timeout: 45000
    })
    const url = result?.shopAuthorizationUrl || result?.url
    if (!url) {
      loadFailed.value = true
      ElMessage.error('未获取到授权地址')
      return
    }
    streamUrl.value = normalizeXpraUrl(url)
    streamFrameKey.value += 1
    loadShop()
  } catch (error) {
    loadFailed.value = true
  } finally {
    preparing.value = false
  }
}

const normalizeXpraUrl = (url) => {
  try {
    const parsed = new URL(url, window.location.origin)
    parsed.searchParams.set('autohide', 'true')
    parsed.searchParams.set('touchaction', 'scroll')
    parsed.searchParams.set('sound', 'false')
    parsed.searchParams.set('video', 'false')
    parsed.searchParams.set('clipboard', 'true')
    parsed.searchParams.set('printing', 'false')
    parsed.searchParams.set('file_transfer', 'false')
    return parsed.toString()
  } catch (error) {
    const separator = String(url || '').includes('?') ? '&' : '?'
    return `${url}${separator}autohide=true&touchaction=scroll&sound=false&video=false&clipboard=true&printing=false&file_transfer=false`
  }
}

const scheduleResizePrepare = () => {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    prepareRemoteWindow()
  }, 500)
}

onMounted(async () => {
  try {
    await loadShop()
  } catch (error) {
  }
  prepareRemoteWindow()
  window.addEventListener('resize', scheduleResizePrepare)
})

onBeforeUnmount(() => {
  window.clearTimeout(resizeTimer)
  window.removeEventListener('resize', scheduleResizePrepare)
})
</script>

<style scoped>
.authorization-window {
  min-height: 100vh;
  background: #f5f6f8;
  color: #111827;
  display: flex;
  flex-direction: column;
}

.authorization-header {
  height: 68px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 18px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.authorization-header h1 {
  font-size: 18px;
  line-height: 1.3;
  margin: 0;
}

.authorization-header p {
  font-size: 13px;
  color: #64748b;
  margin: 4px 0 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.authorization-frame-host {
  flex: 1;
  min-height: 420px;
  position: relative;
  background: #fff;
  overflow: hidden;
}

.authorization-frame {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
}

.authorization-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #475569;
  background: #fff;
}

.authorization-state h2 {
  font-size: 18px;
  margin: 0;
  color: #111827;
}

.authorization-state p {
  font-size: 14px;
  margin: 0;
}

.loading-icon {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
