/**
 * 画布实时图结构校验。
 *
 * 把当前可落盘的 graph 交给 validateGraphJson，检查缺开始节点、HTTP 未绑定、条件分支悬空等，
 * 结果展示在左上角校验条。不含「接口是否仍存在」等语义检查。
 *
 * 若存在未确认的连线 Staging，则延后开始节点唯一性硬拦，避免过滤图误报多入口/无入口。
 */
import { computed } from 'vue'

import { validateGraphJson } from '@/utils/flow/graphValidate'

import { useAiStagingStore } from '../stores/aiStagingStore'
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

export function useFlowValidation() {
  const stagingStore = useAiStagingStore()

  /** 当前画布结构校验结果：errors 阻断类，warnings 提示类 */
  const validation = computed(() => {
    void stagingStore.unitsById
    const deferTopologyStructureRules = hasPendingStagingEdgeUnits(
      Object.values(stagingStore.unitsById),
    )
    return validateGraphJson(buildCanvasPersistGraph(), { deferTopologyStructureRules })
  })

  /** 是否存在任意结构错误或警告 */
  const hasIssues = computed(
    () => validation.value.errors.length > 0 || validation.value.warnings.length > 0,
  )

  return {
    validation,
    hasIssues,
  }
}
