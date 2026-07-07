<template>
  <section class="page">
    <PageTitle title="追溯查询" subtitle="输入批次号执行反向来源追溯与正向流向追溯，递归链路由后端 CTE 计算。">
      <template #actions>
        <el-button :icon="Refresh" @click="runTrace">刷新追溯</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model.trim="batchNo" class="trace-search-input" placeholder="输入批次号，如 RM-20260610-001 / FP-20260614-001" @keyup.enter="runTrace" />
          <el-button type="primary" :icon="Search" :loading="loading" @click="runTrace">查询追溯</el-button>
        </div>
        <div v-if="batch" class="muted">当前批次 ID：{{ batch.id }}</div>
      </div>
    </div>

    <div v-if="batch" class="trace-grid">
      <div class="work-panel trace-panel">
        <div class="trace-heading">
          <h2>反向追溯 · 来源</h2>
          <div class="trace-heading-meta">
            <span>拖动查看，滚轮缩放</span>
            <div class="trace-chart-actions">
              <el-tooltip content="缩小">
                <el-button size="small" :icon="ZoomOut" @click="zoomTraceView('upstream', 0.85)" />
              </el-tooltip>
              <el-tooltip content="放大">
                <el-button size="small" :icon="ZoomIn" @click="zoomTraceView('upstream', 1.18)" />
              </el-tooltip>
              <el-tooltip content="恢复完整视图">
                <el-button size="small" :icon="Refresh" @click="resetTraceView('upstream')">适配</el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <EchartBox ref="upstreamChartRef" :option="upstreamOption" :loading="loading" tall />
      </div>
      <div class="work-panel trace-panel">
        <div class="trace-heading">
          <h2>正向追溯 · 去向</h2>
          <div class="trace-heading-meta">
            <span>拖动查看，滚轮缩放</span>
            <div class="trace-chart-actions">
              <el-tooltip content="缩小">
                <el-button size="small" :icon="ZoomOut" @click="zoomTraceView('downstream', 0.85)" />
              </el-tooltip>
              <el-tooltip content="放大">
                <el-button size="small" :icon="ZoomIn" @click="zoomTraceView('downstream', 1.18)" />
              </el-tooltip>
              <el-tooltip content="恢复完整视图">
                <el-button size="small" :icon="Refresh" @click="resetTraceView('downstream')">适配</el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <EchartBox ref="downstreamChartRef" :option="downstreamOption" :loading="loading" tall />
      </div>
    </div>

    <div v-if="batch" class="work-panel">
      <el-tabs>
        <el-tab-pane label="反向明细">
          <el-table :data="upstreamRows" stripe>
            <el-table-column prop="level" label="层级" width="80" />
            <el-table-column prop="batchNo" label="批次号" min-width="170" />
            <el-table-column label="物料" min-width="200">
              <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="类别" width="110">
              <template #default="{ row }"><StatusTag :value="row.materialCategory" :dict="materialCategoryLabels" /></template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" min-width="160" />
            <el-table-column prop="productionOrderNo" label="产出工单" min-width="160" show-overflow-tooltip />
            <el-table-column label="生产工序" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.processSummary || '外购批次' }}</template>
            </el-table-column>
            <el-table-column label="检验记录" min-width="300" show-overflow-tooltip>
              <template #default="{ row }">{{ row.inspectionSummary || '暂无检验记录' }}</template>
            </el-table-column>
            <el-table-column prop="consumedQuantity" label="消耗数量" width="120" />
            <el-table-column prop="path" label="路径" min-width="220" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="正向明细">
          <el-table :data="downstreamRows" stripe>
            <el-table-column prop="level" label="层级" width="80" />
            <el-table-column prop="batchNo" label="批次号" min-width="170" />
            <el-table-column label="物料" min-width="200">
              <template #default="{ row }">{{ row.materialCode }} · {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><StatusTag :value="row.batchStatus" :dict="batchStatusLabels" /></template>
            </el-table-column>
            <el-table-column prop="remainingQuantity" label="剩余" width="100" />
            <el-table-column prop="shipmentNo" label="出货单" min-width="160" />
            <el-table-column prop="customerName" label="客户" min-width="160" />
            <el-table-column prop="shippedQuantity" label="出货数量" width="110" />
            <el-table-column prop="shipDate" label="出货日期" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div v-else class="empty-hint">输入批次号后展示双向追溯树。</div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Refresh, Search, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import EchartBox from '../components/EchartBox.vue'
import PageTitle from '../components/PageTitle.vue'
import StatusTag from '../components/StatusTag.vue'
import { traceApi } from '../api'
import { batchStatusLabels, materialCategoryLabels } from '../utils/dicts'

const batchNo = ref('RM-20260610-001')
const loading = ref(false)
const batch = ref(null)
const upstreamRows = ref([])
const downstreamRows = ref([])
const upstreamChartRef = ref()
const downstreamChartRef = ref()

