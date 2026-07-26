/**
 * messageAcceptedMap 与 Staging 重灌恢复 confirmed 状态。
 */
import { ref } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import {
  acceptedStagingUnitIds,
  bindStagingAcceptanceMaps,
  recordStagingUnitConfirmed,
  resetStagingAcceptanceMaps,
} from '@/views/project/testFlow/utils/stagingAcceptance';

describe('stagingAcceptance', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const accepted = ref<Record<string, Set<string>>>({});
    const rejected = ref<Record<string, Set<string>>>({});
    bindStagingAcceptanceMaps(accepted, rejected);
    resetStagingAcceptanceMaps();
  });

  it('recordStagingUnitConfirmed 写入 accepted map', () => {
    recordStagingUnitConfirmed('msg-1', 'addNode:9001');
    expect(acceptedStagingUnitIds('msg-1').has('addNode:9001')).toBe(true);
  });

  it('重灌 Staging 时从 accepted map 恢复 confirmed 状态', () => {
    const store = useAiStagingStore();
    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', type: 'http', data: { name: 'A' } }],
      addEdges: [{ id: '8001', source: '9001', target: '1002' }],
    };
    const ctx = {
      nodes: [{ id: '1002', type: 'http', position: { x: 0, y: 0 }, data: { name: 'B' } }],
      edges: [],
      runConfig: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    };

    recordStagingUnitConfirmed('msg-1', 'addNode:9001');
    store.hydrateStagingFromPatch('msg-1', patch, ctx, {
      confirmedUnitIds: acceptedStagingUnitIds('msg-1'),
    });

    expect(store.getUnit('addNode:9001')?.status).toBe('confirmed');
    expect(store.getUnit('addEdge:8001')?.status).toBe('pending');
    expect(store.stagingByNodeId['9001']).toBeUndefined();
    expect(store.buildMessageSummary('msg-1').confirmed).toBe(1);
    expect(store.buildMessageSummary('msg-1').pending).toBe(1);
  });
});
