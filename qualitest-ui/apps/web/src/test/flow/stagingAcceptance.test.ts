/**
 * 测 stagingAcceptance：messageAcceptedMap 与 Staging 重灌恢复 confirmed。
 * 边界：Pinia 内存态，ref 模拟 accepted map。
 * 单跑：yarn test stagingAcceptance   （在 qualitest-ui 或 apps/web 下）
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
    // 前提：调用 recordStagingUnitConfirmed
    // 期望：accepted map 含该 unitId
    recordStagingUnitConfirmed('msg-1', 'addNode:9001');
    expect(acceptedStagingUnitIds('msg-1').has('addNode:9001')).toBe(true);
  });

  it('重灌 Staging 时从 accepted map 恢复 confirmed 状态', () => {
    // 前提：accepted map 已记录 addNode confirmed，再 hydrate 同 patch
    // 期望：addNode confirmed、addEdge pending，摘要计数正确
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
