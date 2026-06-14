import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import Dashboard from '../views/Dashboard.vue'
import Users from '../views/Users.vue'
import Shops from '../views/Shops.vue'
import Logs from '../views/Logs.vue'
import Announcements from '../views/Announcements.vue'
import EngineVersions from '../views/EngineVersions.vue'
import AppVersions from '../views/AppVersions.vue'
import Feedbacks from '../views/Feedbacks.vue'
import Platforms from '../views/Platforms.vue'
import Channels from '../views/Channels.vue'
import ReleaseJobs from '../views/ReleaseJobs.vue'
import SystemParameters from '../views/SystemParameters.vue'
import AdvancedFeatureOpen from '../views/AdvancedFeatureOpen.vue'
import { isSuperAdminUser, getAdminUser } from '../utils/adminSession'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: Dashboard, meta: { title: '概览' } },
      { path: 'users', name: 'Users', component: Users, meta: { title: '用户管理' } },
      { path: 'platforms', name: 'Platforms', component: Platforms, meta: { title: '支持平台', superAdminOnly: true } },
      { path: 'shops', name: 'Shops', component: Shops, meta: { title: '店铺管理' } },
      { path: 'logs', name: 'Logs', component: Logs, meta: { title: '交易日志' } },
      { path: 'feedbacks', name: 'Feedbacks', component: Feedbacks, meta: { title: '问题反馈' } },
      { path: 'channels', name: 'Channels', component: Channels, meta: { title: '渠道管理', superAdminOnly: true } },
      { path: 'release-jobs', name: 'ReleaseJobs', component: ReleaseJobs, meta: { title: '发布任务', superAdminOnly: true } },
      { path: 'advanced-features/open', name: 'AdvancedFeatureOpen', component: AdvancedFeatureOpen, meta: { title: '开放设置', superAdminOnly: true } },
      { path: 'system-parameters', name: 'SystemParameters', component: SystemParameters, meta: { title: '系统参数' } },
      { path: 'announcements', name: 'Announcements', component: Announcements, meta: { title: '公告管理' } },
      { path: 'app-versions', name: 'AppVersions', component: AppVersions, meta: { title: '主 APK 版本' } },
      { path: 'engine-versions', name: 'EngineVersions', component: EngineVersions, meta: { title: '引擎版本' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.meta?.superAdminOnly && !isSuperAdminUser(getAdminUser())) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
