/**
 * Staging 单元 confirm 自动重试。
 * 服务端已返回结果（成功或校验失败）立即结束；仅网络/超时等请求抛错时按次数重试。
 */
import type { useAiStagingStore } from '../stores/aiStagingStore';
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
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
 * - 有响应（成功或校验失败）立即返回
 * - 仅请求抛错（网络/超时）时按上限重试
 */
export async function requestConfirmWithAutoRetry(
  unitId: string,
  patch: FlowDesignPatch,
  projectId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
): Promise<ConfirmRequestWithRetryResult> {
  let lastError: unknown;

  for (let attempt = 1; attempt <= CONFIRM_UNIT_AUTO_RETRY_MAX; attempt += 1) {
    try {
      const { result, requestBaseHash } = await requestConfirmOnce(
        unitId,
        patch,
        projectId,
        stagingStore,
        canvasStore,
      );
      return { result, requestBaseHash, attempts: attempt };
    } catch (error) {
      lastError = error;
      if (attempt >= CONFIRM_UNIT_AUTO_RETRY_MAX) {
        throw error;
      }
      await delayMs(confirmRetryDelayMs(attempt));
    }
  }

  throw lastError instanceof Error ? lastError : new Error('确认请求失败');
}
