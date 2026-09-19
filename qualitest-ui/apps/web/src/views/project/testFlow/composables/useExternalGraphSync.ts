/**
 * 打开画布后订阅外部写入通知，按变更类型轻量刷新（图 / 素材 / 鉴权 / 环境 / 开跑）。
 * 不走整页初始化；本地有脏稿或未决 Staging 时不静默覆盖，顶栏条幅让用户选择。
 * 图事件逐帧串行应用：有增量则合并进画布，失败再整图重拉（不防抖覆盖，避免丢增量）。
 */
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'

import { getTestFlow } from '@/api/project/testFlow'
import {
  subscribeFlowEvents,
  type FlowExternalChangeEvent,
} from '@/api/project/testFlowEvents'

import { clearAllStagingState } from '../utils/stagingCleanup'
import { resetStagingAcceptanceMaps } from '../utils/stagingAcceptance'
import {
  externalChangeSourceBannerSuffix,
  externalChangeSourceLabel,
  isExternalGraphSyncSuppressed,
  noteAppliedGraphUpdateTime,
  setExternalGraphCommittedHandler,
  shouldApplyGraphUpdate,
} from '../utils/externalGraphSyncState'
import {
  hasExternalGraphPatches,
  mergeExternalGraphPatches,
} from '../utils/mergeExternalGraphPatches'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useRunLibraryStore } from '../stores/runLibraryStore'
import { useFlowGraph } from './useFlowGraph'
import { useFlowScenarioRun } from './useFlowScenarioRun'
import { useApiHealthDraftPreview } from './useApiHealthDraftPreview'
import { useRunConfig } from './useRunConfig'
import { fetchProjectAssetRows } from './useProjectVariables'

/** 素材/鉴权/环境防抖（毫秒） */
const OTHER_DEBOUNCE_MS = 800
/** 外部同步节点高亮时长（毫秒） */
const EXTERNAL_HIGHLIGHT_MS = 5000
/** 断线重连间隔（毫秒） */
const RECONNECT_MS = 3000

export interface UseExternalGraphSyncOptions {
  testFlowId: Ref<string>
  testProjectId: Ref<string>
  /** false 时不订阅（如模板画布） */
  enabled?: Ref<boolean>
  /** true：项目设置抽屉打开，鉴权事件不自动刷新 */
  authFormDirty?: Ref<boolean>
  /** true：左栏参数库打开，素材事件不自动刷新 */
  assetFormDirty?: Ref<boolean>
}

/** 顶栏「外部已更新」条幅项 */
export interface ExternalConflictBanner {
  id: string
  text: string
  acceptLabel: string
  onAccept: () => void | Promise<void>
  onDismiss: () => void
}

/** 一组 pending 状态：是否有外部冲突、来源文案、标记/清除 */
function createPendingExternal() {
  const pending = ref(false)
  const source = ref<string | null>(null)
  function mark(src?: string | null) {
    pending.value = true
    source.value = src ?? null
  }
  function clear() {
    pending.value = false
    source.value = null
  }
  return { pending, source, mark, clear }
}

