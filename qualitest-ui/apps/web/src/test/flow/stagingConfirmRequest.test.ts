/**
 * 测 applyConfirmResultWithHashGuard：hash 不一致但画布未变时仍落盘。
 * 边界：mock computeBaseGraphHash 与 confirm API；Pinia 内存态。
 * 单跑：yarn test stagingConfirmRequest   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import {
  applyConfirmResultWithHashGuard,
  applyConfirmedGraph,
  mergePendingStagingIntoConfirmedGraph,
  requestConfirmOnce,
} from '@/views/project/testFlow/composables/stagingConfirmRequest';
import { fromGraphJson } from '@/views/project/testFlow/graphAdapter';

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
    // 前提：服务端 hash 与请求 hash 不同，确认后当前 hash 仍等于请求 hash
    // 期望：落盘成功，不触发 hash 冲突回调
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

  it('确认落盘时保留其它 pending Staging 节点，避免短暂消失像平移', async () => {
    // 前提：画布上同时有已确认节点 A 与 pending 节点 B；服务端只返回 A
    // 期望：applyConfirmedGraph 后 B 仍在画布，坐标不变
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    canvasStore.nodes = [
      { id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } },
      { id: '9002', type: 'http', position: { x: 380, y: 0 }, data: { name: 'B' } },
    ];
    canvasStore.edges = [];
    canvasStore.runConfig = {
      activeScenarioId: 'sc1',
      scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
    };

    stagingStore.hydrateStagingFromPatch(
      'msg-1',
      {
        addNodes: [
          { id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } },
          { id: '9002', type: 'http', position: { x: 380, y: 0 }, data: { name: 'B' } },
        ],
      },
      { nodes: [], edges: [], runConfig: canvasStore.runConfig },
    );
    stagingStore.markConfirmed('addNode:9001');
    expect(stagingStore.getUnit('addNode:9002')?.status).toBe('pending');

    const applied = fromGraphJson({
      nodes: [{ id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } }],
      edges: [],
      meta: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    });
    const merged = mergePendingStagingIntoConfirmedGraph(applied, canvasStore, stagingStore);
    expect(merged.nodes.map((n) => n.id).sort()).toEqual(['9001', '9002']);
    expect(merged.nodes.find((n) => n.id === '9002')?.position).toEqual({ x: 380, y: 0 });

    await applyConfirmedGraph(
      {
        nodes: [{ id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } }],
        edges: [],
        meta: {
          activeScenarioId: 'sc1',
          scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
        },
      },
      canvasStore,
      stagingStore,
    );
    expect(canvasStore.nodes.find((n) => n.id === '9002')?.position).toEqual({ x: 380, y: 0 });
  });
});
