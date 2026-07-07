<template>
  <section class="page">
    <PageTitle title="质量总览看板" subtitle="集中查看待检任务、合格库存、进行中召回与本月合格率。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageTitle>

    <div class="metric-grid">
      <div class="metric-card">
        <div class="metric-label">待处理检验任务</div>
        <div class="metric-value">{{ stats.activeInspectionTasks ?? 0 }}</div>
        <div class="metric-foot">待领取与检验中任务</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">合格在库批次</div>
        <div class="metric-value">{{ stats.qualifiedBatchCount ?? 0 }}</div>
        <div class="metric-foot">可领料或可出货批次</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">进行中召回</div>
        <div class="metric-value">{{ stats.activeRecallCount ?? 0 }}</div>
        <div class="metric-foot">正在跟踪回收明细</div>
      </div>
      <div class="metric-card highlight">
        <div class="metric-label">本月检验合格率</div>
        <div class="metric-value">{{ formatRate(stats.monthPassRate) }}%</div>
        <div class="metric-foot">合格与让步接收计入通过</div>
      </div>
    </div>

    <div class="split-grid">
      <div class="work-panel">
        <div class="panel-heading">
          <h2>检验合格率趋势</h2>
          <span>近 6 个月，按 IQC/IPQC/FQC 聚合</span>
        </div>
        <EchartBox :option="trendOption" :loading="loading && trend.length === 0" />
      </div>
      <div class="work-panel">
        <div class="panel-heading">
          <h2>缺陷帕累托</h2>
          <span>按缺陷记录条数与累计占比展示</span>
        </div>
        <EchartBox :option="paretoOption" :loading="loading && pareto.length === 0" />
      </div>
    </div>

    <div class="work-panel">
      <div class="panel-heading">
        <h2>供应商质量排名</h2>
        <span>来料检验合格率、缺陷与召回数量用于横向比较</span>
      </div>
      <el-table v-loading="loading" :data="suppliers" stripe>
        <el-table-column prop="supplierCode" label="编码" width="120" />
        <el-table-column prop="supplierName" label="供应商" min-width="180" />
        <el-table-column prop="batchTotal" label="来料批次" width="100" />
        <el-table-column prop="iqcTotal" label="IQC单数" width="100" />
        <el-table-column label="合格率" width="130">
          <template #default="{ row }">{{ formatRate(row.passRate) }}%</template>
        </el-table-column>
        <el-table-column prop="defectCount" label="缺陷数" width="100" />
        <el-table-column prop="recallCount" label="召回数" width="100" />
      </el-table>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EchartBox from '../components/EchartBox.vue'
import PageTitle from '../components/PageTitle.vue'
import { statsApi } from '../api'
import { defectTypeLabels, inspectTypeLabels, labelOf } from '../utils/dicts'
import { recentMonthKeys } from '../utils/months'

const stats = reactive({})
const trend = ref([])
const pareto = ref([])
const suppliers = ref([])
const loading = ref(true)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [dashboard, trendRows, paretoRows, supplierRows] = await Promise.all([
      statsApi.dashboard(),
      statsApi.passRateTrend(6),
      statsApi.defectPareto(),
      statsApi.supplierQuality()
    ])
    Object.assign(stats, dashboard || {})
    trend.value = trendRows || []
    pareto.value = paretoRows || []
    suppliers.value = supplierRows || []
  } finally {
    loading.value = false
  }
}

function formatRate(value) {
  if (value === null || value === undefined) return '0.00'
  return Number(value).toFixed(2)
}

const trendOption = computed(() => {
  const months = recentMonthKeys(6)
  const types = [...new Set([...Object.keys(inspectTypeLabels), ...trend.value.map((item) => item.inspectType)])]
  return {
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0 },
    grid: { top: 48, left: 44, right: 22, bottom: 32, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: { type: 'category', data: months, axisLabel: { margin: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, interval: 20, axisLabel: { formatter: '{value}%' } },
    series: types.map((type) => ({
      name: labelOf(inspectTypeLabels, type),
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: months.map((month) => {
        const row = trend.value.find((item) => item.month === month && item.inspectType === type)
        return row ? Number(row.passRate) : null
      })
    }))
  }
})

const paretoOption = computed(() => {
  const names = pareto.value.map((item) => labelOf(defectTypeLabels, item.defectType))
  return {
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0 },
    grid: { top: 48, left: 42, right: 46, bottom: 42, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: names,
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
      {
        name: '缺陷条数',
        type: 'bar',
        barMaxWidth: 32,
        itemStyle: { color: '#1f6f5b' },
        data: pareto.value.map((item) => Number(item.recordCount || 0))
      },
      {
        name: '累计占比',
        type: 'line',
        yAxisIndex: 1,
        symbolSize: 7,
        itemStyle: { color: '#b86b2b' },
        data: pareto.value.map((item) => Number(item.cumulativeRatio || 0))
      }
    ]
  }
})
</script>

<style scoped>
.highlight {
  border-color: #97b8ae;
  background: #f5faf7;
}

.panel-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-heading h2 {
  margin: 0;
  font-size: 17px;
}

.panel-heading span {
  color: var(--app-muted);
  font-size: 12px;
}
</style>
