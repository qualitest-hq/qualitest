<template>
  <div class="prop-form">
    <div class="field">
      <label>Method</label>
      <select
        :value="node.data?.method ?? 'POST'"
        @change="(e) => patch({ method: e.target.value })"
      >
        <option v-for="m in methods" :key="m" :value="m">{{ m }}</option>
      </select>
    </div>
    <div class="field">
      <label>Path</label>
      <input
        :value="node.data?.path ?? ''"
        type="text"
        placeholder="/api/demo"
        @input="(e) => patch({ path: e.target.value })"
      />
    </div>
    <div class="field">
      <label>环境 envId</label>
      <input :value="node.data?.envId ?? ''" type="text" @input="(e) => patch({ envId: e.target.value })" />
    </div>
    <div class="field">
      <label>入口说明（可选）</label>
      <textarea :value="node.data?.doc ?? ''" rows="3" @input="(e) => patch({ doc: e.target.value })" />
    </div>
    <EntryInputShapeSection
      :model-value="node.data?.inputShape ?? null"
      @update:model-value="(v) => patch({ inputShape: v })"
    />
    <div class="field">
      <label>fallbackOut（JSON）</label>
      <textarea :value="fallbackText" rows="8" @change="onFallback" />
      <div class="field__hint">零次 return 时收束交付；须含 status / headers / body</div>
    </div>
  </div>
</template>

<script setup>
/**
 * HTTP 入口属性：method / path / envId / 说明、入参形状声明，
 * 以及没有执行任何返回节点时使用的兜底出参 fallbackOut（status / headers / body）。
 */
import { computed } from 'vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import EntryInputShapeSection from './EntryInputShapeSection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/** fallbackOut 的 JSON 文本展示 */
const fallbackText = computed(() => {
  try {
    return JSON.stringify(props.node.data?.fallbackOut ?? {}, null, 2)
  } catch {
    return '{}'
  }
})

/** 把局部字段合并写回当前节点数据（含入参形状） */
function patch(p) {
  patchNodeData(props.node.id, p)
}

/** 失焦解析 JSON；非法则忽略本次输入，不写脏对象 */
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
