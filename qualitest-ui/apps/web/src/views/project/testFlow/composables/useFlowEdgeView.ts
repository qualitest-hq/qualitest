/**
 * 两种连线共用的绘制：正交路径、标签位置、描边。
 * 点中节点时，接到该节点的线在这里加上流动。
 * 标签文字由各连线组件自己决定。
 */
import { computed } from 'vue'

import { useConnectedEdgeFlow } from './useConnectedEdgeFlow'
import { useFlowEdgePath, type FlowEdgePathProps } from './useFlowEdgePath'
import { hasStagingConfirmError, useStagingMark, useStagingUnitByMark } from './usePendingStagingUnit'
import { applyEdgeFlow } from '../utils/flowEdgeFlow'
import { defaultEdgeStroke, resolveStagingEdgeStroke } from '../utils/stagingEdgeStyle'

export function useFlowEdgeView(props: FlowEdgePathProps & { id: string; selected: boolean }) {
  const path = useFlowEdgePath(props)
  const flowing = useConnectedEdgeFlow(() => props.id)
  const stagingMark = useStagingMark(computed(() => props.id), 'edge')
  const stagingUnit = useStagingUnitByMark(stagingMark)

  /** 标签放在折线中点略上方，避免盖住线 */
  const labelStyle = computed(() => ({
    position: 'absolute',
    transform: `translate(-50%, -50%) translate(${path.value[1]}px, ${path.value[2] - 18}px)`,
    pointerEvents: 'all',
  }))

  /** 待确认样式优先；否则用默认描边。接到选中节点时再叠上流动 */
  const edgeStyle = computed(() => {
    const stagingStroke = resolveStagingEdgeStroke(
      stagingMark.value,
      hasStagingConfirmError(stagingUnit.value),
      props.selected,
    )
    return applyEdgeFlow(stagingStroke ?? defaultEdgeStroke(props.selected), flowing.value)
  })

  return { path, labelStyle, edgeStyle, stagingMark }
}
