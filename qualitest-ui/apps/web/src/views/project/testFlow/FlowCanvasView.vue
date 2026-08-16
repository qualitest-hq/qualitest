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
 * 负责加载/保存流、挂载 API 语义预检、全屏与离开页未保存拦截；
 * 进入页隐藏全局侧栏，离开恢复。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import useAppStore from '@/store/modules/app'

import FlowCanvasLayout from './FlowCanvasLayout.vue'
import { useApiHealthDraftPreview } from './composables/useApiHealthDraftPreview'
import { useFlowGraph } from './composables/useFlowGraph'
import { useProjectTabTitle } from './composables/useProjectTabTitle'
import { resetProjectEnvs, useRunConfig } from './composables/useRunConfig'
import { useApiHealthStore } from './stores/apiHealthStore'
import { useFlowCanvasStore } from './stores/flowCanvasStore'
import { useRunLibraryStore } from './stores/runLibraryStore'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const store = useFlowCanvasStore()
const runLib = useRunLibraryStore()
const apiHealth = useApiHealthStore()
const { loadFlow, saveFlow } = useFlowGraph()
const { loadProjectEnvs } = useRunConfig()
const { loadProjectName } = useProjectTabTitle(
  route,
  () => String(store.flowName || '').trim() || '测试流画布',
)

/** 是否全屏编辑（整页 fixed 覆盖；全局侧栏由进/出画布页统一隐藏） */
const isFullscreen = ref(false)
/** 路由上的测试流 id，驱动预检调度 */
const testFlowIdRef = computed(() => String(route.params.testFlowId ?? ''))
/** runPreview：立刻预检；setSuspended：加载期暂停自动预检，避免重复请求 */
const { runPreview, setSuspended } = useApiHealthDraftPreview(testFlowIdRef)

function handleBack() {
  router.push(`/project/testProject/flows/${route.params.testProjectId}`)
}

/** 保存画布；成功后立刻再预检一次，刷新左上角 API 语义告警 */
async function handleSave() {
  const ok = await saveFlow()
  if (ok) {
    await runPreview()
  }
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

/** 加载指定测试流：重置状态 → 拉图/环境/Run 列表 → 首次 API 语义预检 */
async function initFlow() {
  const testFlowId = String(route.params.testFlowId ?? '')
  const testProjectId = String(route.params.testProjectId ?? '')
  if (!testFlowId) return
  // 加载期间暂停 watch，防止 loadFlow 写节点触发防抖预检，与结尾 runPreview 重复
  setSuspended(true)
  store.reset()
  apiHealth.clear()
  resetProjectEnvs()
  store.testProjectId = testProjectId
  try {
    await loadFlow(testFlowId)
    await loadProjectName(testProjectId)
    await loadProjectEnvs()
    await runLib.loadRuns(testFlowId)
    await runPreview()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载测试流失败'
    ElMessage.error(message)
    if (testProjectId) {
      router.replace(`/project/testProject/flows/${testProjectId}`)
    } else {
      router.replace('/project/testProject')
    }
  } finally {
    setSuspended(false)
  }
}

onMounted(() => {
  // 进入画布即藏全局侧栏，避免遮挡 Staging ✓
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
