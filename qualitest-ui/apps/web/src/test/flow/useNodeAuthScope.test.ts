/**
 * 测 HTTP 节点鉴权标签：无 apiPath 时按接口 id 反查路径再匹配 Profile。
 * 边界：mock 接口详情；Pinia 注入项目鉴权配置。
 * 单跑：pnpm test useNodeAuthScope   （在 qualitest-ui 或 apps/web 下）
 */
import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setupFreshPinia } from '@/test/helpers/pinia'
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore'

const resolveCanvasApiDetailMock = vi.fn()

vi.mock('@/views/project/testFlow/utils/resolveCanvasApiDetail', () => ({
  resolveCanvasApiDetail: (...args: unknown[]) => resolveCanvasApiDetailMock(...args),
}))

import {
  clearNodeAuthApiPathCache,
  useNodeAuthScope,
} from '@/views/project/testFlow/composables/useNodeAuthScope'

const dualAuthJson = JSON.stringify({
  authProfiles: [
    {
      id: 'adminBearer',
      name: '管理端 Bearer',
      match: { pathPrefix: ['/system/', '/monitor/'] },
      headerName: 'Authorization',
      headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
      credentialApi: { method: 'POST', path: '/login' },
      apis: [],
    },
    {
      id: 'clientHeader',
      name: '客户端 Header',
      match: { pathPrefix: ['/api/'] },
      headerName: 'token',
      headerValueTemplate: '{{asset.clientAuth.data}}',
      credentialApi: { method: 'POST', path: '/api/login/login' },
      apis: [],
    },
  ],
})

async function flushAuthResolve() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('useNodeAuthScope', () => {
  beforeEach(() => {
    setupFreshPinia()
    clearNodeAuthApiPathCache()
    resolveCanvasApiDetailMock.mockReset()
    const store = useFlowCanvasStore()
    store.projectAuthConfig = dualAuthJson
  })

  it('薄节点无 apiPath 时反查 /api 路径，期望客户端凭证且无冲突', async () => {
    // 前提：节点无 apiPath，仅有接口 id；详情返回 /api/login/getPhoneByToken；托管头为客户端凭证
    resolveCanvasApiDetailMock.mockResolvedValue({
      apiPath: '/api/login/getPhoneByToken',
    })
    const data = ref<Record<string, unknown>>({
      testProjectApiId: 'api-probe-1',
      headers: [
        {
          name: 'token',
          value: '{{asset.clientAuth.data}}',
          profileManaged: true,
        },
      ],
    })

    const { authScope, authLabel } = useNodeAuthScope(data)
    await flushAuthResolve()

    // 期望：期望凭证为客户端，标签无冲突
    expect(resolveCanvasApiDetailMock).toHaveBeenCalledWith('api-probe-1')
    expect(authScope.value.expectedCredential).toBe('asset.clientAuth.data')
    expect(authScope.value.conflict).toBe(false)
    expect(authLabel.value).toBe('凭证 asset.clientAuth.data')
  })

  it('节点已有 apiPath 时不请求资产详情', async () => {
    // 前提：节点已有 apiPath=/system/user/list，同时带接口 id
    const data = ref<Record<string, unknown>>({
      apiPath: '/system/user/list',
      testProjectApiId: 'api-admin-1',
    })

    const { authScope } = useNodeAuthScope(data)
    await flushAuthResolve()

    // 期望：按路径命中管理端凭证，且不发起详情请求
    expect(resolveCanvasApiDetailMock).not.toHaveBeenCalled()
    expect(authScope.value.expectedCredential).toBe('asset.adminAuth.token')
  })
})
