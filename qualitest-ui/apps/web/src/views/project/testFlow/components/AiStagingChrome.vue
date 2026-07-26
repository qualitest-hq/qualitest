<template>
  <div
      v-if="isPending"
      :class="rootClass"
      :style="rootStyle"
      @click.stop
      @pointerdown.stop
      @mousedown.stop
  >
    <AiStagingConfirmErrors
        v-if="hasError"
        :unit-id="unitId"
        :compact="true"
        :max-lines="2"
    />
    <span v-if="hasError && placement === 'node-corner'" class="ai-staging-chrome__badge" title="确认失败，可修改后重试或点击 ↻">!</span>
    <span v-if="hasError && placement === 'edge-midpoint'" class="ai-staging-chrome__badge ai-staging-chrome__badge--edge">!</span>
    <AiStagingActionButtons :unit-id="unitId" :size="placement === 'edge-midpoint' ? 'compact' : 'compact'" />
  </div>
</template>

<script setup lang="ts">
/** Staging 节点角标 / 边中点浮层：确认、取消与失败提示 */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import { hasStagingConfirmError } from '../composables/usePendingStagingUnit'
import AiStagingActionButtons from './AiStagingActionButtons.vue'
import AiStagingConfirmErrors from './AiStagingConfirmErrors.vue'

const props = defineProps({
  unitId: { type: String, required: true },
  placement: { type: String as () => 'node-corner' | 'edge-midpoint', default: 'node-corner' },
  midpointX: { type: Number, default: 0 },
  midpointY: { type: Number, default: 0 },
})

const stagingStore = useAiStagingStore()
const unit = computed(() => stagingStore.getUnit(props.unitId))
const isPending = computed(() => unit.value?.status === 'pending')
const hasError = computed(() => hasStagingConfirmError(unit.value))

const rootClass = computed(() => [
  'ai-staging-chrome',
  props.placement === 'edge-midpoint' ? 'ai-staging-chrome--edge' : 'ai-staging-chrome--node',
])

const rootStyle = computed(() => {
  if (props.placement !== 'edge-midpoint') return undefined
  return {
    position: 'absolute',
    transform: `translate(-50%, -50%) translate(${props.midpointX}px, ${props.midpointY}px)`,
    pointerEvents: 'all',
  } as Record<string, string>
})
</script>

<style scoped lang="scss">
.ai-staging-chrome {
  z-index: 12;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  pointer-events: auto;
}

.ai-staging-chrome--node {
  position: absolute;
  top: -8px;
  right: -8px;
}

.ai-staging-chrome--edge {
  align-items: center;
}

.ai-staging-chrome__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #dc2626;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);

  &--edge {
    width: 16px;
    height: 16px;
    font-size: 11px;
  }
}
</style>
