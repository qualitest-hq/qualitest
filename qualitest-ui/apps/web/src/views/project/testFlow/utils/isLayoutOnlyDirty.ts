/**
 * 判断画布脏稿是否「仅布局变更」：相对已保存基线，去掉视口与节点坐标后图内容相同，
 * 且没有待确认的 Staging 单元。
 * true 时：只改了坐标/视口，不长占画布写锁；外部合入可保留本地排版；保存版本冲突可走自动重套坐标。
 * false 时：有内容差异或 Staging，需按内容脏处理。
 */
import type { GraphJson } from '@/utils/flow/graphTypes'

import { toGraphJson } from '../graphAdapter'
import { graphJsonToSnapshotString } from './graphFingerprint'
import { stripViewportForCompare } from './reconcileFlowDirty'
import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/**
 * 去掉 meta.viewport，并把每个节点 position 归零，得到只比内容、不比排版的图副本。
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

/** 去掉视口与节点坐标后，规范序列化为可比较的 JSON 字符串 */
export function snapshotStringWithoutLayout(
  graph: GraphJson | Record<string, unknown> | null | undefined,
): string {
  if (!graph) return ''
  return graphJsonToSnapshotString(stripLayoutForCompare(graph as GraphJson))
}

/**
 * 相对已保存基线做「仅布局」判定。
 * 有待确认 Staging、或没有可用基线 → false。
 * 去掉视口与坐标后当前图与基线相同 → true（含坐标也完全相同的干净态）。
 * 调用方通常还要结合 store.dirty：干净且未脏时不必占写锁。
 */
export function isLayoutOnlyDirty(params: {
  currentGraph: GraphJson
  savedGraphSnapshot: string | null | undefined
  pendingStagingCount: number
}): boolean {
  // Staging 未确认视为内容未定稿，不能当仅布局
  if ((params.pendingStagingCount ?? 0) > 0) return false
  const baseline = params.savedGraphSnapshot
  // 没有基线无法判断「只改了排版」
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
 * 从画布 store 现场序列化当前图，再判定是否仅布局脏。
 * 同步计算，供写锁监听等路径即时使用。
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
