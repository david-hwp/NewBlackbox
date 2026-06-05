<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>支持平台</span>
          <el-button @click="fetchPlatforms">刷新</el-button>
        </div>
      </template>

      <el-table :data="platforms" v-loading="loading" style="width: 100%">
        <el-table-column label="图标" width="90">
          <template #default="{ row }">
            <img
              class="platform-icon"
              :class="{ unavailable: !row.available }"
              :src="row.iconUrl"
              :alt="row.name"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="平台名称" min-width="140" />
        <el-table-column prop="id" label="平台标识" min-width="120" />
        <el-table-column prop="packageName" label="应用包名" min-width="240" />
        <el-table-column prop="available" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.available ? 'success' : 'info'">
              {{ row.available ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const platforms = ref([])
const loading = ref(false)

const fetchPlatforms = async () => {
  loading.value = true
  try {
    platforms.value = await request.get('/platforms')
  } finally {
    loading.value = false
  }
}

onMounted(fetchPlatforms)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.platform-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  display: block;
}

.platform-icon.unavailable {
  filter: grayscale(100%);
  opacity: 0.45;
}
</style>
