<template>
  <div class="node-palette">
    <div
        v-for="(cfg, type) in NODE_TYPES"
        :key="type"
        :data-type="type"
        :style="{ '--palette-accent': cfg.color }"
        class="palette-item"
        draggable="true"
        @dblclick="onDblClick(String(type))"
        @dragstart="onDragStart($event, String(type))"
    >
      <div :style="{ background: cfg.color }" class="palette-item__icon">{{ cfg.icon }}</div>
      <div class="palette-item__info">
        <div class="palette-item__name">{{ cfg.label }}</div>
        <div class="palette-item__hint">{{ cfg.desc }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
/** 左栏节点库：拖拽或双击向画布添加节点 */
import { NODE_TYPES } from '../constants/nodeTypes'
import { useFlowNodes } from '../composables/useFlowNodes'

const { addNodeAtCenter } = useFlowNodes()

function onDragStart(event, type) {
  event.dataTransfer?.setData('nodeType', type)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function onDblClick(type) {
  addNodeAtCenter(type)
}
</script>

<style scoped lang="scss">
.node-palette {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  cursor: grab;
  transition: all 0.15s ease;
  user-select: none;

  &:hover {
    border-color: color-mix(in srgb, var(--palette-accent, var(--pd-primary)) 40%, var(--pd-border-subtle));
    box-shadow: var(--pd-shadow-card);
    transform: translateY(-1px);
  }

  &:active {
    cursor: grabbing;
  }
}

.palette-item__icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  align-self: center;
}

.palette-item__info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.palette-item__name {
  font-weight: 600;
  font-size: 12px;
  line-height: 1.35;
}

.palette-item__hint {
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}
</style>
