/**
 * 套件串行跑流：建 suite_run → 逐条 trigger → bind/finish 回写。
 */
import { reactive, ref } from 'vue'

import {
  getTestFlowRun,
  triggerTestFlowRun,
  type ApiTestFlowRunDetail,
} from '@/api/project/testFlowRun'
import {
  abortTestSuiteRun,
  bindTestSuiteRunItem,
  finishTestSuiteRunItem,
  startTestSuiteRun,
} from '@/api/project/testSuiteRun'

import { LIVE_RUN_POLL_MS } from '../constants/flowConfig'
import { TERMINAL_RUN_STATUSES, type RunRecordStatus } from '../constants/runStatus'
import { abortableSleep } from '../utils/abortableSleep'

export type BatchItemRunStatus =
  | 'pending'
  | 'running'
  | 'passed'
  | 'failed'
  | 'paused'
  | 'aborted'
  | 'cancelled'
  | 'skipped'
  | 'error'

export interface BatchRunProgressItem {
  key: string
  testFlowId: string
  flowName: string
  runScenarioId?: string
  scenarioName: string
  status: BatchItemRunStatus
  runId?: string
  errorMessage?: string
  durationMs?: number
  suiteRunItemId?: string
}

export interface FlowBatchRunOptions {
  testProjectId: string
  testSuiteId: string
  suiteNameSnapshot?: string
  testProjectEnvId?: string
  stopOnNonPass?: boolean
  perRunTimeoutMs?: number
}

function mapTerminalStatus(status: string | undefined): BatchItemRunStatus {
  if (status && TERMINAL_RUN_STATUSES.has(status as RunRecordStatus)) {
    return status as BatchItemRunStatus
  }
  return 'failed'
}

/**
 * 创建套件跑会话状态与 start/cancel。
 */
