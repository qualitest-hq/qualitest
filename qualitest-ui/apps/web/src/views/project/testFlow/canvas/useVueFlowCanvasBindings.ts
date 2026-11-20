/**
 * VueFlow 画布事件绑定。
 * 负责：节点选中与空白点击、拖移超过阈值才记入撤销历史、增删改标脏、
 * 节点初始化后灌边并恢复视口。供画布布局壳装配，不掺杂顶栏与侧栏 UI。
 */
import { nextTick, ref } from 'vue'

import { refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty'
import { waitDoubleAnimationFrame } from '../utils/waitDoubleAnimationFrame'
import { nodeChangeAffectsPersist } from '../utils/nodeChangeAffectsPersist'
import { useFlowHistory } from '../composables/useFlowHistory'
import { useFlowNodeInternalsRefresh } from '../composables/useFlowNodeInternalsRefresh'
import { useFlowViewport } from '../composables/useFlowViewport'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

const DRAG_MOVE_THRESHOLD = 3

export function useVueFlowCanvasBindings(options?: {
  /** 点空白时额外清理（如关闭右键菜单） */
  onPaneClickExtra?: () => void
}) {
  const store = useFlowCanvasStore()
  const viewport = useFlowViewport()
  const { refreshAllNodeInternals } = useFlowNodeInternalsRefresh()
  const { pushHistory, commitHistoryResetIfPending } = useFlowHistory()

  /** 用户主动拖移节点时为 true，避免初始化阶段误记入历史 */
  const nodeDragActive = ref(false)
  /** 本次拖动各节点的起点；位移超过阈值才清拐点、记撤销 */
  const dragOrigins = new Map<string, { x: number; y: number }>()
  let dragMoved = false

  function onNodesChange(changes: Array<{ type?: string; dragging?: boolean; position?: { x: number; y: number }; id?: string; from?: { x: number; y: number } }>) {
    if (nodeDragActive.value) {
      for (const change of changes) {
        if (change.type !== 'position' || !change.dragging || !change.position || !change.id) continue
        if (!dragOrigins.has(change.id) && change.from) {
          dragOrigins.set(change.id, { x: change.from.x, y: change.from.y })
        }
        const origin = dragOrigins.get(change.id)
        if (!origin) continue
        const dx = change.position.x - origin.x
        const dy = change.position.y - origin.y
        if (dx * dx + dy * dy > DRAG_MOVE_THRESHOLD * DRAG_MOVE_THRESHOLD) {
          dragMoved = true
          break
        }
      }
    }
    // 仅坐标/增删才标脏；dimensions、select 等打开画布就会触发，误标脏会抢占写锁挡住他端写入
    if (nodeChangeAffectsPersist(changes as Parameters<typeof nodeChangeAffectsPersist>[0])) {
      store.markDirty()
    }
  }

  function conditionNodeIds() {
    return store.nodes.filter((n) => n.type === 'condition').map((n) => n.id)
  }

  /**
   * Vue Flow 节点初始化完成后的收尾：
   * 刷新 handle → 灌入待处理边 → 再刷 condition handle → 恢复视口。
   */
  async function onNodesInitialized() {
    commitHistoryResetIfPending()
    store.endCanvasHydration()
    await nextTick()
    await waitDoubleAnimationFrame()
    const condIds = conditionNodeIds()
    refreshAllNodeInternals(condIds.length ? condIds : store.nodes.map((n) => n.id))
    await nextTick()
    await store.ensureEdgesHydrated()
    await waitDoubleAnimationFrame()
    if (condIds.length) {
      refreshAllNodeInternals(condIds)
    }
    await viewport.restoreFromStore()
    await refreshSavedBaselineIfPristine(store)
  }

  function onNodeClick({ node }: { node: { id: string } }) {
    store.selectItem('node', node.id)
  }

  function onEdgeClick({ edge }: { edge: { id: string } }) {
    store.selectItem('edge', edge.id)
  }

  function onNodeDragStart({ node }: { node?: { id?: string; position?: { x: number; y: number } } }) {
    nodeDragActive.value = true
    dragMoved = false
    if (node?.id && node?.position) {
      dragOrigins.set(node.id, { x: node.position.x, y: node.position.y })
    }
  }

  /** 确实挪动过才记入撤销；点一下高亮不记 */
  function onNodeDragStop() {
    if (!nodeDragActive.value) return
    nodeDragActive.value = false
    dragOrigins.clear()
    if (dragMoved) pushHistory()
    dragMoved = false
  }

  function onPaneClick() {
    options?.onPaneClickExtra?.()
    store.clearSelection()
    if (store.ui.rightMode === 'run') return
    store.ui.rightMode = 'props'
    store.ui.rightOpen = false
  }

  return {
    onNodesChange,
    onNodesInitialized,
    onNodeClick,
    onEdgeClick,
    onNodeDragStart,
    onNodeDragStop,
    onPaneClick,
  }
}
