/**
 * 测 HTTP 节点鉴权标签：消费画布 AUTH issues + 节点托管头。
 * 边界：mock useFlowValidation；不再反查 apiPath / pathPrefix。
 * 单跑：pnpm test useNodeAuthScope   （在 qualitest-ui 或 apps/web 下）
 */
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setupFreshPinia } from '@/test/helpers/pinia'

const issuesRef = ref<Array<{ level: 'error' | 'warning'; message: string; nodeIds: string[]; code?: string }>>([])

vi.mock('@/views/project/testFlow/composables/useFlowValidation', () => ({
  useFlowValidation: () => ({
    issues: computed(() => issuesRef.value),
    issueNodeIds: computed(() => new Set<string>()),
    serverIssues: ref([]),
  }),
  resetFlowValidationForTests: () => {
    issuesRef.value = []
  },
}))

import {
  clearNodeAuthApiPathCache,
  useNodeAuthScope,
} from '@/views/project/testFlow/composables/useNodeAuthScope'

describe('useNodeAuthScope', () => {
  beforeEach(() => {
    setupFreshPinia()
    clearNodeAuthApiPathCache()
    issuesRef.value = []
  })

  it('托管头展示凭证；无 AUTH issue 时不冲突', () => {
    // 前提：节点有客户端托管头；校验条无 AUTH
    const node = ref({
      id: 'n1',
      data: {
        headers: [
          {
            name: 'token',
            value: '{{asset.clientAuth.data}}',
            profileManaged: true,
          },
        ],
      },
    })

    const { authScope, authLabel } = useNodeAuthScope(node)

    // 期望：凭证来自托管头
    expect(authScope.value.expectedCredential).toBe('asset.clientAuth.data')
    expect(authScope.value.conflict).toBe(false)
    expect(authLabel.value).toBe('凭证 asset.clientAuth.data')
  })

  it('本节点 AUTH issue 时标 conflict', () => {
    // 前提：画布 issue 挂本节点
    issuesRef.value = [{
      level: 'error',
      code: 'RUN_READINESS_AUTH',
      message: 'AUTH_TOKEN_MISSING: 缺少凭证来源',
      nodeIds: ['n1'],
    }]
    const node = ref({
      id: 'n1',
      data: {
        headers: [
          {
            name: 'Authorization',
            value: 'Bearer {{asset.adminAuth.token}}',
            profileManaged: true,
          },
        ],
      },
    })

    const { authScope, authLabel } = useNodeAuthScope(node)

    expect(authScope.value.conflict).toBe(true)
    expect(authScope.value.conflictReason).toContain('缺少凭证来源')
    expect(authLabel.value).toBe('凭证 asset.adminAuth.token')
  })
})
