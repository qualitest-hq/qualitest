/**
 * Staging 单元是否可确认：依赖未满足时禁用确认/重试并给出提示。
 */
import { computed, toValue, type MaybeRefOrGetter } from 'vue';

import { useAiStagingStore } from '../stores/aiStagingStore';
import { resolveStagingConfirmDependency } from '../utils/stagingDependencyHints';

export function useStagingConfirmAvailability(unitId: MaybeRefOrGetter<string>) {
  const stagingStore = useAiStagingStore();

  const unit = computed(() => stagingStore.getUnit(toValue(unitId)));

  const dependencyState = computed(() => {
    const current = unit.value;
    if (!current || current.status !== 'pending') {
      return { blocked: false, blockTitle: '' };
    }

    void stagingStore.unitsById;
    const patch = stagingStore.getPatchForMessage(current.messageId);
    if (!patch) {
      return { blocked: false, blockTitle: '请先确认依赖项' };
    }

    const confirmedIds = new Set<string>(stagingStore.listConfirmedUnitIds());
    const resolved = resolveStagingConfirmDependency(toValue(unitId), patch, confirmedIds);
    return {
      blocked: resolved.blocked,
      blockTitle: resolved.blockTitle,
    };
  });

  const blockedByDependency = computed(() => dependencyState.value.blocked);
  const confirmBlockTitle = computed(() => dependencyState.value.blockTitle);
  const confirming = computed(() => unit.value?.confirmInFlight === true);
  const confirmDisabled = computed(
    () => confirming.value || blockedByDependency.value || unit.value?.status !== 'pending',
  );

  return {
    unit,
    confirming,
    blockedByDependency,
    confirmBlockTitle,
    confirmDisabled,
  };
}
