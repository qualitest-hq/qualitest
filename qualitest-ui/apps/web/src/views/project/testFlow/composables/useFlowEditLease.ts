/**
 * 画布写锁管理。
 * <p>
 * 画布有内容改动（不只是挪坐标）时占住服务端写锁，并定时续期；
 * 改动已保存、只剩排版改动、切换测试流、离开画布时释放写锁。
 * 本页持锁期间，其它端（如 MCP）无法写入这张图。
 * <p>
 * 处理分两层：
 * - 观察：画布状态变化时，只记下「现在要不要锁、锁哪条流」；
 * - 执行：串行处理抢锁、释放，每做完一步重新判断，直到该持的锁已持有、该放的锁已放掉。
 * 抢锁请求返回时若想法已经变了（例如画布已保存），立即放掉刚拿到的锁，不续期。
 */
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch, type Ref } from 'vue'

import {
  acquireFlowEditLease,
  getFlowEditLeaseStatus,
  heartbeatFlowEditLease,
  releaseFlowEditLease,
} from '@/api/project/testFlow'

import {
  advanceFlowEditLeaseIntent,
  emptyFlowEditLeaseIntent,
  formatFlowEditLeaseHolder,
  getFlowEditLeaseToken,
  nextFlowEditLeaseStep,
  setFlowEditLeaseToken,
  shouldKeepLeaseAfterAcquire,
  type FlowEditLeaseIntent,
} from '../utils/flowEditLeaseState'
import { isLayoutOnlyDirtyFromStore } from '../utils/isLayoutOnlyDirty'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

/** 续期间隔（毫秒）；须短于服务端写锁存活时间，避免编辑中途锁过期 */
const HEARTBEAT_MS = 10_000
/** 查询他人是否占锁的间隔（毫秒）；只用于顶栏展示长时间占用 */
const STATUS_POLL_MS = 12_000
/** 收到外部改图通知后，顶栏短暂提示来源的时长（毫秒）；用于抢锁时间很短、轮询看不到的写入方 */
const ACTIVITY_PULSE_MS = 5_000

/** 写锁管理的入参 */
export interface UseFlowEditLeaseOptions {
  /** 当前编辑的测试流 id */
  testFlowId: Ref<string>
  /** 为 false 时不抢锁、不续期（例如模板只读画布） */
  enabled?: Ref<boolean>
}

/** 顶栏写锁徽章：self 为本页占锁，other 为他人占锁或刚有外部写入 */
export type FlowEditLeaseBadge =
  | { kind: 'self'; text: string }
  | { kind: 'other'; text: string }
  | null

/**
 * 挂载画布写锁管理，返回顶栏徽章与外部写入提示方法。
 */
