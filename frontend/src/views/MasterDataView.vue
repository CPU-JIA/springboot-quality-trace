<template>
  <section class="page">
    <PageTitle title="主数据维护" subtitle="集中维护物料、往来单位、工序路线、BOM 与检验标准。">
      <template #actions>
        <el-button :icon="Refresh" @click="refreshActive">刷新当前页</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <el-tabs v-model="activeTab" @tab-change="refreshActive">
        <el-tab-pane label="物料" name="materials">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="materialQuery.keyword" clearable placeholder="编码 / 名称" style="width: 220px" />
              <el-select v-model="materialQuery.category" clearable placeholder="类别" style="width: 150px">
                <el-option v-for="(label, value) in materialCategoryLabels" :key="value" :label="label" :value="value" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="loadMaterials">查询</el-button>
            </div>
            <el-button type="primary" :icon="Plus" @click="openMaterial()">新增物料</el-button>
          </div>
          <el-table :data="materialPage.records" stripe style="margin-top: 14px">
            <el-table-column prop="materialCode" label="编码" width="130" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="类别" width="110">
              <template #default="{ row }"><StatusTag :value="row.category" :dict="materialCategoryLabels" /></template>
            </el-table-column>
            <el-table-column prop="spec" label="规格" min-width="160" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="shelfLifeDays" label="保质期(天)" width="110" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" :icon="Edit" @click="openMaterial(row)">编辑</el-button>
                  <el-button text :type="row.status === 1 ? 'warning' : 'success'" @click="toggleMaterial(row)">
                    {{ row.status === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-button text type="danger" :icon="Delete" @click="removeMaterial(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <Pager :page="materialPage" :query="materialQuery" @change="loadMaterials" />
        </el-tab-pane>

        <el-tab-pane label="供应商" name="suppliers">
          <SimpleCrud
            title-code="supplierCode"
            :page="supplierPage"
            :query="supplierQuery"
            :columns="partyColumns"
            code-label="供应商编码"
            name-label="供应商名称"
            @query="loadSuppliers"
            @create="openParty('supplier')"
            @edit="openParty('supplier', $event)"
            @remove="removeParty('supplier', $event)"
          />
        </el-tab-pane>

        <el-tab-pane label="客户" name="customers">
          <SimpleCrud
            title-code="customerCode"
            :page="customerPage"
            :query="customerQuery"
            :columns="partyColumns"
            code-label="客户编码"
            name-label="客户名称"
            @query="loadCustomers"
            @create="openParty('customer')"
            @edit="openParty('customer', $event)"
            @remove="removeParty('customer', $event)"
          />
        </el-tab-pane>

        <el-tab-pane label="工序与路线" name="processes">
          <div class="split-grid">
            <div>
              <div class="toolbar">
                <div class="toolbar-left">
                  <el-input v-model="processQuery.keyword" clearable placeholder="工序编码 / 名称" style="width: 240px" />
                  <el-button type="primary" :icon="Search" @click="loadProcesses">查询</el-button>
                </div>
                <el-button type="primary" :icon="Plus" @click="openProcess()">新增工序</el-button>
              </div>
              <el-table :data="processPage.records" stripe style="margin-top: 14px">
                <el-table-column prop="processCode" label="编码" width="120" />
                <el-table-column prop="processName" label="工序名称" min-width="140" />
                <el-table-column label="IPQC点" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.needIpqc === 1 ? 'warning' : 'info'" size="small">{{ yesNo(row.needIpqc) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
                <el-table-column label="操作" width="150">
                  <template #default="{ row }">
                    <div class="table-actions">
                      <el-button text type="primary" :icon="Edit" @click="openProcess(row)">编辑</el-button>
                      <el-button text type="danger" :icon="Delete" @click="removeProcess(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <Pager :page="processPage" :query="processQuery" @change="loadProcesses" />
            </div>

            <div class="route-editor">
              <div class="drawer-title">工艺路线编辑</div>
              <el-select
                v-model="routeMaterialId"
                filterable
                remote
                reserve-keyword
                placeholder="选择半成品/成品物料"
                :remote-method="searchRouteMaterials"
                :loading="materialOptionLoading.route"
                style="width: 100%"
                @change="loadRoute"
                @visible-change="handleRouteMaterialVisible"
              >
                <el-option
                  v-for="item in routeMaterialOptions"
                  :key="item.id"
                  :label="`${item.materialCode} · ${item.name}`"
                  :value="item.id"
                />
              </el-select>
              <div v-if="routeMaterialId" class="route-steps">
                <div v-for="(step, index) in routeSteps" :key="index" class="route-step-row">
                  <span class="step-no">{{ index + 1 }}</span>
                  <el-select v-model="step.processDefId" filterable placeholder="选择工序">
                    <el-option
                      v-for="process in processOptions"
                      :key="process.id"
                      :label="`${process.processCode} · ${process.processName}`"
                      :value="process.id"
                    />
                  </el-select>
                  <el-button :icon="Delete" text type="danger" @click="routeSteps.splice(index, 1)" />
                </div>
                <div class="toolbar" style="margin-top: 12px">
                  <el-button :icon="Plus" @click="routeSteps.push({ processDefId: null })">添加工序</el-button>
                  <el-button type="primary" :loading="routeSaving" @click="saveRoute">保存路线</el-button>
                </div>
              </div>
              <div v-else class="empty-hint">选择物料后维护它的生产工艺路线。</div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="BOM" name="boms">
          <div class="split-grid">
            <div>
              <div class="toolbar">
                <div class="toolbar-left">
                  <el-select
                    v-model="bomParentId"
                    clearable
                    filterable
                    remote
                    reserve-keyword
                    placeholder="按父项物料筛选"
                    :remote-method="searchBomParents"
                    :loading="materialOptionLoading.bomParent"
                    style="width: 260px"
                    @change="loadBoms"
                    @visible-change="handleBomParentVisible"
                  >
                    <el-option
                      v-for="item in bomParentOptions"
                      :key="item.id"
                      :label="`${item.materialCode} · ${item.name}`"
                      :value="item.id"
                    />
                  </el-select>
                </div>
                <el-button type="primary" :icon="Plus" @click="openBom">新增BOM行</el-button>
              </div>
              <el-table :data="bomRows" stripe style="margin-top: 14px">
                <el-table-column label="父项" min-width="190">
                  <template #default="{ row }">{{ row.parentMaterialCode }} · {{ row.parentMaterialName }}</template>
                </el-table-column>
                <el-table-column label="子项" min-width="190">
                  <template #default="{ row }">{{ row.childMaterialCode }} · {{ row.childMaterialName }}</template>
                </el-table-column>
                <el-table-column label="子项类别" width="110">
                  <template #default="{ row }"><StatusTag :value="row.childCategory" :dict="materialCategoryLabels" /></template>
                </el-table-column>
                <el-table-column prop="quantity" label="单位用量" width="110" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <div class="table-actions">
                      <el-button text type="danger" :icon="Delete" @click="removeBom(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div>
              <div class="drawer-title">BOM 构成树</div>
              <el-select
                v-model="bomTreeMaterialId"
                filterable
                remote
                reserve-keyword
                placeholder="选择根物料"
                :remote-method="searchBomTreeMaterials"
                :loading="materialOptionLoading.bomTree"
                style="width: 100%"
                @change="loadBomTree"
                @visible-change="handleBomTreeVisible"
              >
                <el-option
                  v-for="item in bomTreeMaterialOptions"
                  :key="item.id"
                  :label="`${item.materialCode} · ${item.name}`"
                  :value="item.id"
                />
              </el-select>
              <el-tree
                v-if="bomTree"
                class="bom-tree"
                :data="[bomTree]"
                :props="{ children: 'children', label: 'materialName' }"
                default-expand-all
              >
                <template #default="{ data }">
                  <span>{{ data.materialCode }} · {{ data.materialName }}</span>
                  <el-tag v-if="data.quantity" size="small" effect="plain" style="margin-left: 8px">用量 {{ data.quantity }}</el-tag>
                </template>
              </el-tree>
              <div v-else class="empty-hint">选择根物料后查看多级构成。</div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="检验标准" name="inspectionItems">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="itemQuery.keyword" clearable placeholder="项目编码 / 名称" style="width: 220px" />
              <el-select v-model="itemQuery.inspectType" clearable placeholder="类型" style="width: 150px">
                <el-option v-for="(label, value) in inspectTypeLabels" :key="value" :label="label" :value="value" />
              </el-select>
              <el-select
                v-model="itemQuery.materialId"
                clearable
                filterable
                remote
                reserve-keyword
                placeholder="物料"
                :remote-method="searchItemMaterials"
                :loading="materialOptionLoading.item"
                style="width: 220px"
                @visible-change="handleItemMaterialVisible"
              >
                <el-option v-for="item in materialOptions" :key="item.id" :label="`${item.materialCode} · ${item.name}`" :value="item.id" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="loadInspectionItems">查询</el-button>
            </div>
            <el-button type="primary" :icon="Plus" @click="openInspectionItem()">新增检验项目</el-button>
          </div>
          <el-table :data="itemPage.records" stripe style="margin-top: 14px">
            <el-table-column prop="itemCode" label="编码" width="120" />
            <el-table-column prop="itemName" label="项目" min-width="150" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }"><StatusTag :value="row.inspectType" :dict="inspectTypeLabels" /></template>
            </el-table-column>
            <el-table-column label="物料" min-width="180">
              <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="工序" min-width="140">
              <template #default="{ row }">{{ row.processName || '-' }}</template>
            </el-table-column>
            <el-table-column label="判定" width="100">
              <template #default="{ row }">{{ row.isQuantitative === 1 ? '定量' : '定性' }}</template>
            </el-table-column>
            <el-table-column prop="standardDesc" label="标准描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="限值" width="150">
              <template #default="{ row }">{{ row.lowerLimit ?? '-' }} ~ {{ row.upperLimit ?? '-' }} {{ row.unit || '' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" :icon="Edit" @click="openInspectionItem(row)">编辑</el-button>
                  <el-button text type="danger" :icon="Delete" @click="removeInspectionItem(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <Pager :page="itemPage" :query="itemQuery" @change="loadInspectionItems" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="materialDialog.visible" :title="materialDialog.row ? '编辑物料' : '新增物料'" width="640px">
      <el-form ref="materialRef" :model="materialForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="物料编码" class="span-2" required><el-input v-model.trim="materialForm.materialCode" /></el-form-item>
          <el-form-item label="物料名称" required><el-input v-model.trim="materialForm.name" /></el-form-item>
          <el-form-item label="类别" required>
            <el-select v-model="materialForm.category" :disabled="Boolean(materialDialog.row)" style="width: 100%">
              <el-option v-for="(label, value) in materialCategoryLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格型号"><el-input v-model.trim="materialForm.spec" /></el-form-item>
          <el-form-item label="计量单位" required><el-input v-model.trim="materialForm.unit" /></el-form-item>
          <el-form-item label="保质期天数"><el-input-number v-model="materialForm.shelfLifeDays" :min="0" style="width: 100%" /></el-form-item>
          <el-form-item v-if="materialDialog.row" label="状态"><el-switch v-model="materialForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="materialDialog.visible = false">取消</el-button><el-button type="primary" :loading="materialSaving" @click="saveMaterial">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="partyDialog.visible" :title="partyDialog.row ? '编辑往来单位' : '新增往来单位'" width="620px">
      <el-form :model="partyForm" label-width="110px">
        <div class="form-grid">
          <el-form-item :label="partyDialog.type === 'supplier' ? '供应商编码' : '客户编码'" required><el-input v-model.trim="partyForm.code" /></el-form-item>
          <el-form-item :label="partyDialog.type === 'supplier' ? '供应商名称' : '客户名称'" required><el-input v-model.trim="partyForm.name" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model.trim="partyForm.contactPerson" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model.trim="partyForm.phone" /></el-form-item>
          <el-form-item label="地址" class="span-2"><el-input v-model.trim="partyForm.address" /></el-form-item>
          <el-form-item v-if="partyDialog.row" label="状态"><el-switch v-model="partyForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="partyDialog.visible = false">取消</el-button><el-button type="primary" :loading="partySaving" @click="saveParty">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="processDialog.visible" :title="processDialog.row ? '编辑工序' : '新增工序'" width="560px">
      <el-form :model="processForm" label-width="100px">
        <el-form-item label="工序编码" required><el-input v-model.trim="processForm.processCode" /></el-form-item>
        <el-form-item label="工序名称" required><el-input v-model.trim="processForm.processName" /></el-form-item>
        <el-form-item label="IPQC点"><el-switch v-model="processForm.needIpqc" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="作业说明"><el-input v-model.trim="processForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="processDialog.visible = false">取消</el-button><el-button type="primary" :loading="processSaving" @click="saveProcess">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="bomDialog.visible" title="新增BOM行" width="540px">
      <el-form :model="bomForm" label-width="100px">
        <el-form-item label="父项物料" required>
          <el-select
            v-model="bomForm.parentMaterialId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchBomFormParents"
            :loading="materialOptionLoading.bomFormParent"
            style="width: 100%"
            @visible-change="handleBomFormParentVisible"
          >
            <el-option v-for="item in bomFormParentOptions" :key="item.id" :label="`${item.materialCode} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="子项物料" required>
          <el-select
            v-model="bomForm.childMaterialId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchBomFormChildren"
            :loading="materialOptionLoading.bomFormChild"
            style="width: 100%"
            @visible-change="handleBomFormChildVisible"
          >
            <el-option v-for="item in bomFormChildOptions" :key="item.id" :label="`${item.materialCode} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位用量" required><el-input-number v-model="bomForm.quantity" :min="0.001" :step="0.1" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="bomDialog.visible = false">取消</el-button><el-button type="primary" :loading="bomSaving" @click="saveBom">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="itemDialog.visible" :title="itemDialog.row ? '编辑检验项目' : '新增检验项目'" width="720px">
      <el-form :model="itemForm" label-width="112px">
        <div class="form-grid">
          <el-form-item label="项目编码" required><el-input v-model.trim="itemForm.itemCode" /></el-form-item>
          <el-form-item label="项目名称" required><el-input v-model.trim="itemForm.itemName" /></el-form-item>
          <el-form-item label="检验类型" required>
            <el-select v-model="itemForm.inspectType" style="width: 100%">
              <el-option v-for="(label, value) in inspectTypeLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="适用物料" required>
            <el-select
              v-model="itemForm.materialId"
              filterable
              remote
              reserve-keyword
              :remote-method="searchItemMaterials"
              :loading="materialOptionLoading.item"
              style="width: 100%"
              @visible-change="handleItemMaterialVisible"
            >
              <el-option v-for="item in materialOptions" :key="item.id" :label="`${item.materialCode} · ${item.name}`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="itemForm.inspectType === 'IPQC'" label="挂靠工序" required>
            <el-select v-model="itemForm.processDefId" filterable style="width: 100%">
              <el-option v-for="process in processOptions" :key="process.id" :label="`${process.processCode} · ${process.processName}`" :value="process.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="判定方式">
            <el-radio-group v-model="itemForm.isQuantitative">
              <el-radio-button :value="1">定量</el-radio-button>
              <el-radio-button :value="0">定性</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="下限"><el-input-number v-model="itemForm.lowerLimit" :step="0.1" style="width: 100%" /></el-form-item>
          <el-form-item label="上限"><el-input-number v-model="itemForm.upperLimit" :step="0.1" style="width: 100%" /></el-form-item>
          <el-form-item label="单位"><el-input v-model.trim="itemForm.unit" /></el-form-item>
          <el-form-item label="标准描述" class="span-2" required><el-input v-model.trim="itemForm.standardDesc" type="textarea" :rows="3" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="itemDialog.visible = false">取消</el-button><el-button type="primary" :loading="itemSaving" @click="saveInspectionItem">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElButton } from 'element-plus/es/components/button/index'
