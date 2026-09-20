import { nextTick } from 'vue'

/** 连续两帧 rAF，等布局/字体稳定后再量 DOM 或刷新 Vue Flow internals */
export function waitDoubleAnimationFrame(): Promise<void> {
  return new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
  });
}

/**
 * 等待 Vue 响应式与双帧布局落稳（量尺寸、fitView 前调用）。
 */
export async function flushVueFlowLayout(): Promise<void> {
  await nextTick()
  await waitDoubleAnimationFrame()
}
