<template>
  <div class="flow-canvas-layout flow-canvas-root">
    <FlowCanvasHeader
        v-model:help-open="helpOpen"
        :ai-assistant-title="aiAssistantTitle"
        :back-label="backLabel"
        :can-edit-flow="canEditFlow"
        :can-export-flow="canExportFlow"
        :can-undo="canUndo"
        :can-use-ai-design="canUseAiDesign"
        :dirty="store.dirty"
        :external-conflict-banners="externalConflictBanners"
        :flow-name="store.flowName"
        :is-template-canvas="isTemplateCanvas"
        :layout-preset-id="layoutPresetId"
        :lease-badge="leaseBadge"
        :loading="store.loading"
        :refresh-auth-loading="refreshAuthLoading"
        :save-button-title="saveButtonTitle"
        :staging-batch-busy="stagingBatchBusy"
        :staging-batch-paused-on-unit-id="stagingBatchPausedOnUnitId"
        :staging-confirm-all-label="stagingConfirmAllLabel"
        :staging-confirm-all-title="stagingConfirmAllTitle"
        :staging-pending-count="stagingPendingCount"
        :staging-ready-pending-count="stagingReadyPendingCount"
            :test-project-id="store.testProjectId"
        :tidy-label="tidyLabel"
        :tidy-main-tip="tidyMainTip"
        @back="emit('back')"
        @confirm-all-ready="handleConfirmAllReady"
        @export="exportDrawerVisible = true"
        @import="importModalVisible = true"
        @open-ai="handleOpenAiDesign"
        @open-api-workspace="openApiWorkspace"
        @open-project-setting="openProjectSetting"
        @pick-tidy-preset="pickTidyPreset"
        @refresh-auth="handleRefreshAuthHeaders"
        @resume-confirm-all="handleResumeConfirmAllReady"
        @run-tidy="runTidy"
        @save="handleSave"
        @undo="undo"
    />

    <div
        :class="{
          'is-left-collapsed': store.ui.leftCollapsed,
          'is-right-closed': !store.isRightPanelVisible,
        }"
        class="flow-canvas-app-body app-body"
    >
      <LeftPanel />

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
              :nodes-connectable="canEditFlow"
              :nodes-draggable="canEditFlow"
              :elements-selectable="true"
              :pan-on-drag="true"
              :zoom-on-scroll="true"
              :class="FLOW_CANVAS_ROOT_CLASS"
              :selection-key-code="true"
              @connect="onConnect"
              @edge-click="onEdgeClick"
              @node-click="onNodeClick"
              @node-context-menu="onNodeContextMenu"
              @node-drag-start="onNodeDragStart"
              @node-drag-stop="onNodeDragStop"
              @pane-click="onPaneClick"
              @edges-change="onEdgesChange"
              @nodes-change="onNodesChange"
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
        :capabilities="capabilities"
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
 * 测试流画布根布局（装配层）。
 * 组装顶栏、左右栏、VueFlow、小地图、校验条、AI 侧栏与各类弹层；
 * 顶栏组件、VueFlow 事件绑定、壳层样式分别在 canvas/ 与 shared/styles，
 * 本文件负责挂载、权限门禁、写锁、保存、Staging 确认与跑流入口接线。
 */