import { ElInput } from 'element-plus/es/components/input/index'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { ElPagination } from 'element-plus/es/components/pagination/index'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { bomApi, customerApi, inspectionItemApi, materialApi, processApi, routeApi, supplierApi } from '../api'
import { inspectTypeLabels, materialCategoryLabels, pageSizeOptions, yesNo } from '../utils/dicts'
import { ensurePositive, ensureRequired, warn } from '../utils/formGuards'

const activeTab = ref('materials')
const materialOptions = ref([])
const routeMaterialOptions = ref([])
const bomParentOptions = ref([])
const bomTreeMaterialOptions = ref([])
const bomFormParentOptions = ref([])
const bomFormChildOptions = ref([])
const processOptions = ref([])
const materialOptionLoading = reactive({
  route: false,
  bomParent: false,
  bomTree: false,
  bomFormParent: false,
  bomFormChild: false,
  item: false
})
const materialSaving = ref(false)
const partySaving = ref(false)
const processSaving = ref(false)
const routeSaving = ref(false)
const bomSaving = ref(false)
const itemSaving = ref(false)

const materialQuery = reactive({ current: 1, size: 10, keyword: '', category: '' })
const materialPage = reactive({ records: [], total: 0 })
const supplierQuery = reactive({ current: 1, size: 10, keyword: '' })
const supplierPage = reactive({ records: [], total: 0 })
const customerQuery = reactive({ current: 1, size: 10, keyword: '' })
const customerPage = reactive({ records: [], total: 0 })
const processQuery = reactive({ current: 1, size: 10, keyword: '' })
const processPage = reactive({ records: [], total: 0 })
const itemQuery = reactive({ current: 1, size: 10, keyword: '', inspectType: '', materialId: '' })
const itemPage = reactive({ records: [], total: 0 })

