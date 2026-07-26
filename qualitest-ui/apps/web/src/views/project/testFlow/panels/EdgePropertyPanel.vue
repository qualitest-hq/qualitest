<template>
  <PropEmpty v-if="!edge">
    点击画布上的节点或边<br/>查看与编辑配置
  </PropEmpty>

  <div v-else class="prop-form">
    <AiStagingFieldDiff v-if="stagingUnit" :unit-id="stagingUnit.unitId" />

    <template v-if="showNormalFields">
      <div v-if="condHint" class="field__banner">{{ condHint }}</div>
      <div class="field">
        <label>标签</label>
        <input
            :value="edgeLabel"
            placeholder="画布展示文字（可选）"
            type="text"
            @input="onLabelInput"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
/** 选中边时的右栏属性：Staging 对照 + 标签与 condition 出边提示 */
import { computed } from 'vue';

import AiStagingFieldDiff from '../components/AiStagingFieldDiff.vue';
import PropEmpty from '../components/PropEmpty.vue';
import { syncStagingDraftFromEdge } from '../composables/stagingDraftSync';
import { usePendingStagingUnit, useShowNormalFields } from '../composables/usePendingStagingUnit';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { branchKindLabel, getConditionBranches, getConditionEdgeHandle } from '../utils/conditionUtils';

const store = useFlowCanvasStore();

const edge = computed(() => {
  const sel = store.selected;
  if (!sel || sel.kind !== 'edge') return null;
  return store.edges.find((e) => e.id === sel.id) ?? null;
});

const stagingUnit = usePendingStagingUnit(computed(() => edge.value?.id), 'edge');

const showNormalFields = useShowNormalFields(stagingUnit, ['updateEdge']);

const edgeLabel = computed(() => {
  const label = edge.value?.label;
  return label != null ? String(label) : '';
});

const condHint = computed(() => {
  const e = edge.value;
  if (!e) return '';
  const srcNode = store.nodes.find((n) => n.id === e.source);
  if (srcNode?.type !== 'condition') return '';
  const handle = getConditionEdgeHandle(srcNode, e);
  if (!handle.startsWith('out-')) return '';
  const branchId = handle.slice(4);
  const branch = getConditionBranches(srcNode.data).find((b) => b.id === branchId);
  if (!branch) return '';
  return `条件出边 · ${branchKindLabel(branch)} · 由分支 target 决定`;
});

function onLabelInput(event) {
  if (!edge.value) return;
  store.patchEdgeLabel(edge.value.id, event.target.value);
  syncStagingDraftFromEdge(edge.value.id);
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

@include prop.flow-prop-panel-root;
</style>
