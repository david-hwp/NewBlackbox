<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="sidebar-header">
        <el-icon :size="28" color="#059669"><Shop /></el-icon>
        <span class="title">多店管家</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#059669"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="isSuperAdmin" index="/platforms">
          <el-icon><Grid /></el-icon>
          <span>支持平台</span>
        </el-menu-item>
        <el-menu-item index="/shops">
          <el-icon><Shop /></el-icon>
          <span>店铺管理</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <span>交易日志</span>
        </el-menu-item>
        <el-menu-item index="/feedbacks">
          <el-icon><ChatDotRound /></el-icon>
          <span>问题反馈</span>
        </el-menu-item>
        <el-menu-item v-if="isSuperAdmin" index="/channels">
          <el-icon><SetUp /></el-icon>
          <span>渠道管理</span>
        </el-menu-item>
        <el-menu-item v-if="isSuperAdmin" index="/release-jobs">
          <el-icon><Promotion /></el-icon>
          <span>发布任务</span>
        </el-menu-item>
        <el-sub-menu v-if="isSuperAdmin" index="/advanced-features">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>高级功能</span>
          </template>
          <el-menu-item index="/advanced-features/open">开放设置</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/system-parameters">
          <el-icon><Setting /></el-icon>
          <span>系统参数</span>
        </el-menu-item>
        <el-menu-item index="/announcements">
          <el-icon><Bell /></el-icon>
          <span>公告管理</span>
        </el-menu-item>
        <el-menu-item index="/app-versions">
          <el-icon><Upload /></el-icon>
          <span>主 APK 版本</span>
        </el-menu-item>
        <el-menu-item index="/engine-versions">
          <el-icon><Connection /></el-icon>
          <span>引擎版本</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <h3>{{ pageTitle }}</h3>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar
                :size="32"
                shape="square"
                :src="avatarObjectUrl"
                :icon="avatarObjectUrl ? undefined : UserFilled"
                class="header-avatar"
              />
              <span>{{ userInfo?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Shop, Odometer, User, Document, ArrowDown, UserFilled, Bell, Connection, ChatDotRound, Grid, Upload, Promotion, SetUp, Setting } from '@element-plus/icons-vue'
import { getPreferredImageObjectUrl } from '../utils/files'
import { isSuperAdminUser } from '../utils/adminSession'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta?.title || '后台管理')

const userInfo = ref(JSON.parse(localStorage.getItem('admin_user') || '{}'))
const avatarObjectUrl = ref('')
const objectUrlCache = new Map()
const isSuperAdmin = computed(() => isSuperAdminUser(userInfo.value))

const loadAvatar = async () => {
  const avatarUrl = userInfo.value?.avatarUrl
  if (!avatarUrl) {
    avatarObjectUrl.value = ''
    return
  }
  try {
    avatarObjectUrl.value = await getPreferredImageObjectUrl(avatarUrl, objectUrlCache)
  } catch (e) {
    avatarObjectUrl.value = ''
  }
}

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_user')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

onMounted(loadAvatar)

onBeforeUnmount(() => {
  objectUrlCache.forEach(objectUrl => URL.revokeObjectURL(objectUrl))
  objectUrlCache.clear()
})
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
}

.sidebar {
  background: #1e293b;
  color: #fff;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid #334155;
}

.sidebar-header .title {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.sidebar-menu {
  border-right: none;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header h3 {
  font-size: 18px;
  color: #1e293b;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #475569;
}

.header-avatar {
  border-radius: 8px;
}

.main {
  background: #f5f6f8;
  padding: 20px;
}
</style>