const bomParentId = ref()
const bomTreeMaterialId = ref()
const bomRows = ref([])
const bomTree = ref(null)
const routeMaterialId = ref()
const routeSteps = ref([])

const materialDialog = reactive({ visible: false, row: null })
const materialForm = reactive({ materialCode: '', name: '', category: 'RAW', spec: '', unit: '', shelfLifeDays: null, status: 1 })
const partyDialog = reactive({ visible: false, row: null, type: 'supplier' })
const partyForm = reactive({ code: '', name: '', contactPerson: '', phone: '', address: '', status: 1 })
const processDialog = reactive({ visible: false, row: null })
const processForm = reactive({ processCode: '', processName: '', needIpqc: 0, description: '' })
const bomDialog = reactive({ visible: false })
const bomForm = reactive({ parentMaterialId: null, childMaterialId: null, quantity: 1 })
const itemDialog = reactive({ visible: false, row: null })
const itemForm = reactive({
  itemCode: '',
  itemName: '',
  inspectType: 'IQC',
  materialId: null,
  processDefId: null,
  isQuantitative: 1,
  standardDesc: '',
  lowerLimit: null,
  upperLimit: null,
  unit: ''
})

const partyColumns = [
  { prop: 'contactPerson', label: '联系人', width: 120 },
  { prop: 'phone', label: '联系电话', width: 150 },
  { prop: 'address', label: '地址', minWidth: 220 },
  { prop: 'status', label: '状态', width: 90 }
]

