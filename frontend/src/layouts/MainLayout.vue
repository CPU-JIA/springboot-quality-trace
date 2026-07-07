<template>
  <el-container class="app-shell" :class="{ 'is-sidebar-collapsed': effectiveCollapsed }">
    <aside class="sidebar">
      <div class="brand" :class="{ collapsed: effectiveCollapsed }">
        <img class="brand-mark" src="/qt-logo.png" alt="星辉质量追踪" />
        <div class="brand-text" :aria-hidden="effectiveCollapsed">
          <strong>星辉质量追踪</strong>
          <span>批次追溯与质量闭环</span>
        </div>
      </div>

      <el-menu
        class="side-menu"
        :class="{ collapsed: effectiveCollapsed }"
        :collapse="effectiveCollapsed"
        :collapse-transition="true"
        :default-active="$route.path"
        router
        background-color="#1f2d28"
        text-color="#dbe5df"
        active-text-color="#ffffff"
      >
        <el-menu-item
          v-for="item in visibleMenus"
          :key="item.path"
          :index="item.path"
          :aria-label="item.meta.title"
          :title="item.meta.title"
        >
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <el-button
            v-if="!isNarrowScreen"
            class="sidebar-toggle"
            :class="{ 'is-collapsed': collapsed }"
            :aria-label="collapsed ? '展开导航栏' : '收起导航栏'"
            :title="collapsed ? '展开导航栏' : '收起导航栏'"
            text
            @click="toggleSidebar"
          >
            <transition name="toggle-icon" mode="out-in">
              <el-icon :key="collapsed ? 'expand' : 'fold'" class="toggle-icon">
                <component :is="collapsed ? Expand : Fold" />
              </el-icon>
            </transition>
          </el-button>
          <span class="current-title">{{ $route.meta.title }}</span>
        </div>
        <div class="topbar-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="account-chip" type="button" :aria-label="accountMenuLabel" :title="accountMenuLabel">
              <span class="account-avatar">
                <el-icon><UserFilled /></el-icon>
              </span>
              <span class="account-meta">
                <strong>{{ auth.realName }}</strong>
                <span>{{ accountSubline }}</span>
              </span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">刷新用户信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-area">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ArrowDown, Expand, Fold, UserFilled } from '@element-plus/icons-vue'
import { menuRoutes } from '../router'
import { useAuthStore } from '../stores/auth'
import { labelOf, roleLabels } from '../utils/dicts'

const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const isNarrowScreen = ref(false)
const sidebarTransitionMs = 240
let sidebarTransitionTimer = 0
let narrowQuery = null

const visibleMenus = computed(() => {
  return menuRoutes.filter((item) => auth.hasAnyRole(item.meta?.roles || []))
})

const effectiveCollapsed = computed(() => collapsed.value || isNarrowScreen.value)

const roleSummary = computed(() => {
  const labels = auth.roles.map((role) => labelOf(roleLabels, role))
  return labels.length ? labels.join(' / ') : '未分配角色'
})

const accountSubline = computed(() => {
  return auth.user?.username ? `账号：${auth.user.username}` : roleSummary.value
})

const accountMenuLabel = computed(() => {
  return `当前用户：${auth.realName}，打开账号菜单`
})

function toggleSidebar() {
  window.clearTimeout(sidebarTransitionTimer)
  window.dispatchEvent(new CustomEvent('app:layout-transition-start'))
  collapsed.value = !collapsed.value
  sidebarTransitionTimer = window.setTimeout(() => {
    window.dispatchEvent(new CustomEvent('app:layout-transition-end'))
  }, sidebarTransitionMs + 60)
}

async function handleCommand(command) {
  if (command === 'logout') {
    auth.logout()
    router.push('/login')
    return
  }
  if (command === 'profile') {
    await auth.loadProfile()
    ElMessage.success('用户信息已刷新')
  }
}

function syncNarrowState(event) {
  isNarrowScreen.value = Boolean(event.matches)
}

onMounted(() => {
  narrowQuery = window.matchMedia('(max-width: 820px)')
  syncNarrowState(narrowQuery)
  narrowQuery.addEventListener('change', syncNarrowState)
})

onBeforeUnmount(() => {
  window.clearTimeout(sidebarTransitionTimer)
  narrowQuery?.removeEventListener('change', syncNarrowState)
})
</script>

