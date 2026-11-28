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
      <div class="field__hint">相当于 out = 该路径对象，再写出；status 默认 200</div>
    </div>
    <div class="field">
      <label>HTTP status</label>
      <input
        :value="statusText"
        inputmode="numeric"
        placeholder="200"
        type="text"
        @input="onStatusInput"
      />
    </div>
    <AlwaysRunPropertySection :node="node" />
  </div>
</template>

<script setup>
/**
 * HTTP 返回节点属性：from 指定写入 out 的来源路径（默认 flow.out），
 * status 为写出 HTTP 状态码（空则运行时按 200）。
 */
import { computed } from 'vue'

import { useFlowNodes } from '../../composables/useFlowNodes'
import AlwaysRunPropertySection from './AlwaysRunPropertySection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

/** 编辑框展示用；空串表示未写 status */
const statusText = computed(() => {
  const v = props.node.data?.status
  return v == null || v === '' ? '' : String(v)
})

function patch(p) {
  patchNodeData(props.node.id, p)
}

/** 空输入清掉 status；可解析数字则截整写入，否则原样保留供校验报错 */
function onStatusInput(e) {
  const raw = e.target.value.trim()
  if (!raw) {
    patch({ status: null })
    return
  }
  const n = Number(raw)
  patch({ status: Number.isFinite(n) ? Math.trunc(n) : raw })
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>