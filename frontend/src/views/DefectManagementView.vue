<template>
  <section class="page">
    <PageTitle title="缺陷管理" subtitle="登记批次缺陷或过程缺陷，并由质量主管完成处置闭环。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">登记缺陷</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="缺陷单号 / 描述" style="width: 240px" @keyup.enter="load" />
          <el-select v-model="query.defectType" clearable placeholder="缺陷类型" style="width: 150px">
            <el-option v-for="(label, value) in defectTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select v-model="query.handleStatus" clearable placeholder="处置状态" style="width: 140px">
            <el-option v-for="(label, value) in handleStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="defectNo" label="缺陷单号" min-width="170" fixed />
        <el-table-column label="载体" min-width="180">
          <template #default="{ row }">
            <span v-if="row.batchNo">批次 {{ row.batchNo }} · {{ batchStatusLabels[row.batchStatus] || row.batchStatus }}</span>
            <span v-else>工序 {{ row.stepNo }} · {{ row.processName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }"><StatusTag :value="row.defectType" :dict="defectTypeLabels" /></template>
        </el-table-column>
        <el-table-column label="严重度" width="110">
          <template #default="{ row }"><StatusTag :value="row.severity" :dict="severityLabels" /></template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="处置方式" width="120">
          <template #default="{ row }"><StatusTag :value="row.handleMethod" :dict="handleMethodLabels" /></template>
        </el-table-column>
        <el-table-column label="处置状态" width="120">
          <template #default="{ row }"><StatusTag :value="row.handleStatus" :dict="handleStatusLabels" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="登记时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="canHandleDefect(row)" text type="primary" @click="openHandle(row)">处置</el-button>
              <el-tooltip
                v-else-if="canHandle && row.handleStatus === 'PENDING'"
                content="当前批次状态无可执行处置方式"
                placement="top"
              >
                <el-button text disabled>处置</el-button>
              </el-tooltip>
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

    <el-dialog v-model="createDialog.visible" title="登记缺陷" width="680px">
      <el-form :model="createForm" label-width="112px">
        <el-form-item label="缺陷载体">
          <el-radio-group v-model="carrierType">
            <el-radio-button value="batch">批次缺陷</el-radio-button>
            <el-radio-button value="process">过程缺陷</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="carrierType === 'batch'" label="所属批次" required>
          <el-select
            v-model="createForm.batchId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchDefectBatches"
            :loading="batchOptionLoading"
            style="width: 100%"
            @visible-change="handleBatchVisible"
          >
            <el-option v-for="batch in defectBatchOptions" :key="batch.id" :label="`${batch.batchNo} · ${batch.materialName}`" :value="batch.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="所属工序" required>
          <el-select
            v-model="createForm.processRecordId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchProcessTargets"
            :loading="processTargetLoading"
            style="width: 100%"
            @visible-change="handleProcessTargetVisible"
          >
            <el-option
              v-for="record in processRecordOptions"
              :key="record.id"
              :label="processTargetLabel(record)"
              :value="record.id"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="关联检验单">
            <el-select
              v-model="createForm.inspectionTaskId"
              clearable
              filterable
              remote
              reserve-keyword
              :disabled="inspectionTaskSelectDisabled"
              :remote-method="searchInspectionTasks"
              :loading="inspectionTaskLoading"
              style="width: 100%"
              @visible-change="handleInspectionTaskVisible"
            >
              <el-option
                v-for="task in filteredInspectionTasks"
                :key="task.id"
                :label="`${task.taskNo} · ${inspectTypeLabels[task.inspectType]} · ${task.batchNo || task.processName || '过程检验'}`"
                :value="task.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="缺陷类型" required>
            <el-select v-model="createForm.defectType" style="width: 100%">
              <el-option v-for="(label, value) in defectTypeLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="严重度" required>
            <el-select v-model="createForm.severity" style="width: 100%">
              <el-option v-for="(label, value) in severityLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="缺陷数量" required><el-input-number v-model="createForm.quantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
          <el-form-item label="缺陷描述" class="span-2" required><el-input v-model.trim="createForm.description" type="textarea" :rows="3" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="createDialog.visible = false">取消</el-button><el-button type="primary" :loading="creating" @click="submitCreate">登记</el-button></template>
    </el-dialog>

    <el-dialog v-model="handleDialog.visible" title="缺陷处置" width="460px">
      <el-form :model="handleForm" label-width="100px">
        <el-form-item label="缺陷单号">{{ handleDialog.row?.defectNo }}</el-form-item>
        <el-form-item label="处置方式" required>
          <el-select v-model="handleForm.handleMethod" style="width: 100%">
            <el-option v-for="option in handleMethodOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="handleDialog.visible = false">取消</el-button><el-button type="primary" :loading="handling" @click="submitHandle">确认处置</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { batchApi, defectApi, inspectionTaskApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { batchStatusLabels, defectTypeLabels, handleMethodLabels, handleStatusLabels, inspectTypeLabels, pageSizeOptions, severityLabels } from '../utils/dicts'
import { ensurePositive, ensureRequired } from '../utils/formGuards'

const auth = useAuthStore()
const canCreate = computed(() => auth.hasAnyRole(['INSPECTOR', 'QUALITY_MANAGER', 'ADMIN']))
const canHandle = computed(() => auth.hasAnyRole(['QUALITY_MANAGER', 'ADMIN']))
const loading = ref(false)
const carrierType = ref('batch')
const page = reactive({ records: [], total: 0 })
const query = reactive({ current: 1, size: 10, defectType: '', handleStatus: '', keyword: '' })
const batches = ref([])
const processRecords = ref([])
const inspectionTasks = ref([])
const batchOptionLoading = ref(false)
const processTargetLoading = ref(false)
const inspectionTaskLoading = ref(false)
const creating = ref(false)
const handling = ref(false)
const createDialog = reactive({ visible: false })
const handleDialog = reactive({ visible: false, row: null })
const createForm = reactive({
  batchId: null,
  processRecordId: null,
  inspectionTaskId: null,
  defectType: 'OTHER',
  severity: 'MINOR',
  quantity: 1,
  description: ''
})
const handleForm = reactive({ handleMethod: 'REWORK' })

const defectBatchOptions = computed(() =>
  batches.value
)
const processRecordOptions = computed(() => processRecords.value)
const inspectionTaskSelectDisabled = computed(() =>
  carrierType.value === 'batch'
    ? !createForm.batchId
    : !createForm.processRecordId
)
const filteredInspectionTasks = computed(() =>
  inspectionTasks.value.filter((task) => {
    if (carrierType.value === 'batch') {
      if (task.inspectType === 'IPQC') return false
      return !createForm.batchId || String(task.batchId) === String(createForm.batchId)
    }
    if (task.inspectType !== 'IPQC') return false
    return !createForm.processRecordId || String(task.processRecordId) === String(createForm.processRecordId)
  })
)
const handleMethodOptions = computed(() => {
  const entries = Object.entries(handleMethodLabels).map(([value, label]) => ({ value, label }))
  const allowed = allowedHandleMethods(handleDialog.row)
  return entries.filter((option) => allowed.includes(option.value))
})

watch(carrierType, () => {
  createForm.batchId = null
  createForm.processRecordId = null
  createForm.inspectionTaskId = null
  inspectionTasks.value = []
})

watch(
  () => [createForm.batchId, createForm.processRecordId],
  () => {
    createForm.inspectionTaskId = null
    inspectionTasks.value = []
    if (!inspectionTaskSelectDisabled.value) {
      searchInspectionTasks('')
    }
  }
)

onMounted(async () => {
  await Promise.all([loadOptions(), load()])
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await defectApi.page(query))
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [processTargetPage] = await Promise.all([
    defectApi.processTargets({ current: 1, size: 20 }),
    searchDefectBatches('')
  ])
  processRecords.value = processTargetPage.records || []
}

function openCreate() {
  createDialog.visible = true
  carrierType.value = 'batch'
  Object.assign(createForm, {
    batchId: null,
    processRecordId: null,
    inspectionTaskId: null,
    defectType: 'OTHER',
    severity: 'MINOR',
    quantity: 1,
    description: ''
  })
}

async function handleBatchVisible(visible) {
  if (visible && batches.value.length === 0) {
    await searchDefectBatches('')
  }
}

async function searchDefectBatches(keyword) {
  const selectableStatuses = ['QUALIFIED', 'FROZEN', 'DEPLETED', 'RECALLED', 'RETURNED', 'SCRAPPED']
  batchOptionLoading.value = true
  try {
    const pages = await Promise.all(selectableStatuses.map((status) =>
      batchApi.page({ current: 1, size: 20, status, keyword })
    ))
    batches.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    batchOptionLoading.value = false
  }
}

async function handleProcessTargetVisible(visible) {
  if (visible && carrierType.value === 'process' && processRecords.value.length === 0) {
    await searchProcessTargets('')
  }
}

async function searchProcessTargets(keyword) {
  processTargetLoading.value = true
  try {
    const page = await defectApi.processTargets({ current: 1, size: 20, keyword })
    processRecords.value = page.records || []
  } finally {
    processTargetLoading.value = false
  }
}

function processTargetLabel(record) {
  return `${record.orderNo} · ${record.materialName} · 步骤${record.stepNo} · ${record.processName}`
}

async function handleInspectionTaskVisible(visible) {
  if (visible && !inspectionTaskSelectDisabled.value) {
    await searchInspectionTasks('')
  }
}

async function searchInspectionTasks(keyword) {
  if (inspectionTaskSelectDisabled.value) {
    inspectionTasks.value = []
    return
  }
  const params = {
    current: 1,
    size: 20,
    keyword
  }
  if (carrierType.value === 'batch') {
    params.batchId = createForm.batchId
  } else {
    params.processRecordId = createForm.processRecordId
    params.inspectType = 'IPQC'
  }
  inspectionTaskLoading.value = true
  try {
    const page = await inspectionTaskApi.page(params)
    inspectionTasks.value = page.records || []
  } finally {
    inspectionTaskLoading.value = false
  }
}

async function submitCreate() {
  if (carrierType.value === 'batch' && !ensureRequired(createForm.batchId, '请选择所属批次')) return
  if (carrierType.value === 'process' && !ensureRequired(createForm.processRecordId, '请选择所属工序')) return
  if (!ensureRequired(createForm.defectType, '请选择缺陷类型')) return
  if (!ensureRequired(createForm.severity, '请选择严重度')) return
  if (!ensurePositive(createForm.quantity, '缺陷数量必须大于 0')) return
  if (!ensureRequired(createForm.description, '请填写缺陷描述')) return

  const payload = { ...createForm }
  if (carrierType.value === 'batch') payload.processRecordId = null
  else payload.batchId = null
  creating.value = true
  try {
    await defectApi.create(payload)
    ElMessage.success('缺陷已登记')
    createDialog.visible = false
    await Promise.all([loadOptions(), load()])
  } finally {
    creating.value = false
  }
}

function openHandle(row) {
  const allowed = allowedHandleMethods(row)
  if (allowed.length === 0) {
    ElMessage.warning('当前批次状态无可执行处置方式')
    return
  }
  handleDialog.visible = true
  handleDialog.row = row
  handleForm.handleMethod = allowed[0]
}

function canHandleDefect(row) {
  return canHandle.value
    && row.handleStatus === 'PENDING'
    && allowedHandleMethods(row).length > 0
}

function allowedHandleMethods(row) {
  if (!row) return Object.keys(handleMethodLabels)
  if (!row.batchId) return ['REWORK', 'CONCESSION']
  if (row.batchStatus === 'FROZEN') {
    return row.batchSourceType === 'PURCHASE'
      ? ['REWORK', 'SCRAP', 'CONCESSION', 'RETURN']
      : ['REWORK', 'SCRAP', 'CONCESSION']
  }
  if (row.batchStatus === 'QUALIFIED') {
    return ['CONCESSION']
  }
  if (row.batchStatus === 'RECALLED') {
    return row.batchSourceType === 'PURCHASE' ? ['SCRAP', 'RETURN'] : ['SCRAP']
  }
  return []
}

async function submitHandle() {
  if (!ensureRequired(handleForm.handleMethod, '请选择处置方式')) return

  handling.value = true
  try {
    await defectApi.handle(handleDialog.row.id, handleForm)
    ElMessage.success('缺陷处置已完成')
    handleDialog.visible = false
    await Promise.all([loadOptions(), load()])
  } finally {
    handling.value = false
  }
}

function mergeById(rows) {
  const map = new Map()
  for (const row of rows) {
    map.set(String(row.id), row)
  }
  return [...map.values()]
}
</script>
