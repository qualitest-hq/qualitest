/**
 * Staging 单元 lookup：属性面板与画布组件复用。
 */
import { computed, type ComputedRef, type Ref } from 'vue';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import type { DiffItemKind } from '../types/aiDesignTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';

export type StagingLayer = 'node' | 'edge' | 'scenario';

function stagingMarkForLayer(
  stagingStore: ReturnType<typeof useAiStagingStore>,
  layer: StagingLayer,
  id: string,
) {
  if (layer === 'node') return stagingStore.stagingByNodeId[id];
  if (layer === 'edge') return stagingStore.stagingByEdgeId[id];
  return stagingStore.stagingByScenarioId[id];
}

export function usePendingStagingUnit(
  sourceId: Ref<string | undefined> | ComputedRef<string | undefined>,
  layer: StagingLayer,
): ComputedRef<AiStagingUnit | null> {
  const stagingStore = useAiStagingStore();
  return computed(() => {
    const id = sourceId.value;
    if (!id) return null;
    const mark = stagingMarkForLayer(stagingStore, layer, id);
    if (!mark) return null;
    const unit = stagingStore.getUnit(mark.unitId);
    return unit?.status === 'pending' ? unit : null;
  });
}

export function useStagingMark(
  sourceId: Ref<string | undefined> | ComputedRef<string | undefined>,
  layer: StagingLayer,
) {
  const stagingStore = useAiStagingStore();
  return computed(() => {
    const id = sourceId.value;
    if (!id) return undefined;
    return stagingMarkForLayer(stagingStore, layer, id);
  });
}

export function useStagingUnitByMark(
  mark: ComputedRef<{ unitId: string } | undefined>,
) {
  const stagingStore = useAiStagingStore();
  return computed(() => {
    const m = mark.value;
    return m ? stagingStore.getUnit(m.unitId) : undefined;
  });
}

export function hasStagingConfirmError(unit: AiStagingUnit | undefined | null): boolean {
  return unit?.lastValidation != null && !unit.lastValidation.ok;
}

export function useShowNormalFields(
  stagingUnit: ComputedRef<AiStagingUnit | null>,
  hideOnKinds: DiffItemKind[],
): ComputedRef<boolean> {
  return computed(() => {
    const unit = stagingUnit.value;
    if (!unit) return true;
    return !hideOnKinds.includes(unit.kind);
  });
}
