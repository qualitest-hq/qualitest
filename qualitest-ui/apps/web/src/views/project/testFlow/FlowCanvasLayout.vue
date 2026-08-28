<template>
  <div class="flow-canvas-layout flow-canvas-root">
    <header class="flow-canvas-header">
      <div class="flow-canvas-header__brand">
        <button class="flow-canvas-header__back" type="button" @click="emit('back')">
          <svg aria-hidden="true" class="flow-canvas-header__back-icon" viewBox="0 0 24 24">
            <path
                d="M15 18l-6-6 6-6"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
            />
          </svg>
          {{ backLabel }}
        </button>
        <div class="flow-canvas-header__title-wrap">
          <span class="flow-canvas-header__title">{{ store.flowName || '测试流' }}</span>
          <!-- 有未保存修改时醒目标记（圆点脉冲 + 橙色徽章） -->
          <span
              v-if="store.dirty"
              class="flow-canvas-header__dirty"
              title="画布有未保存的修改"
          >
            <span class="flow-canvas-header__dirty-dot" aria-hidden="true" />
            未保存
          </span>
        </div>
      </div>
      <div class="flow-canvas-header__actions">
        <button
            v-if="!isTemplateCanvas"
            type="button"
            class="flow-canvas-header__icon-btn"
            title="项目设置"
            aria-label="项目设置"
            @click="openProjectSetting"
        >
          <svg-icon class="flow-canvas-header__header-icon" icon-class="system"/>
        </button>
        <button
            v-if="!isTemplateCanvas"
            type="button"
            class="flow-canvas-header__icon-btn"
            title="API"
            aria-label="跳转到 API 工作台"
            @click="openApiWorkspace"
        >
          <svg-icon class="flow-canvas-header__header-icon" icon-class="project-api"/>
        </button>
        <button :disabled="!canUndo" class="btn btn--ghost" title="撤销" type="button" @click="undo">
          ↩ 撤销
        </button>
        <button class="btn" type="button" @click="importModalVisible = true">导入</button>
        <button class="btn" type="button" @click="exportDrawerVisible = true">导出</button>
        <button
            :disabled="!canUseAiDesign"
            :title="aiAssistantTitle"
            class="btn btn--ai"
            type="button"
            @click="handleOpenAiDesign"
        >
          AI 助手
          <span v-if="stagingPendingCount > 0" class="flow-canvas-header__ai-badge">{{ stagingPendingCount }}</span>
        </button>
        <button
            v-if="stagingPendingCount > 0 && canEditFlow"
            :disabled="stagingBatchBusy || stagingReadyPendingCount === 0"
            :title="stagingConfirmAllTitle"
            class="btn btn--ghost"
            type="button"
            @click="handleConfirmAllReady"
        >
          {{ stagingConfirmAllLabel }}
        </button>
        <button
            v-if="stagingBatchPausedOnUnitId && canEditFlow"
            :disabled="stagingBatchBusy"
            class="btn btn--ghost"
            title="跳过失败项，继续确认其余就绪变更"
            type="button"
            @click="handleResumeConfirmAllReady"
        >
          跳过失败继续
        </button>
        <FlowCanvasHelpPopover v-model:open="helpOpen" />
        <button
            v-if="!isTemplateCanvas"
            :disabled="store.loading || !canEditFlow || refreshAuthLoading"
            :title="canEditFlow ? '按项目鉴权配置刷新本流托管头（进 Staging 确认）' : '当前账号无编辑权限'"
            class="btn btn--ghost"
            type="button"
            @click="handleRefreshAuthHeaders"
        >
          {{ refreshAuthLoading ? '刷新中…' : '刷新鉴权头' }}
        </button>
        <!-- dirty 时文案改为「保存更改」，并加橙色强调样式；pending 时角标提示 -->
        <button
            :disabled="store.loading || !canEditFlow"
            :title="saveButtonTitle"
            :class="['btn', 'btn--primary', { 'is-dirty': store.dirty && !store.loading }]"
            type="button"
            @click="handleSave"
        >
          {{ store.loading ? '保存中…' : (store.dirty ? '保存更改' : '保存') }}
          <span v-if="stagingPendingCount > 0" class="flow-canvas-header__ai-badge">{{ stagingPendingCount }}</span>
        </button>
      </div>
    </header>

    <div
        :class="{
          'is-left-collapsed': store.ui.leftCollapsed,
          'is-right-closed': !store.isRightPanelVisible,
        }"
        class="flow-canvas-app-body app-body"
    >
      <LeftPanel @run-scenario="handleRunScenario" />

      <div
          :class="{ 'is-ai-dock-open': store.aiDesignPanelOpen }"
          class="canvas-wrap"
      >
        <button
            v-if="store.ui.leftCollapsed"
            class="left-panel-expand"
            type="button"
            @click="store.ui.leftCollapsed = false"
        >
          展开左栏
        </button>
        <div class="canvas-wrap__viewport">
          <FlowValidationBar />
          <VueFlow
              :id="FLOW_VUE_FLOW_ID"
              v-model:edges="store.edges"
              v-model:nodes="store.nodes"
              :default-viewport="store.viewport"
              :edge-types="edgeTypes"
              :max-zoom="2"
              :min-zoom="0.25"
              :node-types="nodeTypes"
              :pan-on-drag="true"
              :zoom-on-scroll="true"
              class="flow-canvas-vue-flow"
              :selection-key-code="true"
              @connect="onConnect"
              @dragover="handleDragOver"
              @drop="handleDrop"
              @edge-click="onEdgeClick"
              @node-click="onNodeClick"
              @node-context-menu="onNodeContextMenu"
              @node-drag-start="onNodeDragStart"
              @node-drag-stop="onNodeDragStop"
              @pane-click="onPaneClick"
              @edges-change="onEdgesChange"
              @nodes-change="onGraphChange"
              @nodes-initialized="onNodesInitialized"
          >
            <Background :gap="20" :size="1" color="rgba(11, 110, 220, 0.06)" />
            <MiniMap
                v-if="showMinimap"
                :height="minimapHeight"
                :mask-border-radius="minimapMaskBorderRadius"
                :mask-color="minimapMaskColor"
                :mask-stroke-color="minimapMaskStrokeColor"
                :mask-stroke-width="1"
                :node-color="minimapNodeColor"
                :node-stroke-color="minimapNodeStrokeColor"
                :node-stroke-width="2"
                :pannable="true"
                :width="minimapWidth"
                :zoomable="false"
                aria-label="画布小地图导航"
                class="flow-canvas-minimap"
                position="bottom-right"
                @node-click="onMinimapNodeClick"
                @node-dblclick="onMinimapNodeDblClick"
            >
              <template
                  v-for="nodeType in minimapNodeTypes"
                  :key="nodeType"
                  #[`node-${nodeType}`]="nodeProps"
              >
                <FlowMinimapNode v-bind="nodeProps" />
              </template>
            </MiniMap>
            <AiStagingEdgeFlush />
          </VueFlow>
        </div>
        <FlowCanvasOverlay
            :is-fullscreen="isFullscreen"
            :is-scenario-run-active="isScenarioRunActive"
            :minimap-visible="store.ui.minimapVisible"
            :run-disabled="isTemplateCanvas"
            @abort-scenario-run="abortScenarioRun"
            @start-scenario-run="handleRunScenario"
            @toggle-fullscreen="emit('toggle-fullscreen')"
            @toggle-minimap="store.toggleMinimapVisible()"
        />
        <AiDesignChatPanel />
      </div>

      <RightPanel @open-http-config="openHttpConfig" />
    </div>

    <HttpConfigModal v-model:visible="httpModalVisible" :node-id="httpModalNodeId" />
    <ImportGraphModal v-model:visible="importModalVisible" />
    <ExportGraphDrawer v-model:visible="exportDrawerVisible" />
    <FlowNodeContextMenu
        :visible="nodeMenu.visible"
        :x="nodeMenu.x"
        :y="nodeMenu.y"
        @add-to-chat="onAddNodeToChat"
        @delete="onDeleteNodeFromMenu"
    />
    <ProjectSettingDrawer
        v-if="!isTemplateCanvas"
        v-model:visible="projectSettingDrawerVisible"
        v-model:http-transport="projectHttpTransport"
        :drawer-title="drawerTitle"
        :forward-available="forwardAvail.available"
        :setting-context="settingContext"
        :setting-form="settingForm"
        :setting-loading="settingLoading"
        :show-http-transport-setting="showHttpTransportSetting"
        :test-project-id="activeTestProjectId"
        @opened="getSettingOnDrawerOpen"
        @refresh-token="handleRefreshToken"
        @auth-changed="store.loadProjectAuthConfig"
    />
  </div>
