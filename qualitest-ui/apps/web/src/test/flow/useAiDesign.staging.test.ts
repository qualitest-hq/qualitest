/**
 * AI 设计请求 graph_json 应排除未 confirm 的 Staging 对象。
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { toGraphJson } from '@/views/project/testFlow/graphAdapter';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import { buildFlowGraphInput } from '@/views/project/testFlow/utils/stagingGraphInput';

describe('AI design graph_json staging filter', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('pending addNode 不出现在设计请求 graph_json 中', () => {
    const canvasStore = useFlowCanvasStore();
    const stagingStore = useAiStagingStore();

    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', type: 'http', data: { name: '暂存' } }],
    };
    const ctx = {
      nodes: [],
      edges: [],
      runConfig: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    };

    stagingStore.hydrateStagingFromPatch('msg-1', patch, ctx);
    canvasStore.nodes = [
      {
        id: '9001',
        type: 'http',
        position: { x: 0, y: 0 },
        data: { name: '暂存' },
      },
    ];

    const withStaging = toGraphJson(buildFlowGraphInput(canvasStore));
    const filtered = toGraphJson({
      ...buildFlowGraphInput(canvasStore),
      stagingFilter: stagingStore.buildPersistFilter(),
    });

    expect(withStaging.nodes.some((n) => n.id === '9001')).toBe(true);
    expect(filtered.nodes.some((n) => n.id === '9001')).toBe(false);
  });
});
