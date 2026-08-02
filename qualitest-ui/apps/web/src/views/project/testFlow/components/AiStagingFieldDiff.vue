<template>
  <div class="ai-staging-field-diff">
    <div class="ai-staging-field-diff__head">
      <span class="ai-staging-field-diff__title">{{ title }}</span>
      <span v-if="unit?.label" class="ai-staging-field-diff__subtitle">{{ unit.label }}</span>
    </div>

    <AiStagingConfirmErrors :unit-id="unitId" />

    <p v-if="deleteHint" class="ai-staging-field-diff__delete-hint">{{ deleteHint }}</p>

    <table v-if="fieldRows.length" class="ai-staging-field-diff__table">
      <thead>
        <tr>
          <th class="ai-staging-field-diff__col-field">字段</th>
          <th>原值</th>
          <th>现值（可编辑）</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in fieldRows" :key="row.key">
          <td class="ai-staging-field-diff__field">{{ row.label }}</td>
          <td class="ai-staging-field-diff__baseline">
            <pre v-if="row.multiline" class="ai-staging-field-diff__pre">{{ row.baselineText || '—' }}</pre>
            <span v-else>{{ row.baselineText || '—' }}</span>
          </td>
          <td>
            <textarea
                v-if="row.multiline"
                :value="row.draftValue"
                class="ai-staging-field-diff__input ai-staging-field-diff__input--multiline"
                rows="6"
                spellcheck="false"
                @input="onFieldInput(row.key, $event)"
            />
            <input
                v-else
                :value="row.draftValue"
                class="ai-staging-field-diff__input"
                type="text"
                @input="onFieldInput(row.key, $event)"
            />
          </td>
        </tr>
      </tbody>
    </table>

    <AiStagingActionButtons :unit-id="unitId" size="full" />
  </div>
</template>

<script setup>
/** Staging 原/现对照编辑器：节点/边 update 与 add/delete 摘要 */
import { computed } from 'vue'

import AiStagingActionButtons from './AiStagingActionButtons.vue'
import AiStagingConfirmErrors from './AiStagingConfirmErrors.vue'
import { useAiStagingStore } from '../stores/aiStagingStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { stagingDeleteHint, stagingKindTitle } from '../utils/stagingLabels'
import { objectIdFromUnitId } from '../utils/stagingUnitIds'
import {
  applyStagingFieldToDraft,
  buildEdgeStagingFieldRows,
  buildNodeStagingFieldRows,
  buildScenarioStagingFieldRows,
} from '../utils/stagingFieldDiff'

const props = defineProps({
  unitId: { type: String, required: true },
})

const stagingStore = useAiStagingStore()
const canvasStore = useFlowCanvasStore()

const unit = computed(() => stagingStore.getUnit(props.unitId))

const title = computed(() => {
  const kind = unit.value?.kind
  if (!kind) return 'AI 待确认变更'
  if (kind === 'updateNode' || kind === 'updateEdge' || kind === 'updateScenario') {
    return `AI ${stagingKindTitle(kind, 'panel').replace(/^修改/, '建议修改')}`
  }
  if (kind === 'addNode' || kind === 'addEdge' || kind === 'addScenario') {
    return `AI ${stagingKindTitle(kind, 'panel').replace(/^新增/, '建议新增')}`
  }
  if (kind === 'deleteNode' || kind === 'deleteEdge' || kind === 'deleteScenario') {
    return '待确认删除'
  }
  if (kind === 'setActiveScenario') return 'AI 建议切换默认场景'
  return 'AI 待确认变更'
})

const fieldRows = computed(() => {
  const u = unit.value
  if (!u) return []
  if (u.kind === 'updateNode') {
    return buildNodeStagingFieldRows(u.baseline, u.draft)
  }
  if (u.kind === 'updateEdge') {
    return buildEdgeStagingFieldRows(u.baseline, u.draft)
  }
  if (u.kind === 'updateScenario') {
    return buildScenarioStagingFieldRows(u.baseline, u.draft)
  }
  return []
})

const deleteHint = computed(() => {
  const u = unit.value
  if (!u?.kind) return ''
  if (u.kind === 'deleteNode') {
    const nodeId = objectIdFromUnitId(props.unitId)
    const edgeCount = canvasStore.edges.filter(
      (e) => e.source === nodeId || e.target === nodeId,
    ).length
    return stagingDeleteHint('deleteNode', { edgeCount })
  }
  if (u.kind === 'deleteEdge') return stagingDeleteHint('deleteEdge')
  if (u.kind === 'addNode' || u.kind === 'addEdge') {
    return '可在下方属性区继续编辑，确认后写入正式图。'
  }
  if (u.kind === 'addScenario') {
    return '可在下方继续编辑场景参数，确认后写入正式配置。'
  }
  if (u.kind === 'deleteScenario') return stagingDeleteHint('deleteScenario')
  if (u.kind === 'setActiveScenario') {
    return '确认后将切换左侧默认运行场景。'
  }
  return ''
})

function onFieldInput(fieldKey, event) {
  const u = unit.value
  if (!u?.draft) return
  const nextDraft = applyStagingFieldToDraft(u.draft, fieldKey, event.target.value)
  stagingStore.updateDraft(props.unitId, nextDraft)
}
</script>

<style scoped lang="scss">
.ai-staging-field-diff {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, #0b6edc 28%, var(--pd-border-subtle));
  background: color-mix(in srgb, #0b6edc 5%, var(--pd-surface-elevated));
}

.ai-staging-field-diff__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-staging-field-diff__title {
  font-size: 12px;
  font-weight: 700;
  color: #1d4ed8;
}

.ai-staging-field-diff__subtitle {
  font-size: 11px;
  color: var(--pd-text-muted);
}

.ai-staging-field-diff__delete-hint {
  margin: 0;
  font-size: 11px;
  color: #b91c1c;
  line-height: 1.45;
}

.ai-staging-field-diff__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;

  th {
    text-align: left;
    font-weight: 600;
    color: var(--pd-text-muted);
    padding: 0 6px 6px 0;
  }

  td {
    vertical-align: top;
    padding: 4px 6px 4px 0;
  }
}

.ai-staging-field-diff__col-field {
  width: 22%;
}

.ai-staging-field-diff__field {
  font-weight: 600;
  color: var(--pd-text);
  word-break: break-word;
}

.ai-staging-field-diff__baseline {
  color: var(--pd-text-muted);
  max-width: 34%;
  word-break: break-all;
}

.ai-staging-field-diff__pre,
.ai-staging-field-diff__input--multiline {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  line-height: 1.4;
}

.ai-staging-field-diff__pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.ai-staging-field-diff__input {
  width: 100%;
  padding: 5px 7px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-subtle);
  background: #fff;
  font-size: 11px;
  font-family: inherit;
  color: var(--pd-text);
  box-sizing: border-box;

  &:focus {
    outline: none;
    border-color: #0b6edc;
    box-shadow: 0 0 0 1px #0b6edc inset;
  }

  &--multiline {
    min-height: 96px;
    resize: vertical;
  }
}
</style>
