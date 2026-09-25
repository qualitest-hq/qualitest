/**
 * 判断画布脏稿是否「仅布局变更」：相对已保存基线，去掉视口与节点坐标后相同，且无 pending Staging。
 * 供写锁跳过、外部合入、保存冲突分流共用。
 */
import type { GraphJson } from '@/utils/flow/graphTypes'

import { toGraphJson } from '../graphAdapter'
import { graphJsonToSnapshotString } from './graphFingerprint'
import { stripViewportForCompare } from './reconcileFlowDirty'
import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/**
 * 去掉 meta.viewport 与各节点 position（坐标归零）后返回新图，用于内容比较。
 */
export function stripLayoutForCompare(graph: GraphJson): GraphJson {
  const withoutViewport = stripViewportForCompare(graph)
  return {
    ...withoutViewport,
    nodes: (withoutViewport.nodes ?? []).map((n) => ({
      ...n,
      position: { x: 0, y: 0 },
    })),
  }
}

/** 规范序列化为可比较字符串（不含视口与节点坐标） */
export function snapshotStringWithoutLayout(
  graph: GraphJson | Record<string, unknown> | null | undefined,
): string {
  if (!graph) return ''
  return graphJsonToSnapshotString(stripLayoutForCompare(graph as GraphJson))
}

/**
 * 相对已保存基线，去掉视口与节点坐标后相同，且无 pending Staging → true。
 * 完全干净（含坐标也相同）时亦为 true；调用方应再结合 dirty 使用。
 */
export function isLayoutOnlyDirty(params: {
  currentGraph: GraphJson
  savedGraphSnapshot: string | null | undefined
  pendingStagingCount: number
}): boolean {
  if ((params.pendingStagingCount ?? 0) > 0) return false
  const baseline = params.savedGraphSnapshot
  if (baseline == null || baseline === '') return false

  let saved: GraphJson
  try {
    saved = JSON.parse(baseline) as GraphJson
  } catch {
    return false
  }
  if (!saved || typeof saved !== 'object') return false

  return (
    snapshotStringWithoutLayout(params.currentGraph) === snapshotStringWithoutLayout(saved)
  )
}

/**
 * 从画布 store 现场序列化后判定是否仅布局脏。
 * 同步计算，供写锁 watch 等路径使用。
 */
export function isLayoutOnlyDirtyFromStore(
  store: FlowCanvasStore,
  pendingStagingCount: number,
): boolean {
  const graph = toGraphJson({
    nodes: store.nodes,
    edges: store.getEffectiveEdges(),
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
  })
  return isLayoutOnlyDirty({
    currentGraph: graph,
    savedGraphSnapshot: store.savedGraphSnapshot,
    pendingStagingCount,
  })
}
