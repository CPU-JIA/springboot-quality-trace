<template>
  <section class="page">
    <PageTitle title="批次台账" subtitle="以批次为追溯基本单元，查看库存余量、来源、检验历史与流转状态。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canInbound" type="primary" :icon="Plus" @click="openInbound">原材料入库</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="批次号 / 物料 / 供应商 / 工单" style="width: 280px" @keyup.enter="load" />
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 150px">
            <el-option v-for="(label, value) in batchStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select v-model="query.sourceType" clearable placeholder="来源" style="width: 140px">
            <el-option v-for="(label, value) in sourceTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-select v-model="query.materialCategory" clearable placeholder="物料类别" style="width: 140px">
            <el-option v-for="(label, value) in materialCategoryLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="batchNo" label="批次号" min-width="170" fixed />
        <el-table-column label="物料" min-width="190">
          <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="类别" width="100">
          <template #default="{ row }"><StatusTag :value="row.materialCategory" :dict="materialCategoryLabels" /></template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }"><StatusTag :value="row.sourceType" :dict="sourceTypeLabels" /></template>
        </el-table-column>
        <el-table-column label="来源对象" min-width="160">
          <template #default="{ row }">{{ row.supplierName || row.productionOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="初始数量" width="110" />
        <el-table-column prop="remainingQuantity" label="剩余数量" width="110" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag :value="row.status" :dict="batchStatusLabels" /></template>
        </el-table-column>
        <el-table-column prop="warehouseLocation" label="库位" width="120" />
        <el-table-column prop="productionDate" label="生产/入库日期" width="130" />
        <el-table-column prop="createdAt" label="登记时间" width="180" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="inboundDialog.visible" title="原材料入库" width="620px">
      <el-form :model="inboundForm" label-width="110px">
        <el-form-item label="原材料" required>
          <el-select
            v-model="inboundForm.materialId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchRawMaterials"
            :loading="materialOptionLoading"
            style="width: 100%"
            @visible-change="handleMaterialVisible"
          >
            <el-option v-for="item in rawMaterials" :key="item.id" :label="`${item.materialCode} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select
            v-model="inboundForm.supplierId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchSuppliers"
            :loading="supplierOptionLoading"
            style="width: 100%"
            @visible-change="handleSupplierVisible"
          >
            <el-option v-for="item in suppliers" :key="item.id" :label="`${item.supplierCode} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="入库数量" required><el-input-number v-model="inboundForm.quantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
          <el-form-item label="入库日期" required><el-date-picker v-model="inboundForm.productionDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="库位"><el-input v-model.trim="inboundForm.warehouseLocation" placeholder="如 A-01-03" /></el-form-item>
          <el-form-item label="备注"><el-input v-model.trim="inboundForm.remark" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="inboundSaving" @click="submitInbound">确认入库</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer.visible" title="批次详情" size="48%">
      <template v-if="detail">
        <div class="drawer-section">
          <h3 class="drawer-title">批次信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="批次号">{{ detail.overview?.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusTag :value="detail.overview?.status" :dict="batchStatusLabels" /></el-descriptions-item>
            <el-descriptions-item label="物料">{{ detail.overview?.materialCode }} · {{ detail.overview?.materialName }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ materialCategoryLabels[detail.overview?.materialCategory] }}</el-descriptions-item>
            <el-descriptions-item label="初始数量">{{ detail.overview?.quantity }}</el-descriptions-item>
            <el-descriptions-item label="剩余数量">{{ detail.overview?.remainingQuantity }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detail.overview?.supplierName || detail.overview?.productionOrderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="库位">{{ detail.overview?.warehouseLocation || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">检验任务历史</h3>
          <el-table :data="detail.inspectionTasks || []" stripe>
            <el-table-column prop="taskNo" label="检验单号" min-width="160" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }"><StatusTag :value="row.inspectType" :dict="inspectTypeLabels" /></template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><StatusTag :value="row.status" :dict="taskStatusLabels" /></template>
            </el-table-column>
            <el-table-column label="结论" width="110">
              <template #default="{ row }"><StatusTag :value="row.conclusion" :dict="conclusionLabels" /></template>
            </el-table-column>
            <el-table-column prop="completedAt" label="完成时间" min-width="170" />
          </el-table>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">缺陷记录</h3>
          <el-table :data="detail.defects || []" stripe>
            <el-table-column prop="defectNo" label="缺陷单号" min-width="160" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }"><StatusTag :value="row.defectType" :dict="defectTypeLabels" /></template>
            </el-table-column>
            <el-table-column label="严重度" width="100">
              <template #default="{ row }"><StatusTag :value="row.severity" :dict="severityLabels" /></template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="90" />
            <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="处置" width="110">
              <template #default="{ row }"><StatusTag :value="row.handleStatus" :dict="handleStatusLabels" /></template>
            </el-table-column>
          </el-table>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">生产消耗去向</h3>
          <el-table :data="detail.consumptions || []" stripe>
            <el-table-column prop="productionOrderNo" label="消耗方工单" min-width="170" />
            <el-table-column prop="quantity" label="消耗数量" width="110" />
            <el-table-column prop="createdAt" label="领料时间" min-width="170" />
          </el-table>
        </div>

        <div class="drawer-section">
          <h3 class="drawer-title">出货流向</h3>
          <el-table :data="detail.shipments || []" stripe>
            <el-table-column prop="shipmentNo" label="出货单号" min-width="160" />
            <el-table-column prop="customerName" label="客户" min-width="160" />
            <el-table-column prop="quantity" label="数量" width="100" />
            <el-table-column prop="shipDate" label="出货日期" width="120" />
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { batchApi, materialApi, supplierApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { ensureNotFuture, ensurePositive, ensureRequired, todayString } from '../utils/formGuards'
import {
  batchStatusLabels,
  conclusionLabels,
  defectTypeLabels,
  handleStatusLabels,
  inspectTypeLabels,
  materialCategoryLabels,
  pageSizeOptions,
  severityLabels,
  sourceTypeLabels,
  taskStatusLabels
} from '../utils/dicts'

const auth = useAuthStore()
const canInbound = computed(() => auth.hasAnyRole(['WAREHOUSE', 'ADMIN']))
const loading = ref(false)
const page = reactive({ records: [], total: 0 })
const query = reactive({ current: 1, size: 10, keyword: '', status: '', sourceType: '', materialCategory: '' })
const materials = ref([])
const suppliers = ref([])
const materialOptionLoading = ref(false)
const supplierOptionLoading = ref(false)
const detail = ref(null)
const inboundSaving = ref(false)
const detailDrawer = reactive({ visible: false })
const inboundDialog = reactive({ visible: false })
const inboundForm = reactive({
  materialId: null,
  supplierId: null,
  quantity: 1,
  productionDate: todayString(),
  warehouseLocation: '',
  remark: ''
})

const rawMaterials = computed(() => materials.value.filter((item) => item.category === 'RAW' && item.status === 1))

onMounted(async () => {
  await Promise.all([searchRawMaterials(''), searchSuppliers(''), load()])
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await batchApi.page(query))
  } finally {
    loading.value = false
  }
}

async function handleMaterialVisible(visible) {
  if (visible && materials.value.length === 0) {
    await searchRawMaterials('')
  }
}

async function searchRawMaterials(keyword) {
  materialOptionLoading.value = true
  try {
    const page = await materialApi.page({
      current: 1,
      size: 20,
      category: 'RAW',
      status: 1,
      keyword
    })
    materials.value = page.records || []
  } finally {
    materialOptionLoading.value = false
  }
}

async function handleSupplierVisible(visible) {
  if (visible && suppliers.value.length === 0) {
    await searchSuppliers('')
  }
}

async function searchSuppliers(keyword) {
  supplierOptionLoading.value = true
  try {
    const page = await supplierApi.page({
      current: 1,
      size: 20,
      status: 1,
      keyword
    })
    suppliers.value = page.records || []
  } finally {
    supplierOptionLoading.value = false
  }
}

function openInbound() {
  inboundDialog.visible = true
  searchRawMaterials('')
  searchSuppliers('')
  Object.assign(inboundForm, {
    materialId: null,
    supplierId: null,
    quantity: 1,
    productionDate: todayString(),
    warehouseLocation: '',
    remark: ''
  })
}

async function submitInbound() {
  if (!ensureRequired(inboundForm.materialId, '请选择原材料')) return
  if (!ensureRequired(inboundForm.supplierId, '请选择供应商')) return
  if (!ensurePositive(inboundForm.quantity, '入库数量必须大于 0')) return
  if (!ensureRequired(inboundForm.productionDate, '请选择入库日期')) return
  if (!ensureNotFuture(inboundForm.productionDate, '入库日期不能晚于今天')) return

  inboundSaving.value = true
  try {
    await batchApi.purchaseInbound(inboundForm)
    ElMessage.success('入库成功，IQC 检验任务已自动生成')
    inboundDialog.visible = false
    await Promise.all([searchRawMaterials(''), searchSuppliers(''), load()])
  } finally {
    inboundSaving.value = false
  }
}

async function openDetail(row) {
  detail.value = await batchApi.detail(row.id)
  detailDrawer.visible = true
}
</script>
