/**
 * 测试流画布「API 语义」告警状态（Pinia）。
 *
 * 职责：
 * - 保存当前页面图预检得到的告警列表，驱动左上角校验条与节点属性/配置弹窗内联提示
 * - 预检只读页面 graphJson，不要求先保存；仪表盘用的落库告警由保存接口另写
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  previewTestFlowApiHealth,
  type FlowApiHealthWarning,
} from '@/api/project/testFlow'

export const useApiHealthStore = defineStore('flowApiHealth', () => {
  /** 当前页面图预检得到的告警列表 */
  const warnings = ref<FlowApiHealthWarning[]>([])
  /** 是否正在请求预检接口 */
  const loading = ref(false)
  /** 最近一次预检针对的测试流 id（用于忽略过期的异步回包） */
  const lastFlowId = ref('')

  /** 校验条展示用：从告警对象抽出可读文案 */
  const messages = computed(() =>
    warnings.value
      .map((w) => w.message || w.detail || w.code || '')
      .filter((m) => !!m),
  )

  /**
   * 按画布节点 id 过滤告警，供属性面板 / HTTP 配置弹窗内联展示。
   */
  function warningsForNode(nodeId: string | undefined | null) {
    if (!nodeId) return [] as FlowApiHealthWarning[]
    const id = String(nodeId)
    return warnings.value.filter((w) => w.nodeId != null && String(w.nodeId) === id)
  }

  /**
   * 请求后端对给定 graphJson 做语义预检，并更新本地 warnings。
   *
   * @param testFlowId 测试流 id
   * @param graphJson 当前画布序列化结果（字符串或对象）
   */
  async function preview(testFlowId: string, graphJson: string | object) {
    if (!testFlowId) {
      warnings.value = []
      lastFlowId.value = ''
      return
    }
    loading.value = true
    lastFlowId.value = testFlowId
    try {
      const res = await previewTestFlowApiHealth(testFlowId, graphJson)
      const data = res?.data
      // 快速切换测试流时，丢弃已过期请求的回包
      if (lastFlowId.value === testFlowId) {
        warnings.value = Array.isArray(data?.warnings) ? data.warnings : []
      }
    } catch {
      if (lastFlowId.value === testFlowId) {
        warnings.value = []
      }
    } finally {
      loading.value = false
    }
  }

  /** 离开画布或重新初始化时清空告警状态 */
  function clear() {
    warnings.value = []
    lastFlowId.value = ''
  }

  return {
    warnings,
    loading,
    lastFlowId,
    messages,
    warningsForNode,
    preview,
    clear,
  }
})
