<template>
  <div class="node-palette">
    <div
        v-for="(cfg, type) in NODE_TYPES"
        :key="type"
        :data-type="type"
        :style="{ '--palette-accent': cfg.color }"
        class="palette-item"
        @dblclick="onDblClick(String(type))"
        @pointerdown="onPointerDown($event, String(type))"
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
/**
 * 左栏节点库列表。
 * 双击在画布可视区中心创建节点；按下拖动走指针拖放，松手落在画布内才创建。
 * 无编辑权限时不创建。
 */
import { NODE_TYPES } from '../constants/nodeTypes'
import { useFlowCanvasPermissions } from '../composables/useFlowCanvasPermissions'
import { useFlowNodes } from '../composables/useFlowNodes'
import { usePalettePointerDrag } from '../canvas/paletteDrop/usePalettePointerDrag'

const { addNodeAtCenter, addNodeAtClient } = useFlowNodes()
const { canEditFlow } = useFlowCanvasPermissions()
const { onPointerDown } = usePalettePointerDrag({ canEditFlow, addNodeAtClient })

function onDblClick(type) {
  if (!canEditFlow.value) return
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
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
  user-select: none;
  touch-action: none;

  &:hover {
    border-color: color-mix(in srgb, var(--palette-accent, var(--pd-primary)) 40%, var(--pd-border-subtle));
    box-shadow: var(--pd-shadow-card);
    transform: translateY(-1px);
  }

  &:active {
    cursor: move;
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

<style lang="scss">
@use '../shared/styles/paletteDragCursor.scss';
</style>
