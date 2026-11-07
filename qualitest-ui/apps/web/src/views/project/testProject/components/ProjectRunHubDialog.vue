<template>
  <el-dialog
      v-model="visible"
      title="运行中心"
      width="780px"
      append-to-body
      destroy-on-close
      class="project-run-hub"
      @opened="reload"
  >
    <div class="project-run-hub__toolbar">
      <span class="project-run-hub__mode">
        并发：{{ modeLabel }}
      </span>
      <el-button link type="primary" :loading="loading" @click="reload">刷新</el-button>
    </div>

    <div class="project-run-hub__section-head">
      <h4 class="project-run-hub__section">活动</h4>
      <span class="project-run-hub__count">{{ active.length }}</span>
    </div>
    <div v-if="!loading && !active.length" class="project-run-hub__empty">
      暂无排队或执行中的任务
    </div>
    <ul v-else class="project-run-hub__list">
      <li v-for="row in active" :key="row.itemId" class="project-run-hub__row">
        <div class="project-run-hub__row-top">
          <el-tag size="small" effect="light" :type="statusTagType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
          <span class="project-run-hub__title" :title="displayTitle(row)">{{ displayTitle(row) }}</span>
          <div class="project-run-hub__actions">
            <el-button
                v-if="row.status === 'queued'"
                link
                type="danger"
                size="small"
                @click="onCancelQueue(row)"
            >取消排队</el-button>
            <el-button
                v-if="row.testFlowRunId"
                link
                type="primary"
                size="small"
                @click="openFlowRun(row)"
            >查看</el-button>
            <el-button link size="small" @click="onDismiss(row)">移除</el-button>
          </div>
        </div>
        <div class="project-run-hub__meta">{{ metaLine(row, 'active') }}</div>
      </li>
    </ul>

    <div class="project-run-hub__section-head">
      <h4 class="project-run-hub__section">最近记录</h4>
      <span class="project-run-hub__count">{{ recent.length }}</span>
    </div>
    <div v-if="!loading && !recent.length" class="project-run-hub__empty">
      暂无正式跑流记录
    </div>
    <ul v-else class="project-run-hub__list project-run-hub__list--recent">
      <li v-for="row in recent" :key="row.itemId" class="project-run-hub__row">
        <div class="project-run-hub__row-top">
          <el-tag size="small" effect="light" :type="statusTagType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
          <span class="project-run-hub__title" :title="displayTitle(row)">{{ displayTitle(row) }}</span>
          <div class="project-run-hub__actions">
            <el-button
                v-if="row.testFlowId && row.testFlowRunId"
                link
                type="primary"
                size="small"
                @click="openFlowRun(row)"
            >打开</el-button>
          </div>
        </div>
        <div class="project-run-hub__meta">{{ metaLine(row, 'recent') }}</div>
      </li>
    </ul>
  </el-dialog>
</template>

<script setup>
/**
 * 项目运行中心弹窗。
 * 上半：缓存中的排队 / 执行中活动（可取消排队、从列表移除）；
 * 下半：库表最近正式 Run（只读，可跳转画布打开报告）。
 * 顶栏展示当前项目跑流并发模式（并行 / 按环境串行）。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  cancelProjectQueuedRun,
  dismissProjectHubActive,
  getProjectRunHub,
} from '@/api/project/testProject'
import { formatDurationMs } from '@/views/project/testFlow/utils/runStepDisplay'

const visible = defineModel('visible', { type: Boolean, default: false })
const props = defineProps({
  testProjectId: { type: [String, Number], required: true },
})

const router = useRouter()
const loading = ref(false)
/** 项目并发模式：parallel | serial_by_env */
const runConcurrencyMode = ref('serial_by_env')
/** 排队中 / 执行中 */
const active = ref([])
/** 最近正式 Run */
const recent = ref([])

const modeLabel = computed(() =>
  runConcurrencyMode.value === 'parallel' ? '并行' : '按环境串行',
)

const STATUS_LABEL = {
  queued: '排队中',
  running: '执行中',
  passed: '通过',
  failed: '失败',
  paused: '已暂停',
  aborted: '已中止',
  cancelled: '已取消',
}

const SOURCE_LABEL = {
  manual: '手动',
  ai: 'AI',
  mcp: 'MCP',
  suite: '套件',
  ci: 'CI',
  schedule: '定时',
}

