/**
 * 测 aiPatchMessageSelectors：patch 消息懒加载、Diff 展示与活跃 patch 定位。
 * 边界：纯函数，fixture 消息对象。
 * 单跑：yarn test aiPatchMessageSelectors   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  hasVisiblePatchDiff,
  isPatchPendingMessage,
  resolveActivePatchMessageId,
  shouldShowPatchDiffSection,
} from '@/utils/ai/aiPatchMessageSelectors';

describe('aiPatchMessageSelectors', () => {
  it('patchPending 且无 patch 时标记待加载', () => {
    // 前提：patchPending=true 且无 patch / 有 patch
    // 期望：无 patch 为 true，有 patch 为 false
    expect(isPatchPendingMessage({ id: '1', role: 'assistant', patchPending: true })).toBe(true);
    expect(isPatchPendingMessage({ id: '1', role: 'assistant', patch: {}, patchPending: true })).toBe(false);
  });

  it('resolveActivePatchMessageId 从末尾找未合并 patch', () => {
    // 前提：消息列表含 merged 与 patchPending 助手消息
    // 期望：返回末尾 patchPending 消息 id
    const id = resolveActivePatchMessageId([
      { id: 'u1', role: 'user' },
      { id: 'a1', role: 'assistant', patch: {}, merged: true },
      { id: 'a2', role: 'assistant', patchPending: true },
    ]);
    expect(id).toBe('a2');
  });

  it('shouldShowPatchDiffSection 加载中也应展示 Diff 区块', () => {
    // 前提：消息 patchLoading=true
    // 期望：应展示 Diff 区块
    expect(shouldShowPatchDiffSection({ id: '1', role: 'assistant', patchLoading: true })).toBe(true);
  });

  it('hasVisiblePatchDiff patch 已加载且 diffItems 非空时可见', () => {
    // 前提：patch 已加载且 diffItems 非空
    // 期望：hasVisiblePatchDiff 为 true
    expect(
      hasVisiblePatchDiff({
        id: '2',
        role: 'assistant',
        patch: {},
        diffItems: [{ id: 'x' }],
      }),
    ).toBe(true);
  });
});
