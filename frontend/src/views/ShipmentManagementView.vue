<template>
  <section class="page">
    <PageTitle title="出货管理" subtitle="登记成品批次出货流向，作为正向追溯和召回明细的数据来源。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canShip" type="primary" :icon="Plus" @click="openCreate">成品出货</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="出货单号 / 批次号 / 客户" style="width: 260px" @keyup.enter="load" />
          <el-select
            v-model="query.batchId"
            clearable
            filterable
            remote
            reserve-keyword
            placeholder="出货批次"
            :remote-method="searchProductBatches"
            :loading="productBatchLoading"
            style="width: 240px"
            @visible-change="handleProductBatchVisible"
          >
            <el-option v-for="batch in productBatches" :key="batch.id" :label="`${batch.batchNo} · 余量${batch.remainingQuantity}`" :value="batch.id" />
          </el-select>
          <el-select
            v-model="query.customerId"
            clearable
            filterable
            remote
            reserve-keyword
            placeholder="客户"
            :remote-method="searchCustomers"
            :loading="customerLoading"
            style="width: 220px"
            @visible-change="handleCustomerVisible"
          >
            <el-option v-for="customer in customers" :key="customer.id" :label="`${customer.customerCode} · ${customer.name}`" :value="customer.id" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="shipmentNo" label="出货单号" min-width="170" fixed />
        <el-table-column prop="batchNo" label="出货批次" min-width="170" />
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="110" />
        <el-table-column prop="shipDate" label="出货日期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="登记时间" width="180" />
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

    <el-dialog v-model="createDialog.visible" title="成品出货" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="成品批次" required>
          <el-select
            v-model="form.batchId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchProductBatches"
            :loading="productBatchLoading"
            style="width: 100%"
            @visible-change="handleProductBatchVisible"
          >
            <el-option
              v-for="batch in productBatches"
              :key="batch.id"
              :label="`${batch.batchNo} · ${batch.materialName} · 余量${batch.remainingQuantity}`"
              :value="batch.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select
            v-model="form.customerId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchCustomers"
            :loading="customerLoading"
            style="width: 100%"
            @visible-change="handleCustomerVisible"
          >
            <el-option v-for="customer in customers" :key="customer.id" :label="`${customer.customerCode} · ${customer.name}`" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出货数量" required><el-input-number v-model="form.quantity" :min="0.001" :step="1" style="width: 100%" /></el-form-item>
        <el-form-item label="出货日期" required><el-date-picker v-model="form.shipDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createDialog.visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">确认出货</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import { batchApi, customerApi, shipmentApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { pageSizeOptions } from '../utils/dicts'
import { ensureDateNotBefore, ensureNotFuture, ensurePositive, ensureRequired, todayString } from '../utils/formGuards'

const auth = useAuthStore()
const canShip = computed(() => auth.hasAnyRole(['WAREHOUSE', 'ADMIN']))
const loading = ref(false)
const page = reactive({ records: [], total: 0 })
const query = reactive({ current: 1, size: 10, batchId: '', customerId: '', keyword: '' })
const customers = ref([])
const productBatches = ref([])
const customerLoading = ref(false)
const productBatchLoading = ref(false)
const saving = ref(false)
const createDialog = reactive({ visible: false })
const form = reactive({
  batchId: null,
  customerId: null,
  quantity: 1,
  shipDate: todayString(),
  remark: ''
})

onMounted(async () => {
  await Promise.all([searchProductBatches(''), searchCustomers(''), load()])
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await shipmentApi.page(query))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createDialog.visible = true
  searchProductBatches('')
  searchCustomers('')
  Object.assign(form, {
    batchId: null,
    customerId: null,
    quantity: 1,
    shipDate: todayString(),
    remark: ''
  })
}

async function handleProductBatchVisible(visible) {
  if (visible && productBatches.value.length === 0) {
    await searchProductBatches('')
  }
}

async function searchProductBatches(keyword) {
  productBatchLoading.value = true
  try {
    const page = await batchApi.page({
      current: 1,
      size: 20,
      status: 'QUALIFIED',
      materialCategory: 'PRODUCT',
      keyword
    })
    productBatches.value = page.records || []
  } finally {
    productBatchLoading.value = false
  }
}

async function handleCustomerVisible(visible) {
  if (visible && customers.value.length === 0) {
    await searchCustomers('')
  }
}

async function searchCustomers(keyword) {
  customerLoading.value = true
  try {
    const page = await customerApi.page({ current: 1, size: 20, status: 1, keyword })
    customers.value = page.records || []
  } finally {
    customerLoading.value = false
  }
}

async function submitCreate() {
  if (!ensureRequired(form.batchId, '请选择成品批次')) return
  if (!ensureRequired(form.customerId, '请选择客户')) return
  if (!ensurePositive(form.quantity, '出货数量必须大于 0')) return
  if (!ensureRequired(form.shipDate, '请选择出货日期')) return
  if (!ensureNotFuture(form.shipDate, '出货日期不能晚于今天')) return
  const batch = productBatches.value.find((item) => item.id === form.batchId)
  if (!ensureDateNotBefore(form.shipDate, batch?.productionDate, '出货日期不能早于批次生产日期')) return

  saving.value = true
  try {
    await shipmentApi.create(form)
    ElMessage.success('出货已登记，批次余量已扣减')
    createDialog.visible = false
    await Promise.all([searchProductBatches(''), load()])
  } finally {
    saving.value = false
  }
}
</script>
