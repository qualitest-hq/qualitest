/**
 * 画布实时校验汇总（页面级单例）。
 *
 * 合并：图结构校验、Staging 确认失败摘要、运行风险（鉴权/必填）、API 语义告警，
 * 供左上角校验条展示，并算出需描边高亮的节点 id。
 * BaseFlowNode 与校验条共用同一 computed，避免按节点 ×N 整图重算。
 */
import { computed, type ComputedRef } from 'vue'

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
  type FlowCanvasValidationIssue,
} from '../utils/flowValidationIssues'
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

interface FlowValidationApi {
  issues: ComputedRef<FlowCanvasValidationIssue[]>
  issueNodeIds: ComputedRef<Set<string>>
}

let singleton: FlowValidationApi | null = null

function createFlowValidation(): FlowValidationApi {
  const stagingStore = useAiStagingStore()
  const canvasStore = useFlowCanvasStore()
  const apiHealth = useApiHealthStore()
  const runRisk = useRunRiskStore()

  /**
   * 可落盘图单独缓存：只随画布节点 / 边 / Staging 单元变化重算。
   * API 健康、运行风险变化时复用同一份图，避免整图反复序列化。
   */
  const persistGraph = computed(() => buildCanvasPersistGraph())

  const issues = computed(() => {
    const graph = persistGraph.value
    const stagingUnits = Object.values(stagingStore.unitsById)
    const deferTopologyStructureRules = hasPendingStagingEdgeUnits(stagingUnits)
    const validation = validateGraphJson(graph, { deferTopologyStructureRules })
    return [
      ...issuesFromGraphValidation(validation, graph),
      ...issuesFromStagingConfirmFailures(stagingUnits, canvasStore.edges),
      ...issuesFromRunRiskWarnings(runRisk.warnings),
      ...issuesFromApiHealthWarnings(apiHealth.warnings),
    ]
  })

  const issueNodeIds = computed(() => collectIssueNodeIds(issues.value))

  return {
    issues,
    issueNodeIds,
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
