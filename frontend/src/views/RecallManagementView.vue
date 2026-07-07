<template>
  <section class="page">
    <PageTitle title="召回管理" subtitle="基于正向追溯自动圈定影响批次与客户，跟踪通知和回收状态。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canRecall" type="primary" :icon="Plus" @click="openCreate">发起召回</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="召回单号 / 原因 / 批次号" style="width: 260px" @keyup.enter="load" />
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 140px">
            <el-option v-for="(label, value) in recallStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select v-model="query.recallLevel" clearable placeholder="级别" style="width: 140px">
            <el-option v-for="(label, value) in recallLevelLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="recallNo" label="召回单号" min-width="170" fixed />
        <el-table-column prop="sourceBatchNo" label="源头批次" min-width="170" />
        <el-table-column label="级别" width="110">
          <template #default="{ row }"><StatusTag :value="row.recallLevel" :dict="recallLevelLabels" /></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :dict="recallStatusLabels" /></template>
        </el-table-column>
        <el-table-column prop="reason" label="召回原因" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="发起时间" width="180" />
        <el-table-column prop="completedAt" label="完成时间" width="180" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canRecall && row.status === 'IN_PROGRESS'" text type="success" @click="completeRecall(row)">完成</el-button>
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

    <el-dialog v-model="createDialog.visible" title="发起召回" width="860px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="源头批次" required>
          <el-select
            v-model="createForm.sourceBatchId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchSourceBatches"
            :loading="sourceBatchLoading"
            style="width: 100%"
            @change="previewImpact"
            @visible-change="handleSourceBatchVisible"
          >
            <el-option
              v-for="batch in batches"
              :key="batch.id"
              :label="batchOptionLabel(batch)"
              :value="batch.id"
              :disabled="isBatchDisabled(batch)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="召回级别" required>
          <el-select v-model="createForm.recallLevel" style="width: 220px">
            <el-option v-for="(label, value) in recallLevelLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="召回原因" required>
          <el-input v-model.trim="createForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <div class="drawer-section">
        <div class="drawer-title">影响面预览</div>
        <el-table :data="impactRows" height="260" stripe>
          <el-table-column prop="level" label="层级" width="70" />
          <el-table-column prop="batchNo" label="影响批次" min-width="170" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }"><StatusTag :value="row.batchStatus" :dict="batchStatusLabels" /></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="160" />
          <el-table-column prop="shipmentNo" label="出货单" min-width="160" />
          <el-table-column prop="shippedQuantity" label="出货数量" width="110" />
        </el-table>
      </div>

      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button :icon="Refresh" :loading="previewing" @click="previewImpact">预览影响面</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确认发起</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer.visible" title="召回详情" size="58%">
      <template v-if="detail">
        <div class="drawer-section">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="召回单号">{{ detail.recallNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusTag :value="detail.status" :dict="recallStatusLabels" /></el-descriptions-item>
            <el-descriptions-item label="源头批次">{{ detail.sourceBatchNo }}</el-descriptions-item>
            <el-descriptions-item label="级别"><StatusTag :value="detail.recallLevel" :dict="recallLevelLabels" /></el-descriptions-item>
            <el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">回收明细</h3>
          <el-table :data="detail.details || []" stripe>
            <el-table-column prop="affectedBatchNo" label="影响批次" min-width="170" />
            <el-table-column prop="shipmentNo" label="出货单" min-width="160" />
            <el-table-column prop="customerName" label="客户" min-width="160" />
            <el-table-column label="回收状态" width="130">
              <template #default="{ row }"><StatusTag :value="row.recoveryStatus" :dict="recoveryStatusLabels" /></template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column v-if="canRecall && detail.status === 'IN_PROGRESS'" label="操作" width="120">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button v-if="canUpdateRecallDetail(row)" text type="primary" @click="openUpdateDetail(row)">更新</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="detailUpdateDialog.visible" title="更新回收状态" width="460px">
      <el-form :model="detailUpdateForm" label-width="100px">
        <el-form-item label="影响批次">{{ detailUpdateDialog.row?.affectedBatchNo }}</el-form-item>
        <el-form-item label="回收状态" required>
          <el-select v-model="detailUpdateForm.recoveryStatus" style="width: 100%">
            <el-option v-for="option in recoveryStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="detailUpdateForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="detailUpdateDialog.visible = false">取消</el-button><el-button type="primary" :loading="detailSaving" @click="submitDetailUpdate">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { batchApi, recallApi, traceApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { batchStatusLabels, labelOf, pageSizeOptions, recallLevelLabels, recallStatusLabels, recoveryStatusLabels } from '../utils/dicts'
import { ensureRequired, warn } from '../utils/formGuards'

const auth = useAuthStore()
const canRecall = computed(() => auth.hasAnyRole(['QUALITY_MANAGER', 'ADMIN']))
const loading = ref(false)
const page = reactive({ records: [], total: 0 })
const query = reactive({ current: 1, size: 10, status: '', recallLevel: '', keyword: '' })
const batches = ref([])
const impactRows = ref([])
const detail = ref(null)
const sourceBatchLoading = ref(false)
const previewing = ref(false)
const creating = ref(false)
const detailSaving = ref(false)
const createDialog = reactive({ visible: false })
const detailDrawer = reactive({ visible: false })
const detailUpdateDialog = reactive({ visible: false, row: null })
const createForm = reactive({ sourceBatchId: null, recallLevel: 'I', reason: '' })
const detailUpdateForm = reactive({ recoveryStatus: 'NOTIFIED', remark: '' })
const disabledSourceStatuses = new Set(['RECALLED', 'SCRAPPED', 'RETURNED'])
const selectableSourceStatuses = ['PENDING_INSPECT', 'INSPECTING', 'QUALIFIED', 'FROZEN', 'DEPLETED']
const finalRecoveryStatuses = new Set(['RECOVERED', 'UNRECOVERABLE'])
const recoveryStatusOptions = computed(() => {
  return getRecoveryTransitionValues(detailUpdateDialog.row).map((value) => ({ value, label: labelOf(recoveryStatusLabels, value) }))
})

onMounted(async () => {
  await Promise.all([searchSourceBatches(''), load()])
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await recallApi.page(query))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createDialog.visible = true
  impactRows.value = []
  searchSourceBatches('')
  Object.assign(createForm, { sourceBatchId: null, recallLevel: 'I', reason: '' })
}

function batchOptionLabel(batch) {
  return `${batch.batchNo} · ${batch.materialName} · ${labelOf(batchStatusLabels, batch.status)} · 余量 ${batch.remainingQuantity ?? '-'}`
}

function isBatchDisabled(batch) {
  return disabledSourceStatuses.has(batch.status)
}

async function handleSourceBatchVisible(visible) {
  if (visible && batches.value.length === 0) {
    await searchSourceBatches('')
  }
}

async function searchSourceBatches(keyword) {
  sourceBatchLoading.value = true
  try {
    const pages = await Promise.all(selectableSourceStatuses.map((status) =>
      batchApi.page({ current: 1, size: 20, status, keyword })
    ))
    batches.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    sourceBatchLoading.value = false
  }
}

async function previewImpact() {
  if (!createForm.sourceBatchId) return
  previewing.value = true
  try {
    impactRows.value = await traceApi.downstream(createForm.sourceBatchId)
  } finally {
    previewing.value = false
  }
}

async function submitCreate() {
  if (!ensureRequired(createForm.sourceBatchId, '请选择源头批次')) return
  if (!ensureRequired(createForm.recallLevel, '请选择召回级别')) return
  if (!ensureRequired(createForm.reason, '请填写召回原因')) return
  if (impactRows.value.length === 0) {
    warn('请先预览影响面，确认存在出货流向或在库影响')
    return
  }

  creating.value = true
  try {
    await recallApi.create(createForm)
    ElMessage.success('召回单已发起，影响批次已标记为召回')
    createDialog.visible = false
    await Promise.all([searchSourceBatches(''), load()])
  } finally {
    creating.value = false
  }
}

async function openDetail(row) {
  detail.value = await recallApi.detail(row.id)
  detailDrawer.visible = true
}

function openUpdateDetail(row) {
  detailUpdateDialog.visible = true
  detailUpdateDialog.row = row
  const next = getRecoveryTransitionValues(row)[0] || row.recoveryStatus || 'NOTIFIED'
  Object.assign(detailUpdateForm, { recoveryStatus: next, remark: row.remark || '' })
}

function canUpdateRecallDetail(row) {
  return !finalRecoveryStatuses.has(row.recoveryStatus) && getRecoveryTransitionValues(row).length > 0
}

function getRecoveryTransitionValues(row) {
  const current = row?.recoveryStatus || 'PENDING'
  if (current === 'PENDING') {
    return row?.shipmentId ? ['NOTIFIED'] : ['RECOVERED', 'UNRECOVERABLE']
  }
  if (current === 'NOTIFIED') {
    return ['RECOVERED', 'UNRECOVERABLE']
  }
  return []
}

async function submitDetailUpdate() {
  if (!ensureRequired(detailUpdateForm.recoveryStatus, '请选择回收状态')) return
  if (detailUpdateForm.recoveryStatus === 'UNRECOVERABLE' && !ensureRequired(detailUpdateForm.remark, '请填写无法回收原因')) return

  detailSaving.value = true
  try {
    await recallApi.updateDetail(detailUpdateDialog.row.id, detailUpdateForm)
    ElMessage.success('召回明细已更新')
    detailUpdateDialog.visible = false
    detail.value = await recallApi.detail(detail.value.id)
  } finally {
    detailSaving.value = false
  }
}

async function completeRecall(row) {
  await ElMessageBox.confirm(`确认完成召回单 ${row.recallNo}？仅当全部明细达到终态时后端会允许关闭。`, '完成召回')
  await recallApi.complete(row.id)
  ElMessage.success('召回单已完成')
  await load()
  if (detail.value?.id === row.id) detail.value = await recallApi.detail(row.id)
}

function mergeById(rows) {
  const map = new Map()
  for (const row of rows) {
    map.set(String(row.id), row)
  }
  return [...map.values()]
}
</script>
