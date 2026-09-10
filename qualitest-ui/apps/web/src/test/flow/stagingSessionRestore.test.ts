/**
 * 测 restorePendingStagingIfNeeded：在 Staging 已空时按会话消息决定是否回灌待确认变更。
 * 边界：已有 pending、merged、explainOnly、仅 patchPending、reset 后回灌 addNode。
 * 单跑：pnpm test stagingSessionRestore   （在 qualitest-ui 或 apps/web 下）
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

import { setupFreshPinia } from '@/test/helpers/pinia';
import type { AiDesignMessageView, FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { createAiStagingHydration } from '@/views/project/testFlow/composables/useAiStagingHydration';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import { restorePendingStagingIfNeeded } from '@/views/project/testFlow/utils/stagingSessionRestore';

describe('stagingSessionRestore', () => {
  beforeEach(() => {
    setupFreshPinia();
  });

  it('pendingCount>0 时不回灌', async () => {
    // 前提：Staging 已有 pending
    // 期望：eagerLoad / onSessionLoaded 均不调用，返回 false
    const eagerLoadActivePatch = vi.fn(async () => {});
    const onSessionLoaded = vi.fn(async () => {});

    const restored = await restorePendingStagingIfNeeded({
      pendingCount: 2,
      messages: [{ id: 'a1', role: 'assistant', patch: { addNodes: [{ id: 'n1' }] } }],
      eagerLoadActivePatch,
      onSessionLoaded,
    });

    expect(restored).toBe(false);
    expect(eagerLoadActivePatch).not.toHaveBeenCalled();
    expect(onSessionLoaded).not.toHaveBeenCalled();
  });

  it('merged / explainOnly / 无 patch 时不回灌', async () => {
    // 前提：仅 merged、explainOnly 或用户消息
    // 期望：均不调用 onSessionLoaded
    const onSessionLoaded = vi.fn(async () => {});

    expect(
      await restorePendingStagingIfNeeded({
        pendingCount: 0,
        messages: [{ id: 'a1', role: 'assistant', merged: true, patch: { addNodes: [] } }],
        eagerLoadActivePatch: async () => {},
        onSessionLoaded,
      }),
    ).toBe(false);

    expect(
      await restorePendingStagingIfNeeded({
        pendingCount: 0,
        messages: [{ id: 'a2', role: 'assistant', explainOnly: true, patch: { addNodes: [] } }],
        eagerLoadActivePatch: async () => {},
        onSessionLoaded,
      }),
    ).toBe(false);

    expect(
      await restorePendingStagingIfNeeded({
        pendingCount: 0,
        messages: [{ id: 'u1', role: 'user' }],
        eagerLoadActivePatch: async () => {},
        onSessionLoaded,
      }),
    ).toBe(false);

    expect(onSessionLoaded).not.toHaveBeenCalled();
  });

  it('Staging 被清空后按消息回灌 pending addNode', async () => {
    // 前提：消息含 addNode patch 且已灌入；随后清空 Staging 单元表，pendingCount=0
    // 期望：restore 后 pending 再现
    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', type: 'http', data: { name: '登录' } }],
    };
    const messages = ref<AiDesignMessageView[]>([
      {
        id: 'msg-1',
        role: 'assistant',
        content: '已提交修改',
        patch,
        explainOnly: false,
      } as AiDesignMessageView,
    ]);

    const hydration = createAiStagingHydration(messages);
    await hydration.onSessionLoaded();

    const stagingStore = useAiStagingStore();
    expect(stagingStore.pendingCount).toBeGreaterThan(0);

    useFlowCanvasStore().reset();
    expect(stagingStore.pendingCount).toBe(0);

    const restored = await restorePendingStagingIfNeeded({
      pendingCount: stagingStore.pendingCount,
      messages: messages.value,
      eagerLoadActivePatch: async () => {},
      onSessionLoaded: () => hydration.onSessionLoaded(),
    });

    expect(restored).toBe(true);
    expect(stagingStore.pendingCount).toBeGreaterThan(0);
    expect(Object.values(stagingStore.unitsById).some((u) => u.kind === 'addNode')).toBe(true);
  });

  it('patchPending 也可触发回灌路径', async () => {
    // 前提：仅 patchPending、pendingCount=0
    // 期望：先 eagerLoad 再 onSessionLoaded，返回 true
    const eagerLoadActivePatch = vi.fn(async () => {});
    const onSessionLoaded = vi.fn(async () => {});

    const restored = await restorePendingStagingIfNeeded({
      pendingCount: 0,
      messages: [{ id: 'a1', role: 'assistant', patchPending: true }],
      eagerLoadActivePatch,
      onSessionLoaded,
    });

    expect(restored).toBe(true);
    expect(eagerLoadActivePatch).toHaveBeenCalledOnce();
    expect(onSessionLoaded).toHaveBeenCalledOnce();
  });
});
