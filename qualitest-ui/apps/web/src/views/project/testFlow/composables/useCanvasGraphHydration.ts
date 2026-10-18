/**
 * 画布图 JSON 灌入 store 与撤销基线收尾（项目流 / 模板预制流共用）。
 */
import { nextTick } from 'vue'

import type { FromGraphJsonResult } from '../graphAdapter'
import { refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty'
import type { useFlowCanvasStore } from '../stores/flowCanvasStore'

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>

/**
 * 将读出的图写入画布。
 * 排版方式只记到 store，用来决定顶栏名称和句柄方向，不在这里重排节点。
 */
export async function applyAdaptedGraphToStore(store: FlowCanvasStore, adapted: FromGraphJsonResult) {
  store.setPendingEdges(adapted.edges)
  // 先于节点写入，避免卡片先按横向句柄画出来再改方向
  store.setLayoutPreset(adapted.layoutPreset)
  store.viewport = adapted.viewport
  store.runConfig = adapted.runConfig
  store.flowOutputs = adapted.flowOutputs
  store.nodes = adapted.nodes
  store.edges = []
  store.selected = null
  store.ui.rightMode = 'props'
  store.clearRunHighlight()
  store.bumpStagingEdgeFlushToken()
}

/** 空图画布无 onNodesInitialized，需立即建立撤销基线 */
export async function finalizeCanvasHistoryBaseline(
  store: FlowCanvasStore,
  resetHistory: () => void,
) {
  await nextTick()
  store.bumpStagingEdgeFlushToken()
  if (!store.nodes.length) {
    resetHistory()
    store.pendingHistoryReset = false
    store.endCanvasHydration()
    await refreshSavedBaselineIfPristine(store)
  }
}
