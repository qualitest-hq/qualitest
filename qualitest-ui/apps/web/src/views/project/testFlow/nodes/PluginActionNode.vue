<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="pluginAction">
    <div class="flow-node__summary">{{ summary }}</div>
  </BaseFlowNode>
</template>

<script setup>
/**
 * 插件动作节点卡片：在画布上展示该步骤调用的插件 id 与能力名称。
 * 仅负责外观展示，参数编辑在右侧属性面板完成。
 */
import { computed } from 'vue';
import BaseFlowNode from './BaseFlowNode.vue';

const props = defineProps({
  /** 节点唯一 id */
  id: { type: String, required: true },
  /** 节点业务数据（pluginId、capability、config 等） */
  data: { type: Object, required: true },
  /** 当前节点是否被选中 */
  selected: { type: Boolean, default: false },
});

/**
 * 卡片摘要文案：
 * 同时配置了插件 id 与能力时显示「插件 id · 能力」；只有插件 id 时仅显示插件 id；
 * 都未配置时提示用户去配置。
 */
const summary = computed(() => {
  const pluginId = String(props.data?.pluginId || '').trim();
  const capability = String(props.data?.capability || '').trim();
  if (pluginId && capability) return `${pluginId} · ${capability}`;
  if (pluginId) return pluginId;
  return '配置插件能力';
});
</script>
