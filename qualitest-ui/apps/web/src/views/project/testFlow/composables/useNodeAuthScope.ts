/**
 * 当前 HTTP 节点的鉴权作用域（画布卡片 / 属性面板共用）。
 */
import { computed, type MaybeRefOrGetter, toValue } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { formatNodeAuthScopeLabel, resolveNodeAuthScope } from '../utils/nodeAuthScope'

export function useNodeAuthScope(getData: MaybeRefOrGetter<Record<string, unknown> | undefined>) {
  const store = useFlowCanvasStore()
  const authScope = computed(() => {
    const data = toValue(getData) || {}
    return resolveNodeAuthScope({
      apiPath: data.apiPath as string | undefined,
      headers: data.headers,
      extracts: data.extracts,
      authConfig: store.projectAuthConfig,
    })
  })
  const authLabel = computed(() => formatNodeAuthScopeLabel(authScope.value))
  return { authScope, authLabel }
}
