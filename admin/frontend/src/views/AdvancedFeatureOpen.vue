<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>开放设置</span>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </template>

      <el-table :data="features" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="功能" width="110" fixed="left" />
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column label="是否上线" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.online" />
          </template>
        </el-table-column>
        <el-table-column label="每月算力" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.monthlyComputeCost"
              :min="0"
              :max="9999"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="支持平台" min-width="260">
          <template #default="{ row }">
            <el-select
              v-model="row.supportedPlatformPackages"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              placeholder="不选表示全部平台"
              style="width: 100%"
            >
              <el-option
                v-for="platform in platformOptions"
                :key="platform.packageName"
                :label="platformLabel(platform)"
                :value="platform.packageName"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.title" maxlength="128" />
          </template>
        </el-table-column>
        <el-table-column label="第一行文字" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.line1" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column label="第二行文字" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.line2" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column label="外呼系统" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.outboundEnabled" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :loading="savingCode === row.code" @click="saveFeature(row)">
              保存
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { formatDateTime } from '../utils/adminSession'

const features = ref([])
const platforms = ref([])
const loading = ref(false)
const savingCode = ref('')

const platformOptions = ref([])

const fetchData = async () => {
  loading.value = true
  try {
    const [featureList, platformList] = await Promise.all([
      request.get('/advanced-features'),
      request.get('/platforms')
    ])
    platforms.value = platformList || []
    platformOptions.value = platforms.value
      .filter(item => item.packageName)
      .map(item => ({
        ...item,
        packageName: item.packageName.trim()
      }))
      .filter((item, index, list) =>
        item.packageName && list.findIndex(other => other.packageName === item.packageName) === index
      )
    features.value = (featureList || []).map(item => ({
      ...item,
      online: Boolean(item.online),
      outboundEnabled: Boolean(item.outboundEnabled),
      monthlyComputeCost: Number(item.monthlyComputeCost ?? 0),
      supportedPlatformPackages: Array.isArray(item.supportedPlatformPackages)
        ? item.supportedPlatformPackages
        : []
    }))
  } finally {
    loading.value = false
  }
}

const platformLabel = (platform) => `${platform.name || platform.id || '-'} / ${platform.packageName}`

const saveFeature = async (row) => {
  if (!row.title || !row.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  savingCode.value = row.code
  try {
    const saved = await request.put(`/advanced-features/${row.code}`, {
      online: row.online,
      monthlyComputeCost: row.monthlyComputeCost,
      supportedPlatformPackages: row.supportedPlatformPackages,
      title: row.title,
      line1: row.line1,
      line2: row.line2,
      outboundEnabled: row.outboundEnabled
    })
    const index = features.value.findIndex(item => item.code === row.code)
    if (index >= 0) {
      features.value[index] = {
        ...saved,
        supportedPlatformPackages: saved.supportedPlatformPackages || []
      }
    }
    ElMessage.success('保存成功')
  } finally {
    savingCode.value = ''
  }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
