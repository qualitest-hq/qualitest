/**
 * 测 useAiStagingConfirm：deleteNode confirm 后关联 pending addEdge 应 reject。
 * 边界：mock confirm API、viewport、history 等；Pinia 内存态。
 * 单跑：yarn test useAiStagingConfirm   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const removeEdgeMock = vi.fn();

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

vi.mock('@/views/project/testFlow/utils/computeBaseGraphHash', () => ({
  computeBaseGraphHash: vi.fn(async () => 'abcd1234efgh5678'),
  shouldBlockConfirmByBaseGraphHash: vi.fn(() => false),
}));

vi.mock('@/views/project/testFlow/composables/stagingConfirmDialog', () => ({
  confirmDeleteStagingUnit: vi.fn(async () => true),
}));

vi.mock('@/views/project/testFlow/utils/confirmFlowDesignUnit', () => ({
  requestConfirmFlowDesignUnit: vi.fn(async () => ({
    validation: { ok: true, errors: [], warnings: [] },
    graphJson: {
      nodes: [],
      edges: [],
      meta: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    },
    baseGraphHash: 'abcd1234efgh5678',
  })),
  CONFIRM_UNIT_TIMEOUT_MS: 30_000,
}));

vi.mock('@/views/project/testFlow/composables/useFlowGraph', () => ({
  useFlowGraph: () => ({ saveFlow: vi.fn(async () => true) }),
}));

vi.mock('@/views/project/testFlow/composables/useFlowHistory', () => ({
  useFlowHistory: () => ({ pushHistory: vi.fn() }),
}));

vi.mock('@/views/project/testFlow/composables/useFlowViewport', () => ({
  useFlowViewport: () => ({
    waitForCanvasReady: vi.fn(async () => undefined),
    focusNodeIds: vi.fn(async () => true),
    waitForViewportSettled: vi.fn(async () => undefined),
  }),
}));

vi.mock('@/views/project/testFlow/composables/useAiStagingCanvas', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/views/project/testFlow/composables/useAiStagingCanvas')>();
  return {
    ...actual,
    removeStagingEdgeFromCanvas: (edgeId: string) => removeEdgeMock(edgeId),
  };
});

import { useAiStagingConfirm } from '@/views/project/testFlow/composables/useAiStagingConfirm';

describe('useAiStagingConfirm deleteNode', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    removeEdgeMock.mockReset();
  });

  it('confirm deleteNode 后 reject 关联 pending addEdge', async () => {
    // 前提：deleteNode 与依赖已删节点的 pending addEdge 同批 hydrate
    // 期望：deleteNode confirmed，addEdge rejected 且从画布移除
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';

    const patch: FlowDesignPatch = {
      suggestedDeletes: { nodeIds: ['1001'] },
      addEdges: [{ id: '8001', source: '1001', target: '1002' }],
    };
    const ctx = {
      nodes: [
        { id: '1001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } },
        { id: '1002', type: 'http', position: { x: 100, y: 0 }, data: { name: 'B' } },
      ],
      edges: [],
      runConfig: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    };

    stagingStore.hydrateStagingFromPatch('msg-1', patch, ctx);
    const { confirmUnit } = useAiStagingConfirm();

    await confirmUnit('deleteNode:1001');

    expect(stagingStore.getUnit('deleteNode:1001')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('addEdge:8001')?.status).toBe('rejected');
    expect(removeEdgeMock).toHaveBeenCalledWith('8001');
  });
});
