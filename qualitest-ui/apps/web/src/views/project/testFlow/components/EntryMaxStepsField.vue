<template>
  <div class="field">
    <label>单次运行步数上限</label>
    <input
      :value="display"
      inputmode="numeric"
      max="1000"
      min="1"
      :placeholder="`默认 ${DEFAULT_MAX_STEPS}`"
      type="number"
      @change="onChange"
    />
    <div class="field__hint">
      入口壳字段；留空开跑补默认 {{ DEFAULT_MAX_STEPS }}；合法范围 1～{{ MAX_STEPS_CEILING }}
    </div>
  </div>
</template>

<script setup>
/**
 * 平台入口壳 maxSteps：场景 / HTTP / 子流 / 协议入口共用。
 * 默认 / 封顶与后端 FlowContractDefaults 对齐。
 */
import { computed } from 'vue'
import { DEFAULT_MAX_STEPS, MAX_STEPS_CEILING } from '@/utils/flow/flowContractDefaults'
import { useFlowNodes } from '../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

const display = computed(() => {
  const v = props.node?.data?.maxSteps
  return v == null || v === '' ? '' : String(v)
})

function onChange(e) {
  const raw = String(e.target.value ?? '').trim()
  if (!raw) {
    patchNodeData(props.node.id, { maxSteps: null })
    return
  }
  let n = Number(raw)
  if (!Number.isFinite(n)) {
    patchNodeData(props.node.id, { maxSteps: null })
    return
  }
  n = Math.round(n)
  if (n < 1) n = 1
  if (n > MAX_STEPS_CEILING) n = MAX_STEPS_CEILING
  patchNodeData(props.node.id, { maxSteps: n })
}
</script>
