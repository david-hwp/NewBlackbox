<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>店铺列表</span>
          <el-button v-if="canMutate" type="primary" @click="showAddDialog">新增店铺</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item v-if="isSuperAdmin" label="渠道">
          <el-select v-model="filters.channelId" clearable filterable placeholder="全部渠道" style="width: 190px">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="formatChannelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="filters.platform" clearable placeholder="全部平台" style="width: 160px">
            <el-option
              v-for="platform in platforms"
              :key="platform.id"
              :label="platform.name"
              :value="platform.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="输入手机号" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="关联用户">
          <el-input v-model="filters.userKeyword" clearable placeholder="用户名/手机号/ID" style="width: 190px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="店铺名称">
          <el-input v-model="filters.shopName" clearable placeholder="输入店铺名称" style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="shops" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="shopName" label="店铺名称" />
        <el-table-column prop="shopId" label="店铺ID" />
        <el-table-column label="登录态" min-width="170">
          <template #default="{ row }">
            <div v-if="row.hasLoginState" class="login-state-cell">
              <el-tag size="small" type="success">已上传</el-tag>
              <el-text v-if="row.loginStateProfile" class="login-state-profile mono" truncated>
                {{ row.loginStateProfile }}
              </el-text>
              <el-text v-if="formatLoginStateMeta(row)" class="login-state-meta" type="info">
                {{ formatLoginStateMeta(row) }}
              </el-text>
            </div>
            <el-tag v-else size="small" type="info">未上传</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="店铺授权状态" min-width="150">
          <template #default="{ row }">
            <div class="shop-auth-cell">
              <el-tag size="small" :type="shopAuthStatusType(row.shopAuthorizationStatus)">
                {{ shopAuthStatusText(row.shopAuthorizationStatus) }}
              </el-tag>
              <el-text v-if="row.shopAuthorizationCheckedAt" class="shop-auth-meta" type="info">
                {{ formatDateTime(row.shopAuthorizationCheckedAt) }}
              </el-text>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="微信接收方" min-width="180">
          <template #default="{ row }">
            <div v-if="row.wechatReceiverName || row.wechatReceiverId">
              <div>{{ row.wechatReceiverName || '-' }}</div>
              <el-text v-if="row.wechatReceiverId" class="mono receiver-id" truncated>{{ row.wechatReceiverId }}</el-text>
              <el-tag v-if="row.wechatReceiverType" size="small" type="info">{{ formatReceiverType(row.wechatReceiverType) }}</el-tag>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
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
        <el-table-column label="渠道" min-width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ channelText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platformName" label="平台" />
        <el-table-column prop="cardSortOrder" label="排序" width="90" />
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
        <el-table-column v-if="canMutate" label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isSuperAdmin"
              type="success"
              link
              :loading="openingShopAuthId === row.id"
              @click="openRemoteBackend(row)"
            >
              远程后台
            </el-button>
            <el-button v-if="isSuperAdmin" type="warning" link @click="openShopOrders(row)">店铺订单</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑店铺' : '新增店铺'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item v-if="isSuperAdmin" label="所属渠道" prop="channelId">
          <el-select v-model="form.channelId" filterable placeholder="请选择渠道" style="width: 100%">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="formatChannelLabel(channel)"
              :value="channel.id"
            />
          </el-select>
        </el-form-item>
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
        <el-form-item label="微信接收ID">
          <el-input v-model="form.wechatReceiverId" clearable placeholder="微信接收方唯一ID" />
        </el-form-item>
        <el-form-item label="微信接收名">
          <el-input v-model="form.wechatReceiverName" clearable placeholder="微信联系人或群聊名称" />
        </el-form-item>
        <el-form-item label="接收方类型">
          <el-select v-model="form.wechatReceiverType" clearable placeholder="请选择" style="width: 100%">
            <el-option label="个人微信" value="CONTACT" />
            <el-option label="群聊" value="GROUP" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="512" show-word-limit placeholder="展示在APP店铺卡片店铺ID下方" />
        </el-form-item>
        <el-form-item label="剩余天数">
          <el-input-number v-model="form.remainingDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="卡片排序">
          <el-input-number v-model="form.cardSortOrder" :min="0" style="width: 100%" />
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
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { channelFilterParam, formatChannelLabel, useAdminSession } from '../utils/adminSession'

