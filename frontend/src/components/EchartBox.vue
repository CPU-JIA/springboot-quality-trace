<template>
  <div :class="['chart', { tall }]">
    <div ref="chartRef" class="chart-canvas" />
    <transition name="chart-fade">
      <div v-if="showLoading" class="chart-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>数据更新中</span>
      </div>
    </transition>
    <div v-if="showEmpty" class="chart-empty">{{ emptyText }}</div>
  </div>
</template>

<script setup>
import { BarChart, LineChart, TreeChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { Loading } from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

echarts.use([BarChart, LineChart, TreeChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: {
    type: Object,
    required: true
  },
  tall: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  loadingDelay: {
    type: Number,
    default: 600
  },
  emptyText: {
    type: String,
    default: '暂无可展示数据'
  }
})

const chartRef = ref()
const showLoading = ref(false)
let chart = null
let resizeObserver = null
let loadingTimer = null
let renderFrame = 0
let resizeFrame = 0
let settleTimer = null
let layoutTransitioning = false
let pendingResize = false

const showEmpty = computed(() => !props.loading && !optionHasData(props.option))

onMounted(() => {
  chart = echarts.init(chartRef.value)
  renderOption(props.option)
  syncLoading()
  resizeObserver = new ResizeObserver(scheduleResize)
  resizeObserver.observe(chartRef.value)
  window.addEventListener('resize', scheduleResize)
  window.addEventListener('app:layout-transition-start', handleLayoutTransitionStart)
  window.addEventListener('app:layout-transition-end', handleLayoutTransitionEnd)
})

watch(
  () => props.option,
  (option) => {
    renderOption(option)
  },
  { deep: true }
)

watch(
  () => props.loading,
  () => {
    syncLoading()
  },
  { immediate: true }
)

async function renderOption(option) {
  await nextTick()
  cancelAnimationFrame(renderFrame)
  renderFrame = requestAnimationFrame(() => {
    if (!chart) return
    scheduleResize()
    chart.setOption(option, true)
    scheduleResize()
    window.clearTimeout(settleTimer)
    settleTimer = window.setTimeout(scheduleResize, 120)
  })
}

function syncLoading() {
  window.clearTimeout(loadingTimer)
  if (props.loading) {
    const delay = optionHasData(props.option) ? props.loadingDelay : Math.min(props.loadingDelay, 180)
    loadingTimer = window.setTimeout(() => {
      if (props.loading) showLoading.value = true
    }, delay)
  } else {
    showLoading.value = false
  }
}

function scheduleResize() {
  if (layoutTransitioning) {
    pendingResize = true
    return
  }
  cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    chart?.resize()
  })
}

function handleLayoutTransitionStart() {
  layoutTransitioning = true
  pendingResize = false
  cancelAnimationFrame(resizeFrame)
}

function handleLayoutTransitionEnd() {
  layoutTransitioning = false
  if (pendingResize) {
    pendingResize = false
  }
  scheduleResize()
  window.clearTimeout(settleTimer)
  settleTimer = window.setTimeout(scheduleResize, 80)
}

function optionHasData(option) {
  const series = Array.isArray(option?.series) ? option.series : option?.series ? [option.series] : []
  return series.some((item) => seriesHasData(item))
}

function seriesHasData(seriesItem) {
  const data = seriesItem?.data
  if (!Array.isArray(data) || data.length === 0) return false
  if (seriesItem?.type === 'tree') {
    return data.some((node) => node && node.name && !['无数据', '暂无数据'].includes(node.name))
  }
  return data.some((item) => hasValue(item))
}

function hasValue(value) {
  if (Array.isArray(value)) return value.some((item) => hasValue(item))
  if (value && typeof value === 'object') {
    return hasValue(value.value) || hasValue(value.name)
  }
  return value !== null && value !== undefined && value !== ''
}

function resetView() {
  if (!chart) return
  cancelAnimationFrame(renderFrame)
  cancelAnimationFrame(resizeFrame)
  chart.resize()
  chart.clear()
  chart.setOption(props.option, true)
  scheduleResize()
  window.clearTimeout(settleTimer)
  settleTimer = window.setTimeout(scheduleResize, 80)
}

function zoom(factor) {
  if (!chart || !Number.isFinite(factor) || factor <= 0) return
  const option = chart.getOption()
  const currentSeries = Array.isArray(option?.series) ? option.series[0] : option?.series
  const sourceSeries = Array.isArray(props.option?.series) ? props.option.series[0] : props.option?.series
  if (!currentSeries && !sourceSeries) return

  const scaleLimit = currentSeries?.scaleLimit || sourceSeries?.scaleLimit || {}
  const min = Number(scaleLimit.min || 0.2)
  const max = Number(scaleLimit.max || 5)
  const currentZoom = Number(currentSeries?.zoom || sourceSeries?.zoom || 1)
  const nextZoom = Math.min(max, Math.max(min, Number((currentZoom * factor).toFixed(2))))
  const seriesPatch = currentSeries?.id || sourceSeries?.id
    ? { id: currentSeries?.id || sourceSeries?.id, zoom: nextZoom }
    : { zoom: nextZoom }
  chart.setOption({ series: [seriesPatch] })
  scheduleResize()
}

defineExpose({
  resetView,
  zoom
})

onBeforeUnmount(() => {
  window.clearTimeout(loadingTimer)
  window.clearTimeout(settleTimer)
  cancelAnimationFrame(renderFrame)
  cancelAnimationFrame(resizeFrame)
  window.removeEventListener('resize', scheduleResize)
  window.removeEventListener('app:layout-transition-start', handleLayoutTransitionStart)
  window.removeEventListener('app:layout-transition-end', handleLayoutTransitionEnd)
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.chart {
  position: relative;
  min-height: 280px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-loading {
  position: absolute;
  top: 8px;
  right: 10px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  color: var(--app-primary);
  font-size: 12px;
  background: rgba(245, 250, 247, 0.92);
  border: 1px solid #c9ddd5;
  border-radius: 5px;
  pointer-events: none;
}

.chart-empty {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: grid;
  place-items: center;
  color: var(--app-muted);
  font-size: 13px;
  background: rgba(255, 255, 255, 0.72);
  pointer-events: none;
}

.chart-fade-enter-active,
.chart-fade-leave-active {
  transition: opacity 0.16s ease;
}

.chart-fade-enter-from,
.chart-fade-leave-to {
  opacity: 0;
}
</style>
