/**
 * 画布跟随服务端活跃 Run。
 * 人手触发、SSE 唤醒、订阅就绪与回前台都走这里；不按触发方决定是否接管。
 * 本页人手点运行的 pending-* 握手期间不抢。
 */
import { ElMessage } from 'element-plus'

import { LIVE_RUN_POLL_MS } from '../constants/flowConfig'
import { TERMINAL_RUN_STATUSES } from '../constants/runStatus'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import type { RunRecord } from '../stores/runLibraryStore'
import { useRunLibraryStore } from '../stores/runLibraryStore'
import { abortableSleep } from '../utils/abortableSleep'

/**
 * 按 Run 步骤时间线刷新画布高亮。
 * 将 0..stepIndex 的节点标为已访问（失败则失败色），当前步节点标为运行高亮。
 */
export function highlightRunStep(
  store: ReturnType<typeof useFlowCanvasStore>,
  record: RunRecord,
  stepIndex: number,
) {
  store.clearRunHighlight()
  for (let i = 0; i <= stepIndex; i++) {
    const s = record.steps[i]
    if (!s?.nodeId) continue
    store.runVisitedNodeIds[s.nodeId] = s.status === 'failed' ? 'failed' : 'passed'
  }
  const step = record.steps[stepIndex]
  if (step?.nodeId) store.runHighlightNodeId = step.nodeId
}

const inflight = new Map<string, Promise<RunRecord | null>>()

function isPendingHandshake(recordId: string | undefined): boolean {
  return !!recordId && recordId.startsWith('pending-')
}

/** 取最后一个带画布 nodeId 的步骤下标 */
function lastGraphStepIndex(record: RunRecord): number {
  const steps = record.steps ?? []
  for (let i = steps.length - 1; i >= 0; i--) {
    if (steps[i]?.nodeId) return i
  }
  return steps.length > 0 ? steps.length - 1 : -1
}

/**
 * 轮询 Run 详情并高亮。调用前须已确认可以接管当前 live 会话。
 */
async function pollRun(runId: string): Promise<RunRecord | null> {
  const store = useFlowCanvasStore()
  const runLib = useRunLibraryStore()

  runLib.scenarioRunLive = { abort: false, recordId: runId, phase: 'running' }
  store.showRunPanel()
  store.ui.leftTab = 'runs'
  void runLib.loadRuns(store.testFlowId || runLib.currentTestFlowId).catch(() => undefined)

  let lastDetail: RunRecord | null = null
  try {
    while (runLib.scenarioRunLive?.recordId === runId && !runLib.scenarioRunLive.abort) {
      const detail = await runLib.fetchRunDetail(runId)
      if (runLib.scenarioRunLive?.recordId !== runId || runLib.scenarioRunLive.abort) break
      if (!detail) break
      lastDetail = detail
      runLib.upsertRun(detail)
      runLib.selectedRunId = runId

      const stepIdx = lastGraphStepIndex(detail)
      if (stepIdx >= 0 && runLib.scenarioRunLive?.recordId === runId) {
        const aborted = !!runLib.scenarioRunLive.abort
        runLib.scenarioRunLive = {
          abort: aborted,
          recordId: runId,
          phase: 'running',
          stepIndex: stepIdx,
          stepTotal: detail.steps.length,
        }
        runLib.inspectorStepIndex = stepIdx
        highlightRunStep(store, detail, stepIdx)
      }

      if (TERMINAL_RUN_STATUSES.has(detail.status)) {
        if (!runLib.scenarioRunLive?.abort && runLib.scenarioRunLive?.recordId === runId) {
          if (detail.status === 'failed') {
            ElMessage.error(detail.errorMessage ?? '运行失败')
          } else if (detail.status === 'paused') {
            ElMessage.warning(detail.errorMessage ?? '运行已暂停')
          } else if (detail.status === 'passed') {
            ElMessage.success('运行完成')
          }
        }
        break
      }

      await abortableSleep(LIVE_RUN_POLL_MS, () => {
        const live = runLib.scenarioRunLive
        return !live || live.recordId !== runId || !!live.abort
      })
    }
    return lastDetail
  } finally {
    const live = runLib.scenarioRunLive
    if (live?.recordId === runId) {
      if (live.abort) store.clearRunHighlight()
      runLib.scenarioRunLive = null
    }
  }
}

/**
 * 跟随指定 Run：切运行库并边跑边亮。
 * 已在跟同一 runId 时复用进行中的轮询。
 * 人手点运行的 pending-* 握手中，外部调用直接返回；完成本地握手须传 replacePending。
 */
export function followRun(
  runId: string,
  options?: { replacePending?: boolean },
): Promise<RunRecord | null> {
  const id = String(runId || '').trim()
  if (!id) return Promise.resolve(null)

  const pending = inflight.get(id)
  if (pending) return pending

  const runLib = useRunLibraryStore()
  const live = runLib.scenarioRunLive
  if (isPendingHandshake(live?.recordId) && !options?.replacePending) {
    return Promise.resolve(null)
  }

  if (live && live.recordId && live.recordId !== id) {
    live.abort = true
  }

  const task = pollRun(id).finally(() => {
    inflight.delete(id)
  })
  inflight.set(id, task)
  return task
}

/**
 * 以服务端该流的 running Run 对齐画布。
 * 没有进行中的 Run 时不跟历史记录。
 */
export async function reconcileActiveRun(testFlowId: string): Promise<void> {
  const flowId = String(testFlowId || '').trim()
  if (!flowId) return

  const runLib = useRunLibraryStore()
  if (isPendingHandshake(runLib.scenarioRunLive?.recordId)) return

  await runLib.loadRuns(flowId)
  const active = runLib.runs.find((r) => r.status === 'running')
  if (!active) return
  if (runLib.scenarioRunLive?.recordId === active.id) return
  await followRun(active.id)
}
