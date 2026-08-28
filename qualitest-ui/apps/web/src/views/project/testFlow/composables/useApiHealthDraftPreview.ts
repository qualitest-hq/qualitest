/**
 * 画布 API 语义预检调度。
 *
 * 功能：监听画布节点/边/运行配置/Staging 变化，防抖后把当前页面图发给后端预检，
 * 结果写入 apiHealthStore，从而刷新左上角「API 语义」校验条。
 * 打开画布、手动保存成功后也可立刻调用 runPreview。
 * <p>
 * 加载流期间应 setSuspended(true)，避免 loadFlow 写节点触发 watch，
 * 与 init 结束时的主动 runPreview 叠成两次相同 POST。
 */
import { onBeforeUnmount, watch, type Ref } from 'vue'

import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

/** 防抖间隔（毫秒）：连续拖拽/编辑时合并为一次预检请求 */
const PREVIEW_DEBOUNCE_MS = 400

/**
 * @param testFlowId 当前路由上的测试流 id（响应式）
 */
export function useApiHealthDraftPreview(testFlowId: Ref<string>) {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()
  const apiHealth = useApiHealthStore()

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
   * 立刻用当前页面图做一次预检（跳过防抖，并取消已排队的定时预检）。
   * 典型时机：画布加载完成、用户点击保存成功后。
   */
  async function runPreview() {
    clearTimer()
    if (store.canvasMode === 'template') {
      apiHealth.clear()
      return
    }
    const id = String(testFlowId.value || store.testFlowId || '').trim()
    if (!id || id.startsWith('tpl-')) {
      apiHealth.clear()
      return
    }
    await apiHealth.preview(id, buildCanvasPersistGraph())
  }

  /** 排队一次预检：重置定时器，安静 PREVIEW_DEBOUNCE_MS 后再请求 */
  function schedulePreview() {
    if (suspended) return
    clearTimer()
    timer = setTimeout(() => {
      timer = null
      if (suspended) return
      void runPreview()
    }, PREVIEW_DEBOUNCE_MS)
  }

  // 图结构或 Staging 待确认项变化时重新预检
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