onMounted(async () => {
  await Promise.all([loadProcessOptions(), loadMaterials()])
})

async function refreshActive() {
  const map = {
    materials: loadMaterials,
    suppliers: loadSuppliers,
    customers: loadCustomers,
    processes: async () => {
      await Promise.all([loadProcesses(), loadProcessOptions(), searchRouteMaterials('')])
      if (routeMaterialId.value) await loadRoute()
    },
    boms: async () => {
      await Promise.all([searchBomParents(''), searchBomTreeMaterials('')])
      await loadBoms()
      if (bomTreeMaterialId.value) await loadBomTree()
    },
    inspectionItems: async () => {
      await Promise.all([loadInspectionItems(), searchItemMaterials(''), loadProcessOptions()])
    }
  }
  await map[activeTab.value]()
}

async function loadProcessOptions() {
  processOptions.value = await processApi.all()
}

function mergeById(rows) {
  const map = new Map()
  for (const row of rows || []) {
    if (row?.id !== undefined && row?.id !== null && !map.has(row.id)) {
      map.set(row.id, row)
    }
  }
  return [...map.values()]
}

function prependOptionIfMissing(optionsRef, option) {
  if (!option?.id || optionsRef.value.some((item) => item.id === option.id)) return
  optionsRef.value = [option, ...optionsRef.value]
}

