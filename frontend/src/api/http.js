import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index'

const TOKEN_KEY = 'quality_trace_token'
const USER_KEY = 'quality_trace_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else clearToken()
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function clearSessionStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (config.params) {
    config.params = Object.fromEntries(
      Object.entries(config.params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
    )
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code === 'undefined') {
      return body
    }
    if (body.code === 200) {
      return normalizePageResult(body.data)
    }
    if (body.code === 401) {
      clearSessionStorage()
      ElMessage.error(body.message || '登录已过期，请重新登录')
      window.location.hash = '#/login'
      return Promise.reject(new Error(body.message || '未登录'))
    }
    ElMessage.error(body.message || '操作失败')
    return Promise.reject(new Error(body.message || '操作失败'))
  },
  (error) => {
    const message = error?.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

function normalizePageResult(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return data
  }
  if (Array.isArray(data.records) && Object.prototype.hasOwnProperty.call(data, 'total')) {
    return {
      ...data,
      total: Number(data.total || 0),
      current: Number(data.current || 1),
      size: Number(data.size || data.records.length || 10)
    }
  }
  return data
}

export default http
