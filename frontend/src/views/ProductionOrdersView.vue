<template>
  <section class="page">
    <PageTitle title="生产工单" subtitle="创建工单、生产领料、工序流转和完工入库，形成批次消耗追溯边。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canOperate" type="primary" :icon="Plus" @click="openCreate">创建工单</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="工单号" style="width: 220px" @keyup.enter="load" />
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 140px">
            <el-option v-for="(label, value) in orderStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select
            v-model="query.materialId"
            clearable
            filterable
            remote
            reserve-keyword
            placeholder="生产物料"
            :remote-method="searchProducibleMaterials"
            :loading="materialOptionLoading"
            style="width: 240px"
            @visible-change="handleMaterialVisible"
          >
            <el-option v-for="item in materialOptions" :key="item.id" :label="materialOptionLabel(item)" :value="item.id" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="orderNo" label="工单号" min-width="170" fixed />
        <el-table-column label="生产物料" min-width="190">
          <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column prop="planQuantity" label="计划数量" width="110" />
        <el-table-column prop="actualQuantity" label="实际数量" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :dict="orderStatusLabels" /></template>
        </el-table-column>
        <el-table-column prop="planStartDate" label="计划开始" width="120" />
        <el-table-column prop="planEndDate" label="计划结束" width="120" />
        <el-table-column prop="actualStartTime" label="实际开工" width="180" />
        <el-table-column prop="actualEndTime" label="实际完工" width="180" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canOperate && canIssue(row)" text :icon="Box" @click="openIssue(row)">领料</el-button>
              <el-button
                v-if="canOperate && canComplete(row)"
                text
                type="success"
                :loading="completeOpeningId === row.id"
                @click="openCompleteOrder(row)"
              >
                完工入库
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="pageSizeOptions"
          layout="total, sizes, prev, pager, next"
          :total="page.total"
          @change="load"
        />
      </div>
    </div>

    <el-dialog v-model="createDialog.visible" title="创建生产工单" width="620px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="生产物料" required>
          <el-select
            v-model="createForm.materialId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchProducibleMaterials"
            :loading="materialOptionLoading"
            style="width: 100%"
            @visible-change="handleMaterialVisible"
          >
            <el-option v-for="item in materialOptions" :key="item.id" :label="materialOptionLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="计划数量" required><el-input-number v-model="createForm.planQuantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
          <el-form-item label="计划开始" required><el-date-picker v-model="createForm.planStartDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="计划结束" required><el-date-picker v-model="createForm.planEndDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="备注"><el-input v-model.trim="createForm.remark" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="createDialog.visible = false">取消</el-button><el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="issueDialog.visible" title="生产领料" width="760px">
      <div class="muted" style="margin-bottom: 12px">工单：{{ issueDialog.row?.orderNo }}。后端会校验领料批次属于该工单 BOM 子项。</div>
      <div class="bom-hint">
        <span v-for="row in issueBomRows" :key="row.id">
          {{ row.childMaterialCode }} · {{ row.childMaterialName }} × {{ row.quantity }}
        </span>
      </div>
      <el-alert
        v-if="issueableBatches.length === 0"
        title="当前工单 BOM 子项暂无合格在库批次可领用"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <div v-for="(item, index) in issueForm.items" :key="index" class="issue-row">
        <el-select
          v-model="item.batchId"
          filterable
          remote
          reserve-keyword
          placeholder="选择合格在库批次"
          :remote-method="searchIssueBatches"
          :loading="issueBatchLoading"
          @visible-change="handleIssueBatchVisible"
        >
          <el-option
            v-for="batch in issueableBatches"
            :key="batch.id"
            :label="`${batch.batchNo} · ${batch.materialName} · 余量${batch.remainingQuantity}`"
            :value="batch.id"
          />
        </el-select>
        <el-input-number v-model="item.quantity" :min="0.001" :step="1" />
        <el-button :icon="Delete" text type="danger" @click="issueForm.items.splice(index, 1)" />
      </div>
      <el-button :icon="Plus" @click="issueForm.items.push({ batchId: null, quantity: 1 })">添加领料行</el-button>
      <template #footer><el-button @click="issueDialog.visible = false">取消</el-button><el-button type="primary" :loading="issueSaving" @click="submitIssue">确认领料</el-button></template>
    </el-dialog>

    <el-dialog v-model="completeDialog.visible" title="工单完工入库" width="560px">
      <el-alert
        v-if="completeSubmitBlockedReason"
        :title="completeSubmitBlockedReason"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form :model="completeForm" label-width="112px">
        <el-form-item label="实际数量" required><el-input-number v-model="completeForm.actualQuantity" :min="0.001" :step="1" :max="Number(completeDialog.detail?.order?.planQuantity || 999999999)" style="width: 100%" /></el-form-item>
        <el-form-item label="生产日期" required><el-date-picker v-model="completeForm.productionDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="入库库位"><el-input v-model.trim="completeForm.warehouseLocation" /></el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="completeForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="completeDialog.visible = false">取消</el-button><el-button type="primary" :disabled="!!completeSubmitBlockedReason" :loading="completeSaving" @click="submitCompleteOrder">确认入库</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDrawer.visible" title="工单详情" size="58%">
      <template v-if="detail">
        <div class="drawer-section">
          <h3 class="drawer-title">工单概要</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="工单号">{{ detail.order.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusTag :value="detail.order.status" :dict="orderStatusLabels" /></el-descriptions-item>
            <el-descriptions-item label="生产物料">{{ detail.materialCode }} · {{ detail.materialName }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ detail.order.planQuantity }}</el-descriptions-item>
            <el-descriptions-item label="产出批次">{{ detail.outputBatch?.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际数量">{{ detail.order.actualQuantity || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">工序流转</h3>
          <el-table :data="detail.processRecords || []" stripe>
            <el-table-column prop="stepNo" label="步骤" width="70" />
            <el-table-column label="工序" min-width="170">
              <template #default="{ row }">{{ row.processCode }} · {{ row.processName }}</template>
            </el-table-column>
            <el-table-column label="IPQC" width="80">
              <template #default="{ row }">{{ row.needIpqc === 1 ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="放行状态" width="120">
              <template #default="{ row }">
                <span v-if="row.needIpqc !== 1" class="muted">-</span>
                <el-tag v-else :type="row.ipqcReleased ? 'success' : 'warning'" effect="light" size="small">
                  {{ ipqcStateText(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><StatusTag :value="row.status" :dict="processStatusLabels" /></template>
            </el-table-column>
            <el-table-column prop="startTime" label="开工时间" width="170" />
            <el-table-column prop="endTime" label="完工时间" width="170" />
            <el-table-column v-if="canOperate" label="操作" width="150">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-tooltip :disabled="!processStartReason(row)" :content="processStartReason(row)" placement="top">
                    <span class="button-wrap">
                      <el-button
                        text
                        type="primary"
                        :disabled="!!processStartReason(row)"
                        :loading="processBusyId === row.id && processBusyAction === 'start'"
                        @click="startProcess(row)"
                      >
                        开工
                      </el-button>
                    </span>
                  </el-tooltip>
                  <el-tooltip :disabled="!processCompleteReason(row)" :content="processCompleteReason(row)" placement="top">
                    <span class="button-wrap">
                      <el-button
                        text
                        type="success"
                        :disabled="!!processCompleteReason(row)"
                        :loading="processBusyId === row.id && processBusyAction === 'complete'"
                        @click="completeProcess(row)"
                      >
                        报完工
                      </el-button>
                    </span>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="drawer-section">
          <div class="section-title-row">
            <h3 class="drawer-title">BOM 领料达成</h3>
            <el-button v-if="canOperate && canIssue(detail.order)" text :icon="Box" @click="openIssue(detail.order)">继续领料</el-button>
          </div>
          <el-table :data="detail.materialRequirements || []" stripe>
            <el-table-column label="物料" min-width="200">
              <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="单位用量" width="110">
              <template #default="{ row }">{{ formatQty(row.unitQuantity) }}</template>
            </el-table-column>
            <el-table-column label="计划需求" width="110">
              <template #default="{ row }">{{ formatQty(row.requiredQuantity) }}</template>
            </el-table-column>
            <el-table-column label="已领数量" width="110">
              <template #default="{ row }">{{ formatQty(row.issuedQuantity) }}</template>
            </el-table-column>
            <el-table-column label="缺口" width="110">
              <template #default="{ row }">{{ formatQty(row.missingQuantity) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.issuedEnough ? 'success' : 'warning'" effect="light" size="small">
                  {{ row.issuedEnough ? '已足额' : '待补料' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">领料消耗</h3>
          <el-table :data="detail.consumptions || []" stripe>
            <el-table-column prop="batchNo" label="消耗批次" min-width="170" />
            <el-table-column label="物料" min-width="180">
              <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="消耗数量" width="110" />
            <el-table-column label="批次状态" width="120">
              <template #default="{ row }"><StatusTag :value="row.batchStatus" :dict="batchStatusLabels" /></template>
            </el-table-column>
            <el-table-column prop="createdAt" label="领料时间" width="170" />
          </el-table>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { Box, Delete, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { batchApi, bomApi, materialApi, productionApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { batchStatusLabels, conclusionLabels, orderStatusLabels, pageSizeOptions, processStatusLabels, taskStatusLabels } from '../utils/dicts'
import { ensureDateNotBefore, ensureDateOrder, ensureNotFuture, ensurePositive, ensureRequired, todayString, warn } from '../utils/formGuards'

const auth = useAuthStore()
const canOperate = computed(() => auth.hasAnyRole(['PRODUCTION', 'ADMIN']))
const loading = ref(false)
const page = reactive({ records: [], total: 0 })
const query = reactive({ current: 1, size: 10, keyword: '', status: '', materialId: '' })
const materialOptions = ref([])
const issueableBatches = ref([])
const issueBomRows = ref([])
const detail = ref(null)
const materialOptionLoading = ref(false)
const issueBatchLoading = ref(false)
const createSaving = ref(false)
const issueSaving = ref(false)
const completeSaving = ref(false)
const completeOpeningId = ref(null)
const processBusyId = ref(null)
const processBusyAction = ref('')
const createDialog = reactive({ visible: false })
const issueDialog = reactive({ visible: false, row: null })
const completeDialog = reactive({ visible: false, row: null, detail: null })
const detailDrawer = reactive({ visible: false })

const createForm = reactive({
  materialId: null,
  planQuantity: 1,
  planStartDate: todayString(),
  planEndDate: todayString(),
  remark: ''
})
const issueForm = reactive({ items: [] })
const completeForm = reactive({
  actualQuantity: 1,
  productionDate: todayString(),
  warehouseLocation: '',
  remark: ''
})

const completeSubmitBlockedReason = computed(() => completeBlockReason(completeDialog.detail, completeForm.actualQuantity))

onMounted(async () => {
  await Promise.all([searchProducibleMaterials(''), load()])
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await productionApi.page(query))
  } finally {
    loading.value = false
  }
}

function canIssue(row) {
  return ['CREATED', 'IN_PROGRESS'].includes(row.status)
}

function canComplete(row) {
  return row.status === 'IN_PROGRESS'
}

function openCreate() {
  createDialog.visible = true
  searchProducibleMaterials('')
  Object.assign(createForm, {
    materialId: null,
    planQuantity: 1,
    planStartDate: todayString(),
    planEndDate: todayString(),
    remark: ''
  })
}

async function submitCreate() {
  if (!ensureRequired(createForm.materialId, '请选择生产物料')) return
  if (!ensurePositive(createForm.planQuantity, '计划数量必须大于 0')) return
  if (!ensureRequired(createForm.planStartDate, '请选择计划开始日期')) return
  if (!ensureRequired(createForm.planEndDate, '请选择计划结束日期')) return
  if (!ensureDateOrder(createForm.planStartDate, createForm.planEndDate, '计划结束日期不能早于计划开始日期')) return

  createSaving.value = true
  try {
    await productionApi.create(createForm)
    ElMessage.success('工单已创建，工序快照已生成')
    createDialog.visible = false
    await load()
  } finally {
    createSaving.value = false
  }
}

async function openIssue(row) {
  issueDialog.visible = true
  issueDialog.row = row
  issueForm.items = [{ batchId: null, quantity: 1 }]
  const bomRows = await bomApi.list({ parentMaterialId: row.materialId })
  issueBomRows.value = bomRows || []
  await searchIssueBatches('')
}

async function submitIssue() {
  if (!issueForm.items.length) {
    warn('请至少添加一条领料行')
    return
  }
  const usedBatchIds = new Set()
  for (const [index, item] of issueForm.items.entries()) {
    if (!ensureRequired(item.batchId, `第 ${index + 1} 行请选择领料批次`)) return
    if (!ensurePositive(item.quantity, `第 ${index + 1} 行领料数量必须大于 0`)) return
    if (usedBatchIds.has(item.batchId)) {
      warn('同一批次请合并为一条领料行')
      return
    }
    usedBatchIds.add(item.batchId)
  }

  issueSaving.value = true
  try {
    await productionApi.issueMaterials(issueDialog.row.id, issueForm)
    ElMessage.success('领料成功，追溯消耗边已写入')
    issueDialog.visible = false
    await Promise.all([load(), refreshDetail()])
  } finally {
    issueSaving.value = false
  }
}

async function handleMaterialVisible(visible) {
  if (visible && materialOptions.value.length === 0) {
    await searchProducibleMaterials('')
  }
}

async function searchProducibleMaterials(keyword) {
  materialOptionLoading.value = true
  try {
    const params = { current: 1, size: 20, status: 1, keyword }
    const pages = await Promise.all([
      materialApi.page({ ...params, category: 'SEMI' }),
      materialApi.page({ ...params, category: 'PRODUCT' })
    ])
    materialOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.value = false
  }
}

function materialOptionLabel(item) {
  return `${item.materialCode} · ${item.name}`
}

async function handleIssueBatchVisible(visible) {
  if (visible && issueableBatches.value.length === 0) {
    await searchIssueBatches('')
  }
}

async function searchIssueBatches(keyword) {
  const materialIds = [...new Set(issueBomRows.value.map((row) => row.childMaterialId).filter(Boolean))]
  if (materialIds.length === 0) {
    issueableBatches.value = []
    return
  }
  issueBatchLoading.value = true
  try {
    const pages = await Promise.all(materialIds.map((materialId) =>
      batchApi.page({ current: 1, size: 20, status: 'QUALIFIED', materialId, keyword })
    ))
    issueableBatches.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    issueBatchLoading.value = false
  }
}

function mergeById(rows) {
  const map = new Map()
  for (const row of rows) {
    map.set(String(row.id), row)
  }
  return [...map.values()]
}

async function openCompleteOrder(row) {
  completeOpeningId.value = row.id
  try {
    const orderDetail = await productionApi.detail(row.id)
    completeDialog.detail = orderDetail
    completeDialog.row = orderDetail.order
    Object.assign(completeForm, {
      actualQuantity: orderDetail.order.planQuantity || 1,
      productionDate: todayString(),
      warehouseLocation: '',
      remark: ''
    })
    completeDialog.visible = true
  } finally {
    completeOpeningId.value = null
  }
}

async function submitCompleteOrder() {
  if (!ensurePositive(completeForm.actualQuantity, '实际数量必须大于 0')) return
  if (!ensureRequired(completeForm.productionDate, '请选择生产日期')) return
  if (!ensureNotFuture(completeForm.productionDate, '生产日期不能晚于今天')) return
  if (!ensureDateNotBefore(completeForm.productionDate, completeDialog.detail?.order?.planStartDate, '生产日期不能早于计划开始日期')) return
  if (completeSubmitBlockedReason.value) {
    warn(completeSubmitBlockedReason.value)
    return
  }

  completeSaving.value = true
  try {
    await productionApi.completeOrder(completeDialog.row.id, completeForm)
    ElMessage.success('完工入库成功，FQC 检验任务已自动生成')
    completeDialog.visible = false
    completeDialog.detail = null
    await Promise.all([load(), refreshDetail()])
  } finally {
    completeSaving.value = false
  }
}

async function openDetail(row) {
  detail.value = await productionApi.detail(row.id)
  detailDrawer.visible = true
}

async function refreshDetail() {
  if (detail.value?.order?.id) {
    detail.value = await productionApi.detail(detail.value.order.id)
  }
  if (completeDialog.visible && completeDialog.row?.id) {
    completeDialog.detail = await productionApi.detail(completeDialog.row.id)
    completeDialog.row = completeDialog.detail.order
  }
}

async function startProcess(row) {
  processBusyId.value = row.id
  processBusyAction.value = 'start'
  try {
    await productionApi.startProcess(row.id)
    ElMessage.success('工序已开工')
    await refreshDetail()
    await load()
  } finally {
    processBusyId.value = null
    processBusyAction.value = ''
  }
}

async function completeProcess(row) {
  const { value } = await ElMessageBox.prompt('填写报工备注（可为空）', `工序报完工：${row.processName}`, {
    inputPlaceholder: '备注',
    inputType: 'textarea'
  })
  processBusyId.value = row.id
  processBusyAction.value = 'complete'
  try {
    await productionApi.completeProcess(row.id, { remark: value })
    ElMessage.success(row.needIpqc === 1 ? '已报完工，IPQC 任务已生成' : '已报完工')
    await refreshDetail()
    await load()
  } finally {
    processBusyId.value = null
    processBusyAction.value = ''
  }
}

function processStartReason(row) {
  if (row.status !== 'PENDING') return '只有待开工工序可以开工'
  const order = detail.value?.order
  if (!order) return ''
  if (['COMPLETED', 'CLOSED'].includes(order.status)) return '工单已完工或关闭，不能开工'
  if (order.status === 'CREATED' && Number(row.stepNo) === 1) {
    const missing = initialIssueMissingRows()
    if (missing.length) return `${missing.map((item) => item.materialCode).join('、')} 尚未领料，不能开工`
  }
  const previous = previousProcess(row)
  if (previous && previous.status !== 'COMPLETED') return `前道工序 ${previous.stepNo} 尚未完工`
  if (previous?.needIpqc === 1 && !previous.ipqcReleased) return `前道工序 ${previous.stepNo} 的 IPQC 尚未放行`
  return ''
}

function processCompleteReason(row) {
  if (row.status !== 'IN_PROGRESS') return '只有进行中的工序可以报完工'
  const order = detail.value?.order
  if (order && ['COMPLETED', 'CLOSED'].includes(order.status)) return '工单已完工或关闭，不能报完工'
  return ''
}

function previousProcess(row) {
  return (detail.value?.processRecords || []).find((item) => Number(item.stepNo) === Number(row.stepNo) - 1)
}

function initialIssueMissingRows() {
  return (detail.value?.materialRequirements || []).filter((item) => toNumber(item.issuedQuantity) <= 0)
}

function completeBlockReason(orderDetail, actualQuantity) {
  if (!orderDetail) return '正在读取工单详情'
  const order = orderDetail.order
  if (order.status !== 'IN_PROGRESS') return '只有生产中的工单才能完工入库'
  if (toNumber(actualQuantity) <= 0) return '实际数量必须大于 0'
  if (toNumber(actualQuantity) > toNumber(order.planQuantity)) return '实际完工数量不能超过计划数量'
  if (orderDetail.outputBatch) return '该工单已生成产出批次，不能重复入库'

  const materialGap = materialGapForActual(orderDetail, actualQuantity)
  if (materialGap.length) {
    const first = materialGap[0]
    return `${first.materialCode} 尚缺 ${formatQty(first.missingQuantity)}，不能完工入库`
  }
  const incomplete = (orderDetail.processRecords || []).find((row) => row.status !== 'COMPLETED')
  if (incomplete) return `工序 ${incomplete.stepNo} 尚未完成，不能完工入库`
  const unreleasedIpqc = (orderDetail.processRecords || []).find((row) => row.needIpqc === 1 && !row.ipqcReleased)
  if (unreleasedIpqc) return `工序 ${unreleasedIpqc.stepNo} 的 IPQC 尚未放行`
  return ''
}

function materialGapForActual(orderDetail, actualQuantity) {
  return (orderDetail.materialRequirements || [])
    .map((row) => {
      const required = toNumber(row.unitQuantity) * toNumber(actualQuantity)
      const missing = Math.max(required - toNumber(row.issuedQuantity), 0)
      return { ...row, requiredQuantity: required, missingQuantity: missing }
    })
    .filter((row) => row.missingQuantity > 0)
}

function ipqcStateText(row) {
  if (row.ipqcReleased) return conclusionLabels[row.ipqcConclusion] || '已放行'
  if (!row.ipqcTaskStatus) return row.status === 'COMPLETED' ? '待生成' : '未到检验'
  if (row.ipqcConclusion) return conclusionLabels[row.ipqcConclusion] || row.ipqcConclusion
  return taskStatusLabels[row.ipqcTaskStatus] || row.ipqcTaskStatus
}

function formatQty(value) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(toNumber(value))
}

function toNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}
</script>

<style scoped>
.issue-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px 36px;
  gap: 8px;
  margin-bottom: 10px;
}

.bom-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.bom-hint span {
  padding: 5px 8px;
  color: #2a594d;
  background: var(--app-primary-soft);
  border: 1px solid #c7ded6;
  border-radius: 5px;
  font-size: 12px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.section-title-row .drawer-title {
  margin-bottom: 0;
}

.button-wrap {
  display: inline-flex;
}
</style>
