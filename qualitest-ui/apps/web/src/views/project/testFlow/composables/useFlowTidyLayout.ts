/**
 * 顶栏「一键排版」。
 * <p>
 * 按当前画布的节点与连线重新计算全部节点坐标（优先用节点实际渲染宽高），
 * 先记入撤销栈，再写入坐标、标记为未保存，最后缩放视图显示全部节点。
 * 模板画布或无编辑权限时只提示，不改图。
 */
import { useVueFlow } from '@vue-flow/core'
import { ElMessage } from 'element-plus'

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { computeLayeredPositions, type LayoutNodeRef } from '../utils/flowGraphLayeredLayout'
import { applyNodePositions } from '../utils/nodePositions'
import { flushVueFlowLayout } from '../utils/waitDoubleAnimationFrame'
import { useFlowCanvasPermissions } from './useFlowCanvasPermissions'
import { useFlowHistory } from './useFlowHistory'
import { useFlowNodeInternalsRefresh } from './useFlowNodeInternalsRefresh'
import { useFlowViewport } from './useFlowViewport'

/** 提供一键排版方法 */
export function useFlowTidyLayout() {
  const store = useFlowCanvasStore()
  const { canEditFlow, isTemplateCanvas } = useFlowCanvasPermissions()
  const { pushHistory } = useFlowHistory()
  const { refreshAllNodeInternals } = useFlowNodeInternalsRefresh()
  const viewport = useFlowViewport()
  const { findNode } = useVueFlow(FLOW_VUE_FLOW_ID)

  /**
   * 执行一键排版。
   * 依次：校验权限，刷新节点尺寸，按实际宽高和连线计算坐标，记入撤销栈，写入坐标，缩放视图显示全部节点。
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

    // 先刷新节点尺寸，保证下面读到的宽高是最新渲染结果
    refreshAllNodeInternals(nodes.map((n) => n.id))
    await flushVueFlowLayout()

    // 每个节点的类型、实际宽高、条件分支数，作为排版输入
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

    // 按画布上实际生效的连线分层计算坐标
    const positions = computeLayeredPositions(
      layoutNodes,
      store.getEffectiveEdges().map((e) => ({ source: e.source, target: e.target })),
    )
    if (positions.size === 0) return

    // 记入撤销栈后写入新坐标，并标记为未保存
    pushHistory()
    applyNodePositions(store, positions)
    store.markDirty()

    // 写入坐标后再刷新尺寸，然后缩放视图显示全部节点
    refreshAllNodeInternals(store.nodes.map((n) => n.id))
    await flushVueFlowLayout()
    await viewport.fitViewAll(0.2)
    ElMessage.success('已按连线完成一键排版')
  }

  return { tidyLayout }
}
