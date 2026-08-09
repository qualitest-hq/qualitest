<template>
  <div v-if="hasWarnings" class="ai-staging-confirm-warnings">
    <div class="ai-staging-confirm-warnings__label">鉴权提示</div>
    <div
        v-for="message in displayWarnings"
        :key="message"
        class="ai-staging-confirm-warnings__line"
        :title="message"
    >
      {{ message }}
    </div>
  </div>
</template>

<script setup>
/**
 * Staging 鉴权相关 soft warnings（不阻断确认）。
 * 来源：单元 lastValidation.warnings，或草稿 headers 上的 profileManaged 托管头。
 */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import {
  collectAuthManagedHeaderHints,
  filterAuthRelatedWarnings,
} from '../utils/stagingAuthHints'

const props = defineProps({
  unitId: { type: String, required: true },
  maxLines: { type: Number, default: 6 },
})

const stagingStore = useAiStagingStore()

const displayWarnings = computed(() => {
  const unit = stagingStore.getUnit(props.unitId)
  if (!unit) return []
  const fromValidation = filterAuthRelatedWarnings(unit.lastValidation?.warnings)
  const fromHeaders = collectAuthManagedHeaderHints(unit.draft)
  const merged = []
  const seen = new Set()
  for (const msg of [...fromValidation, ...fromHeaders]) {
    const key = msg.trim()
    if (!key || seen.has(key)) continue
    seen.add(key)
    merged.push(key)
  }
  return props.maxLines > 0 ? merged.slice(0, props.maxLines) : merged
})

const hasWarnings = computed(() => displayWarnings.value.length > 0)
</script>

<style scoped lang="scss">
.ai-staging-confirm-warnings {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  font-size: 11px;
  line-height: 1.4;
  text-align: left;
}

.ai-staging-confirm-warnings__label {
  font-weight: 700;
}

.ai-staging-confirm-warnings__line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
