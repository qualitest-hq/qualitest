/** 可中断的步进等待，供回放/模拟/场景运行动画在暂停或终止时提前退出 */
export function abortableSleep(ms: number, shouldAbort: () => boolean): Promise<void> {
  return new Promise((resolve) => {
    const t0 = Date.now();
    const tick = () => {
      if (shouldAbort()) {
        resolve();
        return;
      }
      if (Date.now() - t0 >= ms) {
        resolve();
        return;
      }
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
}
