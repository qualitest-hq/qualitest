/**
 * 画布连线路径：横平竖直的正交折线。
 * 两端几乎对齐时拉成一条直线，避免连接点前出现一小段台阶。
 */
import { getSmoothStepPath, Position, type Position as FlowPosition } from '@vue-flow/core'
import { computed } from 'vue'

export interface FlowEdgePathProps {
  sourceX: number
  sourceY: number
  targetX: number
  targetY: number
  sourcePosition: FlowPosition
  targetPosition: FlowPosition
}

/** 高度或水平差不超过这个像素就拉平，避免连接点前一小段台阶 */
const ALIGN_SLACK = 12

/** 当前边的路径，以及标签用的中点 */
export function useFlowEdgePath(props: FlowEdgePathProps) {
  return computed(() => {
    let sourceX = props.sourceX
    let sourceY = props.sourceY
    let targetX = props.targetX
    let targetY = props.targetY
    // 左右进出时拉平很小的高度差；上下进出时拉平很小的水平差
    const horizontal = props.sourcePosition === Position.Left || props.sourcePosition === Position.Right
    if (horizontal && Math.abs(sourceY - targetY) <= ALIGN_SLACK) {
      const y = (sourceY + targetY) / 2
      sourceY = y
      targetY = y
    } else if (!horizontal && Math.abs(sourceX - targetX) <= ALIGN_SLACK) {
      const x = (sourceX + targetX) / 2
      sourceX = x
      targetX = x
    }
    return getSmoothStepPath({
      sourceX,
      sourceY,
      targetX,
      targetY,
      sourcePosition: props.sourcePosition,
      targetPosition: props.targetPosition,
      borderRadius: 8,
    })
  })
}
