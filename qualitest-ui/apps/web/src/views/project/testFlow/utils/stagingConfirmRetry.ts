/**
 * Staging 单元 confirm 自动重试：网络/超时与本单元校验失败时重试，依赖类错误不重试。
 */
import type { useAiStagingStore } from '../stores/aiStagingStore';
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { classifyStagingConfirmErrors } from './stagingConfirmErrorHints';
import { requestConfirmOnce, type ConfirmRequestResult } from '../composables/stagingConfirmRequest';

/** 单次用户确认操作内的最大自动重试次数（含首次请求） */
export const CONFIRM_UNIT_AUTO_RETRY_MAX = 3;

/** 自动重试基础间隔（逐次递增） */
export const CONFIRM_UNIT_AUTO_RETRY_DELAY_MS = 400;

export interface ConfirmRequestWithRetryResult {
  result: ConfirmRequestResult;
  requestBaseHash: string;
  attempts: number;
}

export function isAutoRetriableConfirmFailure(result: ConfirmRequestResult): boolean {
  if (result.validation.ok) return false;
  const classified = classifyStagingConfirmErrors(
    result.dependencyHints,
    result.validation.errors,
  );
  return !classified.some((line) => line.source === 'dependency');
}

export function confirmRetryDelayMs(attempt: number): number {
  return CONFIRM_UNIT_AUTO_RETRY_DELAY_MS * attempt;
}

export async function delayMs(ms: number): Promise<void> {
  await new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

/**
 * 带自动重试的 confirm 请求。
 * - 校验通过立即返回（不继续重试）
 * - 依赖未满足、网络/超时、本单元或画布校验失败时按策略重试
 */
export async function requestConfirmWithAutoRetry(
  unitId: string,
  patch: FlowDesignPatch,
  projectId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
): Promise<ConfirmRequestWithRetryResult> {
  let last: ConfirmRequestWithRetryResult | null = null;

  for (let attempt = 1; attempt <= CONFIRM_UNIT_AUTO_RETRY_MAX; attempt += 1) {
    try {
      const { result, requestBaseHash } = await requestConfirmOnce(
        unitId,
        patch,
        projectId,
        stagingStore,
        canvasStore,
      );
      last = { result, requestBaseHash, attempts: attempt };

      if (result.validation.ok && result.graphJson) {
        return last;
      }
      if (!isAutoRetriableConfirmFailure(result) || attempt >= CONFIRM_UNIT_AUTO_RETRY_MAX) {
        return last;
      }
    } catch (error) {
      if (attempt >= CONFIRM_UNIT_AUTO_RETRY_MAX) {
        throw error;
      }
      await delayMs(confirmRetryDelayMs(attempt));
      continue;
    }

    await delayMs(confirmRetryDelayMs(attempt));
  }

  if (!last) {
    throw new Error('确认请求失败');
  }
  return last;
}