</template>

<script setup>
/**
 * 测试流画布根布局。
 * 负责顶栏、三栏 Grid、VueFlow 挂载，以及子面板无法内聚的共享 CSS 变量与 .btn。
 * 顶栏「AI 助手」按钮打开 AiDesignChatPanel 侧栏（挂载于 canvas-wrap，不遮挡右栏属性区）。
 */
import { Background } from '@vue-flow/background'
import { VueFlow } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import { markRaw, nextTick, onBeforeUnmount, reactive, ref, watch, getCurrentInstance, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import ProjectSettingDrawer from '@/views/project/testProject/components/ProjectSettingDrawer.vue'
import { useProjectSettingDrawer } from '@/views/project/testProject/composables/useProjectSettingDrawer'

import { refreshSavedBaselineIfPristine } from './utils/reconcileFlowDirty'
import { waitDoubleAnimationFrame } from './utils/waitDoubleAnimationFrame'
import { buildCanvasPersistGraph } from './composables/buildCanvasPersistGraph'
import {
  flowDesignPatchHasChanges,
  hydratePatchToStaging,
} from './utils/hydratePatchToStaging'
import { refreshAuthHeaders } from '@/api/project/testFlow'
import { createClientMessageId } from '@/utils/ai/aiChatSession'

import FlowCanvasHelpPopover from './components/FlowCanvasHelpPopover.vue'
import FlowCanvasOverlay from './components/FlowCanvasOverlay.vue'
import AiStagingEdgeFlush from './components/AiStagingEdgeFlush.vue'
import FlowMinimapNode from './components/FlowMinimapNode.vue'
import FlowNodeContextMenu from './components/FlowNodeContextMenu.vue'
import FlowValidationBar from './components/FlowValidationBar.vue'
import ConditionEdge from './edges/ConditionEdge.vue'
import FlowDefaultEdge from './edges/FlowDefaultEdge.vue'
import ExportGraphDrawer from './modals/ExportGraphDrawer.vue'
import HttpConfigModal from './modals/HttpConfigModal.vue'
import ImportGraphModal from './modals/ImportGraphModal.vue'
import LeftPanel from './panels/LeftPanel.vue'
import RightPanel from './panels/RightPanel.vue'
import AiDesignChatPanel from './panels/AiDesignChatPanel.vue'
import { NODE_REGISTRY, getRegisteredNodeTypes } from './constants/nodeRegistry'
import { FLOW_VUE_FLOW_ID } from './constants/flowConfig'
import { useAiDesign } from './composables/useAiDesign'
import { useFlowConnect } from './composables/useFlowConnect'
import { useFlowNodeInternalsRefresh } from './composables/useFlowNodeInternalsRefresh'
import { useFlowDelete } from './composables/useFlowDelete'
import { useFlowHistory } from './composables/useFlowHistory'
import { useFlowKeyboard } from './composables/useFlowKeyboard'
import { useFlowMinimap } from './composables/useFlowMinimap'
import { useFlowNodes } from './composables/useFlowNodes'
import { useFlowScenarioRun } from './composables/useFlowScenarioRun'
import { useFlowCanvasPermissions } from './composables/useFlowCanvasPermissions'
import { useAiStagingCanvas } from './composables/useAiStagingCanvas'
import { useAiStagingConfirm } from './composables/useAiStagingConfirm'
import { useAiStagingScenario } from './composables/useAiStagingScenario'
import { useFlowViewport } from './composables/useFlowViewport'
import { useAiStagingStore } from './stores/aiStagingStore'
import { useFlowCanvasStore } from './stores/flowCanvasStore'
import { stagingPendingSaveTooltip } from './utils/promptStagingPendingSave'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'

const props = defineProps({
  isFullscreen: {
    type: Boolean,
    default: false,
  },
  /** 顶栏返回按钮文案 */
  backLabel: {
    type: String,
    default: '返回列表',
  },
})

const emit = defineEmits(['back', 'save', 'toggle-fullscreen'])

const { proxy } = getCurrentInstance()
const router = useRouter()
const store = useFlowCanvasStore()
const isTemplateCanvas = computed(() => store.canvasMode === 'template')
const backLabel = computed(() => props.backLabel)
const stagingStore = useAiStagingStore()
const stagingPendingCount = computed(() => stagingStore.pendingCount)
const {
  confirmAllReady,
  resumeConfirmAllReady,
  readyPendingCount: stagingReadyPendingCount,
  batchConfirmBusy: stagingBatchBusy,
  batchPausedOnUnitId: stagingBatchPausedOnUnitId,
} = useAiStagingConfirm()
const stagingConfirmAllLabel = computed(() => {
  if (stagingBatchBusy.value) return '确认中…'
  return `确认全部就绪 (${stagingReadyPendingCount.value})`
})
const stagingConfirmAllTitle = computed(() => {
  if (stagingBatchBusy.value) return '正在按依赖顺序确认就绪项'
  if (stagingReadyPendingCount.value === 0) return '当前没有可确认项（可能仍有依赖未满足）'
  return `按依赖顺序确认 ${stagingReadyPendingCount.value} 项就绪变更`
})
async function handleConfirmAllReady() {
  if (!canEditFlow.value || stagingBatchBusy.value || stagingReadyPendingCount.value === 0) return
  await confirmAllReady()
}
async function handleResumeConfirmAllReady() {
  if (!canEditFlow.value || stagingBatchBusy.value || !stagingBatchPausedOnUnitId.value) return
  await resumeConfirmAllReady()
}
const {
  projectSettingDrawerVisible,
  activeTestProjectId,
  settingContext,
  settingLoading,
  settingForm,
  projectHttpTransport,
  forwardAvail,
  showHttpTransportSetting,
  drawerTitle,
  openProjectSettingById,
  getSettingOnDrawerOpen,
  handleRefreshToken,
} = useProjectSettingDrawer(() => proxy)
const { onConnect, onEdgesChange } = useFlowConnect()
const { refreshAllNodeInternals } = useFlowNodeInternalsRefresh()
const { canUndo, undo, pushHistory, commitHistoryResetIfPending } = useFlowHistory()
/** 画布视口控制器，供节点初始化后与 Staging 首次进入时恢复/聚焦视角 */
const viewport = useFlowViewport()
useAiStagingCanvas()
useAiStagingScenario()

/** 用户主动拖移节点时为 true，避免初始化阶段误记入历史 */
const nodeDragActive = ref(false)
const { handleDrop, handleDragOver } = useFlowNodes()
const { deleteNodeById } = useFlowDelete()
const { queueNodeForChat } = useAiDesign()
const { runActiveScenario, abortScenarioRun, isScenarioRunActive } = useFlowScenarioRun()
const { canUseAiDesign, canEditFlow } = useFlowCanvasPermissions()

const aiAssistantTitle = computed(() => {
  if (!canUseAiDesign.value) return '当前账号无查询权限，无法使用 AI 助手'
  if (stagingPendingCount.value > 0) {
    return `打开 AI 设计助手（${stagingPendingCount.value} 项待确认）`
  }
  return '打开 AI 设计助手'
})

const saveButtonTitle = computed(() => {
  if (!canEditFlow.value) return '当前账号无编辑权限，无法保存'
  if (stagingPendingCount.value > 0) {
    return stagingPendingSaveTooltip(stagingPendingCount.value)
  }
  if (store.dirty) return '有未保存的修改，点击保存'
  return undefined
})

function handleOpenAiDesign() {
  if (!canUseAiDesign.value) {
    ElMessage.warning('当前账号无查询权限，无法使用 AI 助手')
    return
  }
  store.openAiDesignPanel()
}

function handleSave() {
  if (!canEditFlow.value) {
    ElMessage.warning('当前账号无编辑权限，无法保存')
    return
  }
  emit('save')
}

const refreshAuthLoading = ref(false)

async function handleRefreshAuthHeaders() {
  if (!canEditFlow.value) {
    ElMessage.warning('当前账号无编辑权限，无法刷新鉴权头')
    return
  }
  if (!store.testProjectId) {
    ElMessage.warning('缺少项目 id')
    return
  }
  refreshAuthLoading.value = true
  try {
    const graph = buildCanvasPersistGraph()
    const result = await refreshAuthHeaders({
      testProjectId: store.testProjectId,
      graphJson: graph,
    })
    const changed = Number(result.changedCount ?? 0)
    if (changed <= 0 || !flowDesignPatchHasChanges(result.patch)) {
      ElMessage.info(result.message || '当前托管鉴权头已与项目配置一致')
      return
    }
    const pending = await hydratePatchToStaging(result.patch, {
      messageId: createClientMessageId(),
      openAiPanel: true,
    })
    ElMessage.success(result.message || `已生成 ${pending} 项托管头刷新提案`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '刷新鉴权头失败')
  } finally {
    refreshAuthLoading.value = false
  }
}

async function handleRunScenario() {
  if (isTemplateCanvas.value) {
    ElMessage.warning('模板画布不支持运行场景')
    return
  }
  if (!canEditFlow.value) {
    ElMessage.warning('当前账号无编辑权限，无法运行场景')
    return
  }
  await runActiveScenario()
}
const {
  showMinimap,
  minimapNodeColor,
  minimapNodeStrokeColor,
  minimapMaskColor,
  minimapMaskStrokeColor,
  minimapMaskBorderRadius,
  minimapWidth,
  minimapHeight,
  onMinimapNodeClick,
  onMinimapNodeDblClick,
} = useFlowMinimap()

const minimapNodeTypes = getRegisteredNodeTypes()

const nodeMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  nodeId: '',
})

