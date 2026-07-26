<template>
  <aside :class="{ 'is-closed': !store.isRightPanelVisible }" class="flow-panel flow-panel--right panel panel--right">
    <div class="panel__head flow-panel__head">
      <div class="panel__head-row flow-panel__head-row">
        <div class="panel__title flow-panel__title">{{ title }}</div>
        <button class="panel__toggle flow-panel__toggle" type="button" @click="closePanel">关闭</button>
      </div>
      <div class="panel__desc flow-panel__desc">{{ desc }}</div>
    </div>
    <div class="panel__body flow-panel__body flow-panel__body--right">
      <div class="prop-panel-wrap">
        <EdgePropertyPanel v-if="contentKey === 'props-edge'" />
        <NodePropertyPanel
            v-else-if="contentKey === 'props-node'"
            @open-http-config="emit('open-http-config', $event)"
        />
        <ScenarioConfigPanel v-else-if="contentKey === 'scenario'" />
        <RunDetailPanel v-else-if="contentKey === 'run'" />
      </div>
    </div>
  </aside>
</template>

<script setup>
/** 右栏：属性 / 运行配置 / 运行详情，共用滚动容器与标题区 */
import EdgePropertyPanel from './EdgePropertyPanel.vue';
import NodePropertyPanel from './NodePropertyPanel.vue';
import RunDetailPanel from './RunDetailPanel.vue';
import ScenarioConfigPanel from './ScenarioConfigPanel.vue';
import { useRightPanelMeta } from '../composables/useRightPanelMeta';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

const emit = defineEmits(['open-http-config']);

const store = useFlowCanvasStore();
const { title, desc, contentKey } = useRightPanelMeta();

function closePanel() {
  store.ui.rightOpen = false;
  if (store.ui.rightMode === 'run' || store.ui.rightMode === 'scenario') {
    store.ui.rightMode = 'props';
  }
}
</script>
