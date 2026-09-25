/**
 * 节点坐标快照：外部合入或版本冲突恢复时保留本地排版。
 */
import type { Node } from '@vue-flow/core'

import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/** 节点 id 到平面坐标的映射 */
export type NodePositionMap = Map<string, { x: number; y: number }>

/** 收集节点 id → 坐标 */
export function captureNodePositions(
  nodes: Array<{ id?: string; position?: { x?: number; y?: number } | null }>,
): NodePositionMap {
  const map: NodePositionMap = new Map()
  for (const n of nodes) {
    const id = n?.id != null ? String(n.id) : ''
    const pos = n?.position
    if (!id || pos == null || typeof pos.x !== 'number' || typeof pos.y !== 'number') continue
    map.set(id, { x: pos.x, y: pos.y })
  }
  return map
}

/**
 * 把已有坐标写回画布中同 id 节点；无快照的节点保持当前坐标。
 */
export function applyNodePositions(store: FlowCanvasStore, positions: NodePositionMap) {
  if (!positions.size) return
  store.nodes = (store.nodes as Node[]).map((n) => {
    const next = positions.get(String(n.id))
    if (!next) return n
    return { ...n, position: { x: next.x, y: next.y } }
  })
}
