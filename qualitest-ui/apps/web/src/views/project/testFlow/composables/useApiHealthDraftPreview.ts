/**
 * 画布预检调度：API 语义 + 运行风险（鉴权/必填）。
 *
 * 监听节点/边/运行配置/Staging 变化，防抖后用当前页面图请求预检，
 * 结果分别写入 apiHealthStore 与 runRiskStore，刷新左上角校验条。
 * 打开画布、加载完成后应主动 runPreview 一次。
 * 加载流期间 setSuspended(true)，避免写节点触发的 watch 与主动预检叠成两次请求。
 */
import { onBeforeUnmount, watch, type Ref } from 'vue'

import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunRiskStore } from '../stores/runRiskStore'

/** 防抖间隔（毫秒）：连续拖拽/编辑时合并为一次预检 */
const PREVIEW_DEBOUNCE_MS = 400

/**
 * @param testFlowId 当前路由上的测试流 id（响应式）
 */
export function useApiHealthDraftPreview(testFlowId: Ref<string>) {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()
  const apiHealth = useApiHealthStore()
  const runRisk = useRunRiskStore()

  let timer: ReturnType<typeof setTimeout> | null = null
  /** true 时忽略 watch，避免加载期重复预检 */
  let suspended = false

  function clearTimer() {
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  /**
   * 暂停/恢复自动预检。
   * 加载测试流前后应暂停，加载完成主动 runPreview 后再恢复。
   */
  function setSuspended(value: boolean) {
    suspended = value
    if (value) {
      clearTimer()
    }
  }

  /**
   * 立刻用当前页面图做一次预检（取消已排队的定时预检）。
   * 同时刷新 API 语义告警与运行风险（鉴权/必填）列表。
   */
  async function runPreview() {
    clearTimer()
    if (store.canvasMode === 'template') {
      apiHealth.clear()
      runRisk.clear()
      return
    }
    const id = String(testFlowId.value || store.testFlowId || '').trim()
    if (!id || id.startsWith('tpl-')) {
      apiHealth.clear()
      runRisk.clear()
      return
    }
    const graph = buildCanvasPersistGraph()
    await Promise.all([
      apiHealth.preview(id, graph),
      store.testProjectId
        ? runRisk.preview(String(store.testProjectId), graph)
        : Promise.resolve(runRisk.clear()),
    ])
  }

  /** 排队一次预检：安静 PREVIEW_DEBOUNCE_MS 后再请求 */
  function schedulePreview() {
    if (suspended) return
    clearTimer()
    timer = setTimeout(() => {
      timer = null
      if (suspended) return
      void runPreview()
    }, PREVIEW_DEBOUNCE_MS)
  }

  // 图或 Staging 变化时重新预检
  watch(
    () => [
      store.nodes,
      store.edges,
      store.runConfig,
      store.flowOutputs,
      stagingStore.unitsById,
      testFlowId.value,
    ],
    () => {
      if (suspended) return
      if (store.canvasMode === 'template') return
      const id = String(testFlowId.value || store.testFlowId || '').trim()
      if (!id || id.startsWith('tpl-')) return
      schedulePreview()
    },
    { deep: true },
  )

  onBeforeUnmount(() => {
    clearTimer()
  })

  return {
    runPreview,
    schedulePreview,
    setSuspended,
  }
}
