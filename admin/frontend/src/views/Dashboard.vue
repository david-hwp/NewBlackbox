<template>
  <div>
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background: #ecfdf5; color: #059669;">
              <el-icon :size="24"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.userCount }}</div>
              <div class="stat-label">总用户</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background: #eff6ff; color: #3b82f6;">
              <el-icon :size="24"><Shop /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.shopCount }}</div>
              <div class="stat-label">总店铺</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background: #fef3c7; color: #f59e0b;">
              <el-icon :size="24"><Coin /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalCompute }}</div>
              <div class="stat-label">总算力</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background: #fee2e2; color: #dc2626;">
              <el-icon :size="24"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.logCount }}</div>
              <div class="stat-label">交易记录</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 平台分布 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover" title="平台分布">
          <template #header>
            <span>平台分布</span>
          </template>
          <el-table :data="platformStats" style="width: 100%">
            <el-table-column prop="platformName" label="平台" />
            <el-table-column prop="count" label="店铺数" />
            <el-table-column label="占比">
              <template #default="{ row }">
                <el-progress :percentage="getPercent(row.count, stats.shopCount)" :color="getPlatformColor(row.platform)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>最近交易</span>
          </template>
          <el-table :data="recentLogs" style="width: 100%">
            <el-table-column prop="type" label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="getLogTypeTag(row.type)" size="small">{{ getLogTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" />
            <el-table-column prop="createdAt" label="时间" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { User, Shop, Coin, Document } from '@element-plus/icons-vue'
import request from '../utils/request'

const stats = ref({ userCount: 0, shopCount: 0, totalCompute: 0, logCount: 0 })
const platformStats = ref([])
const recentLogs = ref([])

const fetchStats = async () => {
  try {
    const users = await request.get('/users')
    const shops = await request.get('/shops')
    const logs = await request.get('/logs')

    stats.value.userCount = users.length
    stats.value.shopCount = shops.length
    stats.value.totalCompute = users.reduce((sum, u) => sum + (u.computeBalance || 0), 0)
    stats.value.logCount = logs.length

    // 平台统计
    const platformMap = {}
    shops.forEach(s => {
      const key = s.platform || 'other'
      if (!platformMap[key]) {
        platformMap[key] = { platform: key, platformName: s.platformName || key, count: 0 }
      }
      platformMap[key].count++
    })
    platformStats.value = Object.values(platformMap)

    // 最近交易
    recentLogs.value = logs.slice(-5).reverse()
  } catch (e) {
    console.error(e)
  }
}

const getPercent = (count, total) => {
  if (!total) return 0
  return Math.round((count / total) * 100)
}

const getPlatformColor = (platform) => {
  const colors = {
    meituan: '#ff4d4f',
    taobao: '#fa8c16',
    jd: '#2f54eb',
    kuaishou: '#52c41a',
    xiaohongshu: '#eb2f96',
    ali: '#fa541c'
  }
  return colors[platform] || '#059669'
}

const getLogTypeTag = (type) => {
  const map = { CONSUME: 'info', OUT: 'warning', IN: 'success' }
  return map[type] || 'info'
}

const getLogTypeText = (type) => {
  const map = { CONSUME: '消耗', OUT: '转出', IN: '转入' }
  return map[type] || type
}

onMounted(fetchStats)
</script>

<style scoped>
.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
  margin-top: 4px;
}
</style>
