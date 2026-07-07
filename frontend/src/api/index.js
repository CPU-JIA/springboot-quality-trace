import http from './http'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  profile: () => http.get('/auth/profile')
}

export const userApi = {
  page: (params) => http.get('/users', { params }),
  roles: () => http.get('/users/roles'),
  create: (data) => http.post('/users', data),
  update: (id, data) => http.put(`/users/${id}`, data),
  delete: (id) => http.delete(`/users/${id}`),
  resetPassword: (id, data) => http.put(`/users/${id}/password`, data),
  changeStatus: (id, status) => http.put(`/users/${id}/status`, { status })
}

export const materialApi = {
  ...crud('/materials'),
  changeStatus: (id, status) => http.put(`/materials/${id}/status`, { status })
}
export const supplierApi = crud('/suppliers')
export const customerApi = crud('/customers')
export const processApi = {
  ...crud('/processes'),
  all: () => http.get('/processes/all')
}

export const routeApi = {
  get: (materialId) => http.get('/process-routes', { params: { materialId } }),
  save: (data) => http.put('/process-routes', data)
}

export const bomApi = {
  list: (params) => http.get('/boms', { params }),
  tree: (materialId) => http.get(`/boms/tree/${materialId}`),
  create: (data) => http.post('/boms', data),
  delete: (id) => http.delete(`/boms/${id}`)
}

export const inspectionItemApi = crud('/inspection-items')

export const batchApi = {
  page: (params) => http.get('/batches', { params }),
  detail: (id) => http.get(`/batches/${id}`),
  purchaseInbound: (data) => http.post('/batches/purchase-inbound', data)
}

export const productionApi = {
  page: (params) => http.get('/production-orders', { params }),
  detail: (id) => http.get(`/production-orders/${id}`),
  create: (data) => http.post('/production-orders', data),
  issueMaterials: (id, data) => http.post(`/production-orders/${id}/issue-materials`, data),
  startProcess: (processRecordId) => http.post(`/production-orders/process-records/${processRecordId}/start`),
  completeProcess: (processRecordId, data) => http.post(`/production-orders/process-records/${processRecordId}/complete`, data),
  completeOrder: (id, data) => http.post(`/production-orders/${id}/complete`, data)
}

export const inspectionTaskApi = {
  page: (params) => http.get('/inspection-tasks', { params }),
  claim: (id) => http.post(`/inspection-tasks/${id}/claim`),
  submit: (id, data) => http.post(`/inspection-tasks/${id}/submit`, data),
  records: (id) => http.get(`/inspection-tasks/${id}/records`),
  items: (id) => http.get(`/inspection-tasks/${id}/items`)
}

export const defectApi = {
  page: (params) => http.get('/defects', { params }),
  processTargets: (params) => http.get('/defects/process-targets', { params }),
  create: (data) => http.post('/defects', data),
  handle: (id, data) => http.put(`/defects/${id}/handle`, data)
}

export const shipmentApi = {
  page: (params) => http.get('/shipments', { params }),
  create: (data) => http.post('/shipments', data)
}

export const traceApi = {
  byNo: (batchNo) => http.get(`/trace/by-no/${encodeURIComponent(batchNo)}`),
  upstream: (batchId) => http.get(`/trace/upstream/${batchId}`),
  downstream: (batchId) => http.get(`/trace/downstream/${batchId}`)
}

export const recallApi = {
  page: (params) => http.get('/recalls', { params }),
  detail: (id) => http.get(`/recalls/${id}`),
  create: (data) => http.post('/recalls', data),
  updateDetail: (detailId, data) => http.put(`/recalls/details/${detailId}`, data),
  complete: (id) => http.post(`/recalls/${id}/complete`)
}

export const statsApi = {
  dashboard: () => http.get('/stats/dashboard'),
  passRateTrend: (months = 6) => http.get('/stats/pass-rate-trend', { params: { months } }),
  defectPareto: () => http.get('/stats/defect-pareto'),
  supplierQuality: () => http.get('/stats/supplier-quality')
}

function crud(base) {
  return {
    page: (params) => http.get(base, { params }),
    create: (data) => http.post(base, data),
    update: (id, data) => http.put(`${base}/${id}`, data),
    delete: (id) => http.delete(`${base}/${id}`)
  }
}
