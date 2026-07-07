import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getToken } from '../api/http'

export const menuRoutes = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '质量看板', icon: 'DataBoard' }
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('../views/UserManagementView.vue'),
    meta: { title: '用户管理', icon: 'User', roles: ['ADMIN'] }
  },
  {
    path: '/master',
    name: 'MasterData',
    component: () => import('../views/MasterDataView.vue'),
    meta: { title: '主数据维护', icon: 'SetUp', roles: ['ADMIN'] }
  },
  {
    path: '/batches',
    name: 'Batches',
    component: () => import('../views/BatchLedgerView.vue'),
    meta: { title: '批次台账', icon: 'Box' }
  },
  {
    path: '/production',
    name: 'Production',
    component: () => import('../views/ProductionOrdersView.vue'),
    meta: { title: '生产工单', icon: 'Operation', roles: ['PRODUCTION', 'QUALITY_MANAGER', 'ADMIN'] }
  },
  {
    path: '/inspection',
    name: 'Inspection',
    component: () => import('../views/InspectionTasksView.vue'),
    meta: { title: '检验任务', icon: 'Finished', roles: ['INSPECTOR', 'QUALITY_MANAGER', 'ADMIN'] }
  },
  {
    path: '/defects',
    name: 'Defects',
    component: () => import('../views/DefectManagementView.vue'),
    meta: { title: '缺陷管理', icon: 'Warning', roles: ['INSPECTOR', 'QUALITY_MANAGER', 'ADMIN'] }
  },
  {
    path: '/shipments',
    name: 'Shipments',
    component: () => import('../views/ShipmentManagementView.vue'),
    meta: { title: '出货管理', icon: 'Van', roles: ['WAREHOUSE', 'QUALITY_MANAGER', 'ADMIN'] }
  },
  {
    path: '/trace',
    name: 'Trace',
    component: () => import('../views/TraceQueryView.vue'),
    meta: { title: '追溯查询', icon: 'Share' }
  },
  {
    path: '/recalls',
    name: 'Recalls',
    component: () => import('../views/RecallManagementView.vue'),
    meta: { title: '召回管理', icon: 'Bell', roles: ['QUALITY_MANAGER', 'ADMIN'] }
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportsView.vue'),
    meta: { title: '统计报表', icon: 'TrendCharts' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue') },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: menuRoutes
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.name === 'Login') {
    if (auth.isLoggedIn && getToken()) {
      return typeof to.query.redirect === 'string' ? to.query.redirect : '/dashboard'
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (!getToken()) {
    auth.logout()
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (!auth.user || !auth.profileLoaded) {
    try {
      await auth.loadProfile()
    } catch {
      auth.logout()
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
  }
  const roles = to.meta?.roles || []
  if (!auth.hasAnyRole(roles)) {
    return '/dashboard'
  }
  return true
})

export default router
