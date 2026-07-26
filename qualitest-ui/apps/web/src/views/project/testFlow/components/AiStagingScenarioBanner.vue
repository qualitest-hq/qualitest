<template>
  <div class="ai-staging-scenario-banner" @click.stop>
    <div class="ai-staging-scenario-banner__label">{{ bannerLabel }}</div>
    <AiStagingConfirmErrors :unit-id="unitId" />
    <AiStagingActionButtons :unit-id="unitId" size="compact" />
  </div>
</template>

<script setup>
/** 场景列表卡片上的 Staging 确认条 */
import { computed } from 'vue'

import AiStagingActionButtons from './AiStagingActionButtons.vue'
import AiStagingConfirmErrors from './AiStagingConfirmErrors.vue'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { stagingKindTitle } from '../utils/stagingLabels'

const props = defineProps({
  unitId: { type: String, required: true },
})

const stagingStore = useAiStagingStore()

const unit = computed(() => stagingStore.getUnit(props.unitId))

const bannerLabel = computed(() => {
  const kind = unit.value?.kind
  if (!kind) return 'AI 待确认'
  return stagingKindTitle(kind, 'banner')
})
</script>

<style scoped lang="scss">
.ai-staging-scenario-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  background: color-mix(in srgb, #7c3aed 6%, #fff);
  border: 1px solid color-mix(in srgb, #7c3aed 22%, var(--pd-border-subtle));
}

.ai-staging-scenario-banner__label {
  font-size: 10px;
  font-weight: 700;
  color: #6d28d9;
}
</style>
