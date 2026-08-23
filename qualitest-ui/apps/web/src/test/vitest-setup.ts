import { vi } from 'vitest';

/**
 * 把请求封装换成空函数。单测不发 HTTP；避免 Node 环境加载浏览器专用依赖导致用例收集失败。
 */
vi.mock('@/utils/request', () => ({
  default: vi.fn(() => Promise.resolve({})),
  isRelogin: { show: false },
}));

/** Vitest 全局桩：部分 store/组件在 import 链上会读 localStorage / window.setTimeout */
const storage = new Map<string, string>()

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => (storage.has(key) ? storage.get(key)! : null),
    setItem: (key: string, value: string) => storage.set(key, String(value)),
    removeItem: (key: string) => storage.delete(key),
    clear: () => storage.clear(),
  },
  writable: true,
})

/** 最小 window 桩；须 configurable 以便 jsdom 等环境覆盖 */
if (typeof globalThis.window === 'undefined') {
  Object.defineProperty(globalThis, 'window', {
    value: {
      setTimeout: (...args: Parameters<typeof setTimeout>) => globalThis.setTimeout(...args),
      clearTimeout: (...args: Parameters<typeof clearTimeout>) => globalThis.clearTimeout(...args),
    },
    writable: true,
    configurable: true,
  })
}
