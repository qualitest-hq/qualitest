/**
 * 项目内开放 HTTP 路由占用快照（Pinia）。
 * 供画布校验条提前标红跨流 method+path 冲突；保存时后端仍硬拦。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

import {
  listOpenHttpRoutes,
  type OpenHttpPeerRouteRecord,
} from '@/api/project/testFlow'

export const useOpenHttpRouteStore = defineStore('flowOpenHttpRoute', () => {
  const peers = ref<OpenHttpPeerRouteRecord[]>([])
  const loading = ref(false)
  const lastProjectId = ref('')

  /**
   * 拉取项目内已占用路由。
   * 无项目 id 时清空。
   */
  async function refresh(testProjectId: string | number | null | undefined) {
    const projectId = testProjectId != null ? String(testProjectId).trim() : ''
    if (!projectId) {
      peers.value = []
      lastProjectId.value = ''
      return
    }
    loading.value = true
    lastProjectId.value = projectId
    try {
      const res = await listOpenHttpRoutes(projectId)
      if (lastProjectId.value !== projectId) return
      const list = Array.isArray(res?.data) ? res.data : []
      peers.value = list as OpenHttpPeerRouteRecord[]
    } catch {
      if (lastProjectId.value === projectId) {
        peers.value = []
      }
    } finally {
      loading.value = false
    }
  }

  function clear() {
    peers.value = []
    lastProjectId.value = ''
  }

  return {
    peers,
    loading,
    lastProjectId,
    refresh,
    clear,
  }
})
