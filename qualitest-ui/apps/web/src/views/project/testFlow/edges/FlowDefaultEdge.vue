<template>
  <g>
    <BaseEdge :path="path[0]" :style="edgeStyle" />
    <EdgeLabelRenderer v-if="label || stagingMark">
      <div
          v-if="label"
          :style="labelStyle"
          class="flow-default-edge__label"
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
 * 默认连线渲染：支持 Staging 样式与边中点确认浮层。
 */
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core'
import { computed } from 'vue'

import AiStagingChrome from '../components/AiStagingChrome.vue'
import { hasStagingConfirmError, useStagingMark, useStagingUnitByMark } from '../composables/usePendingStagingUnit'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { defaultEdgeStroke, resolveStagingEdgeStroke } from '../utils/stagingEdgeStyle'

const props = defineProps({
  id: { type: String, required: true },
  label: { type: String, default: '' },
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

const label = computed(() => {
  const edge = store.edges.find((e) => e.id === props.id)
  const text = edge?.label != null ? String(edge.label).trim() : ''
  return text || (props.label ? String(props.label).trim() : '')
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
.flow-default-edge__label {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  background: #fff;
  border: 1px solid #e2e8f0;
  white-space: nowrap;
}
</style>
