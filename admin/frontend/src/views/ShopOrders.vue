<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>店铺订单</span>
          <el-button :loading="loading" @click="fetchOrders">刷新</el-button>
        </div>
      </template>

      <el-form class="filter-bar" :model="filters" inline @submit.prevent>
        <el-form-item label="店铺 ID">
          <el-input
            v-model="filters.shopId"
            clearable
            placeholder="后台店铺ID"
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="店铺名称">
          <el-input
            v-model="filters.shopName"
            clearable
            placeholder="输入店铺名称"
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="filters.platform" clearable filterable placeholder="全部平台" style="width: 150px">
            <el-option
              v-for="platform in platforms"
              :key="platform.id"
              :label="platform.name"
              :value="platform.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单状态">
          <el-input
            v-model="filters.status"
            clearable
            placeholder="输入状态"
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="顾客">
          <el-input
            v-model="filters.customerKeyword"
            clearable
            placeholder="姓名/电话/地址"
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input
            v-model="filters.orderKeyword"
            clearable
            placeholder="平台订单号/序号"
            style="width: 190px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="完成时间">
          <el-date-picker
            v-model="completedRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="orders" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" fixed="left" />
        <el-table-column label="店铺" min-width="190" fixed="left">
          <template #default="{ row }">
            <div class="primary-text">{{ row.shopName || '-' }}</div>
            <el-text class="secondary-text" type="info">#{{ row.shopId }} / {{ row.platformShopId || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="订单" min-width="210">
          <template #default="{ row }">
            <div class="mono primary-text">{{ row.platformOrderId || '-' }}</div>
            <el-text class="secondary-text" type="info">
              {{ row.platformOrderNo ? `#${row.platformOrderNo}` : '-' }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ row.statusText || row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.completedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="下单/预计" min-width="190">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.orderedAt) }}</div>
            <el-text class="secondary-text" type="info">
              {{ row.expectedDeliveryAt ? `预计 ${formatDateTime(row.expectedDeliveryAt)}` : (row.orderTimeText || '-') }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column label="顾客" min-width="140">
          <template #default="{ row }">
            <div>{{ row.customerName || '-' }}</div>
            <el-text v-if="row.customerPhoneTail" class="secondary-text" type="info">
              尾号 {{ row.customerPhoneTail }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column label="联系/地址" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.privacyPhone || row.backupPhone || '-' }}</div>
            <el-text class="secondary-text" type="info">{{ row.address || row.recipientAddress || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="150">
          <template #default="{ row }">
            <div>{{ formatMoney(row.estimatedIncome || row.customerPaidAmount || row.actualAmount) }}</div>
            <el-text v-if="row.originalAmount || row.discountAmount" class="secondary-text" type="info">
              原 {{ formatMoney(row.originalAmount) }} / 优 {{ formatMoney(row.discountAmount) }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.itemSummary || formatItems(row.itemsJson) || row.rawText || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="采集时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.lastSeenAt || row.fetchedAt) }}
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
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'

const route = useRoute()
const orders = ref([])
const platforms = ref([])
const loading = ref(false)
const completedRange = ref([])
const filters = ref({
  shopId: '',
  shopName: '',
  platform: '',
  status: '',
  customerKeyword: '',
  orderKeyword: ''
})
const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

const applyRouteQuery = () => {
  const query = route.query || {}
  filters.value.shopId = query.shopId ? String(query.shopId) : filters.value.shopId
  filters.value.shopName = query.shopName ? String(query.shopName) : filters.value.shopName
  filters.value.platform = query.platform ? String(query.platform) : filters.value.platform
  filters.value.status = query.status ? String(query.status) : filters.value.status
  filters.value.customerKeyword = query.customerKeyword ? String(query.customerKeyword) : filters.value.customerKeyword
  filters.value.orderKeyword = query.orderKeyword ? String(query.orderKeyword) : filters.value.orderKeyword
  if (query.completedStart || query.completedEnd) {
    completedRange.value = [
      query.completedStart ? String(query.completedStart) : '',
      query.completedEnd ? String(query.completedEnd) : ''
    ]
  }
}

const fetchPlatforms = async () => {
  const result = await request.get('/platforms', { params: { page: 1, size: 100 } })
  platforms.value = result.list || result.content || result || []
}

const fetchOrders = async () => {
  loading.value = true
  try {
    const result = await request.get('/shop-orders', {
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        shopId: filters.value.shopId || undefined,
        shopName: filters.value.shopName || undefined,
        platform: filters.value.platform || undefined,
        status: filters.value.status || undefined,
        customerKeyword: filters.value.customerKeyword || undefined,
        orderKeyword: filters.value.orderKeyword || undefined,
        completedStart: completedRange.value?.[0] || undefined,
        completedEnd: completedRange.value?.[1] || undefined
      }
    })
    orders.value = result.list || result.content || []
    pagination.value.total = Number(result.total ?? result.totalElements ?? 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchOrders()
}

const resetFilters = () => {
  filters.value = {
    shopId: '',
    shopName: '',
    platform: '',
    status: '',
    customerKeyword: '',
    orderKeyword: ''
  }
  completedRange.value = []
  pagination.value.page = 1
  fetchOrders()
}

const handlePageChange = (page) => {
  pagination.value.page = page
  fetchOrders()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.page = 1
  fetchOrders()
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return `￥${number.toFixed(2)}`
}

const statusType = (status) => {
  const text = String(status || '')
  if (text.includes('取消') || text.includes('退款')) return 'danger'
  if (text.includes('完成') || text.includes('收餐')) return 'success'
  if (text.includes('取餐') || text.includes('配送')) return 'warning'
  return 'info'
}

const formatItems = (itemsJson) => {
  if (!itemsJson) return ''
  try {
    const parsed = JSON.parse(itemsJson)
    if (!Array.isArray(parsed)) return ''
    return parsed
      .map(item => {
        const name = item.name || item.title || item.itemName
        const count = item.count || item.quantity || item.num
        return name ? `${name}${count ? ` x${count}` : ''}` : ''
      })
      .filter(Boolean)
      .join('，')
  } catch (error) {
    return ''
  }
}

watch(
  () => route.query,
  () => {
    applyRouteQuery()
    pagination.value.page = 1
    fetchOrders()
  }
)

onMounted(() => {
  applyRouteQuery()
  fetchPlatforms().finally(fetchOrders)
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

.primary-text {
  line-height: 20px;
  color: #1e293b;
}

.secondary-text {
  display: block;
  line-height: 18px;
  font-size: 12px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}
</style>