watch(
  () => nodeMenu.visible,
  (open) => {
    if (open) {
      document.addEventListener('click', closeNodeMenu)
    } else {
      document.removeEventListener('click', closeNodeMenu)
    }
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('click', closeNodeMenu)
})

function closeNodeMenu() {
  nodeMenu.visible = false
}

function onNodeContextMenu({ event, node }) {
  event.preventDefault()
  store.selectItem('node', node.id)
  nodeMenu.nodeId = node.id
  nodeMenu.x = event.clientX
  nodeMenu.y = event.clientY
  nodeMenu.visible = true
}

function onAddNodeToChat() {
  if (!nodeMenu.nodeId) return
  queueNodeForChat(nodeMenu.nodeId)
  closeNodeMenu()
}

function onDeleteNodeFromMenu() {
  if (!nodeMenu.nodeId) return
  deleteNodeById(nodeMenu.nodeId)
  closeNodeMenu()
}

const helpOpen = ref(false)
useFlowKeyboard({ helpOpen })

const nodeTypes = Object.fromEntries(
  // 从注册表生成 VueFlow nodeTypes，避免 Layout 手写每种节点
  Object.entries(NODE_REGISTRY).map(([type, entry]) => [type, markRaw(entry.canvas)]),
)

/** condition 出边与默认边类型；默认边承载 Staging 浮层 */
const edgeTypes = {
  default: markRaw(FlowDefaultEdge),
  condition: markRaw(ConditionEdge),
}

