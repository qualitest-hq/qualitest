<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="assert">
    <FlowChipList
        v-if="rules.length"
        :format-item="formatRule"
        :items="rules"
        more-unit="条"
    />
    <div v-else class="flow-node__summary">点击右栏配置断言</div>
  </BaseFlowNode>
</template>

<script setup>
/** 断言节点卡片：规则 chip 列表 */
import { computed } from 'vue'

import FlowChipList from '../components/ui/FlowChipList.vue'
import BaseFlowNode from './BaseFlowNode.vue'
import { formatAssertRule, getAssertRules } from '../utils/nodeDataUtils'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const rules = computed(() => getAssertRules(props.data).filter((r) => String(r.left || '').trim()))

function formatRule(r) {
  return formatAssertRule(r)
}
</script>
