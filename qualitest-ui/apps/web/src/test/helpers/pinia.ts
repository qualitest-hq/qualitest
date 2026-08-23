/**
 * Vitest 公共 Pinia 初始化。
 * 边界：仅测试 setup，不含业务 store 逻辑。
 */
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach } from 'vitest'

/** 创建并激活新的 Pinia 实例 */
export function setupFreshPinia() {
  setActivePinia(createPinia())
}

/** describe 级 beforeEach：每个用例前重置 Pinia */
export function withFreshPinia(extraSetup?: () => void) {
  beforeEach(() => {
    setupFreshPinia()
    extraSetup?.()
  })
}
