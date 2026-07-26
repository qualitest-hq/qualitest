<template>
  <PropEmpty v-if="!node">
    点击画布上的节点或边<br/>查看与编辑配置
  </PropEmpty>

  <div v-else class="prop-form">
    <AiStagingFieldDiff v-if="stagingUnit" :unit-id="stagingUnit.unitId" />

    <div v-if="isStart" class="field__banner">▶ 流程开始节点（唯一入口）</div>

    <div v-if="showNormalFields" class="field">
      <label>节点名称</label>
      <input :value="node.data.name" type="text" @input="onNameInput" />
    </div>

    <div v-if="showNormalFields" class="prop-section">
      <component :is="propertySection" :node="node" @open-http-config="(id) => emit('open-http-config', id)" />
    </div>
  </div>
</template>

<script setup>
/** 未选中节点时的空态；选中后按类型渲染 property section；Staging 态展示原/现对照 */
import { computed } from 'vue';

import AiStagingFieldDiff from '../components/AiStagingFieldDiff.vue';
import PropEmpty from '../components/PropEmpty.vue';
import { getNodePropertyComponent } from '../constants/nodeRegistry';
import { useFlowNodes } from '../composables/useFlowNodes';
import { usePendingStagingUnit, useShowNormalFields } from '../composables/usePendingStagingUnit';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

const emit = defineEmits(['open-http-config']);

const store = useFlowCanvasStore();
const { patchNodeData, isStartNode } = useFlowNodes();

const node = computed(() => {
  const sel = store.selected;
  if (!sel || sel.kind !== 'node') return null;
  return store.nodes.find((n) => n.id === sel.id) ?? null;
});

const stagingUnit = usePendingStagingUnit(computed(() => node.value?.id), 'node');

/** update 类仅在对照区编辑；add/delete 仍可用下方属性区 */
const showNormalFields = useShowNormalFields(stagingUnit, ['updateNode']);

const isStart = computed(() => (node.value ? isStartNode(node.value.id) : false));

const propertySection = computed(() => getNodePropertyComponent(node.value?.type));

function onNameInput(e) {
  if (!node.value) return;
  patchNodeData(node.value.id, { name: e.target.value });
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

@include prop.flow-prop-panel-root;

:deep(.field) {
  @include prop.flow-prop-field;
}

:deep(.field__hint) {
  @include prop.flow-prop-field-hint;
}

:deep(.prop-summary) {
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.5;
  background: var(--pd-bg-sunken);
  border: 1px solid var(--pd-border-subtle);
}
</style>
