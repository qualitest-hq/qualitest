/**
 * 测 HTTP 节点鉴权标签 composable。
 * 覆盖：节点 apiAuthMode+apiPath 命中项目 Profile 展示凭证、本节点鉴权 error 标 conflict、mode=none 显示免登。
 * 单跑：pnpm test useNodeAuthScope
 */
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setupFreshPinia } from '@/test/helpers/pinia'
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore'

const issuesRef = ref<Array<{ level: 'error' | 'warning'; message: string; nodeIds: string[]; code?: string }>>([])

vi.mock('@/views/project/testFlow/composables/useFlowValidation', () => ({
  useFlowValidation: () => ({
    issues: computed(() => issuesRef.value),
    errorNodeIds: computed(() => new Set<string>()),
    warningNodeIds: computed(() => new Set<string>()),
    errorCount: computed(() => 0),
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
    const store = useFlowCanvasStore()
    store.projectAuthConfig = JSON.stringify({
      authProfiles: [{
        id: 'clientBearer',
        name: '客户端 Bearer',
        match: { pathPrefix: ['/api/'] },
        headerName: 'Authorization',
        headerValueTemplate: 'Bearer {{asset.clientAuth.token}}',
        apis: [],
      }],
    })
  })

  it('inherit + path 命中 Profile 时展示凭证', () => {
    const node = ref({
      id: 'n1',
      data: {
        apiAuthMode: 'inherit',
        apiPath: '/api/cart',
      },
    })

    const { authScope, authLabel } = useNodeAuthScope(node)

    expect(authScope.value.expectedCredential).toBe('asset.clientAuth.token')
    expect(authScope.value.conflict).toBe(false)
    expect(authLabel.value).toBe('头 asset.clientAuth.token')
  })

  it('本节点 AUTH issue 时标 conflict', () => {
    issuesRef.value = [{
      level: 'error',
      code: 'RUN_READINESS_AUTH',
      message: 'AUTH_LOGIN_FLOWKEY_COLLISION: 登录键碰撞',
      nodeIds: ['n1'],
    }]
    const node = ref({
      id: 'n1',
      data: {
        apiAuthMode: 'inherit',
        apiPath: '/api/cart',
      },
    })

    const { authScope, authLabel } = useNodeAuthScope(node)

    expect(authScope.value.conflict).toBe(true)
    expect(authScope.value.conflictReason).toContain('登录键碰撞')
    expect(authLabel.value).toBe('头 asset.clientAuth.token')
  })

  it('apiAuthMode=none 显示免登', () => {
    const node = ref({
      id: 'n1',
      data: { apiAuthMode: 'none', apiPath: '/login' },
    })
    const { authLabel } = useNodeAuthScope(node)
    expect(authLabel.value).toBe('免登')
  })
})
