/**
 * 测 aiStagingStore stagingByScenarioId：场景类 Staging 映射。
 * 边界：Pinia 内存态，无 API 依赖。
 * 单跑：pnpm test aiStagingScenario   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { withFreshPinia } from '@/test/helpers/pinia';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';

describe('aiStagingStore stagingByScenarioId', () => {
  withFreshPinia();

  it('pending addScenario 映射到 scenarioId', () => {
    // 前提：patch 含 addScenarios
    // 期望：stagingByScenarioId 指向 addScenario 单元
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

  it('pending updateScenario 映射到 scenarioId', () => {
    // 前提：patch 含 updateScenarios
    // 期望：stagingByScenarioId['sc1'] 指向 update 单元
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch(
      'msg-2',
      {
        scenarioPatch: {
          updateScenarios: [{ id: 'sc1', name: '默认V2', testProjectEnvId: '', flowSeed: {} }],
        },
      },
      {
        nodes: [],
        edges: [],
        runConfig: {
          activeScenarioId: 'sc1',
          scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
        },
      },
    );

    expect(stagingStore.stagingByScenarioId['sc1']?.mode).toBe('update');
    expect(stagingStore.stagingByScenarioId['sc1']?.unitId).toBe('updateScenario:sc1');
  });
});
