<template>
  <div
      :class="{ 'flow-canvas-page--fullscreen': isFullscreen }"
      class="flow-canvas-page"
  >
    <FlowCanvasLayout
        :is-fullscreen="isFullscreen"
        back-label="返回模板"
        @back="handleBack"
        @save="handleSave"
        @toggle-fullscreen="toggleFullscreen"
    />
  </div>
</template>

<script setup>
/**
 * 项目模板预制测试流画布页。
 * 从图草稿桥加载/保存 graphJson，不写 test_flow 表；合成 templateApis 供 HTTP 绑定。
 */
import { getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import useAppStore from '@/store/modules/app'

import FlowCanvasLayout from '../testFlow/FlowCanvasLayout.vue'
import { fromGraphJson, rehydrateCanvasSnapshot, toGraphJson } from '../testFlow/graphAdapter'
import { useFlowHistory } from '../testFlow/composables/useFlowHistory'
import { useProjectTabTitle } from '../testFlow/composables/useProjectTabTitle'
import {
  applyAdaptedGraphToStore,
  finalizeCanvasHistoryBaseline,
} from '../testFlow/composables/useCanvasGraphHydration'
import { useFlowCanvasStore } from '../testFlow/stores/flowCanvasStore'
import { useAiStagingStore } from '../testFlow/stores/aiStagingStore'
import { refreshSavedBaseline } from '../testFlow/utils/reconcileFlowDirty'
import { validateGraphJson } from '@/utils/flow/graphValidate'

import { useTemplateFlowDraftStore } from './stores/templateFlowDraftStore'
import { synthesizeTemplateApiCatalog } from './utils/synthesizeTemplateApiTree'
import { parseJsonMaybe, validateTemplateGraphApiBindings, partitionTemplateParams, mergeFlowSeedFromTemplateParams } from './utils/templateForm'
import { ensureTemplateApiIds } from './utils/templateApiId'
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
const stagingStore = useAiStagingStore()
const draftStore = useTemplateFlowDraftStore()
const { scheduleHistoryReset, resetHistory } = useFlowHistory()
useProjectTabTitle(
  route,
  () => '预制测试流',
  () => String(store.flowName || '').trim(),
)

const isFullscreen = ref(false)
const flowIndex = ref(0)

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

async function handleSave() {
  await store.ensureEdgesHydrated()
  const graph = toGraphJson({
    nodes: store.nodes,
    edges: store.getEffectiveEdges(),
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
    stagingFilter: stagingStore.buildPersistFilter(),
  })
  const validation = validateGraphJson(graph)
  if (!validation.ok) {
    ElMessage.error(validation.errors[0] ?? '图校验失败')
    return
  }
  const bindCheck = validateTemplateGraphApiBindings(graph, store.templateApiCatalog)
  if (!bindCheck.ok) {
    ElMessage.error(bindCheck.message)
    return
  }
  const ok = draftStore.saveFlowGraph(flowIndex.value, graph, {
    flowName: store.flowName,
  })
  if (!ok) {
    ElMessage.error('草稿已失效，请返回模板重新打开')
    return
  }
  await refreshSavedBaseline(store)
  ElMessage.success('已写回模板草稿（请在模板页点确定后落库）')
  leaveCanvas()
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  document.body.classList.toggle('fullscreen-detail-mode', isFullscreen.value)
}

function onBeforeUnload(event) {
  if (!store.dirty) return
  event.preventDefault()
  event.returnValue = ''
}

/** 空 flowSeed 时灌入 templateParams 的 flow 初值（同名不覆盖） */
function hydrateFlowSeedFromTemplateParams(templateParams) {
  const { flow } = partitionTemplateParams(templateParams)
  if (!flow.length) return
  const scenarios = store.runConfig?.scenarios
  if (!Array.isArray(scenarios) || !scenarios.length) return
  const activeId = store.runConfig.activeScenarioId
  const scenario = scenarios.find((s) => s.id === activeId) || scenarios[0]
  if (!scenario) return
  const { seed, changed } = mergeFlowSeedFromTemplateParams(scenario.flowSeed, flow)
  if (changed) scenario.flowSeed = seed
}

function stripNodeForDraft(node) {
  return {
    id: node.id,
    type: node.type,
    position: { x: node.position?.x ?? 0, y: node.position?.y ?? 0 },
    data: JSON.parse(JSON.stringify(node.data ?? {})),
  }
}

function stripEdgeForDraft(edge) {
  const out = { id: edge.id, source: edge.source, target: edge.target }
  const label = edge.label != null ? String(edge.label).trim() : ''
  if (label) out.label = label
  if (edge.sourceHandle) out.sourceHandle = edge.sourceHandle
  return out
}

/** 灌入后 store.edges 可能仍在 pending，取当前可用边列表 */
function resolveLoadedEdges(adaptedEdges) {
  if (store.edges.length) return store.edges
  if (store.pendingEdges?.length) return store.pendingEdges
  return adaptedEdges
}

function applyHydratedGraphDraft(graphDraft) {
  const { nodes, edges } = rehydrateCanvasSnapshot(graphDraft.nodes, graphDraft.edges)
  store.setPendingEdges(edges)
  store.nodes = nodes
  store.edges = []
  store.bumpStagingEdgeFlushToken()
}

async function initFromDraft() {
  const draft = draftStore.getDraft()
  const idx = Number(route.params.flowIndex)
  flowIndex.value = Number.isFinite(idx) ? idx : 0
  if (!draft?.form) {
    ElMessage.error('未找到模板草稿，请从模板编辑页打开画布')
    router.replace('/project/testProjectTemplate')
    return
  }
  const flows = Array.isArray(draft.form.templateFlows) ? draft.form.templateFlows : []
  if (flowIndex.value < 0 || flowIndex.value >= flows.length) {
    ElMessage.error('预制流下标无效')
    router.replace('/project/testProjectTemplate')
    return
  }

  store.reset()
  store.templateReadOnly = draft.dialogMode === 'view'
  const { apis: ensuredApis, changed: apisChanged } = ensureTemplateApiIds(draft.form.templateApis || [])
  if (apisChanged) {
    draftStore.patchForm({ templateApis: ensuredApis })
    draft.form.templateApis = ensuredApis
  }
  const { tree, catalog } = synthesizeTemplateApiCatalog(ensuredApis)
  store.setTemplateApiContext(tree, catalog)
  store.setTemplateParamContext(draft.form.templateParams || [])

  const flow = flows[flowIndex.value] || {}
  const templateId = String(draft.templateId || 'new')
  // 合成会话锚点，供 AI designMode=template 使用（非真实项目/流 id）
  store.testProjectId = ''
  store.testFlowId = `tpl-${templateId}-${flowIndex.value}`
  store.flowName = String(flow.flowName || '').trim() || `预制流 ${flowIndex.value + 1}`

  store.loading = true
  store.beginCanvasHydration()
  try {
    const rawGraph = parseJsonMaybe(flow.graphJson) ?? flow.graphJson ?? null
    if (rawGraph && typeof rawGraph === 'object' && !Array.isArray(rawGraph)) {
      rawGraph.edges = recoverLoginFlowEdgesIfMissing(rawGraph.nodes, rawGraph.edges)
    }
    const adapted = fromGraphJson(rawGraph)
    await applyAdaptedGraphToStore(store, adapted)
    const graphDraft = {
      nodes: store.nodes.map(stripNodeForDraft),
      edges: resolveLoadedEdges(adapted.edges).map(stripEdgeForDraft),
    }
    const hydrated = hydrateTemplateFlowGraph(graphDraft, catalog)
    if (hydrated) {
      await applyHydratedGraphDraft(graphDraft)
    }
    if (isLoginFlowSkeleton(store.nodes)) {
      store.viewport = { ...LOGIN_FLOW_VIEWPORT }
    }
    if (hydrated) {
      const graph = toGraphJson({
        nodes: store.nodes,
        edges: store.getEffectiveEdges(),
        viewport: store.viewport,
        runConfig: store.runConfig,
        flowOutputs: store.flowOutputs,
        stagingFilter: stagingStore.buildPersistFilter(),
      })
      draftStore.saveFlowGraph(flowIndex.value, graph, { flowName: store.flowName })
    }
    hydrateFlowSeedFromTemplateParams(draft.form.templateParams || [])
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
  window.addEventListener('beforeunload', onBeforeUnload)
  initFromDraft()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
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
