/**
 * Staging 单元是否可点 ✓：仅 pending 且非 inFlight。
 */
import { computed, toValue, type MaybeRefOrGetter } from 'vue';

import { useAiStagingStore } from '../stores/aiStagingStore';

export function useStagingConfirmAvailability(unitId: MaybeRefOrGetter<string>) {
  const stagingStore = useAiStagingStore();

  const unit = computed(() => stagingStore.getUnit(toValue(unitId)));
  const confirming = computed(() => unit.value?.confirmInFlight === true);
  const confirmDisabled = computed(
    () => confirming.value || unit.value?.status !== 'pending',
  );

  return {
    unit,
    confirming,
    confirmDisabled,
  };
}
