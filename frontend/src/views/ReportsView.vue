<template>
  <section class="page">
    <PageTitle title="统计报表" subtitle="面向质量主管的趋势、帕累托和供应商质量分析。" />

    <div class="work-panel">
      <div class="report-heading">
        <div>
          <h2>合格率趋势</h2>
          <span class="report-subtitle">{{ trendRangeText }} · 月度统计</span>
        </div>
        <div class="chart-actions">
          <el-segmented v-model="months" class="period-switch" :options="monthOptions" @change="loadTrend" />
          <el-button :icon="Refresh" :loading="trendLoading" @click="loadTrend">刷新趋势</el-button>
        </div>
      </div>
      <EchartBox :option="trendOption" :loading="trendLoading && trend.length === 0" />
    </div>

    <div class="split-grid">
      <div class="work-panel">
        <div class="report-heading">
          <h2>缺陷帕累托</h2>
          <el-button :icon="Refresh" :loading="paretoLoading" @click="loadPareto">刷新</el-button>
        </div>
        <EchartBox :option="paretoOption" :loading="paretoLoading && pareto.length === 0" />
      </div>
      <div class="work-panel">
        <div class="report-heading">
          <h2>供应商质量</h2>
          <el-button :icon="Refresh" :loading="supplierLoading" @click="loadSuppliers">刷新</el-button>
        </div>
        <el-table v-loading="supplierLoading" :data="suppliers" height="360" stripe>
          <el-table-column prop="supplierName" label="供应商" min-width="160" />
          <el-table-column label="合格率" width="110">
            <template #default="{ row }">{{ Number(row.passRate || 0).toFixed(2) }}%</template>
          </el-table-column>
          <el-table-column prop="defectCount" label="缺陷" width="80" />
          <el-table-column prop="recallCount" label="召回" width="80" />
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EchartBox from '../components/EchartBox.vue'
import PageTitle from '../components/PageTitle.vue'
import { statsApi } from '../api'
import { defectTypeLabels, inspectTypeLabels, labelOf } from '../utils/dicts'
import { recentMonthKeys } from '../utils/months'

const months = ref(6)
const monthOptions = [
  { label: '近3个月', value: 3 },
  { label: '近6个月', value: 6 },
  { label: '近12个月', value: 12 }
]
const trend = ref([])
const pareto = ref([])
const suppliers = ref([])
const trendLoading = ref(true)
const paretoLoading = ref(true)
const supplierLoading = ref(true)
let trendRequestId = 0

onMounted(async () => {
  await Promise.all([loadTrend(), loadPareto(), loadSuppliers()])
})

async function loadTrend() {
  const requestId = ++trendRequestId
  trendLoading.value = true
  try {
    const rows = await statsApi.passRateTrend(months.value)
    if (requestId === trendRequestId) {
      trend.value = rows || []
    }
  } finally {
    if (requestId === trendRequestId) {
      trendLoading.value = false
    }
  }
}

async function loadPareto() {
  paretoLoading.value = true
  try {
    pareto.value = await statsApi.defectPareto()
  } finally {
    paretoLoading.value = false
  }
}

async function loadSuppliers() {
  supplierLoading.value = true
  try {
    suppliers.value = await statsApi.supplierQuality()
  } finally {
    supplierLoading.value = false
  }
}

const trendOption = computed(() => {
  const monthsAxis = trendMonths.value
  const types = [...new Set([...Object.keys(inspectTypeLabels), ...trend.value.map((item) => item.inspectType)])]
  return {
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0 },
    grid: { top: 48, left: 45, right: 24, bottom: 32, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: { type: 'category', data: monthsAxis, axisLabel: { margin: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, interval: 20, axisLabel: { formatter: '{value}%' } },
    series: types.map((type) => ({
      name: labelOf(inspectTypeLabels, type),
      type: 'line',
      smooth: true,
      data: monthsAxis.map((month) => {
        const row = trend.value.find((item) => item.month === month && item.inspectType === type)
        return row ? Number(row.passRate) : null
      })
    }))
  }
})

const trendMonths = computed(() => {
  return recentMonthKeys(months.value)
})

const trendRangeText = computed(() => {
  if (!trend.value.length) return `近 ${months.value} 个月暂无检验趋势数据`
  return `近 ${months.value} 个月 · ${trendMonths.value[0]} 至 ${trendMonths.value[trendMonths.value.length - 1]}`
})

const paretoOption = computed(() => ({
  tooltip: { trigger: 'axis', confine: true },
  legend: { top: 0 },
  grid: { top: 48, left: 44, right: 48, bottom: 42, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
  xAxis: {
    type: 'category',
    data: pareto.value.map((item) => labelOf(defectTypeLabels, item.defectType)),
    axisLabel: {
      interval: 0,
      hideOverlap: true,
      overflow: 'truncate',
      width: 72
    }
  },
  yAxis: [
    { type: 'value', name: '条数', minInterval: 1 },
    { type: 'value', name: '累计', min: 0, max: 100, interval: 20, axisLabel: { formatter: '{value}%' } }
  ],
  series: [
    { name: '缺陷条数', type: 'bar', barMaxWidth: 34, itemStyle: { color: '#2f6f9f' }, data: pareto.value.map((item) => Number(item.recordCount || 0)) },
    { name: '累计占比', type: 'line', yAxisIndex: 1, itemStyle: { color: '#b86b2b' }, data: pareto.value.map((item) => Number(item.cumulativeRatio || 0)) }
  ]
}))
</script>

<style scoped>
.report-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.report-heading h2 {
  margin: 0;
  font-size: 17px;
}

.report-subtitle {
  display: block;
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.chart-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.period-switch {
  flex: 0 0 auto;
}

.period-switch :deep(.el-segmented__item) {
  min-width: 76px;
  padding: 0 10px;
}

.period-switch :deep(.el-segmented__item-label) {
  overflow: visible;
  text-overflow: clip;
  white-space: nowrap;
}

@media (max-width: 820px) {
  .report-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .chart-actions {
    justify-content: flex-start;
  }
}
</style>
