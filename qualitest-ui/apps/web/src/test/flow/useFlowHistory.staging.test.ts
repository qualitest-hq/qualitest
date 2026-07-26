/**
 * useFlowHistory 与 Staging 单元快照联动测试。
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

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
  beforeEach(() => {
    setActivePinia(createPinia());
  });

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
