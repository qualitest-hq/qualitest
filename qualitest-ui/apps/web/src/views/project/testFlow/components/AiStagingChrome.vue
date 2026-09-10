<template>
  <div
      v-if="isPending"
      :class="rootClass"
      :style="rootStyle"
      @click.stop
      @pointerdown.stop
      @mousedown.stop
  >
    <AiStagingActionButtons :unit-id="unitId" :size="placement === 'edge-midpoint' ? 'compact' : 'compact'" />
  </div>
</template>

<script setup lang="ts">
/** Staging 节点角标 / 边中点浮层：确认与取消（错误文案统一在画布校验条） */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import AiStagingActionButtons from './AiStagingActionButtons.vue'

const props = defineProps({
  unitId: { type: String, required: true },
  placement: { type: String as () => 'node-corner' | 'edge-midpoint', default: 'node-corner' },
  midpointX: { type: Number, default: 0 },
  midpointY: { type: Number, default: 0 },
})

const stagingStore = useAiStagingStore()
const unit = computed(() => stagingStore.getUnit(props.unitId))
const isPending = computed(() => unit.value?.status === 'pending')

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
</style>
