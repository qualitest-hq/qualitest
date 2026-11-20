/**
 * 画布落点判定与屏幕坐标换算。
 *
 * 校验条根节点使用 pointer-events:none，自身不接收命中，因此落点检测按画布根矩形即可，
 * 黄条覆盖区域仍可拖入节点；条内可点的节点名链接单独设为 pointer-events:auto。
 */
import { FLOW_CANVAS_ROOT_CLASS } from '../../constants/flowConfig'
import type { useFlowCanvasStore } from '../../stores/flowCanvasStore'

/** 取 VueFlow 画布根 DOM（落点、居中创建、导图查询共用） */
export function getFlowCanvasRootEl(): Element | null {
  return document.querySelector(`.${FLOW_CANVAS_ROOT_CLASS}`)
}

/** 判断屏幕坐标是否落在画布根矩形内；拖出画布外松手不创建节点 */
export function isOverFlowCanvas(clientX: number, clientY: number): boolean {
  const el = getFlowCanvasRootEl()
  if (!el) return false
  const rect = el.getBoundingClientRect()
  return clientX >= rect.left && clientX <= rect.right && clientY >= rect.top && clientY <= rect.bottom
}

/**
 * 浏览器 client 坐标 → vue-flow 逻辑坐标。
 * 扣除画布根相对视口的偏移，再按当前 viewport 平移与缩放换算。
 */
export function clientToFlowPosition(
  clientX: number,
  clientY: number,
  store: ReturnType<typeof useFlowCanvasStore>,
): { x: number; y: number } {
  const el = getFlowCanvasRootEl()
  const rect = el?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  const { x: vx, y: vy, zoom } = store.viewport
  return {
    x: (clientX - rect.left - vx) / zoom,
    y: (clientY - rect.top - vy) / zoom,
  }
}
