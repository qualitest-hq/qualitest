/**
 * 运行风险告警状态（Pinia）。
 *
 * 只存放鉴权凭证、登录抽取、HTTP 必填等预检文案。
 * 图结构与断言路径错误由画布实时结构校验单独展示，不写入本 store。
 * 不硬拦保存；开跑前若仍有内容则应拦截。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

import { fetchSavePrecheckErrors } from '../utils/runReadiness'

export const useRunRiskStore = defineStore('flowRunRisk', () => {
  /** 当前画布的运行风险文案列表 */
  const warnings = ref<string[]>([])
  /** 是否正在请求预检 */
  const loading = ref(false)
  /** 最近一次预检对应的项目 id，用于丢弃过期回包 */
  const lastProjectId = ref('')

  /**
   * 对给定图跑鉴权/必填预检，结果写入 warnings。
   * 无项目 id 时清空。
   */
  async function preview(testProjectId: string, graphJson: string | object) {
    const projectId = String(testProjectId || '').trim()
    if (!projectId) {
      warnings.value = []
      lastProjectId.value = ''
      return
    }
    loading.value = true
    lastProjectId.value = projectId
    try {
      const next = await fetchSavePrecheckErrors(projectId, graphJson)
      if (lastProjectId.value === projectId) {
        warnings.value = next
      }
    } catch {
      if (lastProjectId.value === projectId) {
        warnings.value = []
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 直接替换风险列表（不发请求）。
   * 用于确认响应带回的预警，或开跑前本地已算好的 precheck 子集。
   */
  function setWarnings(messages: string[] | undefined | null) {
    warnings.value = Array.isArray(messages) ? [...messages] : []
  }

  /** 离开画布或重置时清空 */
  function clear() {
    warnings.value = []
    lastProjectId.value = ''
  }

  return {
    warnings,
    loading,
    lastProjectId,
    preview,
    setWarnings,
    clear,
  }
})
