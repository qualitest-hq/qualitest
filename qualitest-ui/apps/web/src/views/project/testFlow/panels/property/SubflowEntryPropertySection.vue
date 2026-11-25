<template>
  <div class="prop-form">
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
      hint="零次 return 时挂到父节点 out"
      @update:model-value="(v) => patch({ fallbackOut: v })"
    />
  </div>
</template>

<script setup>
/**
 * 子流入口节点属性面板。
 * 可改：步数上限、入口说明（编辑 / Markdown 预览）、入参形状声明、兜底出参 fallbackOut。
 * 子图整次未走到返回节点时，把 fallbackOut 挂到父流对应子流节点的 out。
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

/** 把局部字段合并写回当前节点数据（含入参形状） */
function patch(p) {
  patchNodeData(props.node.id, p)
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>
