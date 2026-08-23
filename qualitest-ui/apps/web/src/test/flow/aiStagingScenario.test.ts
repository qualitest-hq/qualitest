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

  it('pending setActiveScenario 映射到目标场景', () => {
    // 前提：patch 切换 activeScenarioId 至 sc2
    // 期望：stagingByScenarioId['sc2'] 指向切换单元
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
