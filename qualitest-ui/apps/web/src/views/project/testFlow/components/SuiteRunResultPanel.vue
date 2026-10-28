<template>
  <div class="suite-run-result">
    <div class="suite-run-result__summary">{{ summaryText }}</div>
    <el-table :data="items" max-height="360">
      <el-table-column label="测试流" min-width="140" prop="flowName" show-overflow-tooltip />
      <el-table-column label="场景" min-width="120" prop="scenarioName" show-overflow-tooltip />
      <el-table-column align="center" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="耗时" width="90">
        <template #default="{ row }">
          {{ formatDurationMs(row.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="操作" width="90">
        <template #default="{ row }">
          <el-button
              :disabled="!row.runId"
              link
              type="primary"
              @click="openRun(row)"
          >查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <p v-if="hint" class="suite-run-result__hint">{{ hint }}</p>
  </div>
</template>

<script setup>
/**
 * 套件运行结果面板：汇总 + 项列表（直播进度 / 历史详情共用）。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { runStatusLabel, runStatusTagType } from '../constants/runStatus'
import { formatDurationMs } from '../utils/runStepDisplay'

const props = defineProps({
  items: { type: Array, default: () => [] },
  testProjectId: { type: String, required: true },
  hint: { type: String, default: '' },
})

const router = useRouter()

const summaryText = computed(() => {
  const items = props.items || []
  let passed = 0
  let failed = 0
  let paused = 0
  let pending = 0
  let other = 0
  for (const row of items) {
    if (row.status === 'passed') passed += 1
    else if (row.status === 'failed' || row.status === 'error') failed += 1
    else if (row.status === 'paused') paused += 1
    else if (row.status === 'pending' || row.status === 'running' || row.status === 'skipped') pending += 1
    else other += 1
  }
  return `通过 ${passed} / 失败 ${failed} / 暂停 ${paused} / 未跑或跳过 ${pending}${other ? ` / 其他 ${other}` : ''}`
})

function openRun(row) {
  if (!row?.runId || !row?.testFlowId) return
  const resolved = router.resolve({
    path: `/project/testProject/flow/${props.testProjectId}/${row.testFlowId}`,
    query: { runId: String(row.runId) },
  })
  window.open(resolved.href, '_blank')
}

function statusLabel(status) {
  if (status === 'pending') return '等待'
  if (status === 'running') return '运行中'
  if (status === 'skipped') return '跳过'
  if (status === 'error') return '错误'
  return runStatusLabel(status)
}

function statusTagType(status) {
  if (status === 'pending') return 'info'
  if (status === 'running') return 'warning'
  if (status === 'skipped') return 'info'
  if (status === 'error') return 'danger'
  return runStatusTagType(status)
}
</script>

<style scoped lang="scss">
.suite-run-result__summary {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.suite-run-result__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
