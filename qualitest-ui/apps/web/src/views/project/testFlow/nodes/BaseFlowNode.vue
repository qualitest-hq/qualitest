<template>
  <div
      ref="rootEl"
      :class="nodeClasses"
      :style="{ '--node-accent': color }"
      class="flow-node vue-flow__node-default"
      @dblclick.stop="onDblClick"
  >
    <div class="flow-node__bar" />
    <div class="flow-node__head">
      <span class="flow-node__badge">{{ icon }}</span>
      <span class="flow-node__title">{{ data.name || label }}</span>
      <span v-if="isStartNode(id)" class="flow-node__start-badge">开始</span>
    </div>
    <div class="flow-node__body">
      <slot />
    </div>
    <Handle :position="Position.Left" class="handle handle--in" type="target" />
    <slot name="source-handles">
      <Handle
          v-if="!hideSourceHandle"
          :position="Position.Right"
          class="handle handle--out"
          type="source"
      />
    </slot>
    <AiStagingChrome v-if="stagingMark" :unit-id="stagingMark.unitId" placement="node-corner" />
  </div>
</template>

<script setup>
/** 节点卡片外壳：顶栏色条、标题、运行高亮、校验描边、Staging 角标、连接锚点 */
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { computed, inject, onBeforeUnmount, onMounted, ref, toRef } from 'vue'

import AiStagingChrome from '../components/AiStagingChrome.vue'
import { useStagingMark } from '../composables/usePendingStagingUnit'
import { useFlowValidation } from '../composables/useFlowValidation'
import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { NODE_TYPES } from '../constants/nodeTypes'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useFlowNodes } from '../composables/useFlowNodes'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  type: { type: String, required: true },
  selected: { type: Boolean, default: false },
  /** 为 true 时不渲染默认右侧出边锚点，由 source-handles 插槽自行提供 */
  hideSourceHandle: { type: Boolean, default: false },
})

const openHttpConfig = inject('openHttpConfig', null)

const store = useFlowCanvasStore()
const { isStartNode } = useFlowNodes()
const { issueNodeIds } = useFlowValidation()

const stagingMark = useStagingMark(toRef(() => props.id), 'node')

const cfg = computed(() => NODE_TYPES[props.type] || {})
const color = computed(() => cfg.value.color || '#0b6edc')
const icon = computed(() => cfg.value.icon || '?')
const label = computed(() => cfg.value.label || props.type)

const nodeClasses = computed(() => ({
  'is-selected': props.selected,
  'is-start': isStartNode(props.id),
  'is-running': store.runHighlightNodeId === props.id,
  'is-visited': !!store.runVisitedNodeIds[props.id],
  'is-step-passed': store.runVisitedNodeIds[props.id] === 'passed',
  'is-step-failed': store.runVisitedNodeIds[props.id] === 'failed',
  'is-ai-highlight': store.aiHighlightNodeIds.includes(props.id),
  'is-external-sync-highlight': store.externalSyncHighlightNodeIds.includes(props.id),
  'is-ai-staging-add': stagingMark.value?.mode === 'add',
  'is-ai-staging-update': stagingMark.value?.mode === 'update',
  'is-ai-staging-delete': stagingMark.value?.mode === 'delete',
  'is-ai-staging-error': issueNodeIds.value.has(props.id),
}))

function onDblClick() {
  if (props.type === 'http' && props.data?.callMode !== 'external' && openHttpConfig) {
    openHttpConfig(props.id)
  }
}

const rootEl = ref(null)
const { updateNodeInternals } = useVueFlow(FLOW_VUE_FLOW_ID)
let resizeObserver = null

function refreshNodeInternals() {
  updateNodeInternals([props.id])
}

