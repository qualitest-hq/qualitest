<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="subflow-return">
    <div class="flow-node__summary">{{ summaryText }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** 子流返回节点卡片：展示 out 来源 */
import { computed } from 'vue'

import BaseFlowNode from './BaseFlowNode.vue'
import { formatNodeSummary } from '../utils/nodeDataUtils'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const summaryText = computed(() => formatNodeSummary('subflow-return', props.data))
</script>