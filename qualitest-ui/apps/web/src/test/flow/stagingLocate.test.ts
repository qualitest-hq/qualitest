/**
 * stagingLocate 单元测试：摘要定位辅助。
 *
 * 运行（apps/web 目录）：yarn test stagingLocate
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import {
  findFirstPendingGraphUnit,
  findFirstPendingScenarioUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
  resolveScenarioIdFromUnit,
} from '@/views/project/testFlow/utils/stagingLocate';

function unit(partial: Partial<AiStagingUnit> & Pick<AiStagingUnit, 'unitId' | 'kind' | 'status'>): AiStagingUnit {
  return {
    messageId: 'msg-1',
    patchSlice: {},
    ...partial,
  };
}

describe('stagingLocate', () => {
  const units: AiStagingUnit[] = [
    unit({ unitId: 'addNode:n1', kind: 'addNode', status: 'confirmed' }),
    unit({ unitId: 'updateNode:n2', kind: 'updateNode', status: 'pending' }),
    unit({ unitId: 'addScenario:sc2', kind: 'addScenario', status: 'pending' }),
  ];

  it('isGraphStagingKind / isScenarioStagingKind 分类正确', () => {
    expect(isGraphStagingKind('addNode')).toBe(true);
    expect(isScenarioStagingKind('addScenario')).toBe(true);
    expect(isGraphStagingKind('setActiveScenario')).toBe(false);
  });

  it('findFirstPendingGraphUnit 跳过已确认与非图单元', () => {
    expect(findFirstPendingGraphUnit(units)?.unitId).toBe('updateNode:n2');
  });

  it('findFirstPendingScenarioUnit 返回首个 pending 场景单元', () => {
    expect(findFirstPendingScenarioUnit(units)?.unitId).toBe('addScenario:sc2');
  });

  it('resolveScenarioIdFromUnit 解析 setActiveScenario', () => {
    const activeUnit = unit({
      unitId: 'setActiveScenario',
      kind: 'setActiveScenario',
      status: 'pending',
      draft: { activeScenarioId: 'sc9' },
    });
    expect(resolveScenarioIdFromUnit(activeUnit)).toBe('sc9');
  });

  it('resolveScenarioIdFromUnit 从 unitId 解析场景 id', () => {
    expect(resolveScenarioIdFromUnit(units[2])).toBe('sc2');
  });
});

describe('aiStagingStore buildMessageSummary 与单元计数一致', () => {
  it('摘要 pending/confirmed/rejected 与 listUnitsForMessage 一致', async () => {
    const { createPinia, setActivePinia } = await import('pinia');
    const { useAiStagingStore } = await import('@/views/project/testFlow/stores/aiStagingStore');

    setActivePinia(createPinia());
    const store = useAiStagingStore();
    const patch = {
      addNodes: [{ id: 'a1', type: 'http', data: { name: 'A' } }],
      addEdges: [{ id: 'e1', source: 'a1', target: 'b1' }],
      scenarioPatch: {
        addScenarios: [{ id: 'sc-new', name: '新场景', testProjectEnvId: '', flowSeed: {} }],
      },
    };
    const ctx = {
      nodes: [],
      edges: [],
      runConfig: { activeScenarioId: 'sc1', scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }] },
    };

    store.hydrateStagingFromPatch('msg-sum', patch, ctx);
    store.markConfirmed('addNode:a1');
    store.markRejected('addEdge:e1');

    const units = store.listUnitsForMessage('msg-sum');
    const summary = store.buildMessageSummary('msg-sum');

    expect(summary.pending).toBe(units.filter((u) => u.status === 'pending').length);
    expect(summary.confirmed).toBe(units.filter((u) => u.status === 'confirmed').length);
    expect(summary.rejected).toBe(units.filter((u) => u.status === 'rejected').length);
    expect(summary.addNodeCount).toBe(1);
    expect(summary.addEdgeCount).toBe(1);
    expect(summary.scenarioCount).toBe(1);
  });
});
