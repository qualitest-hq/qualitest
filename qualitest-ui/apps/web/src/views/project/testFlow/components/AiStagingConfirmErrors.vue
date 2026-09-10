<template>
  <div v-if="hasErrors" class="ai-staging-confirm-errors">
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
  </div>
</template>

<script setup>
/** Staging 确认失败文案，按来源分组展示（属性面板字段 diff 旁） */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import {
  classifyStagingConfirmErrors,
  groupStagingConfirmErrors,
} from '../utils/stagingConfirmErrorHints'

const props = defineProps({
  unitId: { type: String, required: true },
})

const stagingStore = useAiStagingStore()

const classified = computed(() => {
  const unit = stagingStore.getUnit(props.unitId)
  if (!unit?.lastValidation || unit.lastValidation.ok) return []
  return classifyStagingConfirmErrors(unit.lastDependencyHints, unit.lastValidation.errors)
})

const hasErrors = computed(() => classified.value.length > 0)

const groups = computed(() => groupStagingConfirmErrors(classified.value))
</script>

<style scoped lang="scss">
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
