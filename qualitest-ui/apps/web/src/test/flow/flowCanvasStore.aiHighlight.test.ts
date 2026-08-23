/**
 * 测 flowCanvasStore AI 确认高亮：累积、finalize 清除与 reset。
 * 边界：Pinia 内存态；fake timers 测延迟清除。
 * 单跑：pnpm test flowCanvasStore.aiHighlight   （在 qualitest-ui 或 apps/web 下）
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { setupFreshPinia } from '@/test/helpers/pinia';
import {
  AI_CONFIRM_HIGHLIGHT_CLEAR_MS,
  useFlowCanvasStore,
} from '@/views/project/testFlow/stores/flowCanvasStore';

describe('flowCanvasStore ai confirm highlight', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setupFreshPinia();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('addAiConfirmHighlight 累积节点 id', () => {
    // 前提：多次 addAiConfirmHighlight 含重复 id
    // 期望：去重后按加入顺序累积
    const store = useFlowCanvasStore();

    store.addAiConfirmHighlight(['n1']);
    store.addAiConfirmHighlight(['n2', 'n1']);

    expect(store.aiHighlightNodeIds).toEqual(['n1', 'n2']);
  });

  it('确认过程中高亮保持，超过 4 秒也不自动清除', () => {
    // 前提：已 add 高亮但未 finalize
    // 期望：超时后高亮仍在
    const store = useFlowCanvasStore();

    store.addAiConfirmHighlight(['n1']);
    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS + 1000);

    expect(store.aiHighlightNodeIds).toEqual(['n1']);
  });

  it('finalize 后约 4 秒一起清除', () => {
    // 前提：finalize 前追加高亮，再 finalize
    // 期望：约 4 秒后全部清除
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
    // 前提：已 finalize 高亮后调用 reset
    // 期望：高亮立即清空且后续定时器不再生效
    const store = useFlowCanvasStore();
    store.addAiConfirmHighlight(['n1']);
    store.finalizeAiConfirmHighlight();

    store.reset();

    expect(store.aiHighlightNodeIds).toEqual([]);
    vi.advanceTimersByTime(AI_CONFIRM_HIGHLIGHT_CLEAR_MS);
    expect(store.aiHighlightNodeIds).toEqual([]);
  });

  it('setAiHighlightFocus 替换当前高亮', () => {
    // 前提：已有 n1/n2 高亮，调用 setAiHighlightFocus
    // 期望：高亮被替换为 n3
    const store = useFlowCanvasStore();
    store.addAiConfirmHighlight(['n1', 'n2']);

    store.setAiHighlightFocus(['n3']);

    expect(store.aiHighlightNodeIds).toEqual(['n3']);
  });
});
