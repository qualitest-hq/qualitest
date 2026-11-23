<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="http-return">
    <div class="flow-node__summary">{{ summaryText }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** HTTP 返回节点卡片：展示 out 来源与 status */
import { computed } from 'vue'

import BaseFlowNode from './BaseFlowNode.vue'
import { formatNodeSummary } from '../utils/nodeDataUtils'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const summaryText = computed(() => formatNodeSummary('http-return', props.data))
</script>