async function handleRouteMaterialVisible(visible) {
  if (visible && routeMaterialOptions.value.length === 0) {
    await searchRouteMaterials('')
  }
}

async function searchRouteMaterials(keyword) {
  materialOptionLoading.route = true
  try {
    const pages = await Promise.all([
      materialApi.page({ current: 1, size: 20, category: 'SEMI', status: 1, keyword }),
      materialApi.page({ current: 1, size: 20, category: 'PRODUCT', status: 1, keyword })
    ])
    routeMaterialOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.route = false
  }
}

async function handleBomParentVisible(visible) {
  if (visible && bomParentOptions.value.length === 0) {
    await searchBomParents('')
  }
}

async function searchBomParents(keyword) {
  materialOptionLoading.bomParent = true
  try {
    const pages = await Promise.all([
      materialApi.page({ current: 1, size: 20, category: 'SEMI', keyword }),
      materialApi.page({ current: 1, size: 20, category: 'PRODUCT', keyword })
    ])
    bomParentOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.bomParent = false
  }
}

async function handleBomTreeVisible(visible) {
  if (visible && bomTreeMaterialOptions.value.length === 0) {
    await searchBomTreeMaterials('')
  }
}

async function searchBomTreeMaterials(keyword) {
  materialOptionLoading.bomTree = true
  try {
    const pages = await Promise.all([
      materialApi.page({ current: 1, size: 20, category: 'SEMI', keyword }),
      materialApi.page({ current: 1, size: 20, category: 'PRODUCT', keyword })
    ])
    bomTreeMaterialOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.bomTree = false
  }
}

async function handleBomFormParentVisible(visible) {
  if (visible && bomFormParentOptions.value.length === 0) {
    await searchBomFormParents('')
  }
}

async function searchBomFormParents(keyword) {
  materialOptionLoading.bomFormParent = true
  try {
    const pages = await Promise.all([
      materialApi.page({ current: 1, size: 20, category: 'SEMI', status: 1, keyword }),
      materialApi.page({ current: 1, size: 20, category: 'PRODUCT', status: 1, keyword })
    ])
    bomFormParentOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.bomFormParent = false
  }
}

async function handleBomFormChildVisible(visible) {
  if (visible && bomFormChildOptions.value.length === 0) {
    await searchBomFormChildren('')
  }
}

async function searchBomFormChildren(keyword) {
  materialOptionLoading.bomFormChild = true
  try {
    const pages = await Promise.all([
      materialApi.page({ current: 1, size: 20, category: 'RAW', status: 1, keyword }),
      materialApi.page({ current: 1, size: 20, category: 'SEMI', status: 1, keyword })
    ])
    bomFormChildOptions.value = mergeById(pages.flatMap((page) => page.records || []))
  } finally {
    materialOptionLoading.bomFormChild = false
  }
}

