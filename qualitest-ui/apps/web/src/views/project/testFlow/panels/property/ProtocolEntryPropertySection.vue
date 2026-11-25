<template>
  <div class="prop-form">
    <EntryMaxStepsField :node="node" />
    <EntryDocMarkdownField
      :model-value="node.data?.doc ?? ''"
      :rows="4"
      @update:model-value="(v) => patch({ doc: v })"
    />
    <div class="field__hint">
      协议入口由插件贡献 type/codec；运行限额用平台壳字段 data.maxSteps，不另发明名。
    </div>
  </div>
</template>

<script setup>
/**
 * 协议入口（如 tcp-entry）通用属性：平台壳 maxSteps + 说明。
 * 业务字段由插件面板另行贡献（若有）；本段保证运行限额配置面始终存在。
 */
import EntryDocMarkdownField from '../../components/EntryDocMarkdownField.vue'
import EntryMaxStepsField from '../../components/EntryMaxStepsField.vue'
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