export function useExternalGraphSync(options: UseExternalGraphSyncOptions) {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()
  const { loadFlow } = useFlowGraph()
  const { watchRunLive } = useFlowScenarioRun()
  const { setSuspended } = useApiHealthDraftPreview(options.testFlowId)
  const { loadProjectEnvs } = useRunConfig()
  const runLib = useRunLibraryStore()

  const graphConflict = createPendingExternal()
  const authConflict = createPendingExternal()
  const assetConflict = createPendingExternal()

  let abort: AbortController | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let highlightTimer: ReturnType<typeof setTimeout> | null = null
  const domainTimers: Record<string, ReturnType<typeof setTimeout> | null> = {
    asset: null,
    auth: null,
    env: null,
  }
  /** 图变更逐帧串行队列（有多少推多少，避免 last-wins 丢增量与并发写 store） */
  const pendingGraphEvents: FlowExternalChangeEvent[] = []
  let graphPumpRunning = false
  let stopped = false

  function fingerprintNodes(nodes: Array<{ id?: string; data?: unknown; position?: unknown }>) {
    const map = new Map<string, string>()
    for (const n of nodes) {
      if (!n?.id) continue
      map.set(n.id, JSON.stringify({ data: n.data, position: n.position }))
    }
    return map
  }

  /** 对比灌图前后节点指纹，得到需高亮的 id（新增或 data/position 有变） */
  function diffHighlightIds(
    before: Map<string, string>,
    nodes: Array<{ id?: string; data?: unknown; position?: unknown }>,
  ) {
    const highlightIds: string[] = []
    for (const n of nodes) {
      const id = String(n.id)
      const prev = before.get(id)
      if (prev === undefined) highlightIds.push(id)
      else if (prev !== JSON.stringify({ data: n.data, position: n.position })) highlightIds.push(id)
    }
    return highlightIds
  }

  function matchesCurrentFlow(event: FlowExternalChangeEvent) {
    return !event.testFlowId || String(event.testFlowId) === String(options.testFlowId.value)
  }

  function matchesCurrentProject(event: FlowExternalChangeEvent) {
    return !event.testProjectId || String(event.testProjectId) === String(options.testProjectId.value)
  }

  function scheduleDomain(key: 'asset' | 'auth' | 'env', run: () => void) {
    const prev = domainTimers[key]
    if (prev) clearTimeout(prev)
    domainTimers[key] = setTimeout(() => {
      domainTimers[key] = null
      run()
    }, OTHER_DEBOUNCE_MS)
  }

  function applyHighlight(ids: string[]) {
    if (!ids.length) return
    store.setExternalSyncHighlight(ids)
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => store.clearExternalSyncHighlight(), EXTERNAL_HIGHLIGHT_MS)
  }

  /** 应用一次服务端图更新：有增量则合并，否则整图重拉；本地脏则只亮条幅 */
  async function applyGraphFromServer(event: FlowExternalChangeEvent) {
    const flowId = String(event.testFlowId || options.testFlowId.value || '')
    if (!flowId || flowId !== String(options.testFlowId.value)) return
    if (!shouldApplyGraphUpdate(flowId, event.updateTime)) return
    if (isExternalGraphSyncSuppressed() && event.source === 'web-save') {
      noteAppliedGraphUpdateTime(flowId, event.updateTime)
      return
    }

    const dirty = store.dirty || stagingStore.pendingCount > 0
    if (dirty) {
      graphConflict.mark(event.source)
      return
    }

    const before = fingerprintNodes(store.nodes as Array<{ id?: string; data?: unknown; position?: unknown }>)
    setSuspended(true)
    try {
      let highlightIds: string[] = []
      if (hasExternalGraphPatches(event)) {
        try {
          highlightIds = mergeExternalGraphPatches(store, event)
          await store.ensureEdgesHydrated()
        } catch {
          await loadFlow(flowId)
          highlightIds = diffHighlightIds(before, store.nodes)
        }
      } else {
        await loadFlow(flowId)
        highlightIds = diffHighlightIds(before, store.nodes)
      }

      noteAppliedGraphUpdateTime(flowId, event.updateTime)
      graphConflict.clear()
      applyHighlight(highlightIds)

      if (event.source && event.source !== 'web-save') {
        ElMessage.success(`${externalChangeSourceLabel(event.source)}已更新画布`)
      }
    } catch {
      ElMessage.warning('外部已更新，但同步画布失败，请手动刷新')
    } finally {
      setSuspended(false)
    }
  }

  /** 入队并串行 apply：来一帧推一帧，不覆盖、不加防抖延迟 */
  function enqueueGraph(event: FlowExternalChangeEvent) {
    pendingGraphEvents.push(event)
    void pumpGraphQueue()
  }

  async function pumpGraphQueue() {
    if (graphPumpRunning) return
    graphPumpRunning = true
    try {
      while (!stopped && pendingGraphEvents.length > 0) {
        const e = pendingGraphEvents.shift()
        if (e) await applyGraphFromServer(e)
      }
    } finally {
      graphPumpRunning = false
      // 泵退出到清标志之间若又入队，补跑一轮
      if (!stopped && pendingGraphEvents.length > 0) {
        void pumpGraphQueue()
      }
    }
  }

  /** 放弃本地脏稿并整图重拉 */
  async function discardLocalAndPull() {
    clearAllStagingState()
    resetStagingAcceptanceMaps()
    store.markClean()
    graphConflict.clear()
    const flowId = String(options.testFlowId.value)
    if (!flowId) return
    setSuspended(true)
    try {
      await loadFlow(flowId)
      ElMessage.success('已放弃本地修改并拉取最新画布')
    } catch {
      ElMessage.error('拉取失败')
    } finally {
      setSuspended(false)
    }
  }

  /** 放弃鉴权侧未保存态，重新拉取项目鉴权 */
  async function discardAuthAndRefresh() {
    authConflict.clear()
    try {
      await store.loadProjectAuthConfig()
      ElMessage.success('已刷新鉴权配置')
    } catch {
      ElMessage.error('刷新鉴权失败')
    }
  }

  /** 放弃素材侧未保存态，重新拉取项目素材变量 */
  async function discardAssetAndRefresh() {
    assetConflict.clear()
    const projectId = String(options.testProjectId.value || '')
    if (!projectId) return
    try {
      await fetchProjectAssetRows(projectId)
      ElMessage.success('已刷新素材变量')
    } catch {
      ElMessage.error('刷新素材失败')
    }
  }

  /** 顶栏条幅列表：图 / 鉴权 / 素材冲突 */
  const externalConflictBanners = computed<ExternalConflictBanner[]>(() => {
    const list: ExternalConflictBanner[] = []
    if (graphConflict.pending.value) {
      list.push({
        id: 'graph',
        text: `外部已更新画布${externalChangeSourceBannerSuffix(graphConflict.source.value)}`,
        acceptLabel: '放弃本地并拉取',
        onAccept: discardLocalAndPull,
        onDismiss: graphConflict.clear,
      })
    }
    if (authConflict.pending.value) {
      list.push({
        id: 'auth',
        text: `外部已更新鉴权${externalChangeSourceBannerSuffix(authConflict.source.value)}`,
        acceptLabel: '放弃并刷新',
        onAccept: discardAuthAndRefresh,
        onDismiss: authConflict.clear,
      })
    }
    if (assetConflict.pending.value) {
      list.push({
        id: 'asset',
        text: `外部已更新素材${externalChangeSourceBannerSuffix(assetConflict.source.value)}`,
        acceptLabel: '放弃并刷新',
        onAccept: discardAssetAndRefresh,
        onDismiss: assetConflict.clear,
      })
    }
    return list
  })

  async function refreshFlowMetaOnly() {
    const flowId = String(options.testFlowId.value || '')
    if (!flowId) return
    try {
      const res = await getTestFlow(flowId)
      const name = res.data?.flowName
      if (typeof name === 'string' && name !== store.flowName) {
        store.flowName = name
      }
    } catch {
      // 元数据刷新失败不打断画布
    }
  }

  /** 分发外部事件：图合并/重拉、素材鉴权环境刷新、开跑接听、流名更新 */
  function handleEvent(event: FlowExternalChangeEvent) {
    if (event.type === 'ping' || event.type === 'subscribed') return

    if (event.type === 'graphCommitted') {
      if (!matchesCurrentFlow(event)) return
      if (store.runHighlightNodeId) {
        ElMessage.info(`${externalChangeSourceLabel(event.source)}已改图，将同步到画布`)
      }
      enqueueGraph(event)
      return
    }

    if (event.type === 'runStarted' && event.runId) {
      if (!matchesCurrentFlow(event)) return
      void watchRunLive(String(event.runId), { takeOver: false })
      void runLib.loadRuns(String(options.testFlowId.value)).catch(() => undefined)
      return
    }

    if (event.type === 'assetVariablesChanged') {
      if (!matchesCurrentProject(event)) return
      scheduleDomain('asset', () => {
        if (options.assetFormDirty?.value) {
          assetConflict.mark(event.source)
          return
        }
        const projectId = String(options.testProjectId.value || '')
        if (projectId) void fetchProjectAssetRows(projectId).catch(() => undefined)
        ElMessage.info(`${externalChangeSourceLabel(event.source)}已更新素材变量`)
      })
      return
    }

    if (event.type === 'authConfigChanged') {
      if (!matchesCurrentProject(event)) return
      scheduleDomain('auth', () => {
        if (options.authFormDirty?.value) {
          authConflict.mark(event.source)
          return
        }
        void store.loadProjectAuthConfig()
          .then(() => ElMessage.info(`${externalChangeSourceLabel(event.source)}已更新鉴权配置`))
          .catch(() => undefined)
      })
      return
    }

    if (event.type === 'projectEnvsChanged') {
      if (!matchesCurrentProject(event)) return
      scheduleDomain('env', () => {
        void loadProjectEnvs(true)
          .then(() => ElMessage.info(`${externalChangeSourceLabel(event.source)}已更新环境列表`))
          .catch(() => undefined)
      })
      return
    }

    if (event.type === 'flowMetaChanged') {
      if (!matchesCurrentFlow(event)) return
      void refreshFlowMetaOnly()
    }
  }

  function scheduleReconnect(connect: () => void) {
    if (stopped) return
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(connect, RECONNECT_MS)
  }

  function stop() {
    stopped = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = null
    abort?.abort()
    abort = null
    pendingGraphEvents.length = 0
    for (const key of Object.keys(domainTimers) as Array<keyof typeof domainTimers>) {
      if (domainTimers[key]) clearTimeout(domainTimers[key]!)
      domainTimers[key] = null
    }
    if (highlightTimer) clearTimeout(highlightTimer)
  }

  function start() {
    stop()
    stopped = false
    const flowId = String(options.testFlowId.value || '')
    if (!flowId) return
    if (options.enabled && !options.enabled.value) return

    const connect = () => {
      if (stopped) return
      abort = new AbortController()
      void subscribeFlowEvents({
        testFlowId: flowId,
        signal: abort.signal,
        onEvent: handleEvent,
        onError: () => scheduleReconnect(connect),
      }).catch(() => scheduleReconnect(connect))
    }
    connect()
  }

  watch(
    () => [options.testFlowId.value, options.enabled?.value ?? true] as const,
    () => {
      start()
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    stop()
    store.clearExternalSyncHighlight()
    setExternalGraphCommittedHandler(null)
  })

  setExternalGraphCommittedHandler((testFlowId, updateTime) => {
    enqueueGraph({ type: 'graphCommitted', testFlowId, updateTime, source: 'web-autopilot' })
  })

  return {
    externalConflictBanners,
  }
}
