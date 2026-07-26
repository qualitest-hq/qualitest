/**
 * Staging confirm 自动重试策略。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ConfirmRequestResult } from '@/views/project/testFlow/composables/stagingConfirmRequest';
import {
  CONFIRM_UNIT_AUTO_RETRY_MAX,
  isAutoRetriableConfirmFailure,
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

  it('isAutoRetriableConfirmFailure：依赖错误不重试', () => {
    const result: ConfirmRequestResult = {
      validation: { ok: false, errors: ['需要先确认 addNode:9001'], warnings: [] },
      graphJson: null,
      dependencyHints: ['确认 addEdge:8001 需要先确认 addNode:9001'],
    };
    expect(isAutoRetriableConfirmFailure(result)).toBe(false);
  });

  it('isAutoRetriableConfirmFailure：本单元校验失败可重试', () => {
    const result: ConfirmRequestResult = {
      validation: { ok: false, errors: ['HTTP 节点缺少 externalUrl'], warnings: [] },
      graphJson: null,
    };
    expect(isAutoRetriableConfirmFailure(result)).toBe(true);
  });

  it('校验通过立即返回，不继续重试', async () => {
    requestConfirmOnceMock.mockResolvedValue({
      result: {
        validation: { ok: true, errors: [], warnings: [] },
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

  it('本单元校验失败时自动重试至上限', async () => {
    requestConfirmOnceMock.mockResolvedValue({
      result: {
        validation: { ok: false, errors: ['HTTP 节点缺少 externalUrl'], warnings: [] },
        graphJson: null,
      },
      requestBaseHash: 'hash-1',
    });

    const promise = requestConfirmWithAutoRetry(
      'addNode:9001',
      { addNodes: [] },
      '10',
      {} as never,
      {} as never,
    );

    await vi.runAllTimersAsync();
    const out = await promise;

    expect(out.attempts).toBe(CONFIRM_UNIT_AUTO_RETRY_MAX);
    expect(requestConfirmOnceMock).toHaveBeenCalledTimes(CONFIRM_UNIT_AUTO_RETRY_MAX);
    expect(out.result.validation.ok).toBe(false);
  });

  it('中途成功后停止重试', async () => {
    requestConfirmOnceMock
      .mockResolvedValueOnce({
        result: {
          validation: { ok: false, errors: ['临时失败'], warnings: [] },
          graphJson: null,
        },
        requestBaseHash: 'hash-1',
      })
      .mockResolvedValueOnce({
        result: {
          validation: { ok: true, errors: [], warnings: [] },
          graphJson: { nodes: [], edges: [], meta: {} },
        },
        requestBaseHash: 'hash-2',
      });

    const promise = requestConfirmWithAutoRetry(
      'addNode:9001',
      { addNodes: [] },
      '10',
      {} as never,
      {} as never,
    );

    await vi.runAllTimersAsync();
    const out = await promise;

    expect(out.attempts).toBe(2);
    expect(requestConfirmOnceMock).toHaveBeenCalledTimes(2);
    expect(out.result.validation.ok).toBe(true);
  });
});
