/**
 * 画布壳层出口：顶栏、VueFlow 事件绑定、节点库指针拖放与落点换算。
 */
export { default as FlowCanvasHeader } from './FlowCanvasHeader.vue'
export { useVueFlowCanvasBindings } from './useVueFlowCanvasBindings'
export { usePalettePointerDrag, PALETTE_BODY_DRAG_CLASS } from './paletteDrop/usePalettePointerDrag'
export { clientToFlowPosition, getFlowCanvasRootEl, isOverFlowCanvas } from './paletteDrop/dropTarget'
