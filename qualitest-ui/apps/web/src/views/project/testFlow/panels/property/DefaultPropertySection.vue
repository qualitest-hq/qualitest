<template>
  <div class="prop-summary">{{ summary }}</div>
</template>

<script setup>
import { computed } from 'vue'

import { formatNodeSummary } from '../../utils/nodeDataUtils'

/** 尚未单独实现属性编辑器的节点类型的占位展示 */
const props = defineProps({
  node: { type: Object, required: true },
})

const summary = computed(() => {
  const type = String(props.node?.type ?? '');
  const text = formatNodeSummary(type, props.node?.data ?? {});
  return text || '—';
})
</script>