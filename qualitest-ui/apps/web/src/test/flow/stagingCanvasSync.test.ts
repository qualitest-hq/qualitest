/**
 * 测 computeStagingCanvasSync：hydrate patch 后产出完整节点与边列表。
 * 边界：Pinia 内存态，无真实画布渲染。
 * 单跑：pnpm test stagingCanvasSync   （在 qualitest-ui 或 apps/web 下）
 */
import { beforeEach, describe, expect, it } from 'vitest';

import { setupFreshPinia } from '@/test/helpers/pinia';
import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import {
  computeStagingCanvasSync,
  createStagingCanvasSyncContext,
} from '@/views/project/testFlow/utils/stagingCanvasCompute';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const patch: FlowDesignPatch = {
  addNodes: [
    { id: '9001', type: 'http', position: { x: 40, y: 80 }, data: { name: '用户登录' } },
    { id: '9002', type: 'http', position: { x: 420, y: 80 }, data: { name: '获取当前用户信息' } },
    { id: '9003', type: 'assert', position: { x: 800, y: 80 }, data: { name: '响应验证' } },
  ],
  addEdges: [
    { id: '8001', source: '9001', target: '9002' },
    { id: '8002', source: '9002', target: '9003' },
    { id: '8003', source: '9001', target: '9003' },
  ],
};

describe('stagingCanvasSync', () => {
  beforeEach(() => {
    setupFreshPinia();
  });

  /** 3 节点 3 连线 hydrate 后，sync 应产出 3 个节点与 3 条有效边。 */
  it('hydrate 3 节点 + 3 连线后 sync 应产出 3 条边', () => {
    // 前提：patch 含 3 节点 3 边且已 hydrate
    // 期望：sync 产出 3 节点 3 边且端点有效
    const stagingStore = useAiStagingStore();
    const canvasStore = useFlowCanvasStore();

    stagingStore.hydrateStagingFromPatch('msg-1', patch, { nodes: [], edges: [] });

    expect(Object.values(stagingStore.unitsById).filter((u) => u.kind === 'addEdge')).toHaveLength(3);

    const { nodes, edges } = computeStagingCanvasSync(
      createStagingCanvasSyncContext(
        stagingStore.unitsById,
        canvasStore.nodes,
        canvasStore.edges,
        stagingStore.stagingByNodeId,
        stagingStore.stagingByEdgeId,
      ),
    );

    expect(nodes).toHaveLength(3);
    expect(edges).toHaveLength(3);
    expect(edges.every((e) => e.source && e.target)).toBe(true);
  });

  /** source 或 target 为空时不应生成边，避免无效端点进入 pending。 */
  it('addEdge 缺少 source/target 时不生成无效边', () => {
    // 前提：addEdge 的 source 为空
    // 期望：sync 不产出任何边
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch(
      'msg-bad',
      {
        addNodes: [{ id: '9001', type: 'http', data: { name: 'A' } }],
        addEdges: [{ id: '8001', source: '', target: '9001' }],
      },
      { nodes: [], edges: [] },
    );

    const { edges } = computeStagingCanvasSync(
      createStagingCanvasSyncContext(
        stagingStore.unitsById,
        [{ id: '9001', type: 'http', position: { x: 0, y: 0 }, data: {} }],
        [],
        stagingStore.stagingByNodeId,
        stagingStore.stagingByEdgeId,
      ),
    );
    expect(edges).toHaveLength(0);
  });
});
