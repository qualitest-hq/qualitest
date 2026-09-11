/**
 * 重开造流 AI 助手侧栏时的 Staging 回灌。
 *
 * 画布重载会清空 Staging 单元表，但 AI 会话消息仍留在内存。
 * 若消息里还有未合并的造流 patch（多次 submit_* 累积结果），打开侧栏后把 patch 再灌回 Staging，
 * 让用户继续在画布上确认或拒绝变更。
 * 纯答疑（explainOnly）消息不参与回灌。
 */

import {
  resolveActivePatchMessageId,
  type AiPatchMessageView,
} from '@/utils/ai/aiPatchMessageSelectors';

/** 回灌所需的当前 Staging 计数、消息列表与加载/灌入回调 */
export type RestorePendingStagingDeps = {
  /** 当前待确认 Staging 单元数量；大于 0 表示画布上已有待确认项，无需再灌 */
  pendingCount: number;
  /** 当前 AI 会话消息（含助手消息上的 patch / patchPending） */
  messages: readonly AiPatchMessageView[];
  /** 若助手消息仅有 patchPending，先拉取完整 patch 再灌入 */
  eagerLoadActivePatch: (messages: readonly AiPatchMessageView[]) => Promise<void>;
  /** 清空后按当前消息列表重建 Staging（含已确认/已拒绝状态恢复） */
  onSessionLoaded: () => Promise<void>;
};

/**
 * 在需要时把内存会话中的造流 patch 重新灌入 Staging。
 *
 * 跳过条件：
 * - 已有待确认单元（pendingCount > 0）
 * - 消息中找不到未合并、非 explainOnly、带 patch 或 patchPending 的助手消息
 *
 * @returns true 表示已执行懒加载与重灌；false 表示未执行
 */
export async function restorePendingStagingIfNeeded(
  deps: RestorePendingStagingDeps,
): Promise<boolean> {
  if (deps.pendingCount > 0) return false;
  // 从后往前找最后一条可确认的造流 patch；已合并或仅说明的消息不算
  if (!resolveActivePatchMessageId([...deps.messages])) return false;
  await deps.eagerLoadActivePatch(deps.messages);
  await deps.onSessionLoaded();
  return true;
}