const httpModalVisible = ref(false)
const httpModalNodeId = ref('')
const importModalVisible = ref(false)
const exportDrawerVisible = ref(false)

function openHttpConfig(nodeId) {
  httpModalNodeId.value = nodeId
  httpModalVisible.value = true
}

/** 跳转到当前项目的 API 工作台 */
function openApiWorkspace() {
  const id = store.testProjectId
  if (!id) return
  router.push(`/project/testProject/detail/${id}`)
}

function openProjectSetting() {
  const id = store.testProjectId
  if (!id) return
  openProjectSettingById(id)
}

provide('openHttpConfig', openHttpConfig)

function onGraphChange() {
  store.markDirty()
}

function conditionNodeIds() {
  return store.nodes.filter((n) => n.type === 'condition').map((n) => n.id)
}

/**
 * Vue Flow 节点初始化完成后的收尾：
 * 刷新 handle → 灌入待处理边 → 再刷 condition handle → 恢复视口。
 */
async function onNodesInitialized() {
  commitHistoryResetIfPending()
  store.endCanvasHydration()
  await nextTick()
  await waitDoubleAnimationFrame()
  const condIds = conditionNodeIds()
  refreshAllNodeInternals(condIds.length ? condIds : store.nodes.map((n) => n.id))
  await nextTick()
  await store.ensureEdgesHydrated()
  await waitDoubleAnimationFrame()
  if (condIds.length) {
    refreshAllNodeInternals(condIds)
  }
  await viewport.restoreFromStore()
  await refreshSavedBaselineIfPristine(store)
}

