<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="script">
    <div class="script-node__meta">
      <span class="script-node__lang">{{ languageLabel }}</span>
    </div>
    <div class="flow-node__summary">{{ previewText }}</div>
  </BaseFlowNode>
</template>

<script setup>
/** 脚本节点卡片：展示语言与源码摘要 */
import { computed } from 'vue'

import BaseFlowNode from './BaseFlowNode.vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const languageLabel = computed(() => {
  const lang = String(props.data.language || 'javascript')
  return lang === 'python' ? 'Python' : 'JavaScript'
})

const previewText = computed(() => {
  if (props.data.summary) return String(props.data.summary)
  const source = String(props.data.source || '').trim()
  if (!source) return '未配置源码'
  const firstLine = source.split(/\r?\n/)[0] || source
  return firstLine.length > 48 ? `${firstLine.slice(0, 48)}…` : firstLine
})
</script>

<style scoped lang="scss">
@use '../styles/flowCanvasTokens.scss' as flow;

.script-node__meta {
  margin-bottom: 4px;
}

.script-node__lang {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  @include flow.flow-node-chip-accent;
}
</style>
