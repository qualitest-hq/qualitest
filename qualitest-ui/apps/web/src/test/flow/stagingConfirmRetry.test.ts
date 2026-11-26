/**
 * 测 stagingConfirmRetry：confirm 自动重试策略。
 * 边界：mock requestConfirmOnce；fake timers。
 * 单跑：pnpm test stagingConfirmRetry --run
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  CONFIRM_UNIT_AUTO_RETRY_MAX,
  requestConfirmWithAutoRetry,
} from '@/views/project/testFlow/utils/stagingConfirmRetry';

const requestConfirmOnceMock = vi.fn();

vi.mock('@/views/project/testFlow/composables/stagingConfirmRequest', () => ({
  requestConfirmOnce: (...args: unknown[]) => requestConfirmOnceMock(...args),
}));

describe('stagingConfirmRetry', () => {
  beforeEach(() => {
    requestConfirmOnceMock.mockReset();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('有响应立即返回，不继续重试', async () => {
    requestConfirmOnceMock.mockResolvedValue({
      result: {
        graphJson: { nodes: [], edges: [], meta: {} },
      },
      requestBaseHash: 'hash-1',
    });

    const out = await requestConfirmWithAutoRetry(
      'addNode:9001',
      { addNodes: [] },
      '10',
      {} as never,
      {} as never,
    );

    expect(out.attempts).toBe(1);
    expect(requestConfirmOnceMock).toHaveBeenCalledTimes(1);
  });

  it('无 graphJson 的响应也不重试，立即返回', async () => {
    requestConfirmOnceMock.mockResolvedValue({
      result: {
        graphJson: null,
        error: '确认失败',
      },
      requestBaseHash: 'hash-1',
    });

    const out = await requestConfirmWithAutoRetry(
      'addNode:9001',
      { addNodes: [] },
      '10',
      {} as never,
      {} as never,
    );

    expect(out.attempts).toBe(1);
    expect(requestConfirmOnceMock).toHaveBeenCalledTimes(1);
    expect(out.result.graphJson).toBeNull();
  });

  it('网络抛错时自动重试至上限', async () => {
    requestConfirmOnceMock.mockRejectedValue(new Error('network'));

    const promise = requestConfirmWithAutoRetry(
      'addNode:9001',
      { addNodes: [] },
      '10',
      {} as never,
      {} as never,
    );
    const expectation = expect(promise).rejects.toThrow('network');

    await vi.runAllTimersAsync();
    await expectation;
    expect(requestConfirmOnceMock).toHaveBeenCalledTimes(CONFIRM_UNIT_AUTO_RETRY_MAX);
  });
});
