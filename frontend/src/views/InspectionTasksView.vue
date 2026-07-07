<template>
  <section class="page">
    <PageTitle title="检验任务" subtitle="统一处理 IQC、IPQC、FQC 检验任务，按检验标准逐项录入结果。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="检验单号" style="width: 220px" @keyup.enter="load" />
          <el-select v-model="query.status" clearable placeholder="任务状态" style="width: 140px">
            <el-option v-for="(label, value) in taskStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select v-model="query.inspectType" clearable placeholder="检验类型" style="width: 150px">
            <el-option v-for="(label, value) in inspectTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="taskNo" label="检验单号" min-width="170" fixed />
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><StatusTag :value="row.inspectType" :dict="inspectTypeLabels" /></template>
        </el-table-column>
        <el-table-column label="受检对象" min-width="180">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else>工序 {{ row.stepNo }} · {{ row.processName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :dict="taskStatusLabels" /></template>
        </el-table-column>
        <el-table-column label="结论" width="110">
          <template #default="{ row }"><StatusTag :value="row.conclusion" :dict="conclusionLabels" /></template>
        </el-table-column>
        <el-table-column prop="assignedAt" label="领取时间" width="180" />
        <el-table-column prop="completedAt" label="完成时间" width="180" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="canInspect && row.status === 'PENDING'" text type="primary" @click="claim(row)">领取</el-button>
              <el-button v-if="canExecuteTask(row)" text type="success" @click="openExecute(row)">执行</el-button>
              <el-button text :icon="View" @click="openRecords(row)">明细</el-button>
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

    <el-drawer v-model="executeDrawer.visible" title="执行检验" size="62%">
      <template v-if="executeDrawer.task">
        <div class="drawer-section">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="检验单号">{{ executeDrawer.task.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="检验类型">{{ inspectTypeLabels[executeDrawer.task.inspectType] }}</el-descriptions-item>
            <el-descriptions-item label="受检批次">{{ executeDrawer.task.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工序">{{ executeDrawer.task.processName || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-form :model="submitForm" label-width="100px">
          <div class="inspection-list">
            <div v-for="record in submitForm.records" :key="record.inspectionItemId" class="inspection-row">
              <div class="inspection-main">
                <strong>{{ record.item.itemCode }} · {{ record.item.itemName }}</strong>
                <span>{{ record.item.standardDesc }}</span>
                <small v-if="record.item.isQuantitative === 1">
                  限值：{{ record.item.lowerLimit ?? '-∞' }} ~ {{ record.item.upperLimit ?? '+∞' }} {{ record.item.unit || '' }}
                </small>
              </div>
              <div class="inspection-input">
                <template v-if="record.item.isQuantitative === 1">
                  <el-input-number v-model="record.measuredValue" :step="0.1" style="width: 170px" />
                  <el-tag :type="predictPass(record) ? 'success' : 'danger'" effect="light">
                    {{ predictPass(record) ? '预计通过' : '预计不通过' }}
                  </el-tag>
                </template>
                <template v-else>
                  <el-radio-group v-model="record.isPass">
                    <el-radio-button :value="1">通过</el-radio-button>
                    <el-radio-button :value="0">不通过</el-radio-button>
                  </el-radio-group>
                </template>
                <el-input v-model.trim="record.resultDesc" placeholder="结果描述" />
              </div>
            </div>
          </div>

          <el-form-item label="整单结论" required style="margin-top: 16px">
            <el-select v-model="submitForm.conclusion" style="width: 220px">
              <el-option v-for="(label, value) in conclusionLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="检验备注">
            <el-input v-model.trim="submitForm.remark" type="textarea" :rows="3" />
          </el-form-item>

          <div v-if="submitForm.conclusion === 'CONCESSION'" class="concession-box">
            <h3 class="drawer-title">让步接收缺陷留痕</h3>
            <div class="form-grid">
              <el-form-item label="缺陷类型" required>
                <el-select v-model="submitForm.concessionDefect.defectType" style="width: 100%">
                  <el-option v-for="(label, value) in defectTypeLabels" :key="value" :label="label" :value="value" />
                </el-select>
              </el-form-item>
              <el-form-item label="缺陷数量" required><el-input-number v-model="submitForm.concessionDefect.quantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
              <el-form-item label="缺陷描述" class="span-2" required><el-input v-model.trim="submitForm.concessionDefect.description" /></el-form-item>
            </div>
          </div>

          <div v-if="submitForm.conclusion === 'UNQUALIFIED'" class="unqualified-box">
            <h3 class="drawer-title">不合格缺陷留痕</h3>
            <div class="form-grid">
              <el-form-item label="缺陷类型" required>
                <el-select v-model="submitForm.unqualifiedDefect.defectType" style="width: 100%">
                  <el-option v-for="(label, value) in defectTypeLabels" :key="value" :label="label" :value="value" />
                </el-select>
              </el-form-item>
              <el-form-item label="严重度" required>
                <el-select v-model="submitForm.unqualifiedDefect.severity" style="width: 100%">
                  <el-option v-for="(label, value) in severityLabels" :key="value" :label="label" :value="value" />
                </el-select>
              </el-form-item>
              <el-form-item label="缺陷数量" required><el-input-number v-model="submitForm.unqualifiedDefect.quantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
              <el-form-item label="缺陷描述" class="span-2" required><el-input v-model.trim="submitForm.unqualifiedDefect.description" /></el-form-item>
            </div>
          </div>
        </el-form>

        <div class="drawer-actions">
          <el-button @click="executeDrawer.visible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitInspection">提交检验结论</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="recordsDrawer.visible" title="检验明细" size="48%">
      <el-table :data="recordsDrawer.records" stripe>
        <el-table-column prop="itemCode" label="项目编码" width="120" />
        <el-table-column prop="itemName" label="项目名称" min-width="160" />
        <el-table-column prop="measuredValue" label="实测值" width="110" />
        <el-table-column prop="resultDesc" label="结果描述" min-width="180" />
        <el-table-column label="判定" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPass === 1 ? 'success' : 'danger'" size="small">{{ row.isPass === 1 ? '通过' : '不通过' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inspectedAt" label="检验时间" width="180" />
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { inspectionTaskApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { conclusionLabels, defectTypeLabels, inspectTypeLabels, pageSizeOptions, severityLabels, taskStatusLabels } from '../utils/dicts'
import { ensurePositive, ensureRequired, warn } from '../utils/formGuards'

const auth = useAuthStore()
const canInspect = computed(() => auth.hasAnyRole(['INSPECTOR', 'ADMIN']))
const loading = ref(false)
const query = reactive({ current: 1, size: 10, status: '', inspectType: '', keyword: '' })
const page = reactive({ records: [], total: 0 })
const executeDrawer = reactive({ visible: false, task: null })
const recordsDrawer = reactive({ visible: false, records: [] })
const submitting = ref(false)
const submitForm = reactive({
  conclusion: 'QUALIFIED',
  remark: '',
  records: [],
  concessionDefect: { defectType: 'OTHER', quantity: 1, description: '' },
  unqualifiedDefect: { defectType: 'OTHER', severity: 'MAJOR', quantity: 1, description: '' }
})
const currentUserId = computed(() => auth.user?.userId)

onMounted(load)

async function load() {
  loading.value = true
  try {
    Object.assign(page, await inspectionTaskApi.page(query))
  } finally {
    loading.value = false
  }
}

async function claim(row) {
  await inspectionTaskApi.claim(row.id)
  ElMessage.success('检验任务已领取')
  await load()
}

function canExecuteTask(row) {
  return canInspect.value
    && row.status === 'IN_PROGRESS'
    && String(row.inspectorId || '') === String(currentUserId.value || '')
}

async function openExecute(row) {
  const items = await inspectionTaskApi.items(row.id)
  executeDrawer.task = row
  submitForm.conclusion = 'QUALIFIED'
  submitForm.remark = ''
  submitForm.records = items.map((item) => ({
    item,
    inspectionItemId: item.id,
    measuredValue: null,
    resultDesc: '',
    isPass: item.isQuantitative === 1 ? null : 1
  }))
  submitForm.concessionDefect = { defectType: 'OTHER', quantity: 1, description: '' }
  submitForm.unqualifiedDefect = { defectType: 'OTHER', severity: 'MAJOR', quantity: 1, description: '' }
  executeDrawer.visible = true
}

function predictPass(record) {
  if (record.measuredValue === null || record.measuredValue === undefined) return false
  const value = Number(record.measuredValue)
  const lowerOk = record.item.lowerLimit === null || record.item.lowerLimit === undefined || value >= Number(record.item.lowerLimit)
  const upperOk = record.item.upperLimit === null || record.item.upperLimit === undefined || value <= Number(record.item.upperLimit)
  return lowerOk && upperOk
}

async function submitInspection() {
  if (!ensureRequired(submitForm.conclusion, '请选择整单结论')) return
  if (!submitForm.records.length) {
    warn('该任务没有可提交的检验明细')
    return
  }

  let hasFailedItem = false
  for (const [index, record] of submitForm.records.entries()) {
    if (record.item.isQuantitative === 1) {
      if (!ensureRequired(record.measuredValue, `第 ${index + 1} 项请填写实测值`)) return
      if (!predictPass(record)) hasFailedItem = true
      continue
    }
    if (!ensureRequired(record.isPass, `第 ${index + 1} 项请选择单项判定`)) return
    if (Number(record.isPass) === 0) hasFailedItem = true
  }

  if (submitForm.conclusion === 'QUALIFIED' && hasFailedItem) {
    warn('存在不合格明细时，整单结论不能为合格')
    return
  }
  if ((submitForm.conclusion === 'UNQUALIFIED' || submitForm.conclusion === 'CONCESSION') && !hasFailedItem) {
    warn('不合格或让步接收结论至少需要一项检验明细不通过')
    return
  }
  if (submitForm.conclusion === 'CONCESSION') {
    if (!ensureRequired(submitForm.concessionDefect.defectType, '请选择让步缺陷类型')) return
    if (!ensurePositive(submitForm.concessionDefect.quantity, '让步缺陷数量必须大于 0')) return
    if (!ensureRequired(submitForm.concessionDefect.description, '请填写让步缺陷描述')) return
  }
  if (submitForm.conclusion === 'UNQUALIFIED') {
    if (!ensureRequired(submitForm.unqualifiedDefect.defectType, '请选择不合格缺陷类型')) return
    if (!ensureRequired(submitForm.unqualifiedDefect.severity, '请选择不合格缺陷严重度')) return
    if (!ensurePositive(submitForm.unqualifiedDefect.quantity, '不合格缺陷数量必须大于 0')) return
    if (!ensureRequired(submitForm.unqualifiedDefect.description, '请填写不合格缺陷描述')) return
  }

  const payload = {
    conclusion: submitForm.conclusion,
    remark: submitForm.remark,
    records: submitForm.records.map((record) => ({
      inspectionItemId: record.inspectionItemId,
      measuredValue: record.measuredValue,
      resultDesc: record.resultDesc,
      isPass: record.item.isQuantitative === 1 ? null : record.isPass
    })),
    concessionDefect: submitForm.conclusion === 'CONCESSION' ? submitForm.concessionDefect : null,
    unqualifiedDefect: submitForm.conclusion === 'UNQUALIFIED' ? submitForm.unqualifiedDefect : null
  }
  submitting.value = true
  try {
    await inspectionTaskApi.submit(executeDrawer.task.id, payload)
    ElMessage.success('检验结果已提交，状态联动已完成')
    executeDrawer.visible = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function openRecords(row) {
  recordsDrawer.records = await inspectionTaskApi.records(row.id)
  recordsDrawer.visible = true
}
</script>

<style scoped>
.inspection-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.inspection-row {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(300px, 1.1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: var(--app-panel-soft);
}

.inspection-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.inspection-main span,
.inspection-main small {
  color: var(--app-muted);
}

.inspection-input {
  display: grid;
  grid-template-columns: auto auto minmax(160px, 1fr);
  align-items: center;
  gap: 8px;
}

.concession-box {
  padding: 14px;
  border: 1px solid #dfc6a8;
  border-radius: var(--app-radius);
  background: #fff9f1;
}

.unqualified-box {
  padding: 14px;
  border: 1px solid #e4b5b5;
  border-radius: var(--app-radius);
  background: #fff5f5;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 900px) {
  .inspection-row,
  .inspection-input {
    grid-template-columns: 1fr;
  }
}
</style>