const KIND_LABEL = {
  flow: '单流',
  suite: '套件',
  queue: '排队',
}

/** 状态标签颜色 */
function statusTagType(status) {
  if (status === 'passed') return 'success'
  if (status === 'failed' || status === 'aborted') return 'danger'
  if (status === 'queued' || status === 'paused') return 'warning'
  if (status === 'running') return 'primary'
  return 'info'
}

function statusLabel(status) {
  return STATUS_LABEL[status] || status || '未知'
}

function sourceLabel(source) {
  if (!source) return ''
  return SOURCE_LABEL[source] || source
}

/** 主标题：流名 / 套件名，去掉旧版状态后缀 */
function displayTitle(row) {
  const raw = String(row?.title || row?.flowName || row?.suiteName || row?.itemId || '').trim()
  return raw.replace(/\s*·\s*(passed|failed|running|queued|paused|aborted|cancelled)\s*$/i, '')
}

/**
 * 次行信息：环境、场景、来源、类型、耗时、时间、RunId。
 * @param {'active'|'recent'} mode
 */
function metaLine(row, mode) {
  const parts = []
  if (row.envName) parts.push(row.envName)
  if (row.runScenarioId) parts.push(`场景 ${row.runScenarioId}`)
  if (row.kind && KIND_LABEL[row.kind]) parts.push(KIND_LABEL[row.kind])
  const src = sourceLabel(row.source || row.triggerType)
  if (src) parts.push(src)
  if (row.durationMs != null && row.durationMs >= 0) {
    parts.push(`耗时 ${formatDurationMs(row.durationMs)}`)
  }
  if (row.startedAt) {
    parts.push(mode === 'active' ? `更新 ${row.startedAt}` : row.startedAt)
  } else if (row.finishedAt) {
    parts.push(row.finishedAt)
  }
  if (row.testFlowRunId) parts.push(`Run #${row.testFlowRunId}`)
  else if (row.status === 'queued' && row.itemId) {
    const qid = String(row.itemId).replace(/^queue:/, '')
    if (qid) parts.push(`排队 ${qid}`)
  }
  return parts.join(' · ')
}

/** 拉取运行中心总览 */
async function reload() {
  const pid = props.testProjectId
  if (!pid) return
  loading.value = true
  try {
    const res = await getProjectRunHub(pid, 40)
    const data = res?.data || {}
    runConcurrencyMode.value = data.runConcurrencyMode === 'parallel' ? 'parallel' : 'serial_by_env'
    active.value = Array.isArray(data.active) ? data.active : []
    recent.value = Array.isArray(data.recent) ? data.recent : []
  } catch {
    active.value = []
    recent.value = []
  } finally {
    loading.value = false
  }
}

/** 取消排队（去掉 itemId 上的 queue: 前缀） */
async function onCancelQueue(row) {
  const qid = String(row.itemId || '').replace(/^queue:/, '')
  await cancelProjectQueuedRun(props.testProjectId, qid)
  await reload()
}

/** 从活动列表移除；排队行同时取消排队 */
async function onDismiss(row) {
  await dismissProjectHubActive(props.testProjectId, row.itemId)
  await reload()
}

/** 跳转测试流画布并带上 runId 打开报告 */
function openFlowRun(row) {
  const projectId = row?.testProjectId || props.testProjectId
  if (!row?.testFlowId || projectId == null || projectId === '') return
  const query = row.testFlowRunId != null && row.testFlowRunId !== ''
    ? { runId: String(row.testFlowRunId) }
    : {}
  router.push({
    path: `/project/testProject/flow/${projectId}/${row.testFlowId}`,
    query,
  })
  visible.value = false
}
</script>

<style scoped>
.project-run-hub__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.project-run-hub__mode {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.project-run-hub__section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 8px;
}

.project-run-hub__section {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.project-run-hub__count {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.project-run-hub__empty {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 14px 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
  background: var(--el-fill-color-blank);
}

.project-run-hub__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow: auto;
}

.project-run-hub__list--recent {
  max-height: 320px;
}

.project-run-hub__row {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--el-bg-color);
}

.project-run-hub__row-top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.project-run-hub__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-run-hub__meta {
  padding-left: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-run-hub__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}
</style>
