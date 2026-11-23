<template>
  <div class="prop-form">
    <div class="field">
      <label>out 来源</label>
      <input
        :value="node.data?.from ?? 'flow.out'"
        placeholder="flow.out"
        type="text"
        @input="(e) => patch({ from: e.target.value })"
      />
      <div class="field__hint">相当于 out = 该路径对象，再挂到父流对应子流节点的 out</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 子流返回节点属性：from 指定写入 out 的来源路径（默认 flow.out），
 * 交付时挂到父图画布上对应子流节点的 out，不写 HTTP status。
 */
import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

function patch(p) {
  patchNodeData(props.node.id, p)
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>