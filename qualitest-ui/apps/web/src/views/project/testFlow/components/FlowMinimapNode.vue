<template>
  <rect
      v-if="dimensions.width > 0 && dimensions.height > 0"
      :id="id"
      :class="{ selected, dragging }"
      :fill="color"
      :height="dimensions.height"
      :rx="radius"
      :ry="radius"
      :stroke="strokeColor"
      :stroke-width="strokeWidth"
      :width="dimensions.width"
      :x="position.x"
      :y="position.y"
      class="vue-flow__minimap-node"
      shape-rendering="geometricPrecision"
      @click="$emit('click', $event)"
      @dblclick="$emit('dblclick', $event)"
      @mouseenter="$emit('mouseenter', $event)"
      @mousemove="$emit('mousemove', $event)"
      @mouseleave="$emit('mouseleave', $event)"
  />
</template>

<script setup>
/**
 * 小地图节点：圆角按短边比例计算，避免矮节点看起来像直角、高节点过圆。
 */
import { computed } from 'vue';

import { resolveMinimapNodeRadius } from '../composables/useFlowMinimap';

defineEmits(['click', 'dblclick', 'mouseenter', 'mousemove', 'mouseleave']);

const props = defineProps({
  id: { type: String, required: true },
  position: { type: Object, required: true },
  dimensions: { type: Object, required: true },
  color: { type: String, default: undefined },
  strokeColor: { type: String, default: 'transparent' },
  strokeWidth: { type: Number, default: 2 },
  selected: { type: Boolean, default: false },
  dragging: { type: Boolean, default: false },
});

const radius = computed(() => resolveMinimapNodeRadius(
  props.dimensions?.width ?? 0,
  props.dimensions?.height ?? 0,
));
</script>
