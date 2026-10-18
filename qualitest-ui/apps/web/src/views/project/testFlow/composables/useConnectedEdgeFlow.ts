/**
 * 当前边是否应流动：画布选中了一个节点，且这条边从它出去或连到它。
 */
import { computed, type ComputedRef } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { edgeTouchesNode } from '../utils/flowEdgeFlow'

export function useConnectedEdgeFlow(edgeId: () => string): ComputedRef<boolean> {
  const store = useFlowCanvasStore()
  return computed(() => {
    const selected = store.selected
    if (selected?.kind !== 'node') return false
    const edge = store.getEffectiveEdges().find((item) => item.id === edgeId())
    return edgeTouchesNode(edge, selected.id)
  })
}
