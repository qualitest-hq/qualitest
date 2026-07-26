<template>
  <g>
    <BaseEdge :path="path[0]" :style="edgeStyle" />
    <EdgeLabelRenderer v-if="label || stagingMark">
      <div
          v-if="label"
          :style="labelStyle"
          class="condition-edge__label"
      >
        {{ label }}
      </div>
      <AiStagingChrome
          v-if="stagingMark"
          :midpoint-x="path[1]"
          :midpoint-y="path[2]"
          :unit-id="stagingMark.unitId"
          placement="edge-midpoint"
      />
    </EdgeLabelRenderer>
  </g>
</template>

<script setup>
/**
 * condition 节点出边自定义渲染。
 * 在边中点展示对应分支的 IF / ELIF / ELSE 标签，以及 Staging 确认浮层。
 */
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core'
import { computed } from 'vue'

import AiStagingChrome from '../components/AiStagingChrome.vue'
import { hasStagingConfirmError, useStagingMark, useStagingUnitByMark } from '../composables/usePendingStagingUnit'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { branchKindLabel, getConditionBranches } from '../utils/conditionUtils'
import { defaultEdgeStroke, resolveStagingEdgeStroke } from '../utils/stagingEdgeStyle'

const props = defineProps({
  id: { type: String, required: true },
  source: { type: String, required: true },
  target: { type: String, required: true },
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, required: true },
  targetPosition: { type: String, required: true },
  selected: { type: Boolean, default: false },
})

const store = useFlowCanvasStore()

const path = computed(() =>
  getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    targetX: props.targetX,
    targetY: props.targetY,
    sourcePosition: props.sourcePosition,
    targetPosition: props.targetPosition,
  }),
)

const stagingMark = useStagingMark(computed(() => props.id), 'edge')
const stagingUnit = useStagingUnitByMark(stagingMark)

/** 优先展示用户自定义 label，否则回退为 condition 分支 IF/ELIF/ELSE */
const label = computed(() => {
  const edge = store.edges.find((e) => e.id === props.id)
  const custom = edge?.label != null ? String(edge.label).trim() : ''
  if (custom) return custom

  const srcNode = store.nodes.find((n) => n.id === props.source)
  if (!srcNode || srcNode.type !== 'condition') return ''
  const branches = getConditionBranches(srcNode.data)
  const branch = branches.find((b) => b.target === props.target)
  return branch ? branchKindLabel(branch) : ''
})

const labelStyle = computed(() => ({
  position: 'absolute',
  transform: `translate(-50%, -50%) translate(${path.value[1]}px, ${path.value[2] - 18}px)`,
  pointerEvents: 'all',
}))

const edgeStyle = computed(() => {
  const stagingStroke = resolveStagingEdgeStroke(
    stagingMark.value,
    hasStagingConfirmError(stagingUnit.value),
    props.selected,
  )
  return stagingStroke ?? defaultEdgeStroke(props.selected)
})
</script>

<style scoped lang="scss">
.condition-edge__label {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #c2410c;
  background: #fff;
  border: 1px solid color-mix(in srgb, #c2410c 30%, #c5d8ec);
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(20, 60, 120, 0.08);
}
</style>
