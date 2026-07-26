<template>
  <div v-if="hasErrors" :class="rootClass">
    <template v-if="compact">
      <div
          v-for="line in displayLines"
          :key="line"
          class="ai-staging-confirm-errors__line ai-staging-error-line"
      >
        {{ line }}
      </div>
    </template>
    <template v-else>
      <div v-for="group in groups" :key="group.source" class="ai-staging-confirm-errors__group">
        <div class="ai-staging-confirm-errors__label">{{ group.label }}</div>
        <div
            v-for="message in group.messages"
            :key="`${group.source}:${message}`"
            class="ai-staging-confirm-errors__line"
        >
          {{ message }}
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
/** Staging confirm 失败错误，按来源分组展示 */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import {
  classifyStagingConfirmErrors,
  formatStagingConfirmErrorLine,
  groupStagingConfirmErrors,
} from '../utils/stagingConfirmErrorHints'

const props = defineProps({
  unitId: { type: String, required: true },
  compact: { type: Boolean, default: false },
  maxLines: { type: Number, default: 0 },
})

const stagingStore = useAiStagingStore()

const classified = computed(() => {
  const unit = stagingStore.getUnit(props.unitId)
  if (!unit?.lastValidation || unit.lastValidation.ok) return []
  return classifyStagingConfirmErrors(unit.lastDependencyHints, unit.lastValidation.errors)
})

const hasErrors = computed(() => classified.value.length > 0)

const groups = computed(() => groupStagingConfirmErrors(classified.value))

const displayLines = computed(() => {
  if (!props.compact) return []
  const lines = classified.value.map(formatStagingConfirmErrorLine)
  return props.maxLines > 0 ? lines.slice(0, props.maxLines) : lines
})

const rootClass = computed(() => [
  'ai-staging-confirm-errors',
  props.compact ? 'ai-staging-confirm-errors--compact' : '',
])
</script>

<style scoped lang="scss">
@use '../styles/aiStaging.scss' as staging;

.ai-staging-confirm-errors {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 11px;
  line-height: 1.4;
  text-align: left;

  &--compact {
    @include staging.ai-staging-error-panel(true);
    padding: 4px 6px;
    gap: 2px;
  }
}

.ai-staging-confirm-errors__label {
  font-weight: 700;
  margin-bottom: 2px;
}

.ai-staging-confirm-errors__line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
