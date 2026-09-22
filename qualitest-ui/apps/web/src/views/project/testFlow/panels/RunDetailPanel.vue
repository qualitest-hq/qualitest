<template>
  <div v-if="!run" class="prop-empty">选择左侧运行记录</div>
  <template v-else>
    <div v-if="staleWarning" class="run-stale-warn">{{ staleWarning }}</div>

    <!-- Run 页眉：状态徽标、场景名、失败时的 AI 修复，以及环境/触发/耗时/起止 -->
    <header class="run-hero">
      <div class="run-hero__top">
        <span :class="`run-badge run-badge--${run.status}`">{{ runStatusLabel(run.status) }}</span>
        <span class="run-hero__scenario">{{ run.scenarioName || '未命名场景' }}</span>
        <button
            v-if="run.status === 'failed' || hasFailedStep"
            class="btn btn--primary btn--sm run-ai-fix-btn"
            type="button"
            @click="openAiFix"
        >
          AI 修复
        </button>
      </div>
      <div class="run-hero__grid">
        <div class="run-hero__cell">
          <span class="run-hero__k">环境</span>
          <span class="run-hero__v">{{ runEnvName }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">触发</span>
          <span class="run-hero__v">{{ runTriggerLabel(run.triggerType) || '-' }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">耗时</span>
          <span class="run-hero__v">{{ formatDurationMs(run.durationMs) }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">开始</span>
          <span class="run-hero__v">{{ formatRunTime(run.startedAt) }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">结束</span>
          <span class="run-hero__v">{{ formatRunTime(run.finishedAt) }}</span>
        </div>
      </div>
    </header>

    <!-- 失败摘要：按业务码 / 断言 / 其他分组，可点击跳步 -->
    <div v-if="failureGroups.hasAny" class="run-failure-groups">
      <div class="run-failure-groups__title">失败摘要</div>
      <div
          v-for="group in failureGroups.sections"
          :key="group.id"
          class="run-failure-group"
      >
        <div class="run-failure-group__head">
          <span :class="`run-failure-group__badge run-failure-group__badge--${group.id}`">
            {{ group.label }}
          </span>
          <span class="run-failure-group__count">{{ group.items.length }}</span>
        </div>
        <button
            v-for="item in group.items"
            :key="item.stepIndex"
            class="run-failure-group__item"
            type="button"
            @click="goToStep(item.stepIndex)"
        >
          <span class="run-failure-group__name">
            {{ item.nodeName }}
            <span class="run-failure-group__type">({{ item.typeLabel }})</span>
          </span>
          <span class="run-failure-group__msg">{{ item.summary }}</span>
        </button>
      </div>
    </div>

    <div v-if="run.status === 'paused' && run.pauseInfo" class="run-pause-panel">
      <div class="run-pause-panel__title">
        {{ isAwaitInput ? '等待人工输入' : '运行已暂停，请选择后续操作' }}
      </div>
      <div class="run-pause-panel__meta">
        <span>原因：{{ pauseReasonLabel(run.pauseInfo.pauseReason) }}</span>
        <span v-if="run.pauseInfo.pauseNodeId"> · 节点：{{ run.pauseInfo.pauseNodeId }}</span>
        <span v-if="run.pauseInfo.pausedAt || run.pausedAt"> · {{ formatPauseTime(run.pauseInfo.pausedAt || run.pausedAt) }}</span>
      </div>
      <div v-if="isAwaitInput && run.pauseInfo.prompt" class="run-pause-prompt">
        {{ run.pauseInfo.prompt }}
      </div>
      <div v-if="isAwaitInput && awaitInputFields.length" class="run-await-input-form">
        <div
            v-for="field in awaitInputFields"
            :key="field.name"
            class="run-await-field"
        >
          <label>
            {{ field.label || field.name }}
            <span v-if="field.required" class="run-await-field__req">*</span>
          </label>
          <textarea
              v-if="normalizeInputFieldType(field.type) === 'textarea'"
              v-model="awaitInputs[field.name]"
              :placeholder="field.placeholder || ''"
              rows="3"
          />
          <input
              v-else-if="normalizeInputFieldType(field.type) === 'password'"
              v-model="awaitInputs[field.name]"
              :placeholder="field.placeholder || ''"
              type="password"
          />
          <input
              v-else-if="normalizeInputFieldType(field.type) === 'number'"
              v-model.number="awaitInputs[field.name]"
              :placeholder="field.placeholder || ''"
              type="number"
          />
          <label v-else-if="normalizeInputFieldType(field.type) === 'boolean'" class="run-await-field__bool">
            <input v-model="awaitInputs[field.name]" type="checkbox" />
            {{ field.placeholder || '是' }}
          </label>
          <select
              v-else-if="normalizeInputFieldType(field.type) === 'select'"
              v-model="awaitInputs[field.name]"
          >
            <option disabled value="">请选择</option>
            <option
                v-for="opt in field.options || []"
                :key="String(opt.value)"
                :value="opt.value"
            >
              {{ opt.label || opt.value }}
            </option>
          </select>
          <select
              v-else-if="normalizeInputFieldType(field.type) === 'multiselect'"
              v-model="awaitInputs[field.name]"
              multiple
          >
            <option
                v-for="opt in field.options || []"
                :key="String(opt.value)"
                :value="opt.value"
            >
              {{ opt.label || opt.value }}
            </option>
          </select>
          <input
              v-else-if="normalizeInputFieldType(field.type) === 'date'"
              v-model="awaitInputs[field.name]"
              type="date"
          />
          <input
              v-else-if="normalizeInputFieldType(field.type) === 'datetime'"
              v-model="awaitInputs[field.name]"
              type="datetime-local"
          />
          <input
              v-else
              v-model="awaitInputs[field.name]"
              :placeholder="field.placeholder || ''"
              type="text"
          />
        </div>
      </div>
      <div v-if="run.pauseInfo.snapshotStack?.length" class="run-pause-stack">
        <div class="run-pause-stack__label">快照栈</div>
        <ul>
          <li v-for="(item, i) in run.pauseInfo.snapshotStack" :key="i">
            {{ item.nodeId }} → {{ item.snapshotId }}
          </li>
        </ul>
      </div>
      <div class="run-pause-actions">
        <button
            v-for="dec in visibleDecisions"
            :key="dec.id"
            :class="dec.btnClass"
            :disabled="resumeLoading"
            class="btn btn--sm"
            type="button"
            @click="handleResume(dec.id)"
        >
          {{ dec.label }}
        </button>
      </div>
    </div>

    <div class="run-steps">
      <div
          v-for="(step, idx) in run.steps"
          :key="idx"
          :class="{
            'is-current': idx === runLib.inspectorStepIndex,
            'is-pause-node': step.nodeId === run.pauseInfo?.pauseNodeId,
            'is-audit': isAuditStep(step.nodeType),
          }"
          class="run-step-item"
          @click="goToStep(idx)"
      >
        <span class="run-step-item__icon">{{ stepIcon(step.nodeType) }}</span>
        <div class="run-step-item__body">
          <div class="run-step-item__main">
            <span class="run-step-item__name">{{ step.nodeName || nodeTypeLabelZh(step.nodeType) }}</span>
            <span :class="`run-badge run-badge--${step.status}`">{{ runStatusLabel(step.status) }}</span>
            <span class="run-step-item__dur">{{ formatDurationMs(step.durationMs) }}</span>
          </div>
          <div class="run-step-item__sub">{{ summarizeStep(step) }}</div>
        </div>
      </div>
    </div>

    <div class="run-inspector">
      <div class="run-inspector__tabs">
        <button
            v-for="tab in inspectorTabs"
            :key="tab.id"
            :class="{ 'is-active': runLib.inspectorTab === tab.id }"
            class="run-inspector__tab"
            type="button"
            @click="runLib.inspectorTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="run-inspector__body">
        <div v-if="!currentStep" class="prop-empty" style="padding:20px">选择步骤</div>
        <template v-else>
          <div v-if="runLib.inspectorTab === 'summary'" v-html="summaryHtml" />
          <template v-else-if="runLib.inspectorTab === 'http'">
            <div v-if="!currentStep.http" class="prop-empty" style="padding:20px">该步骤无 HTTP 请求/响应</div>
            <template v-else>
              <div style="margin-bottom:8px;font-weight:600">
                {{ currentStep.http.method }} {{ decodeUrlForDisplay(currentStep.http.url) }} · {{ currentStep.http.status }}
              </div>
              <div
                  v-if="currentStep.http.bizCheck"
                  class="run-biz-check"
                  :class="{ 'is-failed': currentStep.http.bizCheck.passed === false }"
              >
                <div class="run-biz-check__title">
                  业务码校验 · {{ currentStep.http.bizCheck.passed === false ? '失败' : '通过' }}
                </div>
                <div v-if="currentStep.http.bizCheck.actualCode != null">
                  实际 {{ currentStep.http.bizCheck.codePath || 'code' }} =
                  {{ currentStep.http.bizCheck.actualCode }}
                  <template v-if="currentStep.http.bizCheck.successValues?.length">
                    （成功值 {{ currentStep.http.bizCheck.successValues.join(', ') }}）
                  </template>
                </div>
                <div v-if="currentStep.http.bizCheck.message">
                  {{ currentStep.http.bizCheck.message }}
                </div>
              </div>
              <div style="margin-bottom:4px;color:var(--pd-text-muted)">请求</div>
              <pre>{{ JSON.stringify(currentStep.http.request, null, 2) }}</pre>
              <div style="margin:8px 0 4px;color:var(--pd-text-muted)">响应</div>
              <!-- 媒体预览：JSON 内嵌图等可渲染；裸媒体仅 bodyMedia 时显示未保存提示 -->
              <ResponseMediaPreview
                  :body="currentStep.http.response?.body"
                  :body-media="currentStep.http.response?.bodyMedia"
                  :headers="currentStep.http.response?.headers || {}"
              />
              <pre>{{ JSON.stringify(currentStep.http.response, null, 2) }}</pre>
            </template>
          </template>
          <pre v-else-if="runLib.inspectorTab === 'flow'">{{ JSON.stringify(currentStep.flowAfter || {}, null, 2) }}</pre>
        </template>
      </div>
    </div>
  </template>
</template>

<script setup>
/**
 * 右栏运行详情。
 * 含：Run 页眉、失败摘要、暂停续跑 / 人工输入、步骤时间线、摘要 / HTTP / 流程变量 Inspector。
 * 点击步骤会切换 Inspector，并高亮、定位画布上对应节点。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { resumeTestFlowRun } from '@/api/project/testFlowRun'
import ResponseMediaPreview from '@/components/ResponseMediaPreview/index.vue'
import { normalizeInputFieldType } from '@/utils/flow/inputFields'
import { runStatusLabel, runTriggerLabel } from '../constants/runStatus'
import { formatAssertRuleWithActual } from '../utils/nodeDataUtils'
import {
  decodeUrlForDisplay,
  failureCategoryLabel,
  formatDurationMs,
  formatRunTime,
  isAuditStep,
  nodeTypeLabelZh,
  resolveFailureCategory,
  summarizeStep,
} from '../utils/runStepDisplay'
import { toGraphJson } from '../graphAdapter'
import { highlightRunStep } from '../composables/useFlowScenarioRun'
import { useFlowViewport } from '../composables/useFlowViewport'
import { isGraphStructurallyStale } from '../utils/graphFingerprint'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunLibraryStore } from '../stores/runLibraryStore'

const runLib = useRunLibraryStore()
const canvasStore = useFlowCanvasStore()
const viewport = useFlowViewport()
const staleWarning = ref('')
const resumeLoading = ref(false)
/** 人工输入表单的本地值：字段 name → 当前填写内容 */
const awaitInputs = ref({})

/** 各续跑决策按钮文案与样式 */
const DECISION_META = {
  restoreAndRetry: { label: '还原并重试', btnClass: 'btn--primary' },
  retryInPlace: { label: '原地重试', btnClass: 'btn--ghost' },
  skip: { label: '跳过', btnClass: 'btn--ghost' },
  abort: { label: '中止', btnClass: 'btn--danger' },
  continueWithInput: { label: '提交并继续', btnClass: 'btn--primary' },
}

const run = computed(() => runLib.selectedRun)

/** 页眉展示的环境名：取步骤里场景加载结果的 envName，没有则短横线 */
const runEnvName = computed(() => {
  const steps = run.value?.steps || []
  for (const s of steps) {
    const name = s.scenarioLoaded?.envName
    if (name) return name
  }
  return '-'
})

/** 是否因等待人工输入而暂停 */
const isAwaitInput = computed(() => run.value?.pauseInfo?.pauseReason === 'await_input')

/** 暂停面板要渲染的字段列表（来自 pauseInfo.fields） */
const awaitInputFields = computed(() => {
  const fields = run.value?.pauseInfo?.fields
  return Array.isArray(fields) ? fields.filter((f) => f?.name) : []
})

/** 按 pauseInfo.availableDecisions 过滤后的可点决策按钮 */
const visibleDecisions = computed(() => {
  const info = run.value?.pauseInfo
  const available = info?.availableDecisions?.length
    ? info.availableDecisions
    : (Object.keys(DECISION_META))
  return available
    .filter((id) => DECISION_META[id])
    .map((id) => ({ id, ...DECISION_META[id] }))
})

function pauseReasonLabel(reason) {
  if (reason === 'node_failure') return '节点失败'
  if (reason === 'snapshot_failure') return '快照失败'
  if (reason === 'await_input') return '等待人工输入'
  return reason || '未知'
}

function formatPauseTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  return d.toLocaleString()
}

function topSnapshotId() {
  const stack = run.value?.pauseInfo?.snapshotStack
  if (!stack?.length) return undefined
  return stack[stack.length - 1]?.snapshotId
}

/** 用 pauseInfo.fields 的 defaultValue 初始化本地表单 */
function initAwaitInputsFromPauseInfo() {
  const fields = awaitInputFields.value
  const next = {}
  for (const field of fields) {
    const name = field.name
    const type = normalizeInputFieldType(field.type)
    let def = field.defaultValue
    if (def === undefined || def === null) {
      if (type === 'boolean') def = false
      else if (type === 'multiselect') def = []
      else def = ''
    }
    if (type === 'datetime' && typeof def === 'string' && def.includes('T') && def.length > 16) {
      // datetime-local 用 YYYY-MM-DDTHH:mm
      def = def.slice(0, 16)
    }
    next[name] = def
  }
  awaitInputs.value = next
}

watch(
  () => [run.value?.testFlowRunId, run.value?.pauseInfo?.pauseReason, run.value?.pauseInfo?.fields],
  () => {
    if (isAwaitInput.value) {
      initAwaitInputsFromPauseInfo()
    } else {
      awaitInputs.value = {}
    }
  },
  { immediate: true, deep: true },
)

/** 组装 continueWithInput 的 inputs（datetime 补秒；number 转数值） */
function buildContinueInputs() {
  const out = {}
  for (const field of awaitInputFields.value) {
    const name = field.name
    const type = normalizeInputFieldType(field.type)
    let val = awaitInputs.value[name]
    if (type === 'datetime' && typeof val === 'string' && val && val.length === 16) {
      val = `${val}:00`
    }
    if (type === 'number' && val !== '' && val != null) {
      val = Number(val)
    }
    out[name] = val
  }
  return out
}

async function handleResume(decision) {
  const r = run.value
  if (!r?.testFlowRunId || resumeLoading.value) return
  resumeLoading.value = true
  try {
    const body = { decision }
    if (decision === 'restoreAndRetry') {
      const snapId = topSnapshotId()
      if (snapId) body.snapshotId = snapId
    }
    if (decision === 'continueWithInput') {
      body.inputs = buildContinueInputs()
    }
    const res = await resumeTestFlowRun(r.testFlowRunId, body)
    const data = res?.data ?? res
    if (data?.errorCode && !data?.idempotent) {
      ElMessage.warning(data.errorMessage || data.errorCode)
    }
    await runLib.fetchRunDetail(r.testFlowRunId)
    if (data?.status === 'running') {
      ElMessage.success('已继续运行')
    } else if (data?.status === 'passed') {
      ElMessage.success('运行已完成')
    } else if (data?.status) {
      ElMessage.info(`运行状态：${data.status}`)
    }
  } catch (e) {
    ElMessage.error(e?.message ?? '续跑失败')
  } finally {
    resumeLoading.value = false
  }
}

/**
 * 选中运行步骤：切换 Inspector 当前步，并按时间线高亮画布节点。
 * 若该步有 nodeId 且画布上仍存在对应节点，则把视口移到该节点。
 */
function goToStep(idx) {
  runLib.selectInspectorStep(idx)
  const r = run.value
  if (!r?.steps?.length) return
  highlightRunStep(canvasStore, r, idx)
  const nodeId = r.steps[idx]?.nodeId
  if (nodeId && canvasStore.nodes.some((n) => n.id === nodeId)) {
    void viewport.focusNodeIds([nodeId], { onlyIfOffscreen: false })
  }
}

const hasFailedStep = computed(() => {
  const r = run.value
  if (!r) return false
  if (r.status === 'failed') return true
  return (r.steps ?? []).some((s) => s.status === 'failed')
})

/** 打开 AI 设计面板并注入失败 Run 上下文 */
function openAiFix() {
  const r = run.value
  if (!r?.id) return
  canvasStore.pendingAiDesignRunId = r.id
  canvasStore.openAiDesignPanel()
}

/** 对比 Run 快照拓扑与当前画布，结构变化时展示错位提示 */
watch(
  () => [run.value?.graphJsonSnapshot, canvasStore.nodes.length, canvasStore.edges.length],
  () => {
    const r = run.value
    if (!r?.graphJsonSnapshot) {
      staleWarning.value = ''
      return
    }
    const graph = toGraphJson({
      nodes: canvasStore.nodes,
      edges: canvasStore.edges,
      viewport: canvasStore.viewport,
      runConfig: canvasStore.runConfig,
      flowOutputs: canvasStore.flowOutputs,
    })
    const stale = isGraphStructurallyStale(r.graphJsonSnapshot, graph)
    staleWarning.value = stale ? '图已变更，回放高亮可能错位' : ''
  },
  { immediate: true },
)

const currentStep = computed(() => {
  const r = run.value
  if (!r) return null
  return r.steps[runLib.inspectorStepIndex] ?? null
})

const inspectorTabs = [
  { id: 'summary', label: '摘要' },
  { id: 'http', label: '请求/响应' },
  { id: 'flow', label: '流程变量' },
]

/** 步骤时间线左侧节点类型缩写图标 */
function stepIcon(type) {
  const m = { http: 'H', assert: 'A', delay: 'D', condition: 'C', assign: 'S', script: 'P', subflow: 'F', input: 'I', runConfig: '⚙' }
  return m[type] || '?'
}

/**
 * 失败摘要分区：把失败步骤归入业务码 / 断言 / 其他三组。
 * 每项含节点名、类型中文、一行摘要；点击后跳到对应步骤。
 */
const failureGroups = computed(() => {
  const r = run.value
  const empty = { hasAny: false, sections: [] }
  if (!r?.steps?.length) return empty

  const buckets = { bizCode: [], assert: [], other: [] }

  r.steps.forEach((step, stepIndex) => {
    if (step.status !== 'failed') return
    const nodeName = step.nodeName || step.nodeType || `步骤 ${stepIndex}`
    const item = {
      stepIndex,
      nodeName,
      typeLabel: nodeTypeLabelZh(step.nodeType),
      summary: summarizeStep(step),
    }
    const cat = resolveFailureCategory(step)
    buckets[cat].push(item)
  })

  const sections = []
  for (const id of ['bizCode', 'assert', 'other']) {
    if (buckets[id].length) {
      sections.push({ id, label: failureCategoryLabel(id), items: buckets[id] })
    }
  }
  return { hasAny: sections.length > 0, sections }
})


/** 摘要 Tab：当前步骤状态、错误、业务码校验、提取、断言、赋值、脚本与子流等结构化说明 */
const summaryHtml = computed(() => {
  const step = currentStep.value
  if (!step) return ''
  const statusText = runStatusLabel(step.status)
  let html = `<div><strong>${escapeHtml(step.nodeName)}</strong> · <span class="run-badge run-badge--${step.status}">${escapeHtml(statusText)}</span> · ${escapeHtml(formatDurationMs(step.durationMs))}</div>`
  if (step.error) {
    html += `<div style="color:#b91c1c;margin-top:8px">${escapeHtml(step.error.code)}: ${escapeHtml(step.error.message)}</div>`
  }
  // 业务码校验明细：展示路径、实际值、成功白名单与消息
  if (step.http?.bizCheck) {
    const bc = step.http.bizCheck
    const passed = bc.passed === true
    const color = passed ? '#166534' : '#b91c1c'
    html += `<div style="margin-top:8px;font-weight:600;color:${color}">业务码校验 · ${passed ? '通过' : '失败'}</div>`
    html += '<ul style="margin:4px 0 0 16px">'
    if (bc.codePath != null) {
      html += `<li>路径: ${escapeHtml(bc.codePath)}</li>`
    }
    if (bc.actualCode != null) {
      html += `<li>实际值: ${escapeHtml(JSON.stringify(bc.actualCode))}</li>`
    }
    if (Array.isArray(bc.successValues) && bc.successValues.length) {
      html += `<li>成功值: ${escapeHtml(JSON.stringify(bc.successValues))}</li>`
    }
    if (bc.message) {
      html += `<li style="color:${color}">消息: ${escapeHtml(bc.message)}</li>`
    }
    html += '</ul>'
  }
  if (step.extracts?.length) {
    html += '<div style="margin-top:8px">提取:</div><ul style="margin:4px 0 0 16px">'
    step.extracts.forEach((e) => {
      html += `<li>${escapeHtml(e.scope)}.${escapeHtml(e.name)} = ${escapeHtml(JSON.stringify(e.value))}</li>`
    })
    html += '</ul>'
  }
  if (step.assert?.rules) {
    html += '<div style="margin-top:8px">断言:</div><ul style="margin:4px 0 0 16px">'
    step.assert.rules.forEach((r) => {
      const color = r.passed ? '#166534' : '#b91c1c'
      html += `<li style="color:${color}">${escapeHtml(formatAssertRuleWithActual(r))}</li>`
    })
    html += '</ul>'
  }
  if (step.assigns?.length) {
    html += '<div style="margin-top:8px">赋值:</div><ul style="margin:4px 0 0 16px">'
    step.assigns.forEach((a) => {
      html += `<li>flow.${escapeHtml(a.name)} ${escapeHtml(a.op)}: ${escapeHtml(JSON.stringify(a.before))} → ${escapeHtml(JSON.stringify(a.after))}</li>`
    })
    html += '</ul>'
  }
  if (step.branchTaken) {
    html += `<div style="margin-top:8px">分支: ${escapeHtml(step.branchTaken.kind)} (${escapeHtml(step.branchTaken.branchId)})</div>`
  }
  if (step.script?.writes?.length) {
    html += '<div style="margin-top:8px">脚本写入:</div><ul style="margin:4px 0 0 16px">'
    step.script.writes.forEach((w) => {
      html += `<li>flow.${escapeHtml(w.key)} = ${escapeHtml(JSON.stringify(w.value))}</li>`
    })
    html += '</ul>'
  }
  if (step.script?.logs?.length) {
    html += '<div style="margin-top:8px">脚本输出:</div><ul style="margin:4px 0 0 16px">'
    step.script.logs.forEach((line) => {
      html += `<li>${escapeHtml(line)}</li>`
    })
    html += '</ul>'
  }
  if (step.nodeType === 'runConfig' && step.scenarioLoaded) {
    const sl = step.scenarioLoaded
    html += '<div style="margin-top:8px">场景加载</div><ul style="margin:4px 0 0 16px">'
    if (sl.scenarioName || sl.scenarioId) {
      html += `<li>场景: ${escapeHtml(sl.scenarioName || sl.scenarioId)}</li>`
    }
    if (sl.envName || sl.testProjectEnvId) {
      html += `<li>环境: ${escapeHtml(sl.envName || sl.testProjectEnvId)}</li>`
    }
    const flowSeed = sl.flowSeed && typeof sl.flowSeed === 'object' ? sl.flowSeed : {}
    const seedKeys = Object.keys(flowSeed)
    if (seedKeys.length) {
      html += '<li>flow 初值:</li><ul style="margin:2px 0 0 16px">'
      seedKeys.forEach((key) => {
        html += `<li>${escapeHtml(key)} = ${escapeHtml(JSON.stringify(flowSeed[key]))}</li>`
      })
      html += '</ul>'
    }
    html += '</ul>'
  }
  if (step.nodeType === 'subflow' && step.subflow?.childSteps?.length) {
    html += '<div style="margin-top:8px">子流步骤:</div><ul style="margin:4px 0 0 16px">'
    step.subflow.childSteps.forEach((child) => {
      const badge = child.status === 'failed' ? 'color:#b91c1c' : child.status === 'passed' ? 'color:#166534' : ''
      html += `<li style="${badge}">${escapeHtml(child.nodeName || child.nodeType)} · ${escapeHtml(runStatusLabel(child.status))} · ${escapeHtml(formatDurationMs(child.durationMs))}</li>`
      if (child.error?.message) {
        html += `<li style="color:#b91c1c;margin-left:12px">${escapeHtml(child.error.message)}</li>`
      }
    })
    html += '</ul>'
  }
  return html
})

function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
</script>

<style scoped lang="scss">
.run-ai-fix-btn {
  /* 页眉右侧：失败 Run 打开 AI 设计并注入上下文 */
  margin-left: auto;
  height: 26px;
  padding: 0 10px;
  border-radius: 6px;
  border: none;
  background: var(--pd-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;

  &:hover {
    background: #095ec0;
  }
}

.run-stale-warn {
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fcd34d;
}

/* Run 页眉：状态徽标、场景名、AI 修复按钮，以及环境/触发/耗时/起止网格 */
.run-hero {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--pd-divider, #e5e7eb);
  background: var(--pd-bg-sunken, #f8fafc);
}

.run-hero__top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.run-hero__scenario {
  font-size: 13px;
  font-weight: 700;
  color: var(--pd-text);
}

.run-hero__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 12px;
  font-size: 11px;
}

.run-hero__cell {
  display: flex;
  gap: 6px;
  min-width: 0;
}

.run-hero__k {
  color: var(--pd-text-muted);
  flex: 0 0 auto;
}

.run-hero__v {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 失败摘要：按业务码 / 断言 / 其他分组，点击条目跳到时间线对应步骤 */
.run-failure-groups {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  background: #fef2f2;
}

.run-failure-groups__title {
  font-size: 12px;
  font-weight: 700;
  color: #991b1b;
  margin-bottom: 8px;
}

.run-failure-group {
  & + & {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed #fecaca;
  }
}

.run-failure-group__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.run-failure-group__badge {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;

  &--bizCode {
    background: #ffedd5;
    color: #c2410c;
  }

  &--assert {
    background: #ede9fe;
    color: #6d28d9;
  }

  &--other {
    background: #e5e7eb;
    color: #374151;
  }
}

.run-failure-group__count {
  font-size: 10px;
  color: var(--pd-text-muted);
}

.run-failure-group__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  margin-top: 4px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font-size: 11px;

  &:hover {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}

.run-failure-group__name {
  font-weight: 600;
  color: var(--pd-text);
}

.run-failure-group__type {
  margin-left: 4px;
  font-weight: 500;
  color: var(--pd-text-muted);
}

.run-failure-group__msg {
  color: #b91c1c;
  line-height: 1.4;
  word-break: break-all;
}

/* HTTP Tab：业务码校验结果块（路径、实际值、成功白名单、消息） */
.run-biz-check {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  background: #dcfce7;
  border: 1px solid #bbf7d0;
  color: #166534;

  &.is-failed {
    background: #ffedd5;
    border-color: #fed7aa;
    color: #c2410c;
  }
}

.run-biz-check__title {
  font-weight: 700;
  margin-bottom: 4px;
}

.run-pause-panel {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  border: 1px solid #fcd34d;
  background: #fffbeb;
}

.run-pause-panel__title {
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
  margin-bottom: 6px;
}

.run-pause-panel__meta {
  font-size: 11px;
  color: var(--pd-text-muted);
  margin-bottom: 8px;
}

.run-pause-prompt {
  font-size: 12px;
  margin-bottom: 8px;
  color: #78350f;
}

.run-await-input-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}

.run-await-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.run-await-field label {
  font-weight: 600;
  color: #78350f;
}

.run-await-field__req {
  color: #dc2626;
  margin-left: 2px;
}

.run-await-field__bool {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 400 !important;
}

.run-await-field input,
.run-await-field textarea,
.run-await-field select {
  font-size: 12px;
  padding: 4px 8px;
  border: 1px solid #fcd34d;
  border-radius: 4px;
  background: #fff;
}

.run-pause-stack {
  font-size: 11px;
  margin-bottom: 10px;

  ul {
    margin: 4px 0 0 16px;
    padding: 0;
  }
}

.run-pause-stack__label {
  font-weight: 600;
  color: var(--pd-text-muted);
}

.run-pause-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.btn--danger {
  border: 1px solid #fecaca;
  background: #fee2e2;
  color: #b91c1c;

  &:hover:not(:disabled) {
    background: #fecaca;
  }
}

.run-steps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  max-height: 240px;
  overflow: auto;
}

.run-step-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 11px;

  &.is-current {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }

  &.is-pause-node {
    border-color: #f59e0b;
    background: #fffbeb;
  }

  &.is-audit {
    /* 审计步（场景加载等）弱化显示 */
    opacity: 0.72;
  }

  &.is-audit .run-step-item__icon {
    background: transparent;
    border: 1px dashed var(--pd-divider, #d1d5db);
  }
}

.run-step-item__icon {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
  background: var(--pd-bg-sunken);
}

.run-step-item__body {
  min-width: 0;
  flex: 1;
}

/* 时间线主行：节点名、状态徽标、耗时 */
.run-step-item__main {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.run-step-item__name {
  font-weight: 600;
}

.run-step-item__dur {
  color: var(--pd-text-muted);
  font-size: 10px;
}

.run-step-item__sub {
  color: var(--pd-text-muted);
  margin-top: 2px;
  word-break: break-all;
  line-height: 1.35;
}

.run-inspector__tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--pd-divider);
  margin-bottom: 8px;
}

.run-inspector__tab {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;

  &.is-active {
    color: var(--pd-primary);
    border-bottom-color: var(--pd-primary);
  }
}

.run-inspector__body {
  font-size: 11px;
  line-height: 1.5;
  max-height: 280px;
  overflow: auto;

  pre {
    font-size: 10px;
    white-space: pre-wrap;
    word-break: break-all;
    background: var(--pd-bg-sunken);
    padding: 8px;
    border-radius: 6px;
  }
}

.run-badge,
:deep(.run-badge) {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;

  &.run-badge--passed { background: #dcfce7; color: #166534; }
  &.run-badge--failed { background: #fee2e2; color: #b91c1c; }
  &.run-badge--running { background: #dbeafe; color: #1d4ed8; }
  &.run-badge--paused { background: #fef3c7; color: #92400e; }
  &.run-badge--skipped { background: #f3f4f6; color: #6b7280; }
  &.run-badge--aborted,
  &.run-badge--cancelled { background: #f3f4f6; color: #4b5563; }
}

:deep(.prop-empty) {
  text-align: center;
  padding: 40px 16px;
  color: var(--pd-text-muted);
  font-size: 12px;
  line-height: 1.7;
}
</style>