export function useFlowEditLease(options: UseFlowEditLeaseOptions) {
  const store = useFlowCanvasStore()
  const stagingStore = useAiStagingStore()

  /** 当前想要的锁状态：哪条流、要不要锁，以及每次放弃或切流时递增的序号 */
  let intent: FlowEditLeaseIntent = emptyFlowEditLeaseIntent()
  /** 续期定时器；有值表示本页正在续期 */
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  /** 他人占锁状态的轮询定时器 */
  let statusTimer: ReturnType<typeof setInterval> | null = null
  /** 外部写入提示的自动消失定时器 */
  let activityPulseTimer: ReturnType<typeof setTimeout> | null = null
  /** 为 true 表示正在执行抢锁或释放，期间不再并发执行 */
  let busy = false
  /** 执行期间锁需求又变了，当前一轮结束后需要再处理一次 */
  let pendingDrive = false
  /** 本页正在续期的测试流 id；未持锁为空串 */
  let holdingFlowId = ''
  /**
   * 浏览器刷新或关闭过程中为 true。
   * 此时卸载组件不清本标签保存的锁凭证，同一标签重新打开后可直接续期。
   */
  let pageUnloading = false

  /** 本页是否正持有写锁并在续期 */
  const holdingLease = ref(false)
  /** 他人占锁时的展示名；本页持锁或无人占锁时为 null */
  const remoteHolderName = ref<string | null>(null)
  /** 外部写入方的短暂展示名（如 MCP）；对方已释放锁时仍提示几秒 */
  const activityPulseName = ref<string | null>(null)

  /** 清除外部写入提示 */
  function clearActivityPulse() {
    if (activityPulseTimer) {
      clearTimeout(activityPulseTimer)
      activityPulseTimer = null
    }
    activityPulseName.value = null
  }

  /**
   * 收到外部改图通知时调用：顶栏短暂显示「占用中 · 来源」，到时自动消失。
   * 用于抢锁、写完、放锁都很快的写入方，定时轮询通常捕捉不到。
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

  /**
   * 当前是否需要持锁。
   * 画布未保存且改动不只是坐标时需要；有未确认的 AI 待定改动也算需要。
   */
  function needsLongLease(): boolean {
    if (!store.dirty) return false
    return !isLayoutOnlyDirtyFromStore(store, stagingStore.pendingCount)
  }

  /** 停止续期，并把本页标记为未持锁 */
  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    holdingLease.value = false
    holdingFlowId = ''
  }

  /** 标记本页持有指定流的写锁，并开始定时续期 */
  function startHeartbeat(flowId: string) {
    clearHeartbeat()
    holdingLease.value = true
    holdingFlowId = flowId
    remoteHolderName.value = null
    clearActivityPulse()
    heartbeatTimer = setInterval(() => {
      void beat()
    }, HEARTBEAT_MS)
  }

  /**
   * 释放指定测试流的写锁。
   * 若正是本页续期的流，先停止续期；再清本地凭证并请求服务端删除锁。请求失败不影响本地编辑。
   */
  async function releaseFor(flowId: string) {
    const id = String(flowId || '').trim()
    if (holdingFlowId === id) {
      clearHeartbeat()
    }
    if (!id) return
    const t = getFlowEditLeaseToken(id)
    setFlowEditLeaseToken(id, null)
    if (!t) return
    try {
      await releaseFlowEditLease(id, t)
    } catch {
      // 释放失败不打断编辑
    }
  }

  /**
   * 按当前画布状态更新锁需求，然后触发执行。
   * 只记录要不要锁，不在这里直接抢锁或放锁。
   */
  function publishIntent() {
    const flowId = String(options.testFlowId.value || '').trim()
    const enabled = options.enabled?.value ?? true
    const wantLongLease = enabled && !!flowId && needsLongLease()
    intent = advanceFlowEditLeaseIntent(intent, { flowId, wantLongLease })
    void drive()
  }

  /**
   * 执行入口：串行处理抢锁与释放，直到实际持锁状态符合锁需求。
   * 正在执行时再次调用只做标记，当前一轮结束后自动补跑。
   */
  async function drive() {
    if (busy) {
      pendingDrive = true
      return
    }
    busy = true
    try {
      do {
        pendingDrive = false
        await driveOnce()
      } while (pendingDrive)
    } finally {
      busy = false
    }
    // 执行结束到清除 busy 之间若又有新需求，再补跑一次
    if (pendingDrive) {
      void drive()
    }
  }

  /**
   * 执行一步：根据锁需求和本页持锁情况，决定什么都不做、释放，还是抢锁。
   * 抢锁时本地已有凭证则先尝试续期，续期失败再重新抢。
   */
  async function driveOnce() {
    const step = nextFlowEditLeaseStep({
      intent,
      holding: holdingLease.value,
      holdingFlowId,
      hasTokenForIntentFlow: !!intent.flowId && !!getFlowEditLeaseToken(intent.flowId),
    })

    if (step.type === 'idle') return

    if (step.type === 'release') {
      await releaseFor(step.flowId)
      void refreshRemoteStatus()
      pendingDrive = true
      return
    }

    const flowId = step.flowId
    const epochAtStart = step.epoch
    const existing = getFlowEditLeaseToken(flowId)
    if (existing) {
      try {
        await heartbeatFlowEditLease(flowId, existing)
        await commitOrDiscard(flowId, existing, epochAtStart, true)
        return
      } catch {
        // 旧凭证已失效，清掉后重新抢锁
        setFlowEditLeaseToken(flowId, null)
      }
    }

    try {
      const res = await acquireFlowEditLease(flowId)
      const next = (res as { data?: { token?: string } })?.data?.token
      if (typeof next !== 'string' || !next) return
      await commitOrDiscard(flowId, next, epochAtStart, false)
    } catch {
      // 抢锁失败（多半被他人占用）仍可本地改图；保存时服务端会短时抢锁
    }
  }

  /**
   * 抢锁或续期成功后决定留下还是放掉。
   * 请求期间锁需求已作废或已不需要持锁：放掉这把锁，并再处理一次；否则保存凭证并开始续期。
   * alreadyStored 为 true 表示凭证本来就存在本地（续期场景），false 表示刚抢到、还没保存。
   */
  async function commitOrDiscard(
    flowId: string,
    token: string,
    epochAtStart: number,
    alreadyStored: boolean,
  ) {
    if (
      !shouldKeepLeaseAfterAcquire({
        epochAtStart,
        currentEpoch: intent.epoch,
        needsLongLease: needsLongLease(),
      })
    ) {
      if (alreadyStored) {
        await releaseFor(flowId)
      } else {
        try {
          await releaseFlowEditLease(flowId, token)
        } catch {
          // 释放失败不打断编辑
        }
      }
      pendingDrive = true
      return
    }
    if (!alreadyStored) {
      setFlowEditLeaseToken(flowId, token)
    }
    startHeartbeat(flowId)
  }

  /**
   * 定时续期一次。
   * 续期失败说明锁已丢失：清掉本地凭证并停止续期，再按当前画布状态决定是否重新抢锁。
   */
  async function beat() {
    const flowId = holdingFlowId || String(options.testFlowId.value || '').trim()
    const token = getFlowEditLeaseToken(flowId)
    if (!flowId || !token) return
    try {
      await heartbeatFlowEditLease(flowId, token)
    } catch {
      setFlowEditLeaseToken(flowId, null)
      clearHeartbeat()
      publishIntent()
    }
  }

  /** 改为不需要锁并触发执行，用于离开画布或组件卸载 */
  function dropWantAndDrive() {
    intent = advanceFlowEditLeaseIntent(intent, { flowId: intent.flowId, wantLongLease: false })
    void drive()
  }

  /** 查询当前流是否被他人占锁，结果用于顶栏展示；本页持锁时不查 */
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
      // 占锁的是本标签自己的凭证，不算他人占用
      const ours = getFlowEditLeaseToken(flowId)
      if (ours && raw === ours) {
        remoteHolderName.value = null
        return
      }
      remoteHolderName.value = formatFlowEditLeaseHolder(raw)
    } catch {
      // 查询失败不打断编辑
    }
  }

  /** 停止他人占锁状态的轮询 */
  function clearStatusPoll() {
    if (statusTimer) {
      clearInterval(statusTimer)
      statusTimer = null
    }
  }

  /** 立即查一次他人占锁状态，并按固定间隔继续轮询 */
  function startStatusPoll() {
    clearStatusPoll()
    void refreshRemoteStatus()
    statusTimer = setInterval(() => {
      void refreshRemoteStatus()
    }, STATUS_POLL_MS)
  }

  // 未保存标记、AI 待定改动数、已保存基线、流 id、开关变化时，重新计算锁需求
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
      publishIntent()
    },
    { immediate: true },
  )

  // 节点、边、场景、流输出有深层变化且画布未保存时，重新判断是否只是排版改动
  watch(
    () => [store.nodes, store.edges, store.runConfig, store.flowOutputs] as const,
    () => {
      if (!store.dirty) return
      publishIntent()
    },
    { deep: true },
  )

  // 启用且有流 id 时轮询他人占锁状态；否则停止轮询并清空展示
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

  // 页面被缓存隐藏（离开画布）时释放写锁
  onDeactivated(() => {
    dropWantAndDrive()
  })

  // 从缓存重新显示时，按当前画布状态重新决定是否抢锁，并刷新他人占锁状态
  onActivated(() => {
    publishIntent()
    void refreshRemoteStatus()
  })

  /** 标记浏览器即将刷新或关闭页面 */
  function onPageHide() {
    pageUnloading = true
  }

  window.addEventListener('pagehide', onPageHide)

  onBeforeUnmount(() => {
    window.removeEventListener('pagehide', onPageHide)
    clearStatusPoll()
    clearActivityPulse()
    if (pageUnloading) {
      // 刷新或关闭页面：只停续期并作废进行中的抢锁，保留本地凭证，下次打开可直接续期
      intent = advanceFlowEditLeaseIntent(intent, { flowId: intent.flowId, wantLongLease: false })
      clearHeartbeat()
      return
    }
    // 站内跳转卸载：完整释放服务端写锁
    dropWantAndDrive()
  })

  /** 顶栏徽章：优先显示本页持锁，其次他人占锁，最后是外部写入的短暂提示 */
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
