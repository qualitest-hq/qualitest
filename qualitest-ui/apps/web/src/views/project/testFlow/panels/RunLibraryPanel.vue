<template>
  <div v-loading="runLib.listLoading" class="run-lib">
    <div class="run-lib__tools" style="display:flex;gap:6px;margin-bottom:10px">
      <button
          :disabled="!runLib.selectedRunId || runLib.detailLoading"
          class="btn btn--ghost"
          type="button"
          @click="handleReplay"
      >
        回放
      </button>
      <button
          :disabled="!selectedRunId"
          class="btn btn--ghost"
          type="button"
          @click="deleteSelected"
      >
        删除
      </button>
      <button
          :disabled="!runLib.runs.length"
          class="btn btn--ghost"
          type="button"
          @click="handleClear"
      >
        清空
      </button>
    </div>
    <div class="run-lib__list">
      <div v-if="!runLib.runs.length" class="run-lib__empty">暂无运行记录</div>
      <div
          v-for="run in runLib.runs"
          :key="run.id"
          :class="{ 'is-selected': run.id === runLib.selectedRunId }"
          class="run-lib-item"
          @click="selectRun(run.id)"
      >
        <div style="font-weight:600">{{ run.scenarioName || run.envLabel }}</div>
        <div style="font-size:10px;color:var(--pd-text-muted);margin-top:2px">
          {{ formatTime(run.startedAt) }} ·
          <span :class="`run-badge run-badge--${run.status === 'running' ? 'running' : run.status}`">
            {{ statusLabel(run.status) }}
          </span>
          <span v-if="run.triggerType" style="margin-left:4px">{{ run.triggerType }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/** 左栏运行库：从 test_flow_run 加载记录，支持回放与删除 */
import { ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'

import { usePlayback } from '../composables/usePlayback'
import { runStatusLabel } from '../constants/runStatus'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunLibraryStore } from '../stores/runLibraryStore'

const runLib = useRunLibraryStore()
const store = useFlowCanvasStore()
const { selectedRunId } = storeToRefs(runLib)
const { startRunReplay } = usePlayback()

function formatTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

function statusLabel(status) {
  return runStatusLabel(status)
}

async function selectRun(id) {
  await runLib.selectRun(id)
  store.showRunPanel()
}

function deleteSelected() {
  if (selectedRunId.value) runLib.deleteRun(selectedRunId.value)
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('清空运行库中全部记录？', '提示', { type: 'warning' })
    await runLib.clearRuns()
  } catch {
    /* cancelled */
  }
}

async function handleReplay() {
  if (!selectedRunId.value) return
  await startRunReplay(selectedRunId.value, { autoPlay: true })
}
</script>

<style scoped lang="scss">
.run-lib__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.run-lib-item {
  padding: 10px 12px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  cursor: pointer;
  font-size: 12px;

  &.is-selected {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}

.run-lib__empty {
  padding: 10px 0;
  text-align: center;
  font-size: 13px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.run-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;

  &--passed { background: #dcfce7; color: #166534; }
  &--failed { background: #fee2e2; color: #b91c1c; }
  &--running { background: #dbeafe; color: #1d4ed8; }
  &--paused { background: #fef3c7; color: #92400e; }
  &--aborted { background: #f3f4f6; color: #4b5563; }
}
</style>