function onNodeClick({ node }) {
  store.selectItem('node', node.id)
}

function onEdgeClick({ edge }) {
  store.selectItem('edge', edge.id)
}

function onNodeDragStart() {
  nodeDragActive.value = true
}

/** 用户拖移节点结束后记入撤销历史 */
function onNodeDragStop() {
  if (!nodeDragActive.value) return
  nodeDragActive.value = false
  pushHistory()
}

function onPaneClick() {
  closeNodeMenu()
  store.clearSelection()
  if (store.ui.leftTab === 'runConfig') {
    store.showScenarioPanel()
    return
  }
  if (store.ui.rightMode === 'run') return
  store.ui.rightMode = 'props'
  store.ui.rightOpen = false
}
</script>

<style lang="scss">
@use './styles/flowCanvasTokens.scss' as flow;

/* 画布页 CSS 变量，子组件通过继承使用 */
.flow-canvas-root {
  @include flow.flow-pd-layout-vars;
  --node-font-title: 15px;
  --node-font-body: 12px;
  --node-font-badge: 11px;
  --node-font-mono: 11px;
  --node-chip-radius: 6px;
  --node-chip-pad-y: 3px;
  --node-chip-pad-x: 8px;
  --panel-w: 320px;
  --panel-w-right: 420px;
  --header-h: 48px;
  --node-w: 300px;
  --node-min-h: 108px;
  --node-head-h: 52px;
  --node-cond-w: 340px;
  --node-cond-row-h: 40px;
  --scope-flow-bg: rgba(124, 58, 237, 0.12);
  --scope-flow-fg: #7c3aed;
  --scope-flow-border: rgba(124, 58, 237, 0.25);
  --scope-env-bg: rgba(11, 110, 220, 0.12);
  --scope-env-fg: #0b6edc;
  --scope-env-border: rgba(11, 110, 220, 0.25);
  --scope-asset-bg: rgba(22, 163, 74, 0.12);
  --scope-asset-fg: #16a34a;
  --scope-asset-border: rgba(22, 163, 74, 0.25);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: var(--pd-font-body);
}

