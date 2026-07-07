export const roleLabels = {
  ADMIN: '系统管理员',
  WAREHOUSE: '仓库人员',
  PRODUCTION: '生产人员',
  INSPECTOR: '质检员',
  QUALITY_MANAGER: '质量主管'
}

export const materialCategoryLabels = {
  RAW: '原材料',
  SEMI: '半成品',
  PRODUCT: '成品'
}

export const sourceTypeLabels = {
  PURCHASE: '采购入库',
  PRODUCTION: '生产完工'
}

export const batchStatusLabels = {
  PENDING_INSPECT: '待检',
  INSPECTING: '检验中',
  QUALIFIED: '合格在库',
  FROZEN: '冻结',
  DEPLETED: '已耗尽',
  RECALLED: '已召回',
  RETURNED: '已退货',
  SCRAPPED: '已报废'
}

export const orderStatusLabels = {
  CREATED: '已创建',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完工',
  CLOSED: '已关闭'
}

export const processStatusLabels = {
  PENDING: '待开工',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成'
}

export const inspectTypeLabels = {
  IQC: '来料检验',
  IPQC: '过程检验',
  FQC: '成品检验'
}

export const taskStatusLabels = {
  PENDING: '待领取',
  IN_PROGRESS: '检验中',
  COMPLETED: '已完成'
}

export const conclusionLabels = {
  QUALIFIED: '合格',
  UNQUALIFIED: '不合格',
  CONCESSION: '让步接收'
}

export const defectTypeLabels = {
  APPEARANCE: '外观缺陷',
  DIMENSION: '尺寸不良',
  FUNCTION: '功能异常',
  SAFETY: '安全风险',
  OTHER: '其他'
}

export const severityLabels = {
  MINOR: '轻微',
  MAJOR: '严重',
  CRITICAL: '致命'
}

export const handleStatusLabels = {
  PENDING: '待处置',
  COMPLETED: '已处置'
}

export const handleMethodLabels = {
  REWORK: '返工',
  SCRAP: '报废',
  CONCESSION: '让步放行',
  RETURN: '退货'
}

export const recallLevelLabels = {
  I: '一级召回',
  II: '二级召回',
  III: '三级召回'
}

export const recallStatusLabels = {
  CREATED: '已创建',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

export const recoveryStatusLabels = {
  PENDING: '待通知',
  NOTIFIED: '已通知',
  RECOVERED: '已回收',
  UNRECOVERABLE: '无法回收'
}

const tagMap = {
  QUALIFIED: 'success',
  COMPLETED: 'success',
  HANDLED: 'success',
  RECOVERED: 'success',
  CONCESSION: 'warning',
  IN_PROGRESS: 'primary',
  ASSIGNED: 'primary',
  INSPECTING: 'primary',
  PENDING: 'warning',
  PENDING_INSPECT: 'warning',
  PENDING_NOTIFY: 'warning',
  FROZEN: 'danger',
  UNQUALIFIED: 'danger',
  CRITICAL: 'danger',
  RECALLED: 'danger',
  CLOSED: 'info',
  CANCELLED: 'info',
  DEPLETED: 'info',
  RETURNED: 'info',
  SCRAPPED: 'info',
  UNRECOVERABLE: 'info',
  LOST: 'info'
}

export function labelOf(dict, value) {
  if (value === null || value === undefined || value === '') return '-'
  return dict[value] || value
}

export function tagTypeOf(value) {
  return tagMap[value] || 'info'
}

export function yesNo(value) {
  return Number(value) === 1 ? '是' : '否'
}

export const pageSizeOptions = [10, 20, 50, 100]
