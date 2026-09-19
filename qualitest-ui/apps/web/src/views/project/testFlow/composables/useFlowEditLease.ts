/**
 * 画布脏稿写锁生命周期。
 *
 * 有未保存修改时占用服务端写锁，定时心跳续期；保存变干净、路由离开、切流时释放。
 * 本标签页把租约 token 写入 sessionStorage，刷新后可先续约再决定是否重新抢锁。
 * 只挡写图；只读打开画布不占锁。
 */
import { onActivated, onBeforeUnmount, onDeactivated, watch, type Ref } from 'vue'

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

/** 心跳间隔（毫秒）；须短于服务端写锁 TTL，避免编辑中途租约过期 */
const HEARTBEAT_MS = 20_000

/** 写锁 composable 入参 */
export interface UseFlowEditLeaseOptions {
  /** 当前编辑的测试流 id */
  testFlowId: Ref<string>
  /** 为 false 时不抢锁、不续期（例如模板只读画布） */
  enabled?: Ref<boolean>
}

/**
 * 挂载脏稿写锁：监听 dirty / 流 id / 开关，管理抢锁、心跳与释放。
 */
export function useFlowEditLease(options: UseFlowEditLeaseOptions) {
  const store = useFlowCanvasStore()
  /** 心跳定时器；有值表示本页已在续期中 */
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  /** 为 true 时跳过新的抢锁请求，避免并发重复占用 */
  let acquiring = false
  /** 上一次处理过的测试流 id；变化时先释放旧流锁 */
  let watchedFlowId = ''
  /**
   * 浏览器刷新或关闭过程中为 true。
   * 此时卸载组件不清 sessionStorage 里的 token，方便同标签重新打开后续约。
   */
  let pageUnloading = false

  /** 停止心跳定时器 */
  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  /** 启动心跳：每隔 HEARTBEAT_MS 向服务端续期一次 */
  function startHeartbeat() {
    clearHeartbeat()
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
   * 失败则清除本地 token 与定时器；若画布仍脏则再次尝试占锁。
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
      if (store.dirty) {
        void ensureLease()
      }
    }
  }

  // dirty / 流 id / 开关变化：切流先释旧锁；不可编辑或不脏则释放；脏则占锁
  watch(
    () => [store.dirty, options.testFlowId.value, options.enabled?.value ?? true] as const,
    ([dirty, flowId, enabled]) => {
      const nextId = String(flowId || '').trim()
      if (watchedFlowId && watchedFlowId !== nextId) {
        void releaseFor(watchedFlowId)
      }
      watchedFlowId = nextId

      if (!enabled || !nextId || !dirty) {
        void releaseCurrent()
        return
      }
      void ensureLease()
    },
    { immediate: true },
  )

  // 路由缓存停用：离开画布时释放写锁
  onDeactivated(() => {
    void releaseCurrent()
  })

  // 路由缓存重新激活：若仍有未保存修改则重新占锁或续约
  onActivated(() => {
    if (store.dirty && (options.enabled?.value ?? true) && options.testFlowId.value) {
      void ensureLease()
    }
  })

  /** 标记即将刷新或关闭页面 */
  function onPageHide() {
    pageUnloading = true
  }

  window.addEventListener('pagehide', onPageHide)

  onBeforeUnmount(() => {
    window.removeEventListener('pagehide', onPageHide)
    if (pageUnloading) {
      // 刷新/关页：只停心跳，保留 sessionStorage token 供下次续约
      clearHeartbeat()
      return
    }
    // 路由内卸载：完整释放服务端写锁
    void releaseCurrent()
  })
}
