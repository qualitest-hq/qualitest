<template>
  <div
      :class="{ 'flow-canvas-page--fullscreen': isFullscreen }"
      class="flow-canvas-page"
  >
    <FlowCanvasLayout
        :is-fullscreen="isFullscreen"
        @back="handleBack"
        @save="handleSave"
        @toggle-fullscreen="toggleFullscreen"
    />
  </div>
</template>

<script setup>
/**
 * 测试流画布页入口。
 * 负责加载/保存流、全屏与离开页未保存拦截；
 * 进入页隐藏全局侧栏，离开恢复。initFlow 时拉取 loadMemberCapabilities 供画布权限判断。
 * 画布校验（含 API 语义）由 useFlowValidation → validate?stage=canvas，不再平行 apiHealth preview。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import useAppStore from '@/store/modules/app'

import FlowCanvasLayout from './FlowCanvasLayout.vue'
import { useFlowGraph } from './composables/useFlowGraph'
import { useProjectTabTitle } from './composables/useProjectTabTitle'
import { resetProjectEnvs, useRunConfig } from './composables/useRunConfig'
import { useFlowCanvasStore } from './stores/flowCanvasStore'
import { useRunLibraryStore } from './stores/runLibraryStore'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const store = useFlowCanvasStore()
const runLib = useRunLibraryStore()
const { loadFlow, saveFlow } = useFlowGraph()
const { loadProjectEnvs } = useRunConfig()
const { loadProjectName } = useProjectTabTitle(
  route,
  () => String(store.flowName || '').trim() || '测试流画布',
)

/** 是否全屏编辑（整页 fixed 覆盖；全局侧栏由进/出画布页统一隐藏） */
const isFullscreen = ref(false)

function handleBack() {
  router.push(`/project/testProject/flows/${route.params.testProjectId}`)
}

/** 保存画布 */
async function handleSave() {
  await saveFlow()
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  // 全局侧栏在画布页始终隐藏；全屏只切换整页覆盖样式
  document.body.classList.toggle('fullscreen-detail-mode', isFullscreen.value)
}

/** 有未保存修改时，浏览器关闭/刷新弹出系统确认框 */
function onBeforeUnload(event) {
  if (!store.dirty) return
  event.preventDefault()
  event.returnValue = ''
}

/** 加载指定测试流：先出图，再并行拉鉴权/项目名/环境/Run */
async function initFlow() {
  const testFlowId = String(route.params.testFlowId ?? '')
  const testProjectId = String(route.params.testProjectId ?? '')
  if (!testFlowId) return
  store.reset()
  resetProjectEnvs()
  store.testProjectId = testProjectId
  try {
    await loadFlow(testFlowId)
    await Promise.all([
      store.loadProjectAuthConfig(),
      store.loadMemberCapabilities(),
      loadProjectName(testProjectId),
      loadProjectEnvs(),
      runLib.loadRuns(testFlowId),
    ])
    await applyRunIdDeepLink()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载测试流失败'
    ElMessage.error(message)
    if (testProjectId) {
      router.replace(`/project/testProject/flows/${testProjectId}`)
    } else {
      router.replace('/project/testProject')
    }
  }
}

/**
 * 支持 ?runId= 深链：加载运行库后选中该 Run 并打开运行详情。
 * 无效 id 静默忽略。
 */
async function applyRunIdDeepLink() {
  const runId = String(route.query.runId ?? '').trim()
  if (!runId) return
  try {
    const detail = await runLib.fetchRunDetail(runId)
    if (!detail) return
    await runLib.selectRun(runId)
    store.showRunPanel()
  } catch {
    // 深链失败不阻断画布
  }
}

onMounted(() => {
  appStore.toggleSideBarHide(true)
  window.addEventListener('beforeunload', onBeforeUnload)
  initFlow()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  appStore.toggleSideBarHide(false)
  document.body.classList.remove('fullscreen-detail-mode')
})

/** 路由切换到另一条测试流时重新初始化 */
watch(
  () => route.params.testFlowId,
  (id) => {
    if (id) initFlow()
  }
)
</script>

<style scoped lang="scss">
.flow-canvas-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.flow-canvas-page--fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  height: auto;
  min-height: 0;
  background: #fff;
}
</style>
