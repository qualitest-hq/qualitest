<template>
  <div v-if="hasAnyUnits" class="ai-staging-summary">
    <div class="ai-staging-summary__title">本次建议变更</div>
    <p class="ai-staging-summary__breakdown">{{ breakdownText }}</p>
    <p class="ai-staging-summary__counts">
      待确认 {{ summary.pending }} · 已确认 {{ summary.confirmed }} · 已取消 {{ summary.rejected }}
    </p>
    <div v-if="hasGraphPending || hasScenarioPending" class="ai-staging-summary__actions">
      <button
          v-if="hasGraphPending"
          class="btn btn--ghost btn--sm"
          type="button"
          @click="locateCanvas"
      >
        定位到画布
      </button>
      <button
          v-if="hasScenarioPending"
          class="btn btn--ghost btn--sm"
          type="button"
          @click="locateScenario"
      >
        定位到运行场景
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 会话内 Staging 变更摘要：统计、计数与定位 */
import { computed } from 'vue'

import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import {
  findFirstPendingGraphUnit,
  findFirstPendingScenarioUnit,
  focusGraphStagingUnit,
  focusScenarioStagingUnit,
  resolveScenarioIdFromUnit,
} from '../composables/useStagingNavigation'
import { isGraphStagingKind, isScenarioStagingKind } from '../utils/stagingUnitIds'
import { formatMessageSummaryBreakdown } from '../utils/stagingLabels'

const props = defineProps({
  messageId: { type: String, required: true },
})

const stagingStore = useAiStagingStore()
const canvasStore = useFlowCanvasStore()

const units = computed(() => stagingStore.listUnitsForMessage(props.messageId))
const summary = computed(() => stagingStore.buildMessageSummary(props.messageId))

const hasAnyUnits = computed(() => units.value.length > 0)

const hasGraphPending = computed(() =>
  units.value.some((unit) => unit.status === 'pending' && isGraphStagingKind(unit.kind)),
)

const hasScenarioPending = computed(() =>
  units.value.some((unit) => unit.status === 'pending' && isScenarioStagingKind(unit.kind)),
)

const breakdownText = computed(() => formatMessageSummaryBreakdown(summary.value))

function locateCanvas() {
  const unit = findFirstPendingGraphUnit(units.value)
  if (!unit) return
  focusGraphStagingUnit(unit, canvasStore.edges)
}

function locateScenario() {
  const unit = findFirstPendingScenarioUnit(units.value)
  if (!unit) return
  const scenarioId = resolveScenarioIdFromUnit(unit)
  if (scenarioId) {
    focusScenarioStagingUnit(scenarioId)
  }
}
</script>

<style scoped lang="scss">
.ai-staging-summary {
  margin-top: 8px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--pd-primary) 22%, var(--pd-divider));
  background: color-mix(in srgb, var(--pd-primary-soft) 35%, #fff);
}

.ai-staging-summary__title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.ai-staging-summary__breakdown,
.ai-staging-summary__counts {
  margin: 0 0 6px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--pd-text-muted);
}

.ai-staging-summary__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
</style>