export function useFlowBatchRun() {
  const running = ref(false)
  const progress = reactive({ items: [] as BatchRunProgressItem[] })
  const suiteRunId = ref<string>('')
  let abortRequested = false

  function cancel() {
    abortRequested = true
  }

  function reset() {
    abortRequested = false
    running.value = false
    progress.items = []
    suiteRunId.value = ''
  }

  /**
   * 用已有 suite_run 详情填充进度（启动回写 / 历史回看）。
   */
  function loadFromSuiteRunDetail(detail: any) {
    suiteRunId.value = String(detail?.testSuiteRunId ?? '').trim()
    const items = Array.isArray(detail?.items) ? detail.items : []
    progress.items = items.map((it: any) => ({
      key: String(it.testSuiteRunItemId ?? `${it.testFlowId}::${it.runScenarioId ?? ''}`),
      testFlowId: String(it.testFlowId ?? ''),
      flowName: String(it.flowNameSnapshot ?? it.testFlowId ?? ''),
      runScenarioId: it.runScenarioId ? String(it.runScenarioId) : undefined,
      scenarioName: String(it.scenarioNameSnapshot ?? '默认场景'),
      status: (it.status || 'pending') as BatchItemRunStatus,
      runId: it.testFlowRunId != null ? String(it.testFlowRunId) : undefined,
      errorMessage: it.errorMessage || undefined,
      durationMs: it.durationMs != null ? Number(it.durationMs) : undefined,
      suiteRunItemId: it.testSuiteRunItemId != null ? String(it.testSuiteRunItemId) : undefined,
    }))
  }

  async function start(options: FlowBatchRunOptions): Promise<void> {
    const testProjectId = String(options.testProjectId ?? '').trim()
    const testSuiteId = String(options.testSuiteId ?? '').trim()
    if (!testProjectId || !testSuiteId || running.value) return

    abortRequested = false
    running.value = true
    progress.items = []
    suiteRunId.value = ''

    const stopOnNonPass = options.stopOnNonPass !== false
    const perRunTimeoutMs = Math.min(Math.max(options.perRunTimeoutMs ?? 600_000, 1), 1_800_000)
    const envId = String(options.testProjectEnvId ?? '').trim() || undefined

    try {
      const startRes = await startTestSuiteRun({
        testProjectId,
        testSuiteId,
        suiteNameSnapshot: options.suiteNameSnapshot,
        testProjectEnvId: envId,
        stopOnNonPass,
      })
      const detail = startRes?.data ?? startRes
      loadFromSuiteRunDetail(detail)
      if (!progress.items.length) {
        return
      }

      let stoppedEarly = false
      for (const row of progress.items) {
        if (stoppedEarly || abortRequested) {
          row.status = 'skipped'
          row.errorMessage = abortRequested ? '已中止队列' : '队列已提前停止'
          continue
        }

        row.status = 'running'
        row.errorMessage = undefined
        row.runId = undefined
        row.durationMs = undefined

        try {
          const triggerRes = await triggerTestFlowRun({
            testFlowId: row.testFlowId,
            runScenarioId: row.runScenarioId,
            testProjectEnvId: envId,
            triggerType: 'suite',
          })
          if (abortRequested) {
            row.status = 'skipped'
            row.errorMessage = '已中止队列'
            stoppedEarly = true
            continue
          }
          const runId = String(triggerRes?.data ?? '').trim()
          if (!runId || runId === 'null' || runId === 'undefined') {
            row.status = 'error'
            row.errorMessage = '触发运行失败：未返回 runId'
            await persistFinish(row)
            if (stopOnNonPass) stoppedEarly = true
            continue
          }
          row.runId = runId
          await persistBind(row, runId)

          const terminal = await pollUntilTerminal(runId, perRunTimeoutMs, () => abortRequested)
          if (
            abortRequested &&
            (!terminal?.status || !TERMINAL_RUN_STATUSES.has(terminal.status as RunRecordStatus))
          ) {
            row.status = 'skipped'
            row.errorMessage = '已中止队列（当前 Run 仍在后台执行）'
            await persistFinish(row)
            stoppedEarly = true
            continue
          }
          if (!terminal) {
            row.status = 'error'
            row.errorMessage = '无法获取运行状态'
            await persistFinish(row)
            if (stopOnNonPass) stoppedEarly = true
            continue
          }
          row.status = mapTerminalStatus(terminal.status)
          row.durationMs =
            terminal.durationMs != null && terminal.durationMs !== ''
              ? Number(terminal.durationMs)
              : undefined
          row.errorMessage = terminal.errorMessage || undefined
          await persistFinish(row)
          if (terminal.status !== 'passed' && stopOnNonPass) {
            stoppedEarly = true
          }
        } catch (e) {
          row.status = 'error'
          row.errorMessage = e instanceof Error ? e.message : '触发失败'
          await persistFinish(row)
          if (stopOnNonPass) stoppedEarly = true
        }
      }

      if (suiteRunId.value && abortRequested) {
        try {
          await abortTestSuiteRun(suiteRunId.value)
        } catch {
          // 忽略中止回写失败
        }
      } else if (suiteRunId.value && stoppedEarly) {
        for (const row of progress.items) {
          if (row.status === 'skipped') {
            await persistFinish(row)
          }
        }
      }
    } finally {
      running.value = false
    }
  }

  async function persistBind(row: BatchRunProgressItem, runId: string) {
    if (!suiteRunId.value || !row.suiteRunItemId) return
    try {
      await bindTestSuiteRunItem(suiteRunId.value, row.suiteRunItemId, runId)
    } catch {
      // 回写失败不阻断本地进度
    }
  }

  async function persistFinish(row: BatchRunProgressItem) {
    if (!suiteRunId.value || !row.suiteRunItemId) return
    try {
      await finishTestSuiteRunItem(suiteRunId.value, row.suiteRunItemId, {
        status: row.status,
        errorMessage: row.errorMessage,
        durationMs: row.durationMs,
      })
    } catch {
      // 回写失败不阻断本地进度
    }
  }

  return {
    running,
    progress,
    suiteRunId,
    start,
    cancel,
    reset,
    loadFromSuiteRunDetail,
  }
}

type TerminalSnapshot = {
  status?: string
  errorMessage?: string
  durationMs?: string | number
}

async function pollUntilTerminal(
  runId: string,
  timeoutMs: number,
  shouldAbort: () => boolean,
): Promise<TerminalSnapshot | null> {
  const deadline = Date.now() + timeoutMs
  let latest: TerminalSnapshot | null = null
  while (Date.now() <= deadline) {
    if (shouldAbort()) return latest
    try {
      const res = await getTestFlowRun(runId)
      const data = (res?.data ?? res) as ApiTestFlowRunDetail
      const run = data?.run
      if (run) {
        latest = {
          status: run.status,
          errorMessage: run.errorMessage,
          durationMs: run.durationMs,
        }
        if (run.status && TERMINAL_RUN_STATUSES.has(run.status as RunRecordStatus)) {
          return latest
        }
      }
    } catch {
      // 单次轮询失败继续
    }
    await abortableSleep(LIVE_RUN_POLL_MS, shouldAbort)
  }
  return latest
}
