<template>
  <div class="prop-form">
    <div class="field">
      <label>入口说明（可选）</label>
      <textarea :value="node.data?.doc ?? ''" rows="3" @input="(e) => patch({ doc: e.target.value })" />
    </div>
    <div class="field">
      <label>fallbackOut（JSON）</label>
      <textarea :value="fallbackText" rows="8" @change="onFallback" />
      <div class="field__hint">零次 return 时挂到父节点 out</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 子流入口属性：入口说明与 fallbackOut；
 * 子图零次返回时用 fallbackOut 挂到父流对应子流节点的 out。
 */
import { computed } from 'vue'
import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

/** fallbackOut 的 JSON 文本展示 */
const fallbackText = computed(() => {
  try {
    return JSON.stringify(props.node.data?.fallbackOut ?? {}, null, 2)
  } catch {
    return '{}'
  }
})

function patch(p) {
  patchNodeData(props.node.id, p)
}

/** 失焦解析 JSON；非法则忽略本次输入 */
function onFallback(e) {
  try {
    patch({ fallbackOut: JSON.parse(e.target.value || '{}') })
  } catch {
    /* ignore */
  }
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>
