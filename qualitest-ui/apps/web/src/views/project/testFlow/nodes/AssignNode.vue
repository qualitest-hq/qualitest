<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="assign">
    <FlowChipList
        v-if="assignments.length"
        :format-item="formatAssignment"
        :items="assignments"
        more-unit="条"
    />
    <div v-else class="flow-node__summary">点击右栏配置赋值</div>
  </BaseFlowNode>
</template>

<script setup>
/** 赋值节点卡片：变量写入 chip 列表 */
import { computed } from 'vue'

import FlowChipList from '../components/ui/FlowChipList.vue'
import BaseFlowNode from './BaseFlowNode.vue'
import { formatAssignAssignment, getAssignAssignments } from '../utils/nodeDataUtils'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const assignments = computed(() => getAssignAssignments(props.data))

function formatAssignment(a) {
  return formatAssignAssignment(a)
}
</script>
