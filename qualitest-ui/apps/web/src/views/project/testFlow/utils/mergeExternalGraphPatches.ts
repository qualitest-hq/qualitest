/**
 * 把外部图提交事件里的节点/边片段合并进画布。
 * 校验失败或结构异常时抛错，调用方应回退为整图重拉。
 */
import type { Edge, Node } from '@vue-flow/core'

import { rehydrateCanvasSnapshot } from '../graphAdapter'
import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/** 事件中可选的增量字段 */
export interface ExternalGraphPatchEvent {
  /** 变更节点的完整 JSON */
  nodePatches?: unknown[]
  /** 相关边的完整 JSON */
  edgePatches?: unknown[]
  /** 本批删除的节点 */
  deletedNodeIds?: string[]
  /** 本批删除的边 */
  deletedEdgeIds?: string[]
  /** 需要高亮的节点 id（含删除） */
  changedNodeIds?: string[]
}

/** 校验并还原单个节点；不合格返回 null */
function asRawNode(raw: unknown): { id: string; type: string; position: { x: number; y: number }; data?: Record<string, unknown> } | null {
  if (!raw || typeof raw !== 'object') return null
  const n = raw as Record<string, unknown>
  const id = typeof n.id === 'string' ? n.id : null
  const type = typeof n.type === 'string' ? n.type : null
  const pos = n.position as { x?: unknown; y?: unknown } | undefined
  if (!id || !type || pos == null || typeof pos.x !== 'number' || typeof pos.y !== 'number') {
    return null
  }
  return {
    id,
    type,
    position: { x: pos.x, y: pos.y },
    data: (n.data as Record<string, unknown>) ?? {},
  }
}

/** 校验并还原单条边；不合格返回 null */
function asRawEdge(raw: unknown): {
  id: string
  source: string
  target: string
  label?: string
  sourceHandle?: string | null
} | null {
  if (!raw || typeof raw !== 'object') return null
  const e = raw as Record<string, unknown>
  const id = typeof e.id === 'string' ? e.id : null
  const source = typeof e.source === 'string' ? e.source : null
  const target = typeof e.target === 'string' ? e.target : null
  if (!id || !source || !target) return null
  const out: {
    id: string
    source: string
    target: string
    label?: string
    sourceHandle?: string | null
  } = { id, source, target }
  if (typeof e.label === 'string') out.label = e.label
  if (e.sourceHandle != null) out.sourceHandle = String(e.sourceHandle)
  return out
}

/**
 * 按增量片段更新 store 中的节点与边（边先写入 pending，再灌入画布）。
 *
 * @returns 需要高亮的节点 id
 */
export function mergeExternalGraphPatches(
  store: FlowCanvasStore,
  event: ExternalGraphPatchEvent,
): string[] {
  const rawNodes = (event.nodePatches ?? []).map(asRawNode)
  if (rawNodes.some((n) => n == null) && (event.nodePatches?.length ?? 0) > 0) {
    throw new Error('nodePatches 结构无效')
  }
  const rawEdges = (event.edgePatches ?? []).map(asRawEdge)
  if (rawEdges.some((e) => e == null) && (event.edgePatches?.length ?? 0) > 0) {
    throw new Error('edgePatches 结构无效')
  }

  const validNodes = rawNodes.filter(Boolean) as NonNullable<(typeof rawNodes)[number]>[]
  const validEdges = rawEdges.filter(Boolean) as NonNullable<(typeof rawEdges)[number]>[]

  const deletedNodes = new Set((event.deletedNodeIds ?? []).map(String))
  const deletedEdges = new Set((event.deletedEdgeIds ?? []).map(String))

  let adaptedNodes: Node[] = []
  let adaptedEdges: Edge[] = []
  if (validNodes.length || validEdges.length) {
    const snap = rehydrateCanvasSnapshot(validNodes, validEdges)
    adaptedNodes = snap.nodes
    adaptedEdges = snap.edges
  }

  // 按 id 合并节点：删旧、盖新
  const nodeMap = new Map(store.nodes.map((n) => [String(n.id), n]))
  for (const id of deletedNodes) {
    nodeMap.delete(id)
  }
  for (const n of adaptedNodes) {
    nodeMap.set(String(n.id), n)
  }
  store.nodes = Array.from(nodeMap.values())

  // 边：清画布边表后写入 pending，等待节点就绪再灌入
  const baseEdges = store.getEffectiveEdges().map((e) => ({ ...e }))
  const edgeMap = new Map(baseEdges.map((e) => [String(e.id), e]))
  for (const id of deletedEdges) {
    edgeMap.delete(id)
  }
  for (const e of adaptedEdges) {
    edgeMap.set(String(e.id), e)
  }
  const nextEdges = Array.from(edgeMap.values())
  store.edges = []
  store.setPendingEdges(nextEdges)
  store.bumpStagingEdgeFlushToken()

  const highlight =
    event.changedNodeIds?.length
      ? event.changedNodeIds.map(String)
      : adaptedNodes.map((n) => String(n.id))
  return highlight
}

/** 事件是否带有可合并的增量（节点/边片段或删除列表） */
export function hasExternalGraphPatches(event: ExternalGraphPatchEvent): boolean {
  return Boolean(
    (event.nodePatches && event.nodePatches.length > 0)
      || (event.edgePatches && event.edgePatches.length > 0)
      || (event.deletedNodeIds && event.deletedNodeIds.length > 0)
      || (event.deletedEdgeIds && event.deletedEdgeIds.length > 0),
  )
}
