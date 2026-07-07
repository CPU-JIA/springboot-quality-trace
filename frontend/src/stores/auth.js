import { defineStore } from 'pinia'
import { authApi } from '../api'
import { clearSessionStorage, getToken, setToken } from '../api/http'

const USER_KEY = 'quality_trace_user'

function loadCachedSession() {
  const token = getToken()
  const cachedUser = localStorage.getItem(USER_KEY)
  if (!cachedUser) {
    return { token, user: null }
  }
  try {
    return { token, user: JSON.parse(cachedUser) }
  } catch {
    clearSessionStorage()
    return { token: null, user: null }
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const cached = loadCachedSession()
    return {
      token: cached.token,
      user: cached.user,
      profileLoaded: false
    }
  },
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    roles: (state) => state.user?.roles || [],
    realName: (state) => state.user?.realName || state.user?.username || '未登录',
    hasAnyRole: (state) => (roles) => {
      if (!roles || roles.length === 0) return true
      const owned = state.user?.roles || []
      if (owned.includes('ADMIN')) return true
      return roles.some((role) => owned.includes(role))
    }
  },
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      this.setSession(data)
      return data
    },
    async loadProfile() {
      const data = await authApi.profile()
      this.setUser(data)
      return data
    },
    setSession(data) {
      this.token = data.token
      this.setUser(data)
      setToken(data.token)
    },
    setUser(data) {
      const { token, ...user } = data
      this.user = user
      this.profileLoaded = true
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    logout() {
      this.token = null
      this.user = null
      this.profileLoaded = false
      clearSessionStorage()
    }
  }
})
