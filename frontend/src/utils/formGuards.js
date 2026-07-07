import { ElMessage } from 'element-plus/es/components/message/index'

export function hasValue(value) {
  return value !== null && value !== undefined && value !== ''
}

export function isPositive(value) {
  return Number(value) > 0
}

export function warn(message) {
  ElMessage.warning(message)
  return false
}

export function todayString() {
  const today = new Date()
  const local = new Date(today.getTime() - today.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}

export function ensureRequired(value, message) {
  return hasValue(value) || warn(message)
}

export function ensurePositive(value, message) {
  return isPositive(value) || warn(message)
}

export function ensureDateOrder(start, end, message) {
  if (!hasValue(start) || !hasValue(end)) return true
  return String(end) >= String(start) || warn(message)
}

export function ensureNotFuture(value, message) {
  if (!hasValue(value)) return true
  return String(value) <= todayString() || warn(message)
}

export function ensureDateNotBefore(value, minValue, message) {
  if (!hasValue(value) || !hasValue(minValue)) return true
  return String(value) >= String(minValue) || warn(message)
}

export async function validateForm(formRef) {
  if (!formRef?.value) return false
  return formRef.value.validate().catch(() => false)
}
