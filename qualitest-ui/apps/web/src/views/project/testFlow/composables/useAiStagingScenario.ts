/**
 * 场景 Staging 与 runConfig 同步、draft 构建与 reject 恢复。
 */
import type { GraphRunScenario } from '@/utils/flow/graphTypes';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { objectIdFromUnitId } from '../utils/stagingUnitIds';
import { runStagingItemSync, useStagingSync } from './useStagingSync';

export function buildDraftFromCanvasScenario(scenario: GraphRunScenario): Record<string, unknown> {
  return JSON.parse(
    JSON.stringify({
      name: scenario.name,
      testProjectEnvId: scenario.testProjectEnvId,
      flowSeed: scenario.flowSeed ?? {},
      remark: scenario.remark ?? '',
      onNodeFailure: scenario.onNodeFailure,
      onSnapshotFailure: scenario.onSnapshotFailure,
    }),
  ) as Record<string, unknown>;
}

function buildScenarioFromStagingUnit(unit: AiStagingUnit): GraphRunScenario | null {
  if (unit.kind !== 'addScenario' || unit.status !== 'pending') return null;
  const id = objectIdFromUnitId(unit.unitId);
  const patchSlice = unit.patchSlice as GraphRunScenario | undefined;
  const draft = unit.draft ?? {};

  return {
    id,
    name: String(draft.name ?? patchSlice?.name ?? '新场景'),
    testProjectEnvId: String(draft.testProjectEnvId ?? patchSlice?.testProjectEnvId ?? ''),
    flowSeed: (draft.flowSeed as Record<string, unknown> | undefined) ??
      patchSlice?.flowSeed ??
      {},
    remark: String(draft.remark ?? patchSlice?.remark ?? ''),
    onNodeFailure: (draft.onNodeFailure as GraphRunScenario['onNodeFailure']) ??
      patchSlice?.onNodeFailure,
    onSnapshotFailure: (draft.onSnapshotFailure as GraphRunScenario['onSnapshotFailure']) ??
      patchSlice?.onSnapshotFailure,
  };
}

function applyScenarioDraft(unit: AiStagingUnit, scenarios: GraphRunScenario[]): GraphRunScenario[] | null {
  if (unit.kind !== 'updateScenario' || unit.status !== 'pending') return null;
  const id = objectIdFromUnitId(unit.unitId);
  const index = scenarios.findIndex((s) => s.id === id);
  if (index < 0) return null;

  const existing = scenarios[index];
  const draft = unit.draft ?? {};
  const next: GraphRunScenario = {
    ...existing,
    name: draft.name != null ? String(draft.name) : existing.name,
    testProjectEnvId:
      draft.testProjectEnvId != null ? String(draft.testProjectEnvId) : existing.testProjectEnvId,
    remark: draft.remark != null ? String(draft.remark) : existing.remark,
    flowSeed: (draft.flowSeed as Record<string, unknown> | undefined) ?? existing.flowSeed,
    onNodeFailure:
      (draft.onNodeFailure as GraphRunScenario['onNodeFailure']) ?? existing.onNodeFailure,
    onSnapshotFailure:
      (draft.onSnapshotFailure as GraphRunScenario['onSnapshotFailure']) ?? existing.onSnapshotFailure,
  };

  if (JSON.stringify(next) === JSON.stringify(existing)) return null;
  const cloned = [...scenarios];
  cloned[index] = next;
  return cloned;
}

export function rejectScenarioStagingUnit(unit: AiStagingUnit) {
  const store = useFlowCanvasStore();

  switch (unit.kind) {
    case 'addScenario': {
      const id = objectIdFromUnitId(unit.unitId);
      const scenarios = store.runConfig.scenarios.filter((s) => s.id !== id);
      let activeScenarioId = store.runConfig.activeScenarioId;
      if (activeScenarioId === id) {
        activeScenarioId = scenarios[0]?.id ?? activeScenarioId;
      }
      store.runConfig = { ...store.runConfig, scenarios, activeScenarioId };
      break;
    }
    case 'updateScenario': {
      const id = objectIdFromUnitId(unit.unitId);
      const baseline = unit.baseline;
      if (!baseline) break;
      store.runConfig = {
        ...store.runConfig,
        scenarios: store.runConfig.scenarios.map((s) =>
          s.id === id ? { ...s, ...JSON.parse(JSON.stringify(baseline)) } : s,
        ),
      };
      break;
    }
    case 'setActiveScenario': {
      const baselineId = unit.baseline?.activeScenarioId;
      if (baselineId != null) {
        store.runConfig = {
          ...store.runConfig,
          activeScenarioId: String(baselineId),
        };
      }
      break;
    }
    case 'deleteScenario':
      break;
    default:
      break;
  }

  store.markDirty();
}

export function useAiStagingScenario() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();

  function syncStagingScenarios() {
    if (Object.values(stagingStore.unitsById).some((unit) => unit.confirmInFlight)) {
      return;
    }

    let activeScenarioId = store.runConfig.activeScenarioId;

    const scenarios = runStagingItemSync(
      stagingStore.unitsById,
      store.runConfig.scenarios,
      {
        isLayerKind: (unit) =>
          unit.kind === 'addScenario' || unit.kind === 'updateScenario',
        buildFromAddUnit: buildScenarioFromStagingUnit,
        applyUpdateUnit: applyScenarioDraft,
        getItemId: (scenario) => scenario.id,
        getMark: (id) => stagingStore.stagingByScenarioId[id],
        onPrunedAdd: (prunedId, ctx) => {
          if (activeScenarioId === prunedId) {
            activeScenarioId = ctx.items[0]?.id ?? activeScenarioId;
          }
        },
        applySideEffect: (unit) => {
          if (unit.kind !== 'setActiveScenario') return;
          const targetId =
            unit.draft?.activeScenarioId ??
            (unit.patchSlice as { activeScenarioId?: string } | undefined)?.activeScenarioId;
          if (targetId) {
            activeScenarioId = String(targetId);
          }
        },
      },
    );

    const nextConfig = { activeScenarioId, scenarios };
    if (
      JSON.stringify(nextConfig) !==
      JSON.stringify({
        activeScenarioId: store.runConfig.activeScenarioId,
        scenarios: store.runConfig.scenarios,
      })
    ) {
      store.runConfig = nextConfig;
    }
  }

  useStagingSync(syncStagingScenarios, () => stagingStore.unitsById);

  return {
    syncStagingScenarios,
    buildDraftFromCanvasScenario,
    rejectScenarioStagingUnit,
  };
}
