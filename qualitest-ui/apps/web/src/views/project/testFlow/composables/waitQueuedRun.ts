import { parseTriggerOutcome, type TriggerTestFlowRunOutcome } from '@/api/project/testFlowRun'
import { peekQueuedRunId } from '@/api/project/testProject'

/**
 * 轮询排队项直至绑定到正式 runId。
 * 超时、调用方取消、或接口一直空，则返回 null。
 */
export async function waitQueuedRunId(
  testProjectId: string | number,
  queueItemId: string,
  options?: { timeoutMs?: number; intervalMs?: number; shouldAbort?: () => boolean },
): Promise<string | null> {
  const timeoutMs = options?.timeoutMs ?? 600_000
  const intervalMs = options?.intervalMs ?? 800
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (options?.shouldAbort?.()) {
      return null
    }
    try {
      const res = await peekQueuedRunId(testProjectId, queueItemId)
      const runId = String(res?.data ?? '').trim()
      if (runId && runId !== 'null' && runId !== 'undefined') {
        return runId
      }
    } catch {
      // 单次失败继续轮询，避免瞬时网络抖动打断等待
    }
    await new Promise((r) => setTimeout(r, intervalMs))
  }
  return null
}

/** 从 trigger 回执解析 / 等待 runId 时的选项 */
export type ResolveRunIdAfterTriggerOptions = {
  /** 排队等待时需要的项目 id */
  testProjectId?: string | number | null
  /** 返回 true 时中止等待 */
  shouldAbort?: () => boolean
  /** 发现已入队时回调（可用来 toast 提示） */
  onQueued?: (outcome: TriggerTestFlowRunOutcome) => void
}

/**
 * 统一处理 trigger 回执：
 * started 直接取 runId；
 * queued 则按 queueItemId 轮询直到开跑。
 * 场景单跑与套件批量跑共用，避免两处各写一套排队逻辑。
 */
export async function resolveRunIdAfterTrigger(
  triggerData: unknown,
  options?: ResolveRunIdAfterTriggerOptions,
): Promise<{ runId: string; outcome: TriggerTestFlowRunOutcome }> {
  const outcome = parseTriggerOutcome(triggerData)
  let runId = String(outcome.runId ?? '').trim()
  if ((!runId || runId === 'null') && outcome.state === 'queued' && outcome.queueItemId) {
    options?.onQueued?.(outcome)
    const projectId = options?.testProjectId
    if (projectId == null || projectId === '') {
      return { runId: '', outcome }
    }
    runId =
      (await waitQueuedRunId(projectId, outcome.queueItemId, {
        shouldAbort: options?.shouldAbort,
      })) || ''
  }
  if (!runId || runId === 'null' || runId === 'undefined') {
    return { runId: '', outcome }
  }
  return { runId, outcome }
}
