/**
 * 测 aiStagingStore：跨 message 节点冲突时回滚画布旧 Staging。
 * 边界：mock revertStagingUnitOnCanvas；Pinia 内存态。
 * 单跑：yarn test aiStagingStore.conflict   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';

const revertMock = vi.fn();

vi.mock('@/views/project/testFlow/utils/stagingCanvasRevert', () => ({
  revertStagingUnitOnCanvas: (unit: unknown) => revertMock(unit),
}));

describe('aiStagingStore conflict revert', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    revertMock.mockReset();
  });

  const ctx = {
    nodes: [
      {
        id: '1001',
        type: 'http',
        position: { x: 0, y: 0 },
        data: { name: '旧名' },
      },
    ],
    edges: [],
    runConfig: {
      activeScenarioId: 'sc1',
      scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
    },
  };

  it('跨 message 节点冲突时调用 revertStagingUnitOnCanvas', () => {
    // 前提：两批 message 均 update 同一节点
    // 期望：revert 旧单元，保留 msg-2 的 pending 单元
    const store = useAiStagingStore();
    const patch1: FlowDesignPatch = {
      updateNodes: [{ id: '1001', data: { name: '第一批' } }],
    };
    const patch2: FlowDesignPatch = {
      updateNodes: [{ id: '1001', data: { name: '第二批' } }],
    };

    store.hydrateStagingFromPatch('msg-1', patch1, ctx);
    store.hydrateStagingFromPatch('msg-2', patch2, ctx);

    expect(revertMock).toHaveBeenCalledOnce();
    expect(revertMock.mock.calls[0][0].unitId).toBe('updateNode:1001');
    expect(store.getUnit('updateNode:1001')?.messageId).toBe('msg-2');
    expect(store.getUnit('updateNode:1001')?.status).toBe('pending');
  });
});