async function handleItemMaterialVisible(visible) {
  if (visible && materialOptions.value.length === 0) {
    await searchItemMaterials('')
  }
}

async function searchItemMaterials(keyword) {
  materialOptionLoading.item = true
  try {
    const page = await materialApi.page({ current: 1, size: 20, keyword })
    materialOptions.value = page.records || []
  } finally {
    materialOptionLoading.item = false
  }
}

async function loadMaterials() {
  Object.assign(materialPage, await materialApi.page(materialQuery))
}

async function loadSuppliers() {
  Object.assign(supplierPage, await supplierApi.page(supplierQuery))
}

async function loadCustomers() {
  Object.assign(customerPage, await customerApi.page(customerQuery))
}

async function loadProcesses() {
  Object.assign(processPage, await processApi.page(processQuery))
  await loadProcessOptions()
}

async function loadInspectionItems() {
  Object.assign(itemPage, await inspectionItemApi.page(itemQuery))
}

async function loadBoms() {
  bomRows.value = await bomApi.list({ parentMaterialId: bomParentId.value })
}

async function loadBomTree() {
  bomTree.value = bomTreeMaterialId.value ? await bomApi.tree(bomTreeMaterialId.value) : null
}

async function loadRoute() {
  const rows = await routeApi.get(routeMaterialId.value)
  routeSteps.value = rows.map((row) => ({ processDefId: row.processDefId }))
}

function openMaterial(row) {
  materialDialog.visible = true
  materialDialog.row = row || null
  Object.assign(materialForm, row || { materialCode: '', name: '', category: 'RAW', spec: '', unit: '', shelfLifeDays: null, status: 1 })
}

async function saveMaterial() {
  if (!ensureRequired(materialForm.materialCode, '请输入物料编码')) return
  if (!ensureRequired(materialForm.name, '请输入物料名称')) return
  if (!ensureRequired(materialForm.category, '请选择物料类别')) return
  if (!ensureRequired(materialForm.unit, '请输入计量单位')) return
  if (materialForm.shelfLifeDays !== null && materialForm.shelfLifeDays !== undefined && Number(materialForm.shelfLifeDays) < 0) {
    warn('保质期天数不能为负数')
    return
  }

  materialSaving.value = true
  try {
    const payload = { ...materialForm }
    if (!materialDialog.row) delete payload.status
    if (materialDialog.row) await materialApi.update(materialDialog.row.id, payload)
    else await materialApi.create(payload)
    ElMessage.success('物料已保存')
    materialDialog.visible = false
    await loadMaterials()
  } finally {
    materialSaving.value = false
  }
}

async function toggleMaterial(row) {
  await materialApi.changeStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('物料状态已更新')
  await loadMaterials()
}

async function removeMaterial(row) {
  await ElMessageBox.confirm(`确认删除物料 ${row.materialCode}？`, '删除确认')
  await materialApi.delete(row.id)
  ElMessage.success('物料已删除')
  await loadMaterials()
}

function openParty(type, row) {
  partyDialog.visible = true
  partyDialog.type = type
  partyDialog.row = row || null
  Object.assign(partyForm, {
    code: row?.supplierCode || row?.customerCode || '',
    name: row?.name || '',
    contactPerson: row?.contactPerson || '',
    phone: row?.phone || '',
    address: row?.address || '',
    status: row?.status ?? 1
  })
}

async function saveParty() {
  if (!ensureRequired(partyForm.code, partyDialog.type === 'supplier' ? '请输入供应商编码' : '请输入客户编码')) return
  if (!ensureRequired(partyForm.name, partyDialog.type === 'supplier' ? '请输入供应商名称' : '请输入客户名称')) return

  const payload =
    partyDialog.type === 'supplier'
      ? { supplierCode: partyForm.code, ...partyForm }
      : { customerCode: partyForm.code, ...partyForm }
  delete payload.code
  if (!partyDialog.row) delete payload.status
  const api = partyDialog.type === 'supplier' ? supplierApi : customerApi
  partySaving.value = true
  try {
    if (partyDialog.row) await api.update(partyDialog.row.id, payload)
    else await api.create(payload)
    ElMessage.success('往来单位已保存')
    partyDialog.visible = false
    await (partyDialog.type === 'supplier' ? loadSuppliers() : loadCustomers())
  } finally {
    partySaving.value = false
  }
}

