/**
 * 测 useFlowHistory：undo 时恢复 Staging 单元状态。
 * 边界：mock element-plus；Pinia 内存态。
 * 单跑：pnpm test useFlowHistory.staging   （在 qualitest-ui 或 apps/web 下）
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { withFreshPinia } from '@/test/helpers/pinia';

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    info: vi.fn(),
  },
}));

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import { useFlowHistory } from '@/views/project/testFlow/composables/useFlowHistory';

describe('useFlowHistory staging snapshot', () => {
  withFreshPinia();

  const patch: FlowDesignPatch = {
    addNodes: [{ id: '9001', type: 'http', data: { name: '新节点' } }],
  };

  const ctx = {
    nodes: [],
    edges: [],
    runConfig: {
      activeScenarioId: 'sc1',
      scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
    },
  };

  it('pushHistory 后 undo 恢复 Staging 单元状态', async () => {
    // 前提：confirm 后 pushHistory，再执行 undo
    // 期望：单元 status 从 confirmed 恢复为 pending
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();
    const { pushHistory, undo, resetHistory } = useFlowHistory();

    canvasStore.nodes = [];
    canvasStore.edges = [];
    canvasStore.runConfig = ctx.runConfig;
    stagingStore.hydrateStagingFromPatch('msg-1', patch, ctx);
    resetHistory();

    stagingStore.markConfirmed('addNode:9001');
    pushHistory();

    expect(stagingStore.getUnit('addNode:9001')?.status).toBe('confirmed');

    await undo();

    expect(stagingStore.getUnit('addNode:9001')?.status).toBe('pending');
  });
});
