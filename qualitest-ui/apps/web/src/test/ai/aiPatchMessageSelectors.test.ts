import { describe, expect, it } from 'vitest';

import {
  hasVisiblePatchDiff,
  isPatchPendingMessage,
  resolveActivePatchMessageId,
  shouldShowPatchDiffSection,
} from '@/utils/ai/aiPatchMessageSelectors';

describe('aiPatchMessageSelectors', () => {
  it('patchPending 且无 patch 时标记待加载', () => {
    expect(isPatchPendingMessage({ id: '1', role: 'assistant', patchPending: true })).toBe(true);
    expect(isPatchPendingMessage({ id: '1', role: 'assistant', patch: {}, patchPending: true })).toBe(false);
  });

  it('resolveActivePatchMessageId 从末尾找未合并 patch', () => {
    const id = resolveActivePatchMessageId([
      { id: 'u1', role: 'user' },
      { id: 'a1', role: 'assistant', patch: {}, merged: true },
      { id: 'a2', role: 'assistant', patchPending: true },
    ]);
    expect(id).toBe('a2');
  });

  it('shouldShowPatchDiffSection 含加载中与可见 diff', () => {
    expect(shouldShowPatchDiffSection({ id: '1', role: 'assistant', patchLoading: true })).toBe(true);
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