const router = useRouter()
const shops = ref([])
const users = ref([])
const platforms = ref([])
const loading = ref(false)
const metadataLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const openingShopAuthId = ref(null)
const filters = ref({
  platform: '',
  phone: '',
  userKeyword: '',
  shopName: '',
  channelId: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})
const form = ref({
  shopName: '',
  shopId: '',
  userId: '',
  platform: '',
  platformName: '',
  cardSortOrder: 0,
  remainingDays: 0,
  autoRenew: false,
  packageName: '',
  cloneInstanceId: '',
  wechatReceiverId: '',
  wechatReceiverName: '',
  wechatReceiverType: '',
  remark: '',
  channelId: null
})

const { channels, isSuperAdmin, isReadonlyChannel, ownChannelId, fetchChannels, channelText } = useAdminSession()
const canMutate = computed(() => isSuperAdmin.value || !isReadonlyChannel.value)

const rules = {
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  shopId: [{ required: true, message: '请输入店铺ID', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择所属用户', trigger: 'change' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  channelId: [{ required: true, message: '请选择所属渠道', trigger: 'change' }]
}

const fetchShops = async () => {
  loading.value = true
  try {
    const result = await request.get('/shops', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        platform: filters.value.platform || undefined,
        phone: filters.value.phone || undefined,
        userKeyword: filters.value.userKeyword || undefined,
        shopName: filters.value.shopName || undefined,
        channelId: channelFilterParam(isSuperAdmin.value, filters.value.channelId)
      }
    })
    shops.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const fetchMetadata = async () => {
  metadataLoading.value = true
  try {
    const [userList, platformList] = await Promise.all([
      request.get('/users', { params: { page: 1, size: 100 } }),
      request.get('/platforms')
    ])
    users.value = userList.list || userList.content || userList || []
    platforms.value = platformList.list || platformList.content || platformList || []
  } finally {
    metadataLoading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchShops()
}

const resetFilters = () => {
  filters.value = {
    platform: '',
    phone: '',
    userKeyword: '',
    shopName: '',
    channelId: ''
  }
  pagination.value.page = 1
  fetchShops()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchShops()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchShops()
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

const formatReceiverType = (type) => {
  const normalized = String(type || '').toUpperCase()
  if (normalized === 'CONTACT') return '个人微信'
  if (normalized === 'GROUP') return '群聊'
  return type || '-'
}

const formatBytes = (value) => {
  const bytes = Number(value || 0)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatDateTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const formatQueryMinute = (date) => {
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const formatLoginStateMeta = (row) => {
  const parts = []
  const size = formatBytes(row.loginStateSize)
  const exportedAt = formatDateTime(row.loginStateArtifactCreatedAt || row.loginStateUpdatedAt)
  if (size) parts.push(size)
  if (exportedAt) parts.push(exportedAt)
  return parts.join(' / ')
}

const shopAuthStatusText = (status) => {
  const normalized = String(status || 'UNAUTHORIZED').toUpperCase()
  if (normalized === 'AUTHORIZED') return '已授权'
  if (normalized === 'AUTHORIZING') return '授权中'
  if (normalized === 'FAILED') return '授权失败'
  if (normalized === 'UNKNOWN') return '待确认'
  return '未授权'
}

const shopAuthStatusType = (status) => {
  const normalized = String(status || 'UNAUTHORIZED').toUpperCase()
  if (normalized === 'AUTHORIZED') return 'success'
  if (normalized === 'AUTHORIZING') return 'warning'
  if (normalized === 'FAILED') return 'danger'
  if (normalized === 'UNKNOWN') return 'warning'
  return 'info'
}

const popupFeatures = () => {
  const width = Math.max(1024, Math.round(window.screen?.availWidth || 1440))
  const height = Math.max(720, Math.round(window.screen?.availHeight || 900))
  return `popup=yes,left=0,top=0,width=${width},height=${height}`
}

const remoteRoute = (row, mode) => {
  return router.resolve({
    name: 'ShopAuthorizationWindow',
    params: { id: row.id },
    query: { mode }
  }).href
}

const openPopupShell = (row) => {
  try {
    const opened = window.open('', `shop_authorization_${row.id}`, popupFeatures())
    if (opened) {
      opened.document.title = '远程后台'
      opened.document.body.innerHTML = '<div style="font:14px system-ui;padding:24px;color:#334155">正在检测店铺授权状态...</div>'
      opened.moveTo?.(0, 0)
      opened.resizeTo?.(window.screen?.availWidth || 1440, window.screen?.availHeight || 900)
    }
    return opened
  } catch (error) {
    return null
  }
}

const navigatePopup = (opened, url) => {
  if (opened && !opened.closed) {
    opened.location.href = url
    opened.moveTo?.(0, 0)
    opened.resizeTo?.(window.screen?.availWidth || 1440, window.screen?.availHeight || 900)
    return true
  }
  const fallback = window.open(url, '_blank', popupFeatures())
  if (!fallback) {
    ElMessage.error('浏览器已拦截远程后台窗口，请允许弹窗后重试')
    return false
  }
  fallback.moveTo?.(0, 0)
  fallback.resizeTo?.(window.screen?.availWidth || 1440, window.screen?.availHeight || 900)
  return true
}

const closePopup = (opened) => {
  if (opened && !opened.closed) {
    opened.close()
  }
}

const applyProbeResult = (row, result) => {
  row.shopAuthorizationStatus = result?.status || 'UNKNOWN'
  row.shopAuthorizationCheckedAt = result?.checkedAt || row.shopAuthorizationCheckedAt
  row.shopAuthorizationSignals = result?.signals || row.shopAuthorizationSignals
}

const openRemoteBackend = async (row) => {
  if (!isSuperAdmin.value || !row?.id || openingShopAuthId.value) return
  openingShopAuthId.value = row.id
  const opened = openPopupShell(row)
  try {
    const probe = await request.post(`/shops/${row.id}/authorization/probe`, null, { timeout: 45000 })
    applyProbeResult(row, probe)
    if (String(probe?.status || '').toUpperCase() === 'AUTHORIZED') {
      navigatePopup(opened, remoteRoute(row, 'remote-backend'))
      fetchShops()
      return
    }

    const confirm = await ElMessageBox.confirm(
      '该店铺未授权，是否进行授权？',
      '提示',
      {
        confirmButtonText: '是',
        cancelButtonText: '否',
        type: 'warning'
      }
    ).catch((error) => error)

    if (confirm === 'confirm') {
      navigatePopup(opened, remoteRoute(row, 'authorization-login'))
    } else {
      closePopup(opened)
    }
    fetchShops()
  } catch (error) {
    closePopup(opened)
  } finally {
    openingShopAuthId.value = null
  }
}

const openShopOrders = (row) => {
  if (!isSuperAdmin.value || !row?.id) return
  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  router.push({
    name: 'ShopOrders',
    query: {
      shopId: row.id,
      completedStart: formatQueryMinute(start),
      completedEnd: formatQueryMinute(now)
    }
  })
}

const showAddDialog = () => {
  if (!canMutate.value) return
  isEdit.value = false
  form.value = {
    shopName: '',
    shopId: '',
    userId: '',
    platform: '',
    platformName: '',
    cardSortOrder: 0,
    remainingDays: 0,
    autoRenew: false,
    packageName: '',
    cloneInstanceId: '',
    wechatReceiverId: '',
    wechatReceiverName: '',
    wechatReceiverType: '',
    remark: '',
    channelId: isSuperAdmin.value ? (filters.value.channelId || null) : ownChannelId.value
  }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  if (!canMutate.value) return
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!isSuperAdmin.value && !form.value.channelId) {
    form.value.channelId = ownChannelId.value
  }
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

onMounted(() => {
  fetchChannels().finally(() => {
    fetchMetadata()
    fetchShops()
  })
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

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  max-width: 210px;
}

.receiver-id {
  display: block;
  max-width: 150px;
  margin: 2px 0;
}

.login-state-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.login-state-profile {
  max-width: 150px;
  font-size: 12px;
}

.login-state-meta {
  font-size: 12px;
}

.shop-auth-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.shop-auth-meta {
  font-size: 12px;
}
</style>
