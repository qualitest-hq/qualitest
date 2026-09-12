/**
 * 画布实时校验汇总。
 *
 * 合并：图结构校验、Staging 确认失败摘要、运行风险（鉴权/必填）、API 语义告警，
 * 供左上角校验条展示，并算出需描边高亮的节点 id。
 * 若还有未确认的连线 Staging，则延后「开始节点唯一性」硬拦，避免过滤图误报。
 */
import { computed } from 'vue'

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
} from '../utils/flowValidationIssues'
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

export function useFlowValidation() {
  const stagingStore = useAiStagingStore()
  const canvasStore = useFlowCanvasStore()
  const apiHealth = useApiHealthStore()
  const runRisk = useRunRiskStore()

  /** 校验条全部问题列表 */
  const issues = computed(() => {
    void stagingStore.unitsById
    void canvasStore.nodes
    void canvasStore.edges
    void apiHealth.warnings
    void runRisk.warnings
    const graph = buildCanvasPersistGraph()
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

  /** 存在校验问题的节点 id（节点卡片描边） */
  const issueNodeIds = computed(() => collectIssueNodeIds(issues.value))

  return {
    issues,
    issueNodeIds,
  }
}
