/**
 * 测 applyStagingCanvasToStore：Staging 画布写入 store 时边进 pendingEdges。
 * 边界：Pinia 内存态，无真实 Vue Flow 实例。
 * 单跑：pnpm test useAiStagingCanvas   （在 qualitest-ui 或 apps/web 下）
 */
import { nextTick } from 'vue';
import { beforeEach, describe, expect, it } from 'vitest';

import { setupFreshPinia } from '@/test/helpers/pinia';
import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { applyStagingCanvasToStore } from '@/views/project/testFlow/composables/useAiStagingCanvas';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const patch: FlowDesignPatch = {
  addNodes: [
    {
      id: '9001',
      type: 'http',
      position: { x: 40, y: 80 },
      data: { name: '登录', callMode: 'external', externalUrl: 'https://example.com/login', httpMethod: 'POST' },
    },
    {
      id: '9002',
      type: 'http',
      position: { x: 240, y: 80 },
      data: { name: '获取用户信息', callMode: 'external', externalUrl: 'https://example.com/profile', httpMethod: 'GET' },
    },
  ],
  addEdges: [{ id: '8001', source: '9001', target: '9002' }],
};

describe('applyStagingCanvasToStore', () => {
  beforeEach(() => {
    setupFreshPinia();
  });

  /** 节点与边同时新增时，边写入 pendingEdges 并触发灌入计数，不直接覆盖 edges。 */
  it('节点与边同时新增时，边应进入 pendingEdges 等待灌入', async () => {
    // 前提：pending addNode 与 addEdge 同时写入画布
    // 期望：节点落盘，边进 pendingEdges 且 flushToken 递增
    const store = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();

    stagingStore.hydrateStagingFromPatch('msg-1', patch, { nodes: [], edges: [] });

    let nodes = [...store.nodes];
    const pendingAddIds = new Set<string>();
    for (const unit of Object.values(stagingStore.unitsById)) {
      if (unit.kind !== 'addNode' || unit.status !== 'pending') continue;
      const nodeId = unit.unitId.slice('addNode:'.length);
      pendingAddIds.add(nodeId);
      nodes.push({
        id: nodeId,
        type: 'http',
        position: { x: 40, y: 80 },
        data: { name: '登录' },
      });
    }

    const edges = [
      {
        id: '8001',
        type: 'default',
        source: '9001',
        target: '9002',
      },
    ];

    await applyStagingCanvasToStore(store, nodes, edges, stagingStore.unitsById);
    await nextTick();

    expect(store.nodes.map((n) => n.id).sort()).toEqual(['9001', '9002']);
    expect(store.pendingEdges).not.toBeNull();
    expect(store.pendingEdges?.some((e) => e.id === '8001')).toBe(true);
    expect(store.stagingEdgeFlushToken).toBeGreaterThan(0);
  });

  /** 端点未就绪时 bump 不清空 pendingEdges。 */
  it('端点未就绪时保留 pendingEdges', async () => {
    // 前提：pendingEdges 端点节点尚未在 nodes 中
    // 期望：bump 后 pending 保留且 edges 仍为空
    const store = useFlowCanvasStore();

    store.setPendingEdges([
      { id: '8001', type: 'default', source: '9001', target: '9002' },
    ]);
    store.nodes = [];

    store.bumpStagingEdgeFlushToken();
    expect(store.pendingEdges).not.toBeNull();
    expect(store.edges).toHaveLength(0);
  });

  /**
   * 确认落盘后 edges 被清空、完整边在 pendingEdges；若仍有其它 pending addEdge，
   * sync 必须以有效边表为底，否则会把已确认边冲掉。
   */
  it('确认落盘窗口 sync 不得用空 edges 覆盖 pendingEdges 中的已确认边', async () => {
    const store = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();

    store.nodes = [
      { id: 'n1', type: 'http', position: { x: 0, y: 0 }, data: { name: '探活' } },
      { id: 'n2', type: 'condition', position: { x: 200, y: 0 }, data: { name: '探活是否200' } },
      { id: 'n3', type: 'http', position: { x: 200, y: 120 }, data: { name: '登录' } },
    ];
    // 模拟 applyConfirmedGraph：edges 清空，完整边在 pending
    store.edges = [];
    store.setPendingEdges([
      { id: 'e1', type: 'default', source: 'n1', target: 'n2', label: '成功' },
      { id: 'e2', type: 'default', source: 'n2', target: 'n3', label: '探活失败' },
    ]);

    // 仅 e2 仍为 pending Staging（手写单元，避开 hydrate 归一化）
    stagingStore.unitsById = {
      'addEdge:e2': {
        unitId: 'addEdge:e2',
        messageId: 'msg-1',
        kind: 'addEdge',
        status: 'pending',
        label: '探活失败',
        patchSlice: { id: 'e2', source: 'n2', target: 'n3', label: '探活失败' },
        draft: { source: 'n2', target: 'n3', label: '探活失败' },
      },
    } as never;

    const { computeStagingCanvasSync, createStagingCanvasSyncContext } = await import(
      '@/views/project/testFlow/utils/stagingCanvasCompute'
    );
    const baseEdges = store.getEffectiveEdges().map((e) => ({ ...e }));
    expect(baseEdges.map((e) => e.id).sort()).toEqual(['e1', 'e2']);

    // 若误用空 store.edges 作底，e1 会丢失
    const broken = computeStagingCanvasSync(
      createStagingCanvasSyncContext(
        stagingStore.unitsById,
        store.nodes,
        [],
        {},
        { e2: { unitId: 'addEdge:e2', mode: 'add' } },
      ),
    );
    expect(broken.edges.map((e) => e.id)).toEqual(['e2']);

    const { edges } = computeStagingCanvasSync(
      createStagingCanvasSyncContext(
        stagingStore.unitsById,
        store.nodes,
        baseEdges,
        {},
        { e2: { unitId: 'addEdge:e2', mode: 'add' } },
      ),
    );
    await applyStagingCanvasToStore(store, store.nodes, edges, stagingStore.unitsById);

    const effectiveIds = store.getEffectiveEdges().map((e) => e.id).sort();
    expect(effectiveIds).toEqual(['e1', 'e2']);
  });
});
