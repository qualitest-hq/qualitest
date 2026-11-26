/**
 * 画布实时校验汇总（页面级单例）。
 *
 * 合并：服务端 stage=canvas issues、本地落盘地板、Staging 确认失败、
 * 运行风险、API 语义告警。业务结构规则以服务端为准；本地只做即时地板。
 * 平移/缩放视口不触发服务端校验；Staging 有未确认边时 deferTopology。
 */
import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'

import { validateTestFlowGraph, type FlowIssueRecord } from '@/api/project/testFlow'
import { validateGraphJson } from '@/utils/flow/graphValidate'

import { useAiStagingStore } from '../stores/aiStagingStore'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunRiskStore } from '../stores/runRiskStore'
import {
  collectIssueNodeIds,
  issuesFromApiHealthWarnings,
  issuesFromGraphValidation,
  issuesFromRunRiskWarnings,
  issuesFromStagingConfirmFailures,
  resolveNodeIdsFromMessage,
  type FlowCanvasValidationIssue,
} from '../utils/flowValidationIssues'
import { stripViewportForCompare } from '../utils/reconcileFlowDirty'
import { graphJsonToSnapshotString } from '../utils/graphFingerprint'
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

interface FlowValidationApi {
  issues: ComputedRef<FlowCanvasValidationIssue[]>
  issueNodeIds: ComputedRef<Set<string>>
  serverIssues: Ref<FlowIssueRecord[]>
}

let singleton: FlowValidationApi | null = null

function trimId(raw: unknown): string {
  return raw != null ? String(raw).trim() : ''
}

function dedupeCanvasIssues(list: FlowCanvasValidationIssue[]): FlowCanvasValidationIssue[] {
  const seen = new Set<string>()
  const out: FlowCanvasValidationIssue[] = []
  for (const issue of list) {
    const key = `${issue.level}|${issue.message}|${(issue.nodeIds || []).join(',')}|${issue.source}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push(issue)
  }
  return out
}

function createFlowValidation(): FlowValidationApi {
  const stagingStore = useAiStagingStore()
  const canvasStore = useFlowCanvasStore()
  const apiHealth = useApiHealthStore()
  const runRisk = useRunRiskStore()

  const persistGraph = computed(() => buildCanvasPersistGraph())
  const serverIssues = ref<FlowIssueRecord[]>([])
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let requestSeq = 0

  /** 不含 viewport；含 Staging pending 与流 id，避免平移缩放狂打校验 */
  const validationKey = computed(() => {
    const graph = stripViewportForCompare(persistGraph.value)
    const stagingUnits = Object.values(stagingStore.unitsById)
    return {
      fingerprint: graphJsonToSnapshotString(graph),
      deferTopology: hasPendingStagingEdgeUnits(stagingUnits),
      projectId: trimId(canvasStore.testProjectId),
      flowId: trimId(canvasStore.testFlowId),
    }
  })

  async function refreshServerIssues() {
    const key = validationKey.value
    const seq = ++requestSeq
    if (!key.projectId && !key.flowId) {
      serverIssues.value = []
      return
    }
    try {
      const res = await validateTestFlowGraph('canvas', {
        graphJson: JSON.stringify(persistGraph.value),
        testProjectId: key.projectId || undefined,
        testFlowId: key.flowId || undefined,
        deferTopology: key.deferTopology,
      })
      if (seq !== requestSeq) return
      serverIssues.value = Array.isArray(res?.data?.issues) ? res.data!.issues! : []
    } catch {
      if (seq !== requestSeq) return
      // 失败清空，避免跨流脏高亮
      serverIssues.value = []
    }
  }

  watch(
    validationKey,
    (next, prev) => {
      if (prev && (next.projectId !== prev.projectId || next.flowId !== prev.flowId)) {
        serverIssues.value = []
      }
      if (debounceTimer) clearTimeout(debounceTimer)
      debounceTimer = setTimeout(() => {
        void refreshServerIssues()
      }, 320)
    },
    { immediate: true },
  )

  const issues = computed(() => {
    const graph = persistGraph.value
    const stagingUnits = Object.values(stagingStore.unitsById)
    const floor = validateGraphJson(graph, { persistMinimalOnly: true })
    const fromServer: FlowCanvasValidationIssue[] = serverIssues.value.map((issue) => ({
      level: String(issue?.severity || '').toUpperCase() === 'WARNING' ? 'warning' : 'error',
      message: String(issue?.message || issue?.code || '').trim() || '校验失败',
      nodeIds: issue?.nodeId
        ? [String(issue.nodeId)]
        : resolveNodeIdsFromMessage(String(issue?.message || ''), graph),
      source: 'structure',
    }))
    return dedupeCanvasIssues([
      ...issuesFromGraphValidation(floor, graph),
      ...fromServer,
      ...issuesFromStagingConfirmFailures(stagingUnits, canvasStore.edges),
      ...issuesFromRunRiskWarnings(runRisk.warnings),
      ...issuesFromApiHealthWarnings(apiHealth.warnings),
    ])
  })

  const issueNodeIds = computed(() => collectIssueNodeIds(issues.value))

  return {
    issues,
    issueNodeIds,
    serverIssues,
  }
}

/** 页面级单例：校验条与节点外壳共用 */
export function useFlowValidation() {
  if (!singleton) {
    singleton = createFlowValidation()
  }
  return singleton
}

/** 测试用：清空单例，避免跨 Pinia 实例复用旧 store */
export function resetFlowValidationForTests() {
  singleton = null
}
