/**
 * 画布实时图结构校验。
 *
 * 对当前可落盘 graph 做结构校验，并汇总 Staging 确认失败、API 语义告警，
 * 供左上角校验条展示；同时算出需描边高亮的节点 id。
 * 若还有未确认的连线 Staging，则延后「开始节点唯一性」硬拦，避免过滤图误报多入口/无入口。
 */
import { computed } from 'vue'

import { validateGraphJson } from '@/utils/flow/graphValidate'

import { useAiStagingStore } from '../stores/aiStagingStore'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import {
  collectIssueNodeIds,
  issuesFromApiHealthWarnings,
  issuesFromGraphValidation,
  issuesFromStagingConfirmFailures,
} from '../utils/flowValidationIssues'
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

export function useFlowValidation() {
  const stagingStore = useAiStagingStore()
  const canvasStore = useFlowCanvasStore()
  const apiHealth = useApiHealthStore()

  /** 结构校验 + 画布条全部问题（含 Staging / API 语义） */
  const issues = computed(() => {
    void stagingStore.unitsById
    void canvasStore.nodes
    void canvasStore.edges
    void apiHealth.warnings
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
      ...issuesFromApiHealthWarnings(apiHealth.warnings),
    ]
  })

  /** 存在校验问题的节点 id（供节点描边高亮） */
  const issueNodeIds = computed(() => collectIssueNodeIds(issues.value))

  return {
    issues,
    issueNodeIds,
  }
}
