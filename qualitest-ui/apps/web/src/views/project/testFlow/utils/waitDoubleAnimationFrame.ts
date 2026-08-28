/** 连续两帧 rAF，等布局/字体稳定后再量 DOM 或刷新 Vue Flow internals */
export function waitDoubleAnimationFrame(): Promise<void> {
  return new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
  });
}
