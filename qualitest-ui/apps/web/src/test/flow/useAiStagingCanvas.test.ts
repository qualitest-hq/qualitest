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

  /** 端点节点尚不存在时，flushPendingEdges 应失败并保留 pending。 */
  it('端点未就绪时保留 pendingEdges', async () => {
    // 前提：pendingEdges 端点节点尚未在 nodes 中
    // 期望：flush 失败，pending 保留且 edges 仍为空
    const store = useFlowCanvasStore();

    store.setPendingEdges([
      { id: '8001', type: 'default', source: '9001', target: '9002' },
    ]);
    store.nodes = [];

    expect(store.flushPendingEdges()).toBe(false);
    expect(store.pendingEdges).not.toBeNull();
    expect(store.edges).toHaveLength(0);
  });
});
