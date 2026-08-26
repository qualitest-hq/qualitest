<template>
  <div class="flow-canvas-toolbar-anchor">
    <FlowCanvasToolbar
        :is-fullscreen="isFullscreen"
        :is-scenario-run-active="isScenarioRunActive"
        :minimap-visible="minimapVisible"
        :run-disabled="runDisabled"
        :zoom-percent="zoomPercent"
        @abort-scenario-run="emit('abort-scenario-run')"
        @fit-view="fitView"
        @start-scenario-run="emit('start-scenario-run')"
        @toggle-fullscreen="emit('toggle-fullscreen')"
        @toggle-minimap="emit('toggle-minimap')"
        @zoom-in="zoomIn"
        @zoom-out="zoomOut"
        @zoom-reset="resetZoom"
    />
  </div>
</template>

<script setup>
/**
 * 画布底栏锚点组件。
 *
 * 挂载缩放工具栏，并将用户操作与 Vue Flow 视口变更统一委托给 useFlowViewport：
 * - 用户拖拽/滚轮 → onViewportChange → syncFromFlow
 * - 底栏放大/缩小/重置/适应画布 → useFlowViewport 对应方法
 * - 画布 onInit 与组件 onMounted 时恢复 store 中保存的视口
 *
 * 不再监听 suppressDirty 回写视口，避免与 Staging 聚焦动画竞态导致视角跳回。
 */
import { useVueFlow } from '@vue-flow/core'
import { computed, onMounted } from 'vue'

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { useFlowViewport } from '../composables/useFlowViewport'
import FlowCanvasToolbar from './FlowCanvasToolbar.vue'

defineProps({
  isFullscreen: {
    type: Boolean,
    default: false,
  },
  isScenarioRunActive: {
    type: Boolean,
    default: false,
  },
  minimapVisible: {
    type: Boolean,
    default: true,
  },
  runDisabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['toggle-fullscreen', 'start-scenario-run', 'abort-scenario-run', 'toggle-minimap'])

const viewport = useFlowViewport()
const { onViewportChange, onInit } = useVueFlow(FLOW_VUE_FLOW_ID)

/** 用户操作改变视口时，同步到 store 并视情况标记脏 */
onViewportChange((vp) => {
  viewport.syncFromFlow(vp)
})

/** 底栏显示的缩放百分比 */
const zoomPercent = computed(() => viewport.zoomPercent())

function zoomIn() {
  viewport.zoomIn()
}

function zoomOut() {
  viewport.zoomOut()
}

function resetZoom() {
  viewport.resetZoom()
}

/** 适应全部节点到可视区域 */
function fitView() {
  viewport.fitViewAll(0.2)
}

/** Vue Flow 实例就绪后，立即恢复 store 视口 */
onInit(() => {
  viewport.restoreFromStore()
})

/** 组件挂载后再次恢复，覆盖初始化阶段可能的视口漂移 */
onMounted(() => {
  viewport.restoreFromStore()
})
</script>

<style scoped lang="scss">
.flow-canvas-toolbar-anchor {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(calc(-50% - var(--ai-canvas-overlap, 0px) / 2));
  z-index: 110;
  pointer-events: none;
  width: max-content;
  max-width: calc(100% - 24px - var(--ai-canvas-overlap, 0px));
}
</style>
