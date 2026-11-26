/**
 * 测 useFlowBatchRun：套件串跑逐条触发时，trigger 必须标成 suite（而不是画布 ui）。
 * 边界：mock 套件 / 运行 API 与排队解析；不启后端、不落库、不真实轮询。
 * 单跑：pnpm test useFlowBatchRun   （在 qualitest-ui 或 apps/web 下）
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/project/testSuiteRun', () => ({
  startTestSuiteRun: vi.fn(),
  bindTestSuiteRunItem: vi.fn(),
  finishTestSuiteRunItem: vi.fn(),
  abortTestSuiteRun: vi.fn(),
}))

vi.mock('@/api/project/testFlowRun', () => ({
  triggerTestFlowRun: vi.fn(),
  getTestFlowRun: vi.fn(),
}))

vi.mock('@/views/project/testFlow/composables/waitQueuedRun', () => ({
  resolveRunIdAfterTrigger: vi.fn(),
}))

import { startTestSuiteRun } from '@/api/project/testSuiteRun'
import { getTestFlowRun, triggerTestFlowRun } from '@/api/project/testFlowRun'
import { resolveRunIdAfterTrigger } from '@/views/project/testFlow/composables/waitQueuedRun'
import { useFlowBatchRun } from '@/views/project/testFlow/composables/useFlowBatchRun'

const mockedStartSuiteRun = vi.mocked(startTestSuiteRun)
const mockedTriggerRun = vi.mocked(triggerTestFlowRun)
const mockedGetRun = vi.mocked(getTestFlowRun)
const mockedResolveRunId = vi.mocked(resolveRunIdAfterTrigger)

describe('useFlowBatchRun', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('套件逐条触发带 triggerType=suite', async () => {
    // 前提：套件有一条待跑项，触发拿到 runId，轮询一次即为 passed
    // 期望：triggerTestFlowRun 收到 triggerType=suite，进度收敛为 passed
    mockedStartSuiteRun.mockResolvedValue({
      data: {
        testSuiteRunId: 'sr1',
        items: [
          {
            testSuiteRunItemId: 'item1',
            testFlowId: 'f1',
            flowNameSnapshot: '退款主链',
            entryNodeId: 'e1',
            runScenarioId: 'sc1',
            scenarioNameSnapshot: '冒烟',
            status: 'pending',
          },
        ],
      },
    } as any)
    mockedTriggerRun.mockResolvedValue({ data: { runId: 'r1', state: 'started' } } as any)
    mockedResolveRunId.mockResolvedValue({
      runId: 'r1',
      outcome: { state: 'started', runId: 'r1' } as any,
    })
    mockedGetRun.mockResolvedValue({ data: { run: { status: 'passed', durationMs: '12' } } } as any)

    const batch = useFlowBatchRun()
    await batch.start({ testProjectId: 'p1', testSuiteId: 's1' })

    expect(mockedTriggerRun).toHaveBeenCalledTimes(1)
    expect(mockedTriggerRun).toHaveBeenCalledWith(
      expect.objectContaining({
        testFlowId: 'f1',
        entryNodeId: 'e1',
        runScenarioId: 'sc1',
        triggerType: 'suite',
      }),
    )
    expect(batch.progress.items[0]?.status).toBe('passed')
  })
})
