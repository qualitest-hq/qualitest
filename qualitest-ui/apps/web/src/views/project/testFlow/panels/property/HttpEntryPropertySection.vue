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
    <EntryMaxStepsField :node="node" />
    <EntryDocMarkdownField
      :model-value="node.data?.doc ?? ''"
      :rows="4"
      @update:model-value="(v) => patch({ doc: v })"
    />
    <EntryInputShapeSection
      :model-value="node.data?.inputShape ?? null"
      @update:model-value="(v) => patch({ inputShape: v })"
    />
    <FallbackOutJsonField
      :model-value="node.data?.fallbackOut ?? null"
      hint="零次 return 时收束交付；须含 status / headers / body"
      @update:model-value="(v) => patch({ fallbackOut: v })"
    />
  </div>
</template>

<script setup>
/**
 * HTTP 入口节点属性面板。
 * 可改：HTTP 方法、路径、绑定环境 id、步数上限、入口说明（支持编辑与 Markdown 预览）、
 * 入参形状声明、兜底出参 fallbackOut（整次未走到返回节点时对外写出的包）。
 */
import EntryDocMarkdownField from '../../components/EntryDocMarkdownField.vue'
import EntryMaxStepsField from '../../components/EntryMaxStepsField.vue'
import FallbackOutJsonField from '../../components/FallbackOutJsonField.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import EntryInputShapeSection from './EntryInputShapeSection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/** 把局部字段合并写回当前节点数据（含入参形状） */
function patch(p) {
  patchNodeData(props.node.id, p)
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>
