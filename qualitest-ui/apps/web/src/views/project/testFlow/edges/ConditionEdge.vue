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
 * condition 节点出边自定义渲染，路径为横平竖直的正交折线。
 * 在边中点展示对应分支的 IF / ELIF / ELSE 标签，以及 Staging 确认浮层。
 * 路径、描边和点中节点后的流动由共用绘制负责，这里只决定标签文字。
 */
import { BaseEdge, EdgeLabelRenderer } from '@vue-flow/core'
import { computed } from 'vue'

import { useFlowEdgeView } from '../composables/useFlowEdgeView'

import AiStagingChrome from '../components/AiStagingChrome.vue'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { branchKindLabel, getConditionBranches } from '../utils/conditionUtils'

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
const { path, labelStyle, edgeStyle, stagingMark } = useFlowEdgeView(props)

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