/* 根容器纵向占满；侧栏 absolute 相对本容器定位，避免侵入全局页签栏 */
.flow-canvas-layout {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--pd-bg-page);
  color: var(--pd-text);
}

@include flow.flow-pd-buttons-scoped('.flow-canvas-root');

.flow-canvas-root .btn--ai {
  position: relative;
  border-color: color-mix(in srgb, var(--pd-primary) 40%, var(--pd-border-subtle));
  color: var(--pd-primary);
  background: color-mix(in srgb, var(--pd-primary-soft) 50%, #fff);

  &:hover:not(:disabled) {
    background: var(--pd-primary-soft);
    border-color: var(--pd-primary);
  }
}

.flow-canvas-header__ai-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 999px;
  background: #7c3aed;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.flow-canvas-root .btn--icon {
  width: 32px;
  min-width: 32px;
  height: 32px;
  padding: 0;
  justify-content: center;
  font-size: 15px;
  line-height: 1;
}

.flow-canvas-root .btn.is-path-simulate-stop,
.flow-canvas-root .btn.is-scenario-run-stop {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;

  &:hover:not(:disabled) {
    background: #b91c1c;
    border-color: #b91c1c;
  }
}

/* 顶栏：返回、标题、保存 */
.flow-canvas-header {
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: linear-gradient(180deg, #fafcff 0%, #f0f6fc 100%);
  border-bottom: 1px solid var(--pd-border-muted);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
  flex-shrink: 0;
}

.flow-canvas-header__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.flow-canvas-header__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--pd-primary) 25%, transparent);
  border-radius: 6px;
  background: var(--pd-primary-soft);
  color: var(--pd-primary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    background: color-mix(in srgb, var(--pd-primary-soft) 70%, #fff);
    border-color: color-mix(in srgb, var(--pd-primary) 40%, transparent);
  }
}

