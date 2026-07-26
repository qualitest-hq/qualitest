<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="delay">
    <div class="flow-node__summary">{{ data.ms || 0 }} ms</div>
  </BaseFlowNode>
</template>

<script setup>
/** 延时节点卡片：展示等待毫秒数 */
import BaseFlowNode from './BaseFlowNode.vue'

defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
</script>
