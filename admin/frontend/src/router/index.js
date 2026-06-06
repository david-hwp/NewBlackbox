import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import Dashboard from '../views/Dashboard.vue'
import Users from '../views/Users.vue'
import Shops from '../views/Shops.vue'
import Logs from '../views/Logs.vue'
import Announcements from '../views/Announcements.vue'
import EngineVersions from '../views/EngineVersions.vue'
import Feedbacks from '../views/Feedbacks.vue'
import Platforms from '../views/Platforms.vue'

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
      { path: 'platforms', name: 'Platforms', component: Platforms, meta: { title: '支持平台' } },
      { path: 'shops', name: 'Shops', component: Shops, meta: { title: '店铺管理' } },
      { path: 'logs', name: 'Logs', component: Logs, meta: { title: '交易日志' } },
      { path: 'feedbacks', name: 'Feedbacks', component: Feedbacks, meta: { title: '问题反馈' } },
      { path: 'announcements', name: 'Announcements', component: Announcements, meta: { title: '公告管理' } },
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
  } else {
    next()
  }
})

export default router
