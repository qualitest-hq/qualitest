<template>
  <div class="field">
    <label>毫秒</label>
    <input
        :value="node.data.ms ?? DELAY_DEFAULT_MS"
        :max="DELAY_MAX_MS"
        min="0"
        step="100"
        type="number"
        @input="onDelayInput"
    />
    <p class="field__hint">默认 {{ DELAY_DEFAULT_MS }} ms，上限 {{ DELAY_MAX_MS }} ms</p>
  </div>
</template>

<script setup>
/** 等待节点：配置延迟毫秒数；缺 ms 时写入默认值 */
import { onMounted } from 'vue'

import { DELAY_DEFAULT_MS, DELAY_MAX_MS } from '../../constants/flowConfig'
import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

function onDelayInput(e) {
  const raw = Number(e.target.value)
  let ms = Number.isFinite(raw) ? raw : DELAY_DEFAULT_MS
  if (ms < 0) ms = 0
  if (ms > DELAY_MAX_MS) ms = DELAY_MAX_MS
  patchNodeData(props.node.id, { ms })
}

onMounted(() => {
  const raw = props.node?.data?.ms
  if (raw == null || String(raw).trim() === '') {
    patchNodeData(props.node.id, { ms: DELAY_DEFAULT_MS })
  }
})
</script>