.flow-canvas-header__back-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.flow-canvas-header__title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.flow-canvas-header__title {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 标题旁「未保存」徽章：高对比橙色，避免被标题淹没 */
.flow-canvas-header__dirty {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #9a3412;
  background: #ffedd5;
  border: 1px solid #fdba74;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}

/* 徽章内圆点：缓慢外扩脉冲，吸引注意 */
.flow-canvas-header__dirty-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ea580c;
  box-shadow: 0 0 0 0 rgba(234, 88, 12, 0.55);
  animation: flow-dirty-pulse 1.4s ease-out infinite;
}

@keyframes flow-dirty-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(234, 88, 12, 0.55);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(234, 88, 12, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(234, 88, 12, 0);
  }
}

.flow-canvas-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;

  /* 有未保存修改时保存按钮改为橙色强调，并轻微发光 */
  .btn--primary.is-dirty {
    background: #ea580c;
    border-color: #c2410c;
    box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.28);
    animation: flow-save-dirty-glow 1.8s ease-in-out infinite;
  }

  .btn--primary.is-dirty:hover:not(:disabled) {
    background: #c2410c;
    border-color: #9a3412;
  }
}

@keyframes flow-save-dirty-glow {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.22);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(234, 88, 12, 0.38);
  }
}

.flow-canvas-header__icon-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: var(--pd-radius-sm, 6px);
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--pd-primary, #0b6edc);
  transition: background 0.15s ease, color 0.15s ease;

  &:hover {
    background: rgba(11, 110, 220, 0.08);
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 45%, transparent);
    outline-offset: 2px;
  }
}

.flow-canvas-header__header-icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
}

/* 左栏 | 画布 | 右栏 三列 Grid，支持侧栏折叠 */
.flow-canvas-app-body,
.app-body {
  display: grid;
  grid-template-columns: var(--panel-w) minmax(0, 1fr) var(--panel-w-right);
  flex: 1;
  min-height: 0;
  overflow: hidden;

  > .flow-panel,
  > .panel,
  > .canvas-wrap {
    height: 100%;
    min-height: 0;
  }

  &.is-left-collapsed {
    grid-template-columns: 0 minmax(0, 1fr) var(--panel-w-right);
  }

  &.is-right-closed {
    grid-template-columns: var(--panel-w) minmax(0, 1fr) 0;
  }

  &.is-left-collapsed.is-right-closed {
    grid-template-columns: 0 minmax(0, 1fr) 0;
  }
}

/* 左右侧栏公共结构：Tab、标题区、内容区 */
.flow-panel,
.panel {
  background: var(--pd-surface);
  border-right: 1px solid var(--pd-border-muted);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  /* 自包含盒模型，避免再被全局 aside 一类规则注入 padding */
  padding: 0;
  margin: 0;
  line-height: normal;
  border-radius: 0;

  &--left {
    width: var(--panel-w);
    min-width: var(--panel-w);
  }

  &--left,
  &--right,
  &.panel--right {
    /* 左右栏可滚动区域：隐藏滚动条，保留滚轮/触控板滚动 */
    .flow-panel__body--library > .node-palette,
    .flow-panel__body--library > .run-lib,
    .param-lib__list,
    .run-config__scenarios,
    .prop-panel-wrap,
    .run-steps,
    .run-inspector__body {
      scrollbar-width: none;
      -ms-overflow-style: none;

      &::-webkit-scrollbar {
        width: 0;
        height: 0;
      }
    }
  }

  &--right,
  &.panel--right {
    width: var(--panel-w-right);
    min-width: var(--panel-w-right);
    border-right: none;
    border-left: 1px solid var(--pd-border-muted);

    .panel__head,
    .flow-panel__head {
      padding: 8px 8px 6px;
    }
  }

  /* 左栏折叠 / 右栏关闭共用：彻底收起，勿留 padding 残宽 */
  &--left.is-collapsed,
  &--right.is-closed,
  &.panel--right.is-closed {
    width: 0;
    min-width: 0;
    max-width: 0;
    padding: 0;
    margin: 0;
    overflow: hidden;
    border: none;
    visibility: hidden;
    pointer-events: none;
  }
}

