/**
 * 画布脏稿写锁生命周期。
 * <p>
 * 仅内容脏（非只改坐标/排版）时占用服务端写锁并心跳续期；
 * 变为仅布局脏、干净、路由离开、切流时释放。
 * 本标签页把租约 token 写入 sessionStorage，刷新后可先续约再决定是否重新抢锁。
 * 只挡写图；只读打开画布不占锁；排版与拖拽不占长锁。
 */
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch, type Ref } from 'vue'

import {
  acquireFlowEditLease,
  getFlowEditLeaseStatus,
  heartbeatFlowEditLease,
  releaseFlowEditLease,
} from '@/api/project/testFlow'

import {
  getFlowEditLeaseToken,
  setFlowEditLeaseToken,
  formatFlowEditLeaseHolder,
} from '../utils/flowEditLeaseState'
import { isLayoutOnlyDirtyFromStore } from '../utils/isLayoutOnlyDirty'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

/** 心跳间隔（毫秒）；须短于服务端写锁存活时间，避免编辑中途租约过期 */
const HEARTBEAT_MS = 10_000
/** 查询他人占用状态的间隔（毫秒）；短时抢锁不常驻顶栏，仅用于展示长占用 */
const STATUS_POLL_MS = 12_000
/** 收到外部改图通知后，顶栏短暂展示来源的时长（毫秒）；覆盖 MCP 等短抢短释 */
const ACTIVITY_PULSE_MS = 5_000

/** 写锁 composable 入参 */
export interface UseFlowEditLeaseOptions {
  /** 当前编辑的测试流 id */
  testFlowId: Ref<string>
  /** 为 false 时不抢锁、不续期（例如模板只读画布） */
  enabled?: Ref<boolean>
}

/** 顶栏租约徽章文案 */
export type FlowEditLeaseBadge =
  | { kind: 'self'; text: string }
  | { kind: 'other'; text: string }
  | null

/**
 * 挂载脏稿写锁：监听内容脏 / 流 id / 开关，管理抢锁、心跳与释放；并轮询他人占用状态。
 */
