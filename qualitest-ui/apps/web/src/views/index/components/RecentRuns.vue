<template>
  <el-card shadow="never" class="recent-runs">
    <template #header>
      <span class="recent-runs__title">最近运行</span>
    </template>
    <el-table
      v-if="runs.length"
      :data="runs"
      stripe
      style="width: 100%"
      @row-click="handleRowClick"
    >
      <el-table-column label="测试流" prop="flowName" min-width="120" show-overflow-tooltip />
      <el-table-column label="项目" prop="projectName" min-width="100" show-overflow-tooltip />
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" prop="startedAt" width="170" />
      <el-table-column label="耗时" width="90" align="right">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无运行记录" :image-size="80" />
  </el-card>
</template>

<script setup>
import { runStatusLabel, runStatusTagType } from '@/views/project/testFlow/constants/runStatus'

defineProps({
  runs: { type: Array, default: () => [] },
})

const router = useRouter()

function statusLabel(status) {
  return runStatusLabel(status)
}

function statusType(status) {
  return runStatusTagType(status)
}

function formatDuration(ms) {
  const value = Number(ms)
  if (!value && value !== 0) return '-'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(2)}s`
}

function handleRowClick(row) {
  if (!row?.testProjectId || !row?.testFlowId) return
  router.push(`/project/testProject/flow/${row.testProjectId}/${row.testFlowId}`)
}
</script>

<style scoped lang="scss">
.recent-runs__title {
  font-weight: 600;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
