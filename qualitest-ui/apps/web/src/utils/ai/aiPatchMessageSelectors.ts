/**
 * AI 对话中带 patch 的消息筛选工具。
 *
 * 用于判断 Diff 区域是否展示、patch 是否待懒加载、
 * 以及从消息列表定位当前活跃的未合并 patch。
 */

/** patch 相关字段的最低约束 */
export interface AiPatchMessageView {
  id: string;
  role: string;
  merged?: boolean;
  explainOnly?: boolean;
  patch?: unknown;
  /** 历史消息列表只带了摘要，完整 patch 尚未拉取 */
  patchPending?: boolean;
  /** 正在请求 patch 详情 */
  patchLoading?: boolean;
  diffItems?: readonly unknown[];
}

/** patch 已加载且 diffItems 非空，可以渲染勾选列表 */
export function hasVisiblePatchDiff(msg: AiPatchMessageView): boolean {
  return Boolean(msg.patch && !msg.explainOnly && (msg.diffItems?.length ?? 0) > 0);
}

/** assistant 消息标记了 patchPending 但本地还没有 patch 对象，需要懒加载 */
export function isPatchPendingMessage(msg: AiPatchMessageView): boolean {
  return msg.role === 'assistant' && msg.patchPending === true && !msg.patch;
}

/** 是否渲染 Diff 区块：有可见 diff、等待懒加载、或正在加载中 */
export function shouldShowPatchDiffSection(msg: AiPatchMessageView): boolean {
  return hasVisiblePatchDiff(msg) || isPatchPendingMessage(msg) || msg.patchLoading === true;
}

/** 从列表末尾向前找最后一条未合并的 patch 消息 id（含待懒加载） */
export function resolveActivePatchMessageId(messages: AiPatchMessageView[]): string | null {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const msg = messages[i];
    if (msg.role !== 'assistant' || msg.merged || msg.explainOnly) continue;
    if (msg.patch || msg.patchPending) return msg.id;
  }
  return null;
}
