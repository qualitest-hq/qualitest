<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" :type="type">
    <div class="flow-node__summary">{{ summaryText }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** 入口节点卡片：场景 / HTTP / 子流入口 */
import { computed } from 'vue'

import { formatNodeSummary } from '../utils/nodeDataUtils'
import BaseFlowNode from './BaseFlowNode.vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  type: { type: String, required: true },
})

const summaryText = computed(() => formatNodeSummary(props.type, props.data ?? {}))
</script>
