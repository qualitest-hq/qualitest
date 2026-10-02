/**
 * 测谁：写锁需求的更新、下一步动作的判断、抢锁返回后是否留锁。
 * 边界：切流和放弃持锁时序号加一；持有旧流的锁先释放；残留凭证要清掉；请求期间需求变化不留锁。
 * 单跑：pnpm test flowEditLeaseState（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import {
  advanceFlowEditLeaseIntent,
  emptyFlowEditLeaseIntent,
  nextFlowEditLeaseStep,
  shouldKeepLeaseAfterAcquire,
} from '../../views/project/testFlow/utils/flowEditLeaseState'

describe('advanceFlowEditLeaseIntent', () => {
  // 前提：从初始状态开始要锁，流 id 由空变为 f1，视为切流。
  // 期望：写入流 id 和要锁标记，序号加一。
  it('空意图开始要锁 → epoch+1', () => {
    const next = advanceFlowEditLeaseIntent(emptyFlowEditLeaseIntent(), {
      flowId: 'f1',
      wantLongLease: true,
    })
    expect(next).toEqual({ flowId: 'f1', wantLongLease: true, epoch: 1 })
  })

  // 前提：已在要锁，画布状态抖动后仍要同一条流的锁。
  // 期望：序号不变。
  it('同流持续要锁 → epoch 不变', () => {
    const prev = { flowId: 'f1', wantLongLease: true, epoch: 4 }
    const next = advanceFlowEditLeaseIntent(prev, { flowId: 'f1', wantLongLease: true })
    expect(next).toEqual({ flowId: 'f1', wantLongLease: true, epoch: 4 })
  })

  // 前提：原本要锁，现在不要了。
  // 期望：序号加一，进行中的抢锁返回后不再留锁。
  it('丢掉要锁 → epoch+1', () => {
    const prev = { flowId: 'f1', wantLongLease: true, epoch: 2 }
    const next = advanceFlowEditLeaseIntent(prev, { flowId: 'f1', wantLongLease: false })
    expect(next).toEqual({ flowId: 'f1', wantLongLease: false, epoch: 3 })
  })

  // 前提：切到另一条流，仍要锁。
  // 期望：序号加一。
  it('切流 → epoch+1', () => {
    const prev = { flowId: 'f1', wantLongLease: true, epoch: 1 }
    const next = advanceFlowEditLeaseIntent(prev, { flowId: 'f2', wantLongLease: true })
    expect(next).toEqual({ flowId: 'f2', wantLongLease: true, epoch: 2 })
  })

  // 前提：流 id 只有空白，却声明要锁。
  // 期望：流 id 变为空串，要锁标记为 false。
  it('空 flowId 不能要锁', () => {
    const next = advanceFlowEditLeaseIntent(emptyFlowEditLeaseIntent(), {
      flowId: '  ',
      wantLongLease: true,
    })
    expect(next.wantLongLease).toBe(false)
    expect(next.flowId).toBe('')
  })
})

describe('nextFlowEditLeaseStep', () => {
  // 前提：要锁，本页未持锁。
  // 期望：抢锁，并带上当前序号。
  it('要锁未持有 → acquire', () => {
    expect(
      nextFlowEditLeaseStep({
        intent: { flowId: 'f1', wantLongLease: true, epoch: 4 },
        holding: false,
        holdingFlowId: '',
        hasTokenForIntentFlow: false,
      }),
    ).toEqual({ type: 'acquire', flowId: 'f1', epoch: 4 })
  })

  // 前提：要锁，本页已持锁且本地凭证还在。
  // 期望：保持现状。
  it('已持有 → idle', () => {
    expect(
      nextFlowEditLeaseStep({
        intent: { flowId: 'f1', wantLongLease: true, epoch: 1 },
        holding: true,
        holdingFlowId: 'f1',
        hasTokenForIntentFlow: true,
      }),
    ).toEqual({ type: 'idle' })
  })

  // 前提：不要锁，本页仍持锁。
  // 期望：释放该流的锁。
  it('不要锁仍持有 → release', () => {
    expect(
      nextFlowEditLeaseStep({
        intent: { flowId: 'f1', wantLongLease: false, epoch: 2 },
        holding: true,
        holdingFlowId: 'f1',
        hasTokenForIntentFlow: true,
      }),
    ).toEqual({ type: 'release', flowId: 'f1' })
  })

  // 前提：要锁的流已切到 f2，本页还持着 f1 的锁。
  // 期望：先释放 f1。
  it('持旧流 → 先 release 旧流', () => {
    expect(
      nextFlowEditLeaseStep({
        intent: { flowId: 'f2', wantLongLease: true, epoch: 3 },
        holding: true,
        holdingFlowId: 'f1',
        hasTokenForIntentFlow: false,
      }),
    ).toEqual({ type: 'release', flowId: 'f1' })
  })

  // 前提：不要锁，本页未持锁，但本地还留着该流的凭证。
  // 期望：释放该流的锁，清掉残留凭证。
  it('不要锁但有残留 token → release', () => {
    expect(
      nextFlowEditLeaseStep({
        intent: { flowId: 'f1', wantLongLease: false, epoch: 5 },
        holding: false,
        holdingFlowId: '',
        hasTokenForIntentFlow: true,
      }),
    ).toEqual({ type: 'release', flowId: 'f1' })
  })
})

describe('shouldKeepLeaseAfterAcquire', () => {
  // 前提：抢锁请求期间序号没变，画布仍需要持锁。
  // 期望：留下这把锁并开始续期。
  it('epoch 未变且仍需长锁 → true', () => {
    expect(
      shouldKeepLeaseAfterAcquire({
        epochAtStart: 3,
        currentEpoch: 3,
        needsLongLease: true,
      }),
    ).toBe(true)
  })

  // 前提：抢锁请求期间画布已保存或放弃持锁，序号已加一。
  // 期望：不留锁，避免一直占着写锁。
  it('epoch 已变 → false', () => {
    expect(
      shouldKeepLeaseAfterAcquire({
        epochAtStart: 2,
        currentEpoch: 3,
        needsLongLease: true,
      }),
    ).toBe(false)
  })

  // 前提：序号没变，但请求返回时画布已无改动或只剩排版改动。
  // 期望：不留锁。
  it('不再需要长锁 → false', () => {
    expect(
      shouldKeepLeaseAfterAcquire({
        epochAtStart: 1,
        currentEpoch: 1,
        needsLongLease: false,
      }),
    ).toBe(false)
  })
})