export function useFlowEditLease(options: UseFlowEditLeaseOptions) {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()
  /** 心跳定时器；有值表示本页已在续期中 */
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  /** 他人占用状态轮询定时器 */
  let statusTimer: ReturnType<typeof setInterval> | null = null
  /** 外部改图短暂展示定时器 */
  let activityPulseTimer: ReturnType<typeof setTimeout> | null = null
  /** 为 true 时跳过新的抢锁请求，避免并发重复占用 */
  let acquiring = false
  /** 上一次处理过的测试流 id；变化时先释放旧流锁 */
  let watchedFlowId = ''
  /**
   * 浏览器刷新或关闭过程中为 true。
   * 此时卸载组件不清 sessionStorage 里的 token，方便同标签重新打开后续约。
   */
  let pageUnloading = false

  /** 本页是否正持有长租约（已心跳中） */
  const holdingLease = ref(false)
  /** 他人占用时的展示名；本页持锁或无人占用时为 null */
  const remoteHolderName = ref<string | null>(null)
  /** 外部改图短暂展示名（如 MCP）；锁已释放仍提示约数秒 */
  const activityPulseName = ref<string | null>(null)

  /** 清除外部改图短暂展示 */
  function clearActivityPulse() {
    if (activityPulseTimer) {
      clearTimeout(activityPulseTimer)
      activityPulseTimer = null
    }
    activityPulseName.value = null
  }

  /**
   * 收到外部改图通知时调用：顶栏短暂亮「占用中 · 来源」。
   * 用于 MCP 等短抢短释，轮询往往捕不到真实持锁窗口。
   */
  function pulseRemoteActivity(label: string, ms = ACTIVITY_PULSE_MS) {
    const name = label != null ? String(label).trim() : ''
    if (!name) return
    if (activityPulseTimer) {
      clearTimeout(activityPulseTimer)
      activityPulseTimer = null
    }
    activityPulseName.value = name
    activityPulseTimer = setTimeout(() => {
      activityPulseTimer = null
      activityPulseName.value = null
    }, ms)
  }

  /** 是否需要长占写锁：内容脏（非仅布局）且有 pending Staging 也算内容脏 */
  function needsLongLease(): boolean {
    if (!store.dirty) return false
    return !isLayoutOnlyDirtyFromStore(store, stagingStore.pendingCount)
  }

  /** 按当前脏态同步占锁 / 释锁 */
  function syncLeaseForCurrentFlow() {
    const nextId = String(options.testFlowId.value || '').trim()
    if (watchedFlowId && watchedFlowId !== nextId) {
      void releaseFor(watchedFlowId)
    }
    watchedFlowId = nextId

    const enabled = options.enabled?.value ?? true
    if (!enabled || !nextId || !needsLongLease()) {
      // 仅在本页确有租约时释锁，避免仅拖排版时深监听反复打释放/状态接口
      const hadLease = holdingLease.value || !!getFlowEditLeaseToken(nextId)
      if (hadLease) {
        void releaseCurrent().then(() => {
          void refreshRemoteStatus()
        })
      }
      return
    }
    void ensureLease()
  }

  /** 停止心跳定时器 */
  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    holdingLease.value = false
  }

  /** 启动心跳：每隔 HEARTBEAT_MS 向服务端续期一次 */
  function startHeartbeat() {
    clearHeartbeat()
    holdingLease.value = true
    remoteHolderName.value = null
    clearActivityPulse()
    heartbeatTimer = setInterval(() => {
      void beat()
    }, HEARTBEAT_MS)
  }

  /**
   * 释放指定测试流的写锁。
   * 先停心跳、清本地 token，再请求服务端删除租约；请求失败不影响本地编辑。
   */
  async function releaseFor(flowId: string) {
    const id = String(flowId || '').trim()
    clearHeartbeat()
    if (!id) return
    const t = getFlowEditLeaseToken(id)
    setFlowEditLeaseToken(id, null)
    if (!t) return
    try {
      await releaseFlowEditLease(id, t)
    } catch {
      // 释锁失败不打断编辑
    }
  }

  /** 释放当前 options.testFlowId 对应的写锁 */
  function releaseCurrent() {
    return releaseFor(String(options.testFlowId.value || ''))
  }

  /**
   * 确保当前流持有写锁。
   * 若本地已有 token，先心跳续约；续约失败则清空后重新抢锁；成功后启动心跳。
   * 已在心跳中或开关关闭时直接返回。抢锁失败仍允许本地改图。
   */
  async function ensureLease() {
    const flowId = String(options.testFlowId.value || '').trim()
    if (!flowId || acquiring) return
    if (options.enabled && !options.enabled.value) return
    if (!needsLongLease()) return
    if (heartbeatTimer && getFlowEditLeaseToken(flowId)) return

    acquiring = true
    try {
      const existing = getFlowEditLeaseToken(flowId)
      if (existing) {
        try {
          await heartbeatFlowEditLease(flowId, existing)
          startHeartbeat()
          return
        } catch {
          setFlowEditLeaseToken(flowId, null)
        }
      }
      const res = await acquireFlowEditLease(flowId)
      const next = (res as { data?: { token?: string } })?.data?.token
      if (typeof next === 'string' && next) {
        setFlowEditLeaseToken(flowId, next)
        startHeartbeat()
      }
    } catch {
      // 抢锁失败仍可本地改图；保存时由服务端短时抢锁
    } finally {
      acquiring = false
    }
  }

  /**
   * 执行一次心跳续期。
   * 失败则清除本地 token 与定时器；若仍需内容脏长租约则再次尝试占锁。
   */
  async function beat() {
    const flowId = String(options.testFlowId.value || '').trim()
    const token = getFlowEditLeaseToken(flowId)
    if (!flowId || !token) return
    try {
      await heartbeatFlowEditLease(flowId, token)
    } catch {
      setFlowEditLeaseToken(flowId, null)
      clearHeartbeat()
      if (needsLongLease()) {
        void ensureLease()
      }
    }
  }

  /** 查询他人是否占用写锁（本页持锁时不查） */
  async function refreshRemoteStatus() {
    const flowId = String(options.testFlowId.value || '').trim()
    if (!flowId || (options.enabled && !options.enabled.value)) {
      remoteHolderName.value = null
      return
    }
    if (holdingLease.value) {
      remoteHolderName.value = null
      return
    }
    try {
      const res = await getFlowEditLeaseStatus(flowId)
      const held = (res as { data?: { lockHeldBy?: string | null } })?.data?.lockHeldBy
      const raw = held != null ? String(held).trim() : ''
      if (!raw) {
        remoteHolderName.value = null
        return
      }
      const ours = getFlowEditLeaseToken(flowId)
      if (ours && raw === ours) {
        remoteHolderName.value = null
        return
      }
      remoteHolderName.value = formatFlowEditLeaseHolder(raw)
    } catch {
      // 状态接口未就绪或失败时不打断编辑
    }
  }

  /** 停止他人占用状态轮询定时器 */
  function clearStatusPoll() {
    if (statusTimer) {
      clearInterval(statusTimer)
      statusTimer = null
    }
  }

  /** 立即查一次他人占用，并按固定间隔继续轮询 */
  function startStatusPoll() {
    clearStatusPoll()
    void refreshRemoteStatus()
    statusTimer = setInterval(() => {
      void refreshRemoteStatus()
    }, STATUS_POLL_MS)
  }

  // dirty / Staging / 流 id / 开关：切流先释旧锁；不需长租约则释放；否则占锁
  watch(
    () =>
      [
        store.dirty,
        stagingStore.pendingCount,
        store.savedGraphSnapshot,
        options.testFlowId.value,
        options.enabled?.value ?? true,
      ] as const,
    () => {
      syncLeaseForCurrentFlow()
    },
    { immediate: true },
  )

  // 节点/边/场景深变更：已脏时需重判是否仅布局脏（如先拖拽再改内容）
  watch(
    () => [store.nodes, store.edges, store.runConfig, store.flowOutputs] as const,
    () => {
      if (!store.dirty) return
      syncLeaseForCurrentFlow()
    },
    { deep: true },
  )

  // 启用且有流 id 时轮询他人占用
  watch(
    () => [options.testFlowId.value, options.enabled?.value ?? true] as const,
    ([flowId, enabled]) => {
      if (!enabled || !String(flowId || '').trim()) {
        clearStatusPoll()
        remoteHolderName.value = null
        clearActivityPulse()
        return
      }
      startStatusPoll()
    },
    { immediate: true },
  )

  // 路由缓存停用：离开画布时释放写锁
  onDeactivated(() => {
    void releaseCurrent()
  })

  // 路由缓存重新激活：若仍需内容脏长租约则重新占锁或续约
  onActivated(() => {
    if (needsLongLease() && (options.enabled?.value ?? true) && options.testFlowId.value) {
      void ensureLease()
    }
    void refreshRemoteStatus()
  })

  /** 标记即将刷新或关闭页面 */
  function onPageHide() {
    pageUnloading = true
  }

  window.addEventListener('pagehide', onPageHide)

  onBeforeUnmount(() => {
    window.removeEventListener('pagehide', onPageHide)
    clearStatusPoll()
    clearActivityPulse()
    if (pageUnloading) {
      // 刷新/关页：只停心跳，保留 sessionStorage token 供下次续约
      clearHeartbeat()
      return
    }
    // 路由内卸载：完整释放服务端写锁
    void releaseCurrent()
  })

  /** 顶栏徽章：本页持锁 / 轮询到的他人占用 / 外部改图短暂提示 */
  const leaseBadge = computed<FlowEditLeaseBadge>(() => {
    if (holdingLease.value) {
      return { kind: 'self', text: '编辑中 · 外部写图已暂停' }
    }
    if (remoteHolderName.value) {
      return { kind: 'other', text: `占用中 · ${remoteHolderName.value}` }
    }
    if (activityPulseName.value) {
      return { kind: 'other', text: `占用中 · ${activityPulseName.value}` }
    }
    return null
  })

  return {
    leaseBadge,
    pulseRemoteActivity,
  }
}
