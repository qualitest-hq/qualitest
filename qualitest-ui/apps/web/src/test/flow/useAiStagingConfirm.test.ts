/**
 * 测 useAiStagingConfirm：deleteNode confirm 后关联 pending addEdge 应 reject；
 * 全局单飞确认（连点多 deleteEdge 只进一笔）；确认全部就绪波次串行 / 失败停顿 / resume。
 * 边界：mock confirm API、viewport、history 等；Pinia 内存态。
 * 单跑：pnpm test useAiStagingConfirm   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const removeEdgeMock = vi.fn();
const requestConfirmMock = vi.fn();

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('@/views/project/testFlow/utils/computeBaseGraphHash', () => ({
  computeBaseGraphHash: vi.fn(async () => 'abcd1234efgh5678'),
  shouldBlockConfirmByBaseGraphHash: vi.fn(() => false),
}));

vi.mock('@/views/project/testFlow/utils/confirmFlowDesignUnit', () => ({
  requestConfirmFlowDesignUnit: (...args: unknown[]) => requestConfirmMock(...args),
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

import { ElMessage } from 'element-plus';
import {
  resetStagingConfirmGatesForTests,
  stagingBatchPausedOnUnitId,
  useAiStagingConfirm,
} from '@/views/project/testFlow/composables/useAiStagingConfirm';

function okConfirmResult() {
  return {
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
  };
}

function failConfirmResult(message = '校验失败') {
  return {
    validation: { ok: false, errors: [message], warnings: [] },
    graphJson: undefined,
    baseGraphHash: 'abcd1234efgh5678',
  };
}

const defaultRunConfig = {
  activeScenarioId: 'sc1',
  scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
};

function hydrateDeleteEdges(stagingStore: ReturnType<typeof useAiStagingStore>) {
  const patch: FlowDesignPatch = {
    suggestedDeletes: { edgeIds: ['e1', 'e2', 'e3'] },
  };
  const ctx = {
    nodes: [
      { id: 'n1', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } },
      { id: 'n2', type: 'http', position: { x: 100, y: 0 }, data: { name: 'B' } },
    ],
    edges: [
      { id: 'e1', source: 'n1', target: 'n2' },
      { id: 'e2', source: 'n1', target: 'n2' },
      { id: 'e3', source: 'n1', target: 'n2' },
    ],
    runConfig: defaultRunConfig,
  };
  stagingStore.hydrateStagingFromPatch('msg-del', patch, ctx);
}

function hydrateNodeEdgeChain(stagingStore: ReturnType<typeof useAiStagingStore>) {
  const patch: FlowDesignPatch = {
    addNodes: [
      { id: '9001', type: 'http', data: { name: 'A' }, position: { x: 0, y: 0 } },
      { id: '9002', type: 'http', data: { name: 'B' }, position: { x: 100, y: 0 } },
    ],
    addEdges: [{ id: '8001', source: '9001', target: '9002' }],
  };
  stagingStore.hydrateStagingFromPatch('msg-chain', patch, {
    nodes: [],
    edges: [],
    runConfig: defaultRunConfig,
  });
}

function mockFailUnit(unitId: string) {
  requestConfirmMock.mockImplementation(async (payload: { unitId?: string }) => {
    if (payload?.unitId === unitId) return failConfirmResult('边校验失败');
    return okConfirmResult();
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  resetStagingConfirmGatesForTests();
  removeEdgeMock.mockReset();
  requestConfirmMock.mockReset();
  requestConfirmMock.mockImplementation(async () => okConfirmResult());
  vi.mocked(ElMessage.success).mockClear();
  vi.mocked(ElMessage.error).mockClear();
});

describe('useAiStagingConfirm deleteNode', () => {
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
      runConfig: defaultRunConfig,
    };

    stagingStore.hydrateStagingFromPatch('msg-1', patch, ctx);
    const { confirmUnit } = useAiStagingConfirm();

    await confirmUnit('deleteNode:1001');

    expect(stagingStore.getUnit('deleteNode:1001')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('addEdge:8001')?.status).toBe('rejected');
    expect(removeEdgeMock).toHaveBeenCalledWith('8001');
    expect(ElMessage.success).toHaveBeenCalledWith('已确认删除');
  });

  it('并行连点多条 deleteEdge 时全局单飞只确认一条', async () => {
    // 前提：一批 3 条 deleteEdge；confirm API 挂起直到放行
    // 期望：同时触发 3 次 confirm 只发起 1 次 API，其余仍 pending
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';

    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    requestConfirmMock.mockImplementation(async () => {
      await gate;
      return okConfirmResult();
    });

    hydrateDeleteEdges(stagingStore);
    expect(stagingStore.getUnit('deleteEdge:e1')?.status).toBe('pending');
    expect(stagingStore.getUnit('deleteEdge:e2')?.status).toBe('pending');
    expect(stagingStore.getUnit('deleteEdge:e3')?.status).toBe('pending');

    const { confirmUnit } = useAiStagingConfirm();

    const p1 = confirmUnit('deleteEdge:e1');
    const p2 = confirmUnit('deleteEdge:e2');
    const p3 = confirmUnit('deleteEdge:e3');
    await vi.waitFor(() => {
      expect(requestConfirmMock).toHaveBeenCalledTimes(1);
    });

    release();
    await Promise.all([p1, p2, p3]);

    expect(stagingStore.getUnit('deleteEdge:e1')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('deleteEdge:e2')?.status).toBe('pending');
    expect(stagingStore.getUnit('deleteEdge:e3')?.status).toBe('pending');
    expect(requestConfirmMock).toHaveBeenCalledTimes(1);
  });
});

describe('useAiStagingConfirm confirmAllReady', () => {
  it('链式 addNode+addEdge 一次确认全部就绪后 pending 归零', async () => {
    // 前提：两节点与依赖边均 pending
    // 期望：confirmAllReady 后三者 confirmed，API 调用 3 次且无并行重叠
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    hydrateNodeEdgeChain(stagingStore);

    let inflight = 0;
    let maxInflight = 0;
    requestConfirmMock.mockImplementation(async () => {
      inflight += 1;
      maxInflight = Math.max(maxInflight, inflight);
      await Promise.resolve();
      inflight -= 1;
      return okConfirmResult();
    });

    const { confirmAllReady } = useAiStagingConfirm();
    await confirmAllReady();

    expect(stagingStore.getUnit('addNode:9001')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('addNode:9002')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('addEdge:8001')?.status).toBe('confirmed');
    expect(stagingStore.pendingCount).toBe(0);
    expect(requestConfirmMock).toHaveBeenCalledTimes(3);
    expect(maxInflight).toBe(1);
    expect(ElMessage.success).toHaveBeenCalledWith('已确认 3 项');
  });

  it('中途校验失败时已成功项保留且停在失败项', async () => {
    // 前提：3 条独立 deleteEdge；e2 的 confirm 始终校验失败（含自动重试）
    // 期望：e1 confirmed，e2 pending 且带 lastValidation，e3 仍 pending；pausedOnUnitId=e2
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    hydrateDeleteEdges(stagingStore);

    mockFailUnit('deleteEdge:e2');

    const { confirmAllReady } = useAiStagingConfirm();
    await confirmAllReady();

    expect(stagingStore.getUnit('deleteEdge:e1')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('deleteEdge:e2')?.status).toBe('pending');
    expect(stagingStore.getUnit('deleteEdge:e2')?.lastValidation?.ok).toBe(false);
    expect(stagingStore.getUnit('deleteEdge:e3')?.status).toBe('pending');
    expect(stagingBatchPausedOnUnitId.value).toBe('deleteEdge:e2');
    expect(ElMessage.error).toHaveBeenCalled();
  });

  it('跳过失败继续后确认其余就绪项', async () => {
    // 前提：e2 始终失败停顿后调用 resumeConfirmAllReady
    // 期望：跳过 e2，e3 被确认；e2 仍 pending
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    hydrateDeleteEdges(stagingStore);

    mockFailUnit('deleteEdge:e2');

    const { confirmAllReady, resumeConfirmAllReady } = useAiStagingConfirm();
    await confirmAllReady();
    expect(stagingBatchPausedOnUnitId.value).toBe('deleteEdge:e2');

    await resumeConfirmAllReady();

    expect(stagingStore.getUnit('deleteEdge:e1')?.status).toBe('confirmed');
    expect(stagingStore.getUnit('deleteEdge:e2')?.status).toBe('pending');
    expect(stagingStore.getUnit('deleteEdge:e3')?.status).toBe('confirmed');
    expect(stagingBatchPausedOnUnitId.value).toBeNull();
  });

  it('批量确认进行中再调 confirmUnit 被单飞挡住', async () => {
    // 前提：confirmAllReady 进行中（API 挂起）；同时再点单条 confirmUnit
    // 期望：仅批量路径发起 API，单条不额外请求
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    canvasStore.testProjectId = '10';
    hydrateDeleteEdges(stagingStore);

    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    requestConfirmMock.mockImplementation(async () => {
      await gate;
      return okConfirmResult();
    });

    const { confirmAllReady, confirmUnit } = useAiStagingConfirm();
    const batchPromise = confirmAllReady();
    await vi.waitFor(() => {
      expect(requestConfirmMock).toHaveBeenCalledTimes(1);
    });

    await confirmUnit('deleteEdge:e2');
    expect(requestConfirmMock).toHaveBeenCalledTimes(1);

    release();
    await batchPromise;

    expect(stagingStore.pendingCount).toBe(0);
    expect(requestConfirmMock).toHaveBeenCalledTimes(3);
  });
});
