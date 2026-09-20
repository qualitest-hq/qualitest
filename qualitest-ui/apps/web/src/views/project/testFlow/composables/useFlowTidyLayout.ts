/**
 * 顶栏「一键排版」。
 * <p>
 * 按当前画布节点与边重算全部坐标（优先用 Vue Flow 实测宽高），写入撤销栈、标记脏数据，再适应视图。
 * 模板画布或无编辑权限时只提示，不改图。
 */
import { useVueFlow } from '@vue-flow/core'
import { ElMessage } from 'element-plus'

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { computeLayeredPositions, type LayoutNodeRef } from '../utils/flowGraphLayeredLayout'
import { flushVueFlowLayout } from '../utils/waitDoubleAnimationFrame'
import { useFlowCanvasPermissions } from './useFlowCanvasPermissions'
import { useFlowHistory } from './useFlowHistory'
import { useFlowNodeInternalsRefresh } from './useFlowNodeInternalsRefresh'
import { useFlowViewport } from './useFlowViewport'

export function useFlowTidyLayout() {
  const store = useFlowCanvasStore()
  const { canEditFlow, isTemplateCanvas } = useFlowCanvasPermissions()
  const { pushHistory } = useFlowHistory()
  const { refreshAllNodeInternals } = useFlowNodeInternalsRefresh()
  const viewport = useFlowViewport()
  const { findNode } = useVueFlow(FLOW_VUE_FLOW_ID)

  /**
   * 执行一键排版。
   * 流程：权限校验 → 刷新尺寸 → 按实测宽高算坐标 → 入撤销栈 → 写回节点 → 适应视图。
   */
  async function tidyLayout() {
    if (!canEditFlow.value || isTemplateCanvas.value) {
      ElMessage.warning(isTemplateCanvas.value ? '模板画布不支持一键排版' : '当前账号无编辑权限')
      return
    }
    const nodes = store.nodes
    if (!nodes.length) {
      ElMessage.info('画布暂无节点')
      return
    }

    // 量尺寸前：刷新 DOM dimensions
    refreshAllNodeInternals(nodes.map((n) => n.id))
    await flushVueFlowLayout()

    const layoutNodes: LayoutNodeRef[] = nodes.map((n) => {
      const vf = findNode(n.id)
      const w = vf?.dimensions?.width
      const h = vf?.dimensions?.height
      const data = n.data as Record<string, unknown> | undefined
      const branches = data?.branches
      const branchCount = Array.isArray(branches) ? branches.length : undefined
      return {
        id: n.id,
        type: typeof n.type === 'string' ? n.type : undefined,
        width: typeof w === 'number' && w > 0 ? w : undefined,
        height: typeof h === 'number' && h > 0 ? h : undefined,
        branchCount,
      }
    })

    const positions = computeLayeredPositions(
      layoutNodes,
      store.edges.map((e) => ({ source: e.source, target: e.target })),
    )
    if (positions.size === 0) return

    pushHistory()
    store.nodes = nodes.map((n) => {
      const pos = positions.get(n.id)
      if (!pos) return n
      return { ...n, position: { x: pos.x, y: pos.y } }
    })
    store.markDirty()

    // 写坐标后：再刷尺寸并 fitView，避免视口落在旧包围盒上
    refreshAllNodeInternals(store.nodes.map((n) => n.id))
    await flushVueFlowLayout()
    await viewport.fitViewAll(0.2)
    ElMessage.success('已按连线完成一键排版')
  }

  return { tidyLayout }
}
