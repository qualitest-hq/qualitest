<template>
  <div class="debug-kv-sheet">
    <div class="debug-kv-scroll">
      <div class="debug-kv-head">
        <div class="debug-kv-cell debug-kv-cell--muted" />
        <div class="debug-kv-cell debug-kv-cell--muted">{{ labels.name }}</div>
        <div class="debug-kv-cell debug-kv-cell--muted">{{ labels.value }}</div>
        <div class="debug-kv-cell debug-kv-cell--muted" />
      </div>
      <div v-for="(row, idx) in rows" :key="idx" class="debug-kv-row">
        <div class="debug-kv-cell">
          <input v-model="row._enabled" class="debug-kv-check" type="checkbox" />
        </div>
        <div class="debug-kv-cell">
          <input v-model="row.name" :placeholder="labels.namePh" class="debug-kv-field-input" type="text" />
        </div>
        <div class="debug-kv-cell">
          <input
              v-model="row.value"
              :placeholder="labels.valuePh"
              class="debug-kv-field-input"
              type="text"
          />
        </div>
        <div class="debug-kv-cell">
          <button
              :disabled="rows.length <= 1"
              class="debug-kv-del"
              title="删除行"
              type="button"
              @click="removeRow(idx)"
          >
            ×
          </button>
        </div>
      </div>
    </div>
    <button class="btn btn--ghost debug-kv-add-btn" type="button" @click="addRow">+ 添加行</button>
  </div>
</template>

<script setup>
/** HTTP 配置弹窗内的键值表（Headers / Query / Path 等） */
import { computed } from 'vue'

import { emptyKVRow, ensureTrailingEmptyRow, KV_SHEET_LABELS } from '../utils/httpWorkbenchUtils'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  labelKey: { type: String, default: 'query' },
})

const emit = defineEmits(['update:modelValue'])

const labels = computed(() => KV_SHEET_LABELS[props.labelKey] || KV_SHEET_LABELS.query)

const rows = computed({
  get() {
    return props.modelValue?.length ? props.modelValue : [emptyKVRow()]
  },
  set(val) {
    emit('update:modelValue', val)
  },
})

function addRow() {
  const next = [...rows.value, emptyKVRow()]
  emit('update:modelValue', next)
}

function removeRow(idx) {
  const next = rows.value.slice()
  next.splice(idx, 1)
  ensureTrailingEmptyRow(next)
  emit('update:modelValue', next.length ? next : [emptyKVRow()])
}
</script>

<style scoped lang="scss">
.debug-kv-sheet {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
}

.debug-kv-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
  }
}

.debug-kv-head,
.debug-kv-row {
  display: grid;
  grid-template-columns: 28px 1fr 1fr 28px;
  gap: 6px;
  align-items: center;
}

.debug-kv-head {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 0 0 5px;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
  margin-bottom: 2px;
  background: var(--pd-surface, #fbfdff);
}

.debug-kv-row {
  padding: 3px 0;
}

.debug-kv-cell--muted {
  font-size: 10px;
  color: var(--pd-text-muted, #5a6b86);
  font-weight: 600;
}

.debug-kv-check {
  width: 14px;
  height: 14px;
  margin: 0;
  accent-color: var(--pd-primary, #0b6edc);
  cursor: pointer;
}

.debug-kv-field-input {
  width: 100%;
  height: 28px;
  padding: 0 7px;
  border: 1px solid var(--pd-border-subtle, #c5d8ec);
  border-radius: 6px;
  background: var(--pd-surface-elevated, #fff);
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--pd-text, #0f172a);
  box-sizing: border-box;
  transition: border-color 0.15s, box-shadow 0.15s;

  &::placeholder {
    color: var(--pd-text-muted, #5a6b86);
    font-family: inherit;
    font-size: 11px;
  }

  &:focus {
    outline: none;
    border-color: var(--pd-primary, #0b6edc);
    box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
  }
}

.debug-kv-del {
  width: 26px;
  height: 26px;
  border: 1px solid var(--pd-border-subtle, #c5d8ec);
  border-radius: 6px;
  background: var(--pd-surface-elevated, #fff);
  color: var(--pd-text-muted, #5a6b86);
  font-size: 15px;
  line-height: 1;
  padding: 0;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;

  &:hover:not(:disabled) {
    border-color: #ef4444;
    color: #ef4444;
    background: #fef2f2;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.debug-kv-add-btn {
  margin-top: 8px;
  height: 26px;
  font-size: 11px;
  padding: 0 9px;
  flex-shrink: 0;
}
</style>