async function removeParty(type, row) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`, '删除确认')
  await (type === 'supplier' ? supplierApi.delete(row.id) : customerApi.delete(row.id))
  ElMessage.success('已删除')
  await (type === 'supplier' ? loadSuppliers() : loadCustomers())
}

function openProcess(row) {
  processDialog.visible = true
  processDialog.row = row || null
  Object.assign(processForm, row || { processCode: '', processName: '', needIpqc: 0, description: '' })
}

async function saveProcess() {
  if (!ensureRequired(processForm.processCode, '请输入工序编码')) return
  if (!ensureRequired(processForm.processName, '请输入工序名称')) return

  processSaving.value = true
  try {
    if (processDialog.row) await processApi.update(processDialog.row.id, processForm)
    else await processApi.create(processForm)
    ElMessage.success('工序已保存')
    processDialog.visible = false
    await loadProcesses()
  } finally {
    processSaving.value = false
  }
}

async function removeProcess(row) {
  await ElMessageBox.confirm(`确认删除工序 ${row.processName}？`, '删除确认')
  await processApi.delete(row.id)
  ElMessage.success('工序已删除')
  await loadProcesses()
}

async function saveRoute() {
  if (!ensureRequired(routeMaterialId.value, '请选择需要维护路线的物料')) return
  const routeMaterial = routeMaterialOptions.value.find((item) => item.id === routeMaterialId.value)
  if (!routeMaterial || routeMaterial.status !== 1 || !['SEMI', 'PRODUCT'].includes(routeMaterial.category)) {
    warn('请选择启用的半成品或成品维护工艺路线')
    return
  }
  if (!routeSteps.value.length) {
    warn('工艺路线至少包含一道工序')
    return
  }
  const usedProcessIds = new Set()
  for (const [index, step] of routeSteps.value.entries()) {
    if (!ensureRequired(step.processDefId, `第 ${index + 1} 道请选择工序`)) return
    if (usedProcessIds.has(step.processDefId)) {
      warn('同一条工艺路线中不应重复选择同一工序')
      return
    }
    usedProcessIds.add(step.processDefId)
  }

  routeSaving.value = true
  try {
    await routeApi.save({
      materialId: routeMaterialId.value,
      steps: routeSteps.value.map((step, index) => ({ processDefId: step.processDefId, stepNo: index + 1 }))
    })
    ElMessage.success('工艺路线已保存')
    await loadRoute()
  } finally {
    routeSaving.value = false
  }
}

async function openBom() {
  bomDialog.visible = true
  await Promise.all([searchBomFormParents(''), searchBomFormChildren('')])
  const defaultParent = bomFormParentOptions.value.some((item) => item.id === bomParentId.value)
    ? bomParentId.value
    : null
  Object.assign(bomForm, { parentMaterialId: defaultParent, childMaterialId: null, quantity: 1 })
}

async function saveBom() {
  if (!ensureRequired(bomForm.parentMaterialId, '请选择父项物料')) return
  if (!ensureRequired(bomForm.childMaterialId, '请选择子项物料')) return
  if (String(bomForm.parentMaterialId) === String(bomForm.childMaterialId)) {
    warn('父项物料与子项物料不能相同')
    return
  }
  const parent = bomFormParentOptions.value.find((item) => item.id === bomForm.parentMaterialId)
  const child = bomFormChildOptions.value.find((item) => item.id === bomForm.childMaterialId)
  if (!parent || parent.status !== 1 || !['SEMI', 'PRODUCT'].includes(parent.category)) {
    warn('请选择启用的半成品或成品作为父项物料')
    return
  }
  if (!child || child.status !== 1 || !['RAW', 'SEMI'].includes(child.category)) {
    warn('请选择启用的原材料或半成品作为子项物料')
    return
  }
  if (!ensurePositive(bomForm.quantity, '单位用量必须大于 0')) return

  bomSaving.value = true
  try {
    await bomApi.create(bomForm)
    ElMessage.success('BOM行已保存')
    bomDialog.visible = false
    await loadBoms()
  } finally {
    bomSaving.value = false
  }
}

async function removeBom(row) {
  await ElMessageBox.confirm(`确认删除 ${row.parentMaterialName} → ${row.childMaterialName}？`, '删除确认')
  await bomApi.delete(row.id)
  ElMessage.success('BOM行已删除')
  await loadBoms()
}

function openInspectionItem(row) {
  if (row?.materialId) {
    prependOptionIfMissing(materialOptions, {
      id: row.materialId,
      materialCode: row.materialCode,
      name: row.materialName,
      category: row.materialCategory,
      status: 1
    })
  }
  itemDialog.visible = true
  itemDialog.row = row || null
  Object.assign(itemForm, row || {
    itemCode: '',
    itemName: '',
    inspectType: 'IQC',
    materialId: null,
    processDefId: null,
    isQuantitative: 1,
    standardDesc: '',
    lowerLimit: null,
    upperLimit: null,
    unit: ''
  })
}

async function saveInspectionItem() {
  if (!ensureRequired(itemForm.itemCode, '请输入项目编码')) return
  if (!ensureRequired(itemForm.itemName, '请输入项目名称')) return
  if (!ensureRequired(itemForm.inspectType, '请选择检验类型')) return
  if (!ensureRequired(itemForm.materialId, '请选择适用物料')) return
  if (itemForm.inspectType === 'IPQC' && !ensureRequired(itemForm.processDefId, '请选择挂靠工序')) return
  if (itemForm.isQuantitative === 1 && itemForm.lowerLimit === null && itemForm.upperLimit === null) {
    warn('定量检验项目至少需要填写一个上下限')
    return
  }
  if (
    itemForm.isQuantitative === 1 &&
    itemForm.lowerLimit !== null &&
    itemForm.upperLimit !== null &&
    Number(itemForm.lowerLimit) > Number(itemForm.upperLimit)
  ) {
    warn('定量检验项目的下限不能大于上限')
    return
  }
  if (!ensureRequired(itemForm.standardDesc, '请填写检验标准描述')) return

  if (itemForm.inspectType !== 'IPQC') itemForm.processDefId = null
  itemSaving.value = true
  try {
    if (itemDialog.row) await inspectionItemApi.update(itemDialog.row.id, itemForm)
    else await inspectionItemApi.create(itemForm)
    ElMessage.success('检验项目已保存')
    itemDialog.visible = false
    await loadInspectionItems()
  } finally {
    itemSaving.value = false
  }
}

async function removeInspectionItem(row) {
  await ElMessageBox.confirm(`确认删除检验项目 ${row.itemName}？`, '删除确认')
  await inspectionItemApi.delete(row.id)
  ElMessage.success('检验项目已删除')
  await loadInspectionItems()
}

const Pager = defineComponent({
  props: { page: Object, query: Object },
  emits: ['change'],
  setup(props, { emit }) {
    return () =>
      h('div', { class: 'table-footer' }, [
        h(ElPagination, {
          currentPage: props.query.current,
          pageSize: props.query.size,
          'onUpdate:currentPage': (value) => (props.query.current = value),
          'onUpdate:pageSize': (value) => (props.query.size = value),
          pageSizes: pageSizeOptions,
          layout: 'total, sizes, prev, pager, next',
          total: props.page.total,
          onChange: () => emit('change')
        })
      ])
  }
})

const SimpleCrud = defineComponent({
  props: {
    titleCode: String,
    page: Object,
    query: Object,
    columns: Array,
    codeLabel: String,
    nameLabel: String
  },
  emits: ['query', 'create', 'edit', 'remove'],
  setup(props, { emit }) {
    return () =>
      h('div', [
        h('div', { class: 'toolbar' }, [
          h('div', { class: 'toolbar-left' }, [
            h(ElInput, {
              modelValue: props.query.keyword,
              'onUpdate:modelValue': (value) => (props.query.keyword = value),
              clearable: true,
              placeholder: '编码 / 名称',
              style: 'width: 240px'
            }),
            h(ElButton, { type: 'primary', icon: Search, onClick: () => emit('query') }, () => '查询')
          ]),
          h(ElButton, { type: 'primary', icon: Plus, onClick: () => emit('create') }, () => '新增')
        ]),
        h(
          ElTable,
          { data: props.page.records, stripe: true, style: 'margin-top: 14px' },
          () => [
            h(ElTableColumn, { prop: props.titleCode, label: props.codeLabel, width: 140 }),
            h(ElTableColumn, { prop: 'name', label: props.nameLabel, minWidth: 180 }),
            ...props.columns.map((column) =>
              h(ElTableColumn, {
                prop: column.prop,
                label: column.label,
                width: column.width,
                minWidth: column.minWidth,
                formatter: column.prop === 'status' ? (row) => (row.status === 1 ? '启用' : '停用') : undefined
              })
            ),
            h(ElTableColumn, { label: '操作', width: 150, fixed: 'right' }, {
              default: ({ row }) =>
                h('div', { class: 'table-actions' }, [
                  h(ElButton, { text: true, type: 'primary', icon: Edit, onClick: () => emit('edit', row) }, () => '编辑'),
                  h(ElButton, { text: true, type: 'danger', icon: Delete, onClick: () => emit('remove', row) }, () => '删除')
                ])
            })
          ]
        ),
        h(Pager, { page: props.page, query: props.query, onChange: () => emit('query') })
      ])
  }
})
</script>

<style scoped>
.route-editor {
  min-height: 420px;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: var(--app-panel-soft);
}

.route-steps {
  margin-top: 14px;
}

.route-step-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 34px;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.step-no {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #ffffff;
  background: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.bom-tree {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: var(--app-panel-soft);
}
</style>
