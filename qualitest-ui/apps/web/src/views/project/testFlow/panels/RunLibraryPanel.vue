<template>
  <div v-loading="runLib.listLoading" class="run-lib">
    <div class="run-lib__tools" style="display:flex;gap:6px;margin-bottom:10px;flex-wrap:wrap">
      <button
          :disabled="!runLib.selectedRunId || runLib.detailLoading"
          class="btn btn--ghost"
          type="button"
          @click="handleReplay"
      >
        回放
      </button>
      <button
          :disabled="!canExportHtml"
          class="btn btn--ghost"
          type="button"
          title="导出简易 HTML 报告"
          @click="handleExportHtml"
      >
        导出报告
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
            {{ runStatusLabel(run.status) }}
          </span>
          <span v-if="run.triggerType" style="margin-left:4px">{{ runTriggerLabel(run.triggerType) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 左栏运行库：加载正式运行记录列表。
 * 支持选中后回放、导出简易 HTML 报告、删除单条与清空全部。
 */
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'

import { downloadTestFlowRunHtmlReport } from '@/api/project/testFlowRun'
import { blobValidate } from '@/utils/qualitest'

import { usePlayback } from '../composables/usePlayback'
import { runStatusLabel, runTriggerLabel } from '../constants/runStatus'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunLibraryStore } from '../stores/runLibraryStore'

/** 允许导出 HTML 报告的运行状态：成功、失败、中止、取消 */
const EXPORTABLE_STATUSES = new Set(['passed', 'failed', 'aborted', 'cancelled'])

const runLib = useRunLibraryStore()
const store = useFlowCanvasStore()
const { selectedRunId } = storeToRefs(runLib)
const { startRunReplay } = usePlayback()
/** 报告下载进行中，防止重复点击 */
const exporting = ref(false)

/** 当前选中的运行摘要 */
const selectedRun = computed(() =>
  runLib.runs.find((r) => r.id === selectedRunId.value) ?? null,
)

/** 是否可点「导出报告」：已选中、状态允许且未在下载中 */
const canExportHtml = computed(() => {
  const run = selectedRun.value
  if (!run || exporting.value) return false
  return EXPORTABLE_STATUSES.has(run.status)
})

/** 列表时间展示：时:分:秒 */
function formatTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

/** 选中一条运行并打开右侧运行详情面板 */
async function selectRun(id) {
  await runLib.selectRun(id)
  store.showRunPanel()
}

/** 删除当前选中运行 */
function deleteSelected() {
  if (selectedRunId.value) runLib.deleteRun(selectedRunId.value)
}

/** 确认后清空本流运行库全部记录 */
async function handleClear() {
  try {
    await ElMessageBox.confirm('清空运行库中全部记录？', '提示', { type: 'warning' })
    await runLib.clearRuns()
  } catch {
    /* 用户取消 */
  }
}

/** 对选中运行做画布回放 */
async function handleReplay() {
  if (!selectedRunId.value) return
  await startRunReplay(selectedRunId.value, { autoPlay: true })
}

/**
 * 下载选中运行的简易 HTML 报告。
 * 文件名优先用响应头 download-filename；业务错误 JSON 则提示 msg。
 */
async function handleExportHtml() {
  const id = selectedRunId.value
  if (!id || !canExportHtml.value) return
  exporting.value = true
  try {
    const { blob, fileName } = await downloadTestFlowRunHtmlReport(id)
    if (!blobValidate(blob)) {
      const text = await blob.text()
      let msg = '导出失败'
      try {
        const obj = JSON.parse(text)
        if (obj?.msg) msg = obj.msg
      } catch {
        /* 非 JSON 错误体 */
      }
      ElMessage.error(msg)
      return
    }
    saveAs(new Blob([blob], { type: 'text/html;charset=utf-8' }), fileName)
  } catch (e) {
    console.error(e)
    ElMessage.error('导出报告失败')
  } finally {
    exporting.value = false
  }
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
  padding: 24px 12px;
  text-align: center;
  color: var(--pd-text-muted);
  font-size: 12px;
}

/* 运行状态色块：避免被元数据行的灰色字色盖住 */
.run-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.4;

  &--passed {
    background: #dcfce7;
    color: #166534;
  }

  &--failed {
    background: #fee2e2;
    color: #b91c1c;
  }

  &--running {
    background: #dbeafe;
    color: #1d4ed8;
  }

  &--paused {
    background: #fef3c7;
    color: #92400e;
  }

  &--aborted,
  &--cancelled {
    background: #f3f4f6;
    color: #4b5563;
  }
}
</style>
