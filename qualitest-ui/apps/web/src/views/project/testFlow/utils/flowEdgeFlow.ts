/**
 * 点中节点时，连到该节点的线沿现有路径流动。
 * 用来顺着还能分开的那段，认进重合走廊的是哪一条。
 * 没选中节点时线保持静止。不改路径，也不挪节点。
 */
import { STAGING_UPDATE_COLOR } from '../constants/stagingTheme'
import { EDGE_IDLE_STROKE } from './stagingEdgeStyle'

/** 虚线一节的长度。流动一圈正好移过这一节 */
const FLOW_DASH = '8 6'

export interface EdgeFlowStroke {
  stroke: string
  strokeWidth: number
  strokeDasharray?: string
  animation?: string
}

/** 这条边是否接在指定节点上（出边或入边） */
export function edgeTouchesNode(
  edge: { source?: string | null; target?: string | null } | null | undefined,
  nodeId: string | null | undefined,
): boolean {
  if (!edge || !nodeId) return false
  return edge.source === nodeId || edge.target === nodeId
}

/**
 * 给接到选中节点的线加上流动虚线。
 * 原来已有虚线（例如待确认的新增、删除）时沿用那段虚线，只让它动起来。
 * 普通灰线改成蓝色，便于和静止的线分开。
 */
export function applyEdgeFlow<T extends EdgeFlowStroke>(style: T, flowing: boolean): T {
  if (!flowing) return style
  return {
    ...style,
    stroke: style.stroke === EDGE_IDLE_STROKE ? STAGING_UPDATE_COLOR : style.stroke,
    strokeWidth: Math.max(style.strokeWidth, 2.5),
    strokeDasharray: style.strokeDasharray ?? FLOW_DASH,
    animation: `qualitest-edge-flow 0.9s linear infinite`,
  }
}
