<template>
  <el-card shadow="never" class="run-trend-chart">
    <template #header>
      <span class="run-trend-chart__title">近 7 天运行趋势</span>
    </template>
    <div v-if="hasData" ref="chartRef" class="run-trend-chart__canvas" />
    <el-empty v-else description="暂无运行数据" :image-size="80" />
  </el-card>
</template>

<script setup>
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, watch } from 'vue'

const props = defineProps({
  runTrend: { type: Array, default: () => [] },
})

const chartRef = ref(null)
let chartInstance = null

const hasData = computed(() =>
  (props.runTrend || []).some((item) => Number(item.total) > 0),
)

function renderChart() {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, 'macarons')
  }

  const dates = props.runTrend.map((item) => item.date?.slice(5) || item.date)
  const passed = props.runTrend.map((item) => Number(item.passed) || 0)
  const failed = props.runTrend.map((item) => Number(item.failed) || 0)
  const cancelled = props.runTrend.map((item) => Number(item.cancelled) || 0)

  chartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['成功', '失败', '取消'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '成功', type: 'bar', stack: 'total', data: passed, itemStyle: { color: '#67c23a' } },
      { name: '失败', type: 'bar', stack: 'total', data: failed, itemStyle: { color: '#f56c6c' } },
      { name: '取消', type: 'bar', stack: 'total', data: cancelled, itemStyle: { color: '#909399' } },
    ],
  })
}

function handleResize() {
  chartInstance?.resize()
}

watch(
  () => props.runTrend,
  () => {
    if (hasData.value) {
      nextTick(() => renderChart())
    }
  },
  { deep: true },
)

onMounted(() => {
  if (hasData.value) {
    nextTick(() => renderChart())
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped lang="scss">
.run-trend-chart {
  height: 100%;
}

.run-trend-chart__title {
  font-weight: 600;
}

.run-trend-chart__canvas {
  height: 320px;
}
</style>
