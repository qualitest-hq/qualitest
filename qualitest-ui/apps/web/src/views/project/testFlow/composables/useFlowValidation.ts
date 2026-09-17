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

  /** 序列化缓存：nodes/edges 引用未变时复用 graph */
  let cachedNodes: unknown = null
  let cachedEdges: unknown = null
  let cachedGraph: ReturnType<typeof buildCanvasPersistGraph> | null = null

  const issues = computed(() => {
    void stagingStore.unitsById
    void canvasStore.nodes
    void canvasStore.edges
    void apiHealth.warnings
    void runRisk.warnings

    if (cachedNodes !== canvasStore.nodes || cachedEdges !== canvasStore.edges || !cachedGraph) {
      cachedNodes = canvasStore.nodes
      cachedEdges = canvasStore.edges
      cachedGraph = buildCanvasPersistGraph()
    }
    const graph = cachedGraph
    const deferTopologyStructureRules = hasPendingStagingEdgeUnits(
      Object.values(stagingStore.unitsById),
    )
    const validation = validateGraphJson(graph, { deferTopologyStructureRules })
    return [
      ...issuesFromGraphValidation(validation, graph),
      ...issuesFromStagingConfirmFailures(
        Object.values(stagingStore.unitsById),
        canvasStore.edges,
      ),
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
