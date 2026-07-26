/**
 * aiStagingStore stagingByScenarioId 单元测试。
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';

describe('aiStagingStore stagingByScenarioId', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('pending addScenario 映射到 scenarioId', () => {
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch(
      'msg-1',
      {
        scenarioPatch: {
          addScenarios: [{ id: 'sc-new', name: '回归场景', testProjectEnvId: '1', flowSeed: {} }],
        },
      },
      { nodes: [], edges: [], runConfig: { activeScenarioId: 'sc1', scenarios: [] } },
    );

    expect(stagingStore.stagingByScenarioId['sc-new']?.mode).toBe('add');
    expect(stagingStore.stagingByScenarioId['sc-new']?.unitId).toBe('addScenario:sc-new');
  });

  it('pending setActiveScenario 映射到目标场景', () => {
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch(
      'msg-2',
      {
        scenarioPatch: { activeScenarioId: 'sc2' },
      },
      {
        nodes: [],
        edges: [],
        runConfig: {
          activeScenarioId: 'sc1',
          scenarios: [
            { id: 'sc1', name: 'A', testProjectEnvId: '', flowSeed: {} },
            { id: 'sc2', name: 'B', testProjectEnvId: '', flowSeed: {} },
          ],
        },
      },
    );

    expect(stagingStore.stagingByScenarioId['sc2']?.unitId).toBe('scenario:activeScenarioId');
  });
});
