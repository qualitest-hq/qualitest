<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="input">
    <div class="flow-node__summary">{{ summary }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** 画布 Input 卡片：展示 prompt / 字段数量摘要 */
import { computed } from 'vue'

import { formatInputSummary } from '@/utils/flow/inputFields'

import BaseFlowNode from './BaseFlowNode.vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const summary = computed(() => formatInputSummary(props.data || {}))
</script>
