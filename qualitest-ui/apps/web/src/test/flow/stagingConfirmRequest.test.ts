/**
 * confirm hash 守卫：服务端 baseGraphHash 与客户端算法不一致时不应误阻断落盘。
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import {
  applyConfirmResultWithHashGuard,
  requestConfirmOnce,
} from '@/views/project/testFlow/composables/stagingConfirmRequest';

const computeBaseGraphHashMock = vi.fn();

vi.mock('@/views/project/testFlow/utils/computeBaseGraphHash', () => ({
  computeBaseGraphHash: (...args: unknown[]) => computeBaseGraphHashMock(...args),
  shouldBlockConfirmByBaseGraphHash: (expected: string, current: string) => expected !== current,
}));

vi.mock('@/views/project/testFlow/utils/confirmFlowDesignUnit', () => ({
  requestConfirmFlowDesignUnit: vi.fn(async () => ({
    validation: { ok: true, errors: [], warnings: [] },
    graphJson: {
      nodes: [{ id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } }],
      edges: [],
      meta: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    },
    baseGraphHash: 'server-hash-mismatch',
  })),
}));

describe('applyConfirmResultWithHashGuard', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    computeBaseGraphHashMock.mockReset();
    computeBaseGraphHashMock.mockResolvedValue('client-request-hash');
  });

  it('服务端 baseGraphHash 与客户端不一致但画布未变时仍落盘', async () => {
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    canvasStore.nodes = [];
    canvasStore.edges = [];
    canvasStore.runConfig = {
      activeScenarioId: 'sc1',
      scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
    };

    stagingStore.hydrateStagingFromPatch(
      'msg-1',
      { addNodes: [{ id: '9001', type: 'http', data: { name: 'A' } }] },
      { nodes: [], edges: [], runConfig: canvasStore.runConfig },
    );

    const { result, requestBaseHash } = await requestConfirmOnce(
      'addNode:9001',
      stagingStore.getPatchForMessage('msg-1')!,
      '10',
      stagingStore,
      canvasStore,
    );

    computeBaseGraphHashMock.mockResolvedValue(requestBaseHash);

    const onHashConflict = vi.fn();
    const applied = await applyConfirmResultWithHashGuard(
      'addNode:9001',
      result,
      requestBaseHash,
      stagingStore.getPatchForMessage('msg-1')!,
      '10',
      stagingStore,
      canvasStore,
      onHashConflict,
    );

    expect(applied).toBe(true);
    expect(onHashConflict).not.toHaveBeenCalled();
    expect(canvasStore.nodes.some((n) => n.id === '9001')).toBe(true);
  });
});
