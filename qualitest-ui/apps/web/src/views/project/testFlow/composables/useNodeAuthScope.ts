/**
 * 解析当前 HTTP 节点的鉴权展示：凭证路径读节点 data；
 * conflict 消费画布 validate?stage=canvas 的 AUTH_* issues。
 */
import { computed, type MaybeRefOrGetter, toValue } from 'vue'

import { useFlowValidation } from './useFlowValidation'
import { formatNodeAuthScopeLabel, resolveNodeAuthScope } from '../utils/nodeAuthScope'

/**
 * @param getNode 当前 HTTP 节点（需 id + data）
 * @returns authScope 鉴权作用域；authLabel 展示文案
 */
export function useNodeAuthScope(
  getNode: MaybeRefOrGetter<{ id?: string; data?: Record<string, unknown> } | undefined>,
) {
  const { issues } = useFlowValidation()

  const authScope = computed(() => {
    const node = toValue(getNode) || {}
    const data = (node.data || {}) as Record<string, unknown>
    return resolveNodeAuthScope({
      nodeId: node.id != null ? String(node.id) : undefined,
      headers: data.headers,
      extracts: data.extracts,
      authIssues: issues.value,
    })
  })
  const authLabel = computed(() => formatNodeAuthScopeLabel(authScope.value))
  return { authScope, authLabel }
}

/** @deprecated 路径缓存已移除；保留空实现以免旧测试 import 报错 */
export function clearNodeAuthApiPathCache() {
  // no-op：不再按 pathPrefix 拉资产路径
}
