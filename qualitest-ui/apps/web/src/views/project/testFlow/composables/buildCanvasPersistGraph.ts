/**
 * 从画布编辑态组装「可落盘」的 graph_json。
 *
 * 包含：节点、边、视口、运行场景配置、流输出声明；
 * 会排除尚未确认的 AI Staging 新增项，并把未确认的修改按基线回滚后再序列化，
 * 避免临时草稿污染结构校验与 API 语义预检结果。
 */
import type { GraphJson } from '@/utils/flow/graphTypes'

import { toGraphJson } from '../graphAdapter'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

/** @returns 当前画布对应的 GraphJson 对象 */
export function buildCanvasPersistGraph(): GraphJson {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()
  return toGraphJson({
    nodes: store.nodes,
    edges: store.getEffectiveEdges(),
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
    stagingFilter: stagingStore.buildPersistFilter(),
  })
}
