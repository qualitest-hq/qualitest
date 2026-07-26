import { listTestProjectAsset } from '@/api/project/testProjectAsset'
import { buildDebugFlowContext } from '@/utils/flow/flowContextBuilder'
import { ref, watch } from 'vue'

/** sessionStorage 键前缀：按项目隔离 flow 变量 */
const FLOW_STORAGE_PREFIX = 'qualitest-debug-flow-'

function readPersistedFlow(projectId) {
  if (projectId == null) return {}
  try {
    const raw = sessionStorage.getItem(`${FLOW_STORAGE_PREFIX}${projectId}`)
    if (!raw) return {}
    const o = JSON.parse(raw)
    return o && typeof o === 'object' && !Array.isArray(o) ? o : {}
  } catch {
    return {}
  }
}

function writePersistedFlow(projectId, flow) {
  if (projectId == null) return
  try {
    sessionStorage.setItem(`${FLOW_STORAGE_PREFIX}${projectId}`, JSON.stringify(flow ?? {}))
  } catch {
    /* ignore quota */
  }
}

/**
 * 测试流场景运行用的 FlowRunContext 组合式函数。
 *
 * - env / asset：随 testProjectId、选中环境、素材列表变化而重建
 * - flow：跨步骤保留，并写入 sessionStorage（刷新页面可恢复）
 * - lastResponse：由场景运行执行器在 http 步骤后更新
 */
export function useDebugFlowContext(getOptions) {
  const flowCtx = ref(buildDebugFlowContext())
  const assetEntries = ref([])
  const assetsLoading = ref(false)

  async function loadAssets(testProjectId) {
    if (!testProjectId) {
      assetEntries.value = []
      return
    }
    assetsLoading.value = true
    try {
      const res = await listTestProjectAsset({ testProjectId })
      assetEntries.value = res?.rows ?? res?.data ?? []
    } catch {
      assetEntries.value = []
    } finally {
      assetsLoading.value = false
    }
  }

  /** 用当前环境、素材与持久化的 flow 重建上下文 */
  function rebuildContext() {
    const opts = typeof getOptions === 'function' ? getOptions() : getOptions
    const projectId = opts?.testProjectId
    const env = opts?.envList?.find(
      (e) => String(e.testProjectEnvId) === String(opts?.testProjectEnvId),
    )
    const persistedFlow = readPersistedFlow(projectId)
    flowCtx.value = buildDebugFlowContext({
      envUrl: env?.envUrl,
      envVariables: env?.envVariables,
      assetEntries: assetEntries.value,
      flow: persistedFlow,
    })
  }

  function persistFlow() {
    const opts = typeof getOptions === 'function' ? getOptions() : getOptions
    writePersistedFlow(opts?.testProjectId, flowCtx.value.flow)
  }

  /** 清空 flow 与 lastResponse，并清除 session 缓存 */
  function resetFlow() {
    const opts = typeof getOptions === 'function' ? getOptions() : getOptions
    flowCtx.value.flow = {}
    flowCtx.value.lastResponse = null
    writePersistedFlow(opts?.testProjectId, {})
  }

  watch(
    () => {
      const opts = typeof getOptions === 'function' ? getOptions() : getOptions
      return [opts?.testProjectId, opts?.testProjectEnvId, opts?.envList]
    },
    async ([projectId]) => {
      await loadAssets(projectId)
      rebuildContext()
    },
    { immediate: true, deep: true },
  )

  watch(
    () => flowCtx.value.flow,
    () => persistFlow(),
    { deep: true },
  )

  return {
    flowCtx,
    assetEntries,
    assetsLoading,
    rebuildContext,
    resetFlow,
    persistFlow,
  }
}
