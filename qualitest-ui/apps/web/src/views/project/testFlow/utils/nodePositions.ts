/**
 * 节点坐标的记录与写回。
 * 用于外部改图合入后恢复本地排版、版本冲突后恢复坐标、一键排版写入新坐标。
 */
import type { Node } from '@vue-flow/core'

import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/** 节点 id 到平面坐标的映射 */
export type NodePositionMap = Map<string, { x: number; y: number }>

/** 记录每个节点的坐标；缺 id 或坐标不完整的节点跳过 */
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
 * 把坐标写回画布中同 id 的节点；映射里没有的节点保持原坐标。
 * 只改节点的坐标字段，不替换节点对象，画布上的连线保持不变。
 */
export function applyNodePositions(store: FlowCanvasStore, positions: NodePositionMap) {
  if (!positions.size) return
  for (const n of store.nodes as Node[]) {
    const next = positions.get(String(n.id))
    if (!next) continue
    n.position = { x: next.x, y: next.y }
  }
}
