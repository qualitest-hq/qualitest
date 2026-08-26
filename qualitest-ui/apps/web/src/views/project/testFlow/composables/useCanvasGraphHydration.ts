/**
 * 画布图 JSON 灌入 store 与撤销基线收尾（项目流 / 模板预制流共用）。
 */
import { nextTick } from 'vue'

import type { FromGraphJsonResult } from '../graphAdapter'
import { refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty'
import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/** 将 fromGraphJson 结果写入 store：先 nodes、待灌边，节点就绪后再 flush edges */
export async function applyAdaptedGraphToStore(store: FlowCanvasStore, adapted: FromGraphJsonResult) {
  store.setPendingEdges(adapted.edges)
  store.viewport = adapted.viewport
  store.runConfig = adapted.runConfig
  store.flowOutputs = adapted.flowOutputs
  store.nodes = adapted.nodes
  store.edges = []
  store.selected = null
  store.ui.rightMode = 'props'
  store.clearRunHighlight()
  await nextTick()
  store.flushPendingEdges()
  await nextTick()
  if (store.pendingEdges?.length && store.edges.length === 0) {
    store.flushPendingEdges()
  } else if (store.edges.length > 0) {
    store.pendingEdges = null
  }
}

/** 空图画布无 onNodesInitialized，需立即建立撤销基线 */
export async function finalizeCanvasHistoryBaseline(
  store: FlowCanvasStore,
  resetHistory: () => void,
) {
  await nextTick()
  store.flushPendingEdges()
  if (!store.nodes.length) {
    resetHistory()
    store.pendingHistoryReset = false
    store.endCanvasHydration()
    await refreshSavedBaselineIfPristine(store)
  }
}
