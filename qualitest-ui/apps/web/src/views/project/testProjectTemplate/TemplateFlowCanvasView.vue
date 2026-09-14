<template>
  <div
      :class="{ 'flow-canvas-page--fullscreen': isFullscreen }"
      class="flow-canvas-page"
  >
    <!-- 复用测试流布局；模板模式下保存 / AI / 改图均不可用 -->
    <FlowCanvasLayout
        :is-fullscreen="isFullscreen"
        back-label="返回模板"
        @back="handleBack"
        @toggle-fullscreen="toggleFullscreen"
    />
  </div>
</template>

<script setup>
/**
 * 项目模板「预制测试流」画布页。
 *
 * 能力：
 * - 只读浏览一条预制流的 graphJson（节点、边、探活/登录骨架等）
 * - 用模板里的预制接口合成 HTTP 绑定目录，方便看清节点绑了哪个接口
 * - 不落库、不写回模板的 templateFlows、不写 test_flow 表
 *
 * 数据从哪来：打开画布前模板抽屉把整份表单写入草稿；本页按路由 flowIndex 取对应流。
 * 返回模板：只恢复打开前的表单草稿，流图本身不会被本页改掉。
 */
import { getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import useAppStore from '@/store/modules/app'

import FlowCanvasLayout from '../testFlow/FlowCanvasLayout.vue'
import { fromGraphJson, rehydrateCanvasSnapshot } from '../testFlow/graphAdapter'
import { useFlowHistory } from '../testFlow/composables/useFlowHistory'
import { useProjectTabTitle } from '../testFlow/composables/useProjectTabTitle'
import {
  applyAdaptedGraphToStore,
  finalizeCanvasHistoryBaseline,
} from '../testFlow/composables/useCanvasGraphHydration'
import { useFlowCanvasStore } from '../testFlow/stores/flowCanvasStore'

import { useTemplateFlowDraftStore } from './stores/templateFlowDraftStore'
import { synthesizeTemplateApiCatalog } from './utils/synthesizeTemplateApiTree'
import { parseJsonMaybe } from './utils/templateForm'
import {
  hydrateTemplateFlowGraph,
  isLoginFlowSkeleton,
  LOGIN_FLOW_VIEWPORT,
  recoverLoginFlowEdgesIfMissing,
} from './utils/templateCanvasHydrate'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const appStore = useAppStore()
const store = useFlowCanvasStore()
const draftStore = useTemplateFlowDraftStore()
const { scheduleHistoryReset, resetHistory } = useFlowHistory()
useProjectTabTitle(
  route,
  () => '预制测试流',
  () => String(store.flowName || '').trim(),
)

/** 全屏时撑满视口并隐藏侧栏占用感 */
const isFullscreen = ref(false)

/** 优先浏览器后退；否则关当前页签 */
function leaveCanvas() {
  if (window.history.state?.back != null) {
    router.back()
    return
  }
  proxy?.$tab?.closePage(route)
}

function handleBack() {
  leaveCanvas()
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  document.body.classList.toggle('fullscreen-detail-mode', isFullscreen.value)
}

/** 灌入前裁成可再水合的节点快照（去掉运行态多余字段） */
function stripNodeForDraft(node) {
  return {
    id: node.id,
    type: node.type,
    position: { x: node.position?.x ?? 0, y: node.position?.y ?? 0 },
    data: JSON.parse(JSON.stringify(node.data ?? {})),
  }
}

/** 灌入前裁成可再水合的边快照 */
function stripEdgeForDraft(edge) {
  const out = { id: edge.id, source: edge.source, target: edge.target }
  const label = edge.label != null ? String(edge.label).trim() : ''
  if (label) out.label = label
  if (edge.sourceHandle) out.sourceHandle = edge.sourceHandle
  return out
}

/**
 * 取当前可用的边列表。
 * 灌入过程中边可能还在 pendingEdges，尚未进 store.edges。
 */
function resolveLoadedEdges(adaptedEdges) {
  if (store.edges.length) return store.edges
  if (store.pendingEdges?.length) return store.pendingEdges
  return adaptedEdges
}

/** 把水合后的节点/边写回画布 store（边先走 pending，再由布局合并） */
function applyHydratedGraphDraft(graphDraft) {
  const { nodes, edges } = rehydrateCanvasSnapshot(graphDraft.nodes, graphDraft.edges)
  store.setPendingEdges(edges)
  store.nodes = nodes
  store.edges = []
  store.bumpStagingEdgeFlushToken()
}

/**
 * 从草稿加载指定下标的预制流并渲染。
 * 失败则提示并退回模板列表。
 */
async function initFromDraft() {
  const draft = draftStore.getDraft()
  const idx = Number(route.params.flowIndex)
  const flowIndex = Number.isFinite(idx) ? idx : 0
  if (!draft?.form) {
    ElMessage.error('未找到模板草稿，请从模板编辑页打开画布')
    router.replace('/project/testProjectTemplate')
    return
  }
  const flows = Array.isArray(draft.form.templateFlows) ? draft.form.templateFlows : []
  if (flowIndex < 0 || flowIndex >= flows.length) {
    ElMessage.error('预制流下标无效')
    router.replace('/project/testProjectTemplate')
    return
  }

  store.reset()
  // 合成 HTTP 接口树/目录，供节点展示绑定关系；不改草稿里的 templateApis
  const { tree, catalog } = synthesizeTemplateApiCatalog(draft.form.templateApis || [])
  store.setTemplateApiContext(tree, catalog)
  store.setTemplateParamContext(draft.form.templateParams || [], draft.form.templateEnvs || [])

  const flow = flows[flowIndex] || {}
  const templateId = String(draft.templateId || 'new')
  // 模板画布没有真实项目/流主键；占位 id 仅作会话锚点
  store.testProjectId = ''
  store.testFlowId = `tpl-${templateId}-${flowIndex}`
  store.flowName = String(flow.flowName || '').trim() || `预制流 ${flowIndex + 1}`

  store.loading = true
  store.beginCanvasHydration()
  try {
    const rawGraph = parseJsonMaybe(flow.graphJson) ?? flow.graphJson ?? null
    // 登录骨架缺边时补全，避免只读浏览断线
    if (rawGraph && typeof rawGraph === 'object' && !Array.isArray(rawGraph)) {
      rawGraph.edges = recoverLoginFlowEdgesIfMissing(rawGraph.nodes, rawGraph.edges)
    }
    const adapted = fromGraphJson(rawGraph)
    await applyAdaptedGraphToStore(store, adapted)
    const graphDraft = {
      nodes: store.nodes.map(stripNodeForDraft),
      edges: resolveLoadedEdges(adapted.edges).map(stripEdgeForDraft),
    }
    // 补齐 HTTP 节点与预制接口的展示字段（名称、method 等）
    if (hydrateTemplateFlowGraph(graphDraft, catalog)) {
      await applyHydratedGraphDraft(graphDraft)
    }
    if (isLoginFlowSkeleton(store.nodes)) {
      store.viewport = { ...LOGIN_FLOW_VIEWPORT }
    }
    store.markClean()
    scheduleHistoryReset()
    await finalizeCanvasHistoryBaseline(store, resetHistory)
    if (store.ui.leftTab === 'runConfig') {
      store.showScenarioPanel()
    }
  } catch (error) {
    store.endCanvasHydration()
    const message = error instanceof Error ? error.message : '加载预制流失败'
    ElMessage.error(message)
    router.replace('/project/testProjectTemplate')
  } finally {
    store.loading = false
  }
}

onMounted(() => {
  appStore.toggleSideBarHide(true)
  initFromDraft()
})

onBeforeUnmount(() => {
  appStore.toggleSideBarHide(false)
  document.body.classList.remove('fullscreen-detail-mode')
  store.reset()
})
</script>

<style scoped lang="scss">
.flow-canvas-page {
  height: calc(100vh - 84px);
  min-height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.flow-canvas-page--fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  height: 100vh;
  min-height: 100vh;
  background: #fff;
}
</style>