<style scoped>
.app-shell {
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  width: 232px;
  height: 100vh;
  overflow: hidden;
  background: #1f2d28;
  transition: width 0.24s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: width;
}

.app-shell.is-sidebar-collapsed .sidebar {
  width: 72px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  color: #f2f7f4;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  transition:
    gap 0.2s ease,
    padding 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.brand.collapsed {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  display: block;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16);
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  max-width: 150px;
  overflow: hidden;
  opacity: 1;
  transform: translateX(0);
  transition:
    max-width 0.2s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.16s ease,
    transform 0.2s ease;
  white-space: nowrap;
}

.brand.collapsed .brand-text {
  max-width: 0;
  opacity: 0;
  transform: translateX(-6px);
  pointer-events: none;
}

.brand-text strong {
  font-size: 15px;
  letter-spacing: 0;
}

.brand-text span {
  color: #9fb0a8;
  font-size: 11px;
}

.side-menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 12px;
}

.side-menu :deep(.el-menu-item) {
  height: 44px;
  margin: 2px 0;
  border-radius: 5px;
  transition:
    width 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    margin 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    padding 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    color 0.12s ease,
    background-color 0.12s ease;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: #2d6c5d;
}

.side-menu.collapsed {
  width: 100%;
  padding: 8px;
}

.side-menu.collapsed :deep(.el-menu-item) {
  display: flex;
  justify-content: center;
  width: 56px;
  padding: 0 !important;
  margin: 2px auto;
}

.side-menu.collapsed :deep(.el-menu-item .el-menu-tooltip__trigger) {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  padding: 0;
}

.side-menu.collapsed :deep(.el-menu-item .el-icon) {
  width: 22px;
  margin: 0 !important;
  font-size: 18px;
  transition:
    margin 0.18s ease,
    transform 0.18s ease,
    font-size 0.18s ease;
}

.side-menu :deep(.el-menu-item .el-icon) {
  transition:
    margin 0.18s ease,
    transform 0.18s ease,
    font-size 0.18s ease;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 22px;
  background: #ffffff;
  border-bottom: 1px solid var(--app-border);
}

.topbar-left,
.topbar-right,
.account-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-toggle.el-button {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  color: #4b5a55;
  background: var(--app-panel-soft);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    background-color 0.12s ease,
    border-color 0.12s ease,
    color 0.12s ease;
}

.sidebar-toggle.el-button :deep(span) {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
}

.sidebar-toggle.el-button:hover,
.sidebar-toggle.el-button:focus-visible {
  color: var(--app-primary);
  background: #ffffff;
  border-color: var(--app-border);
}

.toggle-icon,
.sidebar-toggle.el-button :deep(.el-icon) {
  width: 18px;
  height: 18px;
  margin: 0;
  font-size: 18px;
}

.toggle-icon {
  display: grid;
  place-items: center;
}

.toggle-icon-enter-active,
.toggle-icon-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}

.toggle-icon-enter-from,
.toggle-icon-leave-to {
  opacity: 0;
  transform: scale(0.86);
}

.current-title {
  font-size: 16px;
  font-weight: 700;
}

.account-chip {
  min-height: 42px;
  padding: 0 10px 0 8px;
  color: var(--app-text);
  background: var(--app-panel-soft);
  border: 1px solid var(--app-border);
  border-radius: 5px;
  cursor: pointer;
}

.account-chip:hover {
  border-color: var(--app-border-strong);
  background: #ffffff;
}

.account-avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: #ffffff;
  background: var(--app-primary);
  border-radius: 5px;
}

.account-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  line-height: 1.2;
}

.account-meta strong {
  max-width: 120px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-meta span {
  max-width: 160px;
  margin-top: 2px;
  overflow: hidden;
  color: var(--app-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-area {
  min-width: 0;
  height: calc(100vh - 64px);
  overflow: auto;
  padding: 18px 22px 28px;
  background: var(--app-bg);
}

@media (max-width: 820px) {
  .topbar {
    padding: 0 14px;
  }

  .topbar-left {
    min-width: 0;
  }

  .current-title {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .account-meta {
    display: none;
  }

  .sidebar {
    width: 72px !important;
  }

  .main-area {
    padding: 14px 12px 22px;
  }
}
</style>