async function runTrace() {
  if (!batchNo.value) {
    ElMessage.warning('请输入批次号')
    return
  }
  loading.value = true
  try {
    batch.value = await traceApi.byNo(batchNo.value)
    const [upstream, downstream] = await Promise.all([
      traceApi.upstream(batch.value.id),
      traceApi.downstream(batch.value.id)
    ])
    upstreamRows.value = upstream || []
    downstreamRows.value = downstream || []
    await nextTick()
    upstreamChartRef.value?.resetView()
    downstreamChartRef.value?.resetView()
  } finally {
    loading.value = false
  }
}

const upstreamOption = computed(() => treeOption(buildBatchTree(upstreamRows.value, false), '#1f6f5b', 'upstream'))
const downstreamOption = computed(() => treeOption(buildBatchTree(downstreamRows.value, true), '#2f6f9f', 'downstream'))

onMounted(() => {
  runTrace()
})

function buildBatchTree(rows, includeShipments) {
  if (!rows.length) return { name: '无数据' }
  const byPath = new Map()
  let root = null
  rows.forEach((row) => {
    const ids = String(row.path).split('>')
    ids.forEach((_, index) => {
      const path = ids.slice(0, index + 1).join('>')
      if (!byPath.has(path)) {
        const source = rows.find((item) => String(item.path) === path) || row
        byPath.set(path, {
          nodeType: 'batch',
          name: source.batchNo,
          value: `${source.materialCode} · ${source.materialName}`,
          symbolSize: [136, 32],
          label: {
            width: 118
          },
          children: []
        })
      }
      if (index === 0) root = byPath.get(path)
      if (index > 0) {
        const parentPath = ids.slice(0, index).join('>')
        const parent = byPath.get(parentPath)
        const node = byPath.get(path)
        if (parent && !parent.children.includes(node)) parent.children.push(node)
      }
    })
    if (includeShipments && row.shipmentNo) {
      const node = byPath.get(row.path)
      node.children.push({
        nodeType: 'shipment',
        name: row.shipmentNo,
        value: `${row.customerName || '未知客户'} · ${row.shippedQuantity || 0}`,
        symbolSize: [144, 32],
        itemStyle: { color: '#b86b2b', borderColor: '#b86b2b' },
        label: {
          width: 126
        }
      })
    }
  })
  return root
}

function resetTraceView(type) {
  const target = type === 'upstream' ? upstreamChartRef.value : downstreamChartRef.value
  target?.resetView()
}

function zoomTraceView(type, factor) {
  const target = type === 'upstream' ? upstreamChartRef.value : downstreamChartRef.value
  target?.zoom(factor)
}

function treeOption(data, color, direction) {
  const isDownstream = direction === 'downstream'
  const nodeWidth = isDownstream ? 144 : 136
  const sidePadding = Math.ceil(nodeWidth / 2) + (isDownstream ? 54 : 44)
  return {
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params) => `${params.name}<br/>${params.value || ''}`
    },
    series: [
      {
        id: `trace-${direction}`,
        type: 'tree',
        data: [data],
        top: 48,
        left: sidePadding,
        bottom: 54,
        right: sidePadding,
        orient: 'LR',
        symbol: 'roundRect',
        symbolSize: [nodeWidth, 32],
        itemStyle: { color, borderColor: color },
        label: {
          position: 'inside',
          color: '#ffffff',
          fontSize: 11,
          overflow: 'truncate',
          width: nodeWidth - 20
        },
        leaves: {
          label: {
            position: 'inside',
            color: '#ffffff',
            fontSize: 11,
            width: nodeWidth - 20,
            overflow: 'truncate'
          }
        },
        lineStyle: { color: '#9ca8a2' },
        roam: true,
        zoom: isDownstream ? 0.68 : 0.74,
        scaleLimit: {
          min: 0.42,
          max: 2.4
        },
        emphasis: {
          focus: 'descendant'
        },
        expandAndCollapse: true,
        initialTreeDepth: 8,
        animationDuration: 260,
        animationDurationUpdate: 260
      }
    ]
  }
}
</script>

<style scoped>
.trace-search-input {
  width: min(420px, 100%);
}

.trace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 680px), 1fr));
  gap: 16px;
}

.trace-panel {
  min-width: 0;
  overflow: hidden;
  contain: layout paint;
}

.trace-panel :deep(.chart.tall) {
  height: clamp(520px, 58vh, 640px);
}

.trace-panel :deep(.chart-canvas),
.trace-panel :deep(.chart-canvas canvas) {
  cursor: grab;
}

.trace-panel :deep(.chart-canvas:active),
.trace-panel :deep(.chart-canvas:active canvas) {
  cursor: grabbing;
}

.trace-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.trace-heading h2 {
  margin: 0;
  font-size: 17px;
}

.trace-heading-meta {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.trace-chart-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.trace-chart-actions :deep(.el-button) {
  margin-left: 0;
}

.trace-heading-meta span {
  color: var(--app-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .trace-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .trace-heading-meta {
    justify-content: flex-start;
  }
}
</style>