.panel-tabs,
.flow-panel-tabs {
  display: flex;
  gap: 0;
  padding: 8px 12px 0;
  border-bottom: 1px solid var(--pd-divider);
  background: var(--pd-gradient-panel-head);
  flex-shrink: 0;
}

.panel-tab,
.flow-panel-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 4px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--pd-text-muted);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--pd-text);
  }

  &.is-active {
    color: var(--pd-primary);
    border-bottom-color: var(--pd-primary);
  }
}

.panel__head,
.flow-panel__head {
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--pd-divider);
  background: var(--pd-gradient-panel-head);
  flex-shrink: 0;
}

.panel__head-row,
.flow-panel__head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel__title,
.flow-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--pd-text);
}

.panel__toggle,
.flow-panel__toggle {
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: #fff;
  color: var(--pd-text-muted);
  font-size: 11px;
  cursor: pointer;

  &:hover {
    color: var(--pd-text);
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}

.panel__desc,
.flow-panel__desc {
  margin-top: 4px;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.panel__body,
.flow-panel__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;

  &--flush {
    padding: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  &--library {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
    padding: 4px;

    > .param-lib,
    > .node-palette,
    > .run-lib {
      flex: 1;
      min-height: 0;
    }

    > .node-palette,
    > .run-lib {
      overflow: auto;
    }
  }

  &--right {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
    padding: 6px 8px;
  }
}

.prop-panel-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* 中间画布容器与左栏折叠后的展开按钮 */
.canvas-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  height: 100%;
  background: var(--pd-bg-sunken);
  --ai-dock-w: min(480px, 38vw);
  --ai-canvas-overlap: 0px;

  &.is-ai-dock-open {
    --ai-canvas-overlap: var(--ai-dock-w);
  }

  /* AI 侧栏贴满画布列高度；底栏/小地图 z-index 更高，浮于其上 */
  .ai-design-chat-dock {
    --ai-chat-panel-width: var(--ai-dock-w);
  }
}

.canvas-wrap__viewport {
  flex: 1;
  min-height: 0;
  min-width: 0;
  position: relative;
  overflow: hidden;
}

.left-panel-expand {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 21;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  color: var(--pd-text);
  font-size: 12px;
  cursor: pointer;
  box-shadow: var(--pd-shadow-card);

  &:hover {
    border-color: var(--pd-primary);
    color: var(--pd-primary);
    background: color-mix(in srgb, var(--pd-primary-soft) 78%, #fff);
  }
}

/* Vue Flow：连线、节点容器、连接锚点 */
.flow-canvas-vue-flow {
  width: 100%;
  height: 100%;
  background: var(--pd-bg-sunken);

  /* 小地图：右下角悬浮卡片，风格对齐底栏 toolbar */
  .vue-flow__panel.vue-flow__minimap.flow-canvas-minimap {
    top: auto !important;
    left: auto !important;
    bottom: 12px !important;
    right: calc(12px + var(--ai-canvas-overlap, 0px)) !important;
    margin: 0 !important;
    transform: none !important;
    z-index: 110;
    padding: 4px;
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid var(--pd-border-subtle);
    border-radius: 10px;
    overflow: hidden;
    box-shadow: var(--pd-shadow-card);
    backdrop-filter: blur(8px);
    pointer-events: auto;

    svg {
      display: block;
      border-radius: 6px;
      background: var(--pd-bg-sunken);
    }

    &.pannable {
      cursor: grab;
    }

    &.dragging {
      cursor: grabbing;
    }
  }

  .vue-flow__edge-path {
    stroke: #7eb0e0;
    stroke-width: 2.5;
  }

  .vue-flow__edge.selected .vue-flow__edge-path,
  .vue-flow__edge.selectable:focus .vue-flow__edge-path,
  .vue-flow__edge.selectable:focus-visible .vue-flow__edge-path {
    stroke: var(--pd-primary);
    stroke-width: 4;
  }

  .vue-flow__node {
    padding: 0;
    border: none;
    background: transparent;
    box-shadow: none;
    text-align: left;
  }

  .vue-flow__node-default {
    text-align: left;
  }

  .handle {
    width: 14px;
    height: 14px;
    min-width: 14px;
    min-height: 14px;
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
}
</style>
