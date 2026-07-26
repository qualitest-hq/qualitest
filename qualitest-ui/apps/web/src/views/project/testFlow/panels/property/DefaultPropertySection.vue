<template>
  <div class="prop-summary">{{ node.data.summary || '—' }}</div>
</template>

<script setup>
/** 尚未单独实现属性编辑器的节点类型（当前仅 assign）的占位展示 */
defineProps({
  node: { type: Object, required: true },
})
</script>
