<template>
  <div class="field">
    <label>毫秒</label>
    <input
        :value="node.data.ms || 0"
        min="0"
        step="100"
        type="number"
        @input="onDelayInput"
    />
  </div>
</template>

<script setup>
/** 等待节点：配置延迟毫秒数 */
import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

function onDelayInput(e) {
  patchNodeData(props.node.id, { ms: Number(e.target.value) || 0 })
}
</script>
