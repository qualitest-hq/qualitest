/**
 * AI 确认高亮：累积高亮、最后统一清除。
 */
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AI_CONFIRM_HIGHLIGHT_CLEAR_MS,
  useFlowCanvasStore,
} from '@/views/project/testFlow/stores/flowCanvasStore';

describe('flowCanvasStore ai confirm highlight', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('addAiConfirmHighlight 累积节点 id', () => {
    const store = useFlowCanvasStore();

    store.addAiConfirmHighlight(['n1']);
    store.addAiConfirmHighlight(['n2', 'n1']);

    expect(store.aiHighlightNodeIds).toEqual(['n1', 'n2']);
  });

  it('确认过程中高亮保持，超过 4 秒也不自动清除', () => {
    const store = useFlowCanvasStore();

    store.addAiConfirmHighlight(['n1']);
    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS + 1000);

    expect(store.aiHighlightNodeIds).toEqual(['n1']);
  });

  it('finalize 后约 4 秒一起清除', () => {
    const store = useFlowCanvasStore();

    store.addAiConfirmHighlight(['n1']);
    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS - 1000);
    store.addAiConfirmHighlight(['n2']);
    store.finalizeAiConfirmHighlight();

    expect(store.aiHighlightNodeIds).toEqual(['n1', 'n2']);

    vi.advanceTimersByTime(1000);
    expect(store.aiHighlightNodeIds).toEqual(['n1', 'n2']);

    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS - 1000);
    expect(store.aiHighlightNodeIds).toEqual([]);
  });

  it('reset 清除高亮与定时器', () => {
    const store = useFlowCanvasStore();
    store.addAiConfirmHighlight(['n1']);
    store.finalizeAiConfirmHighlight();

    store.reset();

    expect(store.aiHighlightNodeIds).toEqual([]);
    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS);
    expect(store.aiHighlightNodeIds).toEqual([]);
  });

  it('setAiHighlightFocus 替换当前高亮', () => {
    const store = useFlowCanvasStore();
    store.addAiConfirmHighlight(['n1', 'n2']);

    store.setAiHighlightFocus(['n3']);

    expect(store.aiHighlightNodeIds).toEqual(['n3']);
  });
});
