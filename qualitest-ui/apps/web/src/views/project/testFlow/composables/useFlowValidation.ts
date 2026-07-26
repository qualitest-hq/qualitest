/**
 * 画布实时图结构校验。
 *
 * 把当前页面可落盘的 graph_json 交给 validateGraphJson，
 * 检查缺开始节点、HTTP 未绑定、条件分支悬空等结构问题，
 * 结果给左上角校验条上半部分（errors / warnings）展示。
 * 不包含 API 是否仍存在等语义检查（那部分由预检接口负责）。
 */
import { computed } from 'vue'

import { validateGraphJson } from '@/utils/flow/graphValidate'

import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

export function useFlowValidation() {
  /** 当前画布结构校验结果：errors 阻断类，warnings 提示类 */
  const validation = computed(() => validateGraphJson(buildCanvasPersistGraph()))

  /** 是否存在任意结构错误或警告 */
  const hasIssues = computed(
    () => validation.value.errors.length > 0 || validation.value.warnings.length > 0,
  )

  return {
    validation,
    hasIssues,
  }
}
