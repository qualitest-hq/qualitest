/**
 * 当前 HTTP 节点鉴权展示：读节点上的 apiAuthMode / apiPath / extracts，
 * 结合画布 store 的项目鉴权配置与校验 issues，算出 authScope 与短标签。
 */
import { computed, type MaybeRefOrGetter, toValue } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useFlowValidation } from './useFlowValidation'
import { resolveNodeAuthScope } from '../utils/nodeAuthScope'

/**
 * @param getNode 当前 HTTP 节点（需 id 与 data）
 * @returns authScope 鉴权作用域；authLabel 卡片短文案（即 scope.label）
 */
export function useNodeAuthScope(
  getNode: MaybeRefOrGetter<{ id?: string; data?: Record<string, unknown> } | undefined>,
) {
  const { issues } = useFlowValidation()
  const store = useFlowCanvasStore()

  const authScope = computed(() => {
    const node = toValue(getNode) || {}
    const data = (node.data || {}) as Record<string, unknown>
    return resolveNodeAuthScope({
      nodeId: node.id != null ? String(node.id) : undefined,
      apiAuthMode: data.apiAuthMode,
      apiPath: data.apiPath,
      projectAuthConfig: store.projectAuthConfig,
      authIssues: issues.value,
    })
  })
  const authLabel = computed(() => authScope.value.label || '')
  return { authScope, authLabel }
}

/** 历史路径缓存已删除；保留空函数，避免旧测试仍 import 时报错 */
export function clearNodeAuthApiPathCache() {
  // no-op
}
