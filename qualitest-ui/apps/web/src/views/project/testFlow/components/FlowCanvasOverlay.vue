<template>
  <div class="flow-canvas-toolbar-anchor">
    <FlowCanvasToolbar
        :is-fullscreen="isFullscreen"
        :is-scenario-run-active="isScenarioRunActive"
        :minimap-visible="minimapVisible"
        :zoom-percent="zoomPercent"
        @fit-view="fitView"
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
 * 画布底栏锚点：挂载工具栏，并把视口操作接到 Vue Flow。
 * 用户拖拽/滚轮改变视口时写回 store；底栏缩放与适应视图走视口 composable；
 * 画布初始化与组件挂载时恢复已保存视口。
 */
import { useVueFlow } from '@vue-flow/core'
import { computed, onMounted } from 'vue'

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig'
import { useFlowViewport } from '../composables/useFlowViewport'
import FlowCanvasToolbar from './FlowCanvasToolbar.vue'

defineProps({
  /** 画布是否全屏 */
  isFullscreen: {
    type: Boolean,
    default: false,
  },
  /** 场景真实跑流是否进行中（用于禁用路径模拟） */
  isScenarioRunActive: {
    type: Boolean,
    default: false,
  },
  /** 小地图是否可见 */
  minimapVisible: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['toggle-fullscreen', 'toggle-minimap'])

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