onMounted(() => {
  if (!rootEl.value) return
  resizeObserver = new ResizeObserver(() => {
    refreshNodeInternals()
  })
  resizeObserver.observe(rootEl.value)
  refreshNodeInternals()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

defineExpose({ rootEl })
</script>

<style scoped lang="scss">
@use '../styles/flowCanvasTokens.scss' as flow;

/* 卡片容器与场景运行态（running / visited / step-passed 等） */
.flow-node {
  @include flow.flow-node-accent-derivatives;
  width: var(--node-w);
  min-height: var(--node-min-h);
  /* 覆盖 Vue Flow theme .vue-flow__node-default 的 padding:10px，否则行内 Handle 贴不到节点边 */
  padding: 0;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  box-shadow: var(--pd-shadow-card);
  font-family: inherit;
  position: relative;
  cursor: move;
  user-select: none;
  text-align: left;
  transition: box-shadow 0.15s, border-color 0.15s;
  box-sizing: border-box;

  &:hover {
    border-color: color-mix(in srgb, var(--node-accent, var(--pd-primary)) 50%, var(--pd-border-subtle));
  }

  &.is-selected {
    z-index: 6;
    border-color: var(--node-accent, var(--pd-primary));
    background: color-mix(in srgb, var(--node-accent, var(--pd-primary)) 9%, #fff);
    box-shadow:
      0 0 0 2px var(--node-accent, var(--pd-primary)),
      0 0 0 6px color-mix(in srgb, var(--node-accent, var(--pd-primary)) 28%, transparent),
      0 10px 28px color-mix(in srgb, var(--node-accent, var(--pd-primary)) 22%, transparent);
  }

  &.is-selected .flow-node__bar {
    height: 4px;
  }

  &.is-start {
    border-color: color-mix(in srgb, var(--node-accent, var(--pd-primary)) 40%, var(--pd-border-subtle));
  }

  &.is-start .flow-node__start-badge {
    display: inline-flex;
  }

  &.is-running {
    z-index: 10;
    border-color: #16a34a !important;
    background: color-mix(in srgb, #16a34a 8%, #fff);
    animation: node-pulse 1s ease-in-out infinite;
  }

  &.is-running.is-selected {
    border-color: #16a34a !important;
    background: color-mix(in srgb, #16a34a 10%, #fff);
    box-shadow:
      0 0 0 2px #16a34a,
      0 0 0 6px color-mix(in srgb, #16a34a 30%, transparent),
      0 10px 28px color-mix(in srgb, #16a34a 22%, transparent);
  }

  &.is-visited {
    border-color: color-mix(in srgb, #16a34a 45%, var(--pd-border-subtle));
    opacity: 0.92;
  }

  &.is-step-passed .flow-node__bar {
    box-shadow: inset 0 -3px 0 #16a34a;
  }

  &.is-step-failed .flow-node__bar {
    box-shadow: inset 0 -3px 0 #dc2626;
  }

  &.is-step-failed {
    border-color: #b91c1c !important;
  }

  &.is-step-failed.is-running {
    box-shadow: 0 0 0 2px rgba(220, 38, 38, 0.35);
  }

  &.is-ai-highlight {
    z-index: 7;
    border-color: #7c3aed !important;
    background: color-mix(in srgb, #7c3aed 10%, #fff);
    box-shadow:
      0 0 0 2px #7c3aed,
      0 0 0 6px color-mix(in srgb, #7c3aed 28%, transparent),
      0 10px 28px color-mix(in srgb, #7c3aed 20%, transparent);
    animation: ai-highlight-pulse 1.6s ease-in-out infinite;
  }

  &.is-external-sync-highlight {
    z-index: 7;
    border-color: #0d9488 !important;
    background: color-mix(in srgb, #0d9488 8%, #fff);
    box-shadow:
      0 0 0 2px #0d9488,
      0 0 0 6px color-mix(in srgb, #0d9488 22%, transparent);
    animation: external-sync-enter 0.45s ease-out, ai-highlight-pulse 1.6s ease-in-out 0.45s infinite;
  }

  &.is-ai-staging-add {
    z-index: 7;
    border-style: dashed;
    border-color: #7c3aed !important;
    box-shadow:
      0 0 0 2px rgba(124, 58, 237, 0.35),
      0 0 0 6px rgba(124, 58, 237, 0.1);
  }

  &.is-ai-staging-update {
    z-index: 6;
    border-color: #0b6edc !important;
    box-shadow:
      0 0 0 2px rgba(11, 110, 220, 0.55),
      0 0 0 6px rgba(11, 110, 220, 0.15);
    animation: ai-patch-update-pulse 1.4s ease-in-out infinite;
  }

  &.is-ai-staging-delete {
    z-index: 6;
    border-style: dashed;
    border-color: #b91c1c !important;
    opacity: 0.88;
    box-shadow:
      0 0 0 2px rgba(185, 28, 28, 0.45),
      0 0 0 6px rgba(185, 28, 28, 0.12);
  }

  &.is-ai-staging-error {
    border-color: #dc2626 !important;
    box-shadow:
      0 0 0 2px rgba(220, 38, 38, 0.45),
      0 0 0 6px rgba(220, 38, 38, 0.12);
  }

  /* condition：加宽；body 去水平 padding，供行内 Handle 贴右缘（须压过下方默认 body padding） */
  &.flow-node--condition {
    width: var(--node-cond-w);
    min-height: calc(var(--node-head-h) + var(--node-cond-row-h) + 12px);

    > .flow-node__body {
      padding: 0 0 6px;
    }
  }
}

/** Diff 预览态：待修改节点的蓝色脉冲描边 */
@keyframes ai-patch-update-pulse {
  0%,
  100% {
    box-shadow:
      0 0 0 2px rgba(11, 110, 220, 0.45),
      0 0 0 5px rgba(11, 110, 220, 0.1);
  }
  50% {
    box-shadow:
      0 0 0 2px rgba(11, 110, 220, 0.85),
      0 0 0 8px rgba(11, 110, 220, 0.18);
  }
}

@keyframes ai-highlight-pulse {
  0%, 100% {
    box-shadow:
      0 0 0 2px #7c3aed,
      0 0 0 6px color-mix(in srgb, #7c3aed 24%, transparent);
  }
  50% {
    box-shadow:
      0 0 0 2px #7c3aed,
      0 0 0 10px color-mix(in srgb, #7c3aed 18%, transparent);
  }
}

@keyframes external-sync-enter {
  from {
    opacity: 0.55;
    transform: scale(0.96);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes node-pulse {
  0%, 100% {
    box-shadow: 0 0 0 2px color-mix(in srgb, #16a34a 40%, transparent);
  }
  50% {
    box-shadow: 0 0 14px color-mix(in srgb, #16a34a 50%, transparent);
  }
}

.flow-node__bar {
  height: 3px;
  border-radius: var(--pd-radius-sm) var(--pd-radius-sm) 0 0;
  background: var(--node-accent, var(--pd-primary));
}

.flow-node__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px 6px;
  min-height: var(--node-head-h);
  box-sizing: border-box;
}

.flow-node__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  font-size: var(--node-font-badge);
  font-weight: 700;
  padding: var(--node-chip-pad-y) var(--node-chip-pad-x);
  border-radius: var(--node-chip-radius);
  color: var(--node-accent-fg);
  background: var(--node-accent-soft);
  border: 1px solid var(--node-accent-border);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  flex-shrink: 0;
  line-height: 1;
}

.flow-node__title {
  font-size: var(--node-font-title);
  font-weight: 600;
  color: var(--pd-text);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
  text-align: left;
}

.flow-node__start-badge {
  display: none;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  font-size: var(--node-font-badge);
  font-weight: 700;
  padding: var(--node-chip-pad-y) var(--node-chip-pad-x);
  border-radius: 999px;
  background: var(--node-accent-soft);
  color: var(--node-accent-fg);
  border: 1px solid var(--node-accent-border);
  flex-shrink: 0;
  line-height: 1;
  box-sizing: border-box;
}

.flow-node__body {
  padding: 2px 14px 12px;
  font-size: var(--node-font-body);
  color: var(--pd-text-muted);
  line-height: 1.45;
  text-align: left;
}

:deep(.flow-node__req),
:deep(.flow-node__summary) {
  font-size: var(--node-font-body);
  color: var(--pd-text-muted);
  line-height: 1.45;
}

:deep(.flow-node__summary) {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:deep(.handle) {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--pd-border-subtle);
  transition: border-color 0.12s, background 0.12s, box-shadow 0.12s;

  &:hover {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--pd-primary) 28%, transparent);
  }
}
</style>