import { Background } from '@vue-flow/background'
import { VueFlow } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import { markRaw, onBeforeUnmount, provide, reactive, ref, watch, getCurrentInstance, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import ProjectSettingDrawer from '@/views/project/testProject/components/ProjectSettingDrawer.vue'
import { useProjectSettingDrawer } from '@/views/project/testProject/composables/useProjectSettingDrawer'

import { buildCanvasPersistGraph } from './composables/buildCanvasPersistGraph'
import {
  flowDesignPatchHasChanges,
  hydratePatchToStaging,
} from './utils/hydratePatchToStaging'
import { refreshAuthHeaders } from '@/api/project/testFlow'
import { createClientMessageId } from '@/utils/ai/aiChatSession'

import FlowCanvasHeader from './canvas/FlowCanvasHeader.vue'
import { useVueFlowCanvasBindings } from './canvas/useVueFlowCanvasBindings'
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
import { FLOW_CANVAS_ROOT_CLASS, FLOW_VUE_FLOW_ID } from './constants/flowConfig'
import { useAiDesign } from './composables/useAiDesign'
import { useFlowConnect } from './composables/useFlowConnect'
import { useFlowDelete } from './composables/useFlowDelete'
import { useFlowHistory } from './composables/useFlowHistory'
import { useFlowKeyboard } from './composables/useFlowKeyboard'
import { useFlowMinimap } from './composables/useFlowMinimap'
import { useFlowScenarioRun } from './composables/useFlowScenarioRun'
import { useFlowCanvasPermissions } from './composables/useFlowCanvasPermissions'
import { useAiStagingCanvas } from './composables/useAiStagingCanvas'
import { useAiStagingConfirm } from './composables/useAiStagingConfirm'
import { useAiStagingScenario } from './composables/useAiStagingScenario'
import { useFlowTidyLayout } from './composables/useFlowTidyLayout'
import { presetById } from './utils/flowLayoutPreset'
import { useAiStagingStore } from './stores/aiStagingStore'
import { useFlowCanvasStore } from './stores/flowCanvasStore'
import { stagingPendingSaveTooltip } from './utils/promptStagingPendingSave'
import { useExternalGraphSync } from './composables/useExternalGraphSync'
import { useFlowEditLease } from './composables/useFlowEditLease'
import { externalChangeSourceLabel } from './utils/externalGraphSyncState'

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
const route = useRoute()
const router = useRouter()
const store = useFlowCanvasStore()
const isTemplateCanvas = computed(() => store.canvasMode === 'template')
const backLabel = computed(() => props.backLabel)
const stagingStore = useAiStagingStore()
const stagingPendingCount = computed(() => stagingStore.pendingCount)

/** 写锁 / SSE 跟地址栏 id，避免 store 滞后握着已删旧流去打 editLease */
const externalSyncTestFlowId = computed(() => String(route.params.testFlowId ?? ''))
const externalSyncProjectId = computed(() =>
  String(route.params.testProjectId ?? store.testProjectId ?? ''),
)
const externalSyncEnabled = computed(() => !isTemplateCanvas.value && !!externalSyncTestFlowId.value)

/** 项目设置抽屉；capabilities 来自 myProjectContext，传给 ProjectSettingDrawer */
const {
  projectSettingDrawerVisible,
  activeTestProjectId,
  settingContext,
  capabilities,
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

const authFormDirty = computed(() => !!projectSettingDrawerVisible.value)
const assetFormDirty = computed(() => store.ui.leftTab === 'params')

// 内容脏占用写锁并心跳续期；仅布局脏或干净时释放；顶栏展示占用状态
const { leaseBadge, pulseRemoteActivity } = useFlowEditLease({
  testFlowId: externalSyncTestFlowId,
  enabled: externalSyncEnabled,
})

// 订阅外部写入；条幅驱动顶栏「放弃并刷新」
const { externalConflictBanners } = useExternalGraphSync({
  testFlowId: externalSyncTestFlowId,
  testProjectId: externalSyncProjectId,
  enabled: externalSyncEnabled,
  authFormDirty,
  assetFormDirty,
  onGraphWriteActivity: (source) => {
    pulseRemoteActivity(externalChangeSourceLabel(source))
  },
})

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
const { onConnect, onEdgesChange } = useFlowConnect()
const { canUndo, undo } = useFlowHistory()
const { tidyLayout, layoutPresetId } = useFlowTidyLayout()
const tidyLabel = computed(() => presetById(layoutPresetId.value).label)
const tidyMainTip = computed(() => `按「${tidyLabel.value}」重算全部节点坐标并适应视图`)

function runTidy() {
  void tidyLayout()
}

function pickTidyPreset(id) {
  void tidyLayout(id)
}

useAiStagingCanvas()
useAiStagingScenario()

const { deleteNodeById } = useFlowDelete()
const { queueNodeForChat } = useAiDesign()
const { isScenarioRunActive } = useFlowScenarioRun()
/** 画布编辑与 AI：菜单权限 + store.capabilities（flow:edit、ai:flow:design） */
const { canUseAiDesign, canEditFlow, canExportFlow } = useFlowCanvasPermissions()

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
  if (store.dirty) return '有未保存的修改，点击保存（Ctrl+S）'
  return '保存（Ctrl+S）'
})

function handleOpenAiDesign() {
  if (!canUseAiDesign.value) {
    ElMessage.warning('当前账号无查询权限，无法使用 AI 助手')
    return
  }
  store.openAiDesignPanel()
}

function handleSave() {
  if (store.loading) return
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

const {
  onNodesChange,
  onNodesInitialized,
  onNodeClick,
  onEdgeClick,
  onNodeDragStart,
  onNodeDragStop,
  onPaneClick,
} = useVueFlowCanvasBindings({ onPaneClickExtra: closeNodeMenu })

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
useFlowKeyboard({ helpOpen, onSave: handleSave })

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
</script>

<style lang="scss">
@use './shared/styles/flowCanvasChrome.scss';
</style>
