/**
 * 测试流外部变更长连接（GET 事件流）。
 * 打开画布时订阅他端写库通知；与 AI 对话推流无关。
 */
import { getToken } from '@/utils/auth'
import { readSseJsonStream } from '@/utils/ai/consumeSseStream'

const BASE_API = import.meta.env.VITE_APP_BASE_API as string

export type FlowExternalChangeType =
  | 'subscribed'
  | 'ping'
  | 'graphCommitted'
  | 'assetVariablesChanged'
  | 'authConfigChanged'
  | 'projectEnvsChanged'
  | 'runStarted'
  | 'flowMetaChanged'
  | 'flowCreated'

/** 服务端推送的一条外部变更 */
export interface FlowExternalChangeEvent {
  type: FlowExternalChangeType | string
  testFlowId?: string
  testProjectId?: string
  source?: string
  updateTime?: string
  runId?: string
  /** 素材变更涉及的 key */
  keys?: string[]
  /** 图增量：变更节点 id */
  changedNodeIds?: string[]
  /** 图增量：节点 JSON 片段 */
  nodePatches?: unknown[]
  /** 图增量：边 JSON 片段 */
  edgePatches?: unknown[]
  deletedNodeIds?: string[]
  deletedEdgeIds?: string[]
}

export interface SubscribeFlowEventsOptions {
  testFlowId: string
  onEvent: (event: FlowExternalChangeEvent) => void
  signal?: AbortSignal
  onError?: (err: unknown) => void
}

/**
 * 带登录态订阅外部变更事件流，直到 abort 或连接结束。
 * 断线后由调用方决定是否重连。
 */
export async function subscribeFlowEvents(options: SubscribeFlowEventsOptions): Promise<void> {
  const { testFlowId, onEvent, signal, onError } = options
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`${BASE_API}/project/testFlow/${testFlowId}/events`, {
      method: 'GET',
      headers,
      signal,
    })
  } catch (e) {
    if (signal?.aborted) return
    onError?.(e)
    throw e
  }

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    const err = new Error(text || `订阅失败 (${response.status})`)
    onError?.(err)
    throw err
  }
  if (!response.body) {
    throw new Error('SSE 无正文')
  }

  try {
    await readSseJsonStream(response.body, (payload) => {
      try {
        onEvent(JSON.parse(payload) as FlowExternalChangeEvent)
      } catch {
        // 半包或非 JSON，忽略
      }
    })
  } catch (e) {
    if (signal?.aborted) return
    onError?.(e)
    throw e
  }
}
