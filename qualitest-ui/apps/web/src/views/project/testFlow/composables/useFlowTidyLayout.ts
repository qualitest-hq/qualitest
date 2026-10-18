/**
 * 顶栏排版。
 * 主按钮用这条流已记住的方式重排；从菜单另选一种时，先记住再重排。
 * 默认排版用内置的从左往右分层并折行；横向分层、横向折行、纵向分层用分层引擎。
 * 连线按连接点画横平竖直的正交折线，不把拐点写进图。
 * 纵向会先把句柄改成上入下出，再量尺寸、再排坐标。
 * 先记入撤销，再写入坐标并标成未保存，最后缩放以看全节点。
 * 只换排版不算内容修改，不在这里占用写锁。
 * 模板画布只读，无编辑权限时也不改图，两者都只提示。
 */
import { useVueFlow } from '@vue-flow/core'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { layoutWithElk, type ElkLayoutNode } from '../utils/flowElkLayout'
import { computeLayeredPositions } from '../utils/flowGraphLayeredLayout'
import { presetById, type FlowLayoutPresetId } from '../utils/flowLayoutPreset'
import { applyNodePositions } from '../utils/nodePositions'
import { flushVueFlowLayout } from '../utils/waitDoubleAnimationFrame'
import { useFlowCanvasPermissions } from './useFlowCanvasPermissions'
import { useFlowHistory } from './useFlowHistory'
import { useFlowNodeInternalsRefresh } from './useFlowNodeInternalsRefresh'
import { useFlowViewport } from './useFlowViewport'

/** 提供排版方法，以及这条流当前的排版方式 id */
export function useFlowTidyLayout() {
  const store = useFlowCanvasStore()
  const { canEditFlow, isTemplateCanvas } = useFlowCanvasPermissions()
  const { pushHistory } = useFlowHistory()
  const { refreshAllNodeInternals } = useFlowNodeInternalsRefresh()
  const viewport = useFlowViewport()
  const { findNode } = useVueFlow(FLOW_VUE_FLOW_ID)
  const layoutPresetId = computed(() => store.layoutPresetId)

  /**
   * 按指定方式重排整张图。
   * 未传方式时用这条流已记住的；传入则先记住再排，并标成未保存。
   * 依次：校验能否编辑，按方向摆句柄，刷新节点尺寸，计算坐标，记入撤销，写入坐标，缩放视图。
   */
  async function tidyLayout(presetId?: FlowLayoutPresetId) {
    if (!canEditFlow.value || isTemplateCanvas.value) {
      ElMessage.warning(isTemplateCanvas.value ? '模板画布不支持排版' : '当前账号无编辑权限')
      return
    }
    const nodes = store.nodes
    if (!nodes.length) {
      ElMessage.info('画布暂无节点')
      return
    }

    const preset = presetById(presetId ?? layoutPresetId.value)
    store.setLayoutPreset(preset.id)
    // 纵向先改句柄方向，再量宽高，避免线仍从左右侧拉出
    await flushVueFlowLayout()
    refreshAllNodeInternals(nodes.map((n) => n.id))
    await flushVueFlowLayout()

    const layoutNodes: ElkLayoutNode[] = nodes.map((n) => {
      const vf = findNode(n.id)
      const w = vf?.dimensions?.width
      const h = vf?.dimensions?.height
      const data = n.data as Record<string, unknown> | undefined
      const rawBranches = data?.branches
      const branchIds = Array.isArray(rawBranches)
        ? rawBranches
          .map((branch) => {
            if (!branch || typeof branch !== 'object') return ''
            const id = (branch as { id?: unknown }).id
            return id != null && String(id).trim() ? `out-${String(id)}` : ''
          })
          .filter((id) => id)
        : []
      return {
        id: n.id,
        type: typeof n.type === 'string' ? n.type : undefined,
        width: typeof w === 'number' && w > 0 ? w : undefined,
        height: typeof h === 'number' && h > 0 ? h : undefined,
        branchCount: n.type === 'condition' ? branchIds.length : undefined,
        sourcePortOrder: n.type === 'condition' ? branchIds : undefined,
      }
    })
    const layoutEdges = store.getEffectiveEdges().map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: typeof e.sourceHandle === 'string' ? e.sourceHandle : undefined,
    }))

    let positions
    let fallback = false
    // 默认排版走内置折行；另外三种走分层引擎，引擎失败时再退回内置折行
    if (preset.engine === 'builtin') {
      positions = computeLayeredPositions(layoutNodes, layoutEdges)
    } else {
      const laid = await layoutWithElk(layoutNodes, layoutEdges, preset)
      positions = laid.positions
      fallback = laid.fallback
    }
    if (!positions || positions.size === 0) return

    pushHistory()
    applyNodePositions(store, positions)
    store.markDirty()

    refreshAllNodeInternals(store.nodes.map((n) => n.id))
    await flushVueFlowLayout()
    await viewport.fitViewAll(0.2)
    ElMessage.success(
      fallback
        ? `已按${preset.label}排版（布局引擎不可用，已用内置排版）`
        : `已按${preset.label}排版`,
    )
  }

  return { tidyLayout, layoutPresetId }
}
