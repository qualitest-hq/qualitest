/**
 * 套件串行跑流前端会话。
 * 只负责：调用 start 建套件运行 → 轮询详情刷新进度 → cancel 时调 abort。
 * 各项的 trigger / 等待终态由后端后台导演完成，关浏览器页不影响串跑。
 */
import { reactive, ref } from 'vue'

import {
  abortTestSuiteRun,
  getTestSuiteRun,
  startTestSuiteRun,
} from '@/api/project/testSuiteRun'

import { LIVE_RUN_POLL_MS } from '../constants/flowConfig'
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

export interface BatchRunProgressItem {
  key: string
  testFlowId: string
  flowName: string
  entryNodeId?: string
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
      key: String(it.testSuiteRunItemId
        ?? `${it.testFlowId}::${it.entryNodeId ?? ''}::${it.runScenarioId ?? ''}`),
      testFlowId: String(it.testFlowId ?? ''),
      flowName: String(it.flowNameSnapshot ?? it.testFlowId ?? ''),
      entryNodeId: it.entryNodeId ? String(it.entryNodeId) : undefined,
      runScenarioId: it.runScenarioId ? String(it.runScenarioId) : undefined,
      scenarioName: String(it.scenarioNameSnapshot ?? '默认场景'),
      status: mapItemStatus(it.status),
      runId: it.testFlowRunId != null ? String(it.testFlowRunId) : undefined,
      errorMessage: it.errorMessage || undefined,
      durationMs: it.durationMs != null ? Number(it.durationMs) : undefined,
      suiteRunItemId: it.testSuiteRunItemId != null ? String(it.testSuiteRunItemId) : undefined,
    }))
  }

  /**
   * 启动套件跑：POST start（服务端立刻后台串跑）后轮询 GET 详情直到头离开 running。
   * 用户 cancel 时请求 abort，再拉一次详情刷新进度。
   */
  async function start(options: FlowBatchRunOptions): Promise<void> {
    const testProjectId = String(options.testProjectId ?? '').trim()
    const testSuiteId = String(options.testSuiteId ?? '').trim()
    if (!testProjectId || !testSuiteId || running.value) return

    abortRequested = false
    running.value = true
    progress.items = []
    suiteRunId.value = ''

    const stopOnNonPass = options.stopOnNonPass !== false
    const envId = String(options.testProjectEnvId ?? '').trim() || undefined
    const overallTimeoutMs = Math.min(
      Math.max((options.perRunTimeoutMs ?? 600_000) * 20, 60_000),
      3_600_000,
    )

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
      if (!suiteRunId.value) {
        return
      }

      const deadline = Date.now() + overallTimeoutMs
      while (Date.now() <= deadline) {
        if (abortRequested) {
          try {
            await abortTestSuiteRun(suiteRunId.value)
          } catch {
            // 忽略中止回写失败
          }
          const afterAbort = await getTestSuiteRun(suiteRunId.value)
          loadFromSuiteRunDetail(afterAbort?.data ?? afterAbort)
          break
        }
        try {
          const res = await getTestSuiteRun(suiteRunId.value)
          const latest = res?.data ?? res
          loadFromSuiteRunDetail(latest)
          const headerStatus = String(latest?.status ?? '').trim()
          if (headerStatus && headerStatus !== 'running') {
            break
          }
        } catch {
          // 单次轮询失败继续
        }
        await abortableSleep(LIVE_RUN_POLL_MS, () => abortRequested)
      }
    } finally {
      running.value = false
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

function mapItemStatus(status: string | undefined): BatchItemRunStatus {
  const s = String(status ?? 'pending').trim()
  if (s === 'error') return 'failed'
  if (
    s === 'pending'
    || s === 'running'
    || s === 'passed'
    || s === 'failed'
    || s === 'paused'
    || s === 'aborted'
    || s === 'cancelled'
    || s === 'skipped'
  ) {
    return s
  }
  return 'failed'
}
