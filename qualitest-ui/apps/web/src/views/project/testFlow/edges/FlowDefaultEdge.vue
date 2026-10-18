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
 * 默认连线渲染：横平竖直的正交折线，支持 Staging 样式与边中点确认浮层。
 * 路径、描边和点中节点后的流动由共用绘制负责，这里只决定标签文字。
 */
import { BaseEdge, EdgeLabelRenderer } from '@vue-flow/core'
import { computed } from 'vue'

import { useFlowEdgeView } from '../composables/useFlowEdgeView'

import AiStagingChrome from '../components/AiStagingChrome.vue'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

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
const { path, labelStyle, edgeStyle, stagingMark } = useFlowEdgeView(props)

const label = computed(() => {
  const edge = store.edges.find((e) => e.id === props.id)
  const text = edge?.label != null ? String(edge.label).trim() : ''
  return text || (props.label ? String(props.label).trim() : '')
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
