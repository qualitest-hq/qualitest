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
import { fromGraphJson, toGraphJson } from '../testFlow/graphAdapter'
import { useFlowHistory } from '../testFlow/composables/useFlowHistory'
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
import { parseJsonMaybe } from './utils/templateForm'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const appStore = useAppStore()
const store = useFlowCanvasStore()
const stagingStore = useAiStagingStore()
const draftStore = useTemplateFlowDraftStore()
const { scheduleHistoryReset, resetHistory } = useFlowHistory()

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
    edges: store.edges,
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
  store.canvasMode = 'template'
  store.templateReadOnly = draft.dialogMode === 'view'
  const { tree, catalog } = synthesizeTemplateApiCatalog(draft.form.templateApis || [])
  store.setTemplateApiContext(tree, catalog)

  const flow = flows[flowIndex.value] || {}
  const templateId = String(draft.templateId || 'new')
  // 合成会话锚点，供 AI designMode=template 使用（非真实项目/流 id）
  store.testProjectId = ''
  store.testFlowId = `tpl-${templateId}-${flowIndex.value}`
  store.flowName = String(flow.flowName || '').trim() || `预制流 ${flowIndex.value + 1}`

  store.loading = true
  store.beginCanvasHydration()
  try {
    const raw = parseJsonMaybe(flow.graphJson) ?? flow.graphJson ?? null
    const adapted = fromGraphJson(raw)
    await applyAdaptedGraphToStore(store, adapted)
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
