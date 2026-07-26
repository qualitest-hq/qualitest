<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="subflow">
    <div v-if="data.templateId" class="subflow-node__tpl">📦 {{ data.templateId }}</div>
    <div class="flow-node__summary">{{ summaryText }}</div>
    <div v-if="ioHint" class="subflow-node__io">{{ ioHint }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** 子流节点卡片：模板标识、摘要与输入输出提示 */
import { computed } from 'vue'

import { formatSubflowSummary } from '../utils/nodeDataUtils'
import BaseFlowNode from './BaseFlowNode.vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const summaryText = computed(() => {
  if (props.data.summary) return String(props.data.summary)
  return formatSubflowSummary(props.data)
})

const ioHint = computed(() => {
  const inputs = (props.data.inputs || []).map((i) => i.name).filter(Boolean)
  const outputs = (props.data.outputs || []).map((o) => o.flowKey || o.name).filter(Boolean)
  const parts = []
  if (inputs.length) parts.push(`← ${inputs.join(' · ')}`)
  if (outputs.length) parts.push(`→ flow.${outputs.join(', flow.')}`)
  return parts.join('  ')
})
</script>

<style scoped lang="scss">
.subflow-node__tpl {
  font-size: 10px;
  font-weight: 600;
  color: var(--node-accent-fg);
  margin-bottom: 4px;
}

.subflow-node__io {
  margin-top: 4px;
  font-size: 10px;
  color: var(--pd-text-muted);
  line-height: 1.4;
}
</style>
