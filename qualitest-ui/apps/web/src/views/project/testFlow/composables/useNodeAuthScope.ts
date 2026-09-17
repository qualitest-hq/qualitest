/**
 * 解析当前 HTTP 节点的鉴权作用域，供画布卡片与属性面板展示凭证标签。
 *
 * 节点通常只存 testProjectApiId，不存 apiPath。
 * 无 apiPath 时按接口 id 拉取资产路径，再按 pathPrefix 选 Profile；
 * 若路径为空则无法命中前缀，会回落到鉴权配置数组第一条 Profile，标签可能显示错误端凭证。
 */
import { computed, ref, watch, type MaybeRefOrGetter, toValue } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { formatNodeAuthScopeLabel, resolveNodeAuthScope } from '../utils/nodeAuthScope'
import { resolveCanvasApiDetail } from '../utils/resolveCanvasApiDetail'

/** 按接口 id 缓存已解析的资产路径，减少重复请求 */
const apiPathCache = new Map<string, string>()

/** 清空资产路径缓存（单测隔离用） */
export function clearNodeAuthApiPathCache() {
  apiPathCache.clear()
}

/**
 * @param getData 当前 HTTP 节点 data（可含 apiPath、testProjectApiId、headers、extracts）
 * @returns authScope 鉴权作用域；authLabel 展示文案
 */
export function useNodeAuthScope(getData: MaybeRefOrGetter<Record<string, unknown> | undefined>) {
  const store = useFlowCanvasStore()
  /** 按接口 id 异步解析出的资产路径 */
  const resolvedApiPath = ref('')

  /** 节点上直接保存的路径（有则优先，不再请求资产） */
  const inlineApiPath = computed(() => {
    const data = toValue(getData) || {}
    return String(data.apiPath || '').trim()
  })

  /** 需要反查路径时的接口 id；已有 inline 路径时为空 */
  const targetApiId = computed(() => {
    if (inlineApiPath.value) return ''
    const data = toValue(getData) || {}
    return String(data.testProjectApiId ?? '').trim()
  })

  // 接口 id 变化时拉取资产路径；命中缓存直接用；异步返回时校验仍是当前 id，避免错位
  watch(
    targetApiId,
    async (apiId) => {
      if (!apiId) {
        resolvedApiPath.value = ''
        return
      }
      if (apiPathCache.has(apiId)) {
        resolvedApiPath.value = apiPathCache.get(apiId) || ''
        return
      }
      const detail = await resolveCanvasApiDetail(apiId)
      const path = String(detail?.apiPath ?? '').trim()
      apiPathCache.set(apiId, path)
      if (targetApiId.value === apiId) {
        resolvedApiPath.value = path
      }
    },
    { immediate: true },
  )

  const authScope = computed(() => {
    const data = toValue(getData) || {}
    return resolveNodeAuthScope({
      apiPath: inlineApiPath.value || resolvedApiPath.value || undefined,
      headers: data.headers,
      extracts: data.extracts,
      authConfig: store.projectAuthConfig,
    })
  })
  const authLabel = computed(() => formatNodeAuthScopeLabel(authScope.value))
  return { authScope, authLabel }
}
