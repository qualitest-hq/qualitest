/**
 * 画布脏稿写锁。
 * 画布变 dirty 时抢锁；约每 20 秒心跳续期；变干净、离开页或切流时释放。
 * token 存模块状态，保存请求头带上可续期不换锁。
 */
import { onBeforeUnmount, watch, type Ref } from 'vue'

import {
  acquireFlowEditLease,
  heartbeatFlowEditLease,
  releaseFlowEditLease,
} from '@/api/project/testFlow'

import {
  getFlowEditLeaseToken,
  setFlowEditLeaseToken,
} from '../utils/flowEditLeaseState'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

/** 心跳间隔（毫秒），须小于服务端租约 TTL */
const HEARTBEAT_MS = 20_000

export interface UseFlowEditLeaseOptions {
  testFlowId: Ref<string>
  /** false 时不占锁（如模板画布） */
  enabled?: Ref<boolean>
}

export function useFlowEditLease(options: UseFlowEditLeaseOptions) {
  const store = useFlowCanvasStore()
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  /** 防止并发重复抢锁 */
  let acquiring = false

  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  /** 释放写锁并清空本地 token */
  async function release() {
    clearHeartbeat()
    const flowId = String(options.testFlowId.value || '')
    const t = getFlowEditLeaseToken()
    setFlowEditLeaseToken(null)
    if (!flowId || !t) return
    try {
      await releaseFlowEditLease(flowId, t)
    } catch {
      // 释锁失败不打断编辑
    }
  }

  /** 抢占写锁并启动心跳；已持锁或抢锁失败则跳过 */
  async function acquire() {
    const flowId = String(options.testFlowId.value || '')
    if (!flowId || acquiring || getFlowEditLeaseToken()) return
    if (options.enabled && !options.enabled.value) return
    acquiring = true
    try {
      const res = await acquireFlowEditLease(flowId)
      const next = (res as { data?: { token?: string } })?.data?.token
      if (typeof next === 'string' && next) {
        setFlowEditLeaseToken(next)
        clearHeartbeat()
        heartbeatTimer = setInterval(() => {
          void beat()
        }, HEARTBEAT_MS)
      }
    } catch {
      // 抢锁失败仍可本地改图；保存时服务端再短抢
    } finally {
      acquiring = false
    }
  }

  /** 单次心跳；失败则清 token，若仍 dirty 则重新抢锁 */
  async function beat() {
    const flowId = String(options.testFlowId.value || '')
    const token = getFlowEditLeaseToken()
    if (!flowId || !token) return
    try {
      await heartbeatFlowEditLease(flowId, token)
    } catch {
      setFlowEditLeaseToken(null)
      clearHeartbeat()
      if (store.dirty) {
        void acquire()
      }
    }
  }

  watch(
    () => [store.dirty, options.testFlowId.value, options.enabled?.value ?? true] as const,
    ([dirty, flowId, enabled]) => {
      if (!enabled || !flowId) {
        void release()
        return
      }
      if (dirty) {
        void acquire()
      } else {
        void release()
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    void release()
  })
}
