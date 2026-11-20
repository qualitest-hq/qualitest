<template>
  <div class="assign-editor">
    <div class="assign-presets">
      <button
          v-for="p in ASSIGN_PRESETS"
          :key="p.id"
          class="btn btn--ghost"
          type="button"
          @click="applyPreset(p.assignment)"
      >
        {{ p.label }}
      </button>
    </div>

    <div
        v-for="(row, index) in rows"
        :key="index"
        class="assign-row"
    >
      <div :class="['assign-row__grid', `assign-row__grid--${assignOpCategory(row.op)}`]">
        <select
            :value="normalizeAssignScope(row.scope)"
            title="作用域"
            @change="onScopeChange(index, $event)"
        >
          <option v-for="s in ASSIGN_SCOPES" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
        <input
            :value="row.name"
            :placeholder="normalizeAssignScope(row.scope) === 'out' ? 'body / status' : 'pollAttempt'"
            type="text"
            @input="onTextInput(index, 'name', $event)"
        />
        <select
            :disabled="normalizeAssignScope(row.scope) === 'out'"
            :value="row.op"
            @change="onOpChange(index, $event)"
        >
          <option v-for="op in ASSIGN_OPS" :key="op.value" :value="op.value">{{ op.label }}</option>
        </select>
        <template v-if="assignOpCategory(row.op) === 'value'">
          <textarea
              v-if="normalizeAssignScope(row.scope) === 'out' || isStructuredValue(row.value)"
              :value="valueAsEditorText(row.value)"
              class="assign-row__struct"
              :placeholder="outValuePlaceholder"
              rows="4"
              @change="onStructValueChange(index, $event)"
          />
          <input
              v-else
              :value="row.value"
              :placeholder="valuePlaceholder"
              type="text"
              @input="onTextInput(index, 'value', $event)"
          />
        </template>
        <template v-else>
          <input
              :value="row.step"
              placeholder="步长"
              title="步长"
              type="number"
              @input="onNumberInput(index, 'step', $event, 1)"
          />
          <input
              :value="row.ifMissing"
              placeholder="缺省"
              title="变量不存在时视为"
              type="number"
              @input="onNumberInput(index, 'ifMissing', $event, 0)"
          />
        </template>
        <FlowRowRemoveButton title="删除" @click="removeRow(index)" />
      </div>
      <div class="field__hint">{{ rowHint(row) }}</div>
    </div>

    <button class="btn btn--ghost assign-add" type="button" @click="addRow">+ 添加赋值</button>
    <div class="field__hint">作用域 flow（中间量）或 out（开放入站回包）；出参建议一次 set 含 status/headers/body</div>

    <SnapshotPropertySection :node="node" />
    <AlwaysRunPropertySection :node="node" />
  </div>
</template>

<script setup>
/**
 * Assign 节点属性区。
 * 编辑 assignments：作用域 flow（中间量）或 out（出参）、运算符、标量或 JSON 结构 value。
 * 选 out 时引导写 body/status，value 可用多行 JSON，叶子支持 {{in.x}}。
 */
import { computed, onMounted } from 'vue'

import {
  assignOpCategory,
  ASSIGN_OPS,
  ASSIGN_SCOPES,
  defaultAssignments,
  emptyAssignment,
  normalizeAssignNodeData,
  normalizeAssignScope,
} from '@/utils/flow/assign'

import { ASSIGN_PRESETS } from '../../constants/nodeTypes'
import { useFlowNodes } from '../../composables/useFlowNodes'
import FlowRowRemoveButton from '../../components/FlowRowRemoveButton.vue'
import AlwaysRunPropertySection from './AlwaysRunPropertySection.vue'
import SnapshotPropertySection from './SnapshotPropertySection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

const valuePlaceholder = '0 / {{flow.x}} / {{in.x}}'
const outValuePlaceholder = '{\n  "code": "SUCCESS",\n  "echo": "{{in.orderId}}"\n}'

onMounted(() => {
  const list = props.node?.data?.assignments
  if (!Array.isArray(list) || !list.length) {
    const data = { assignments: defaultAssignments() }
    normalizeAssignNodeData(data)
    patchNodeData(props.node.id, { assignments: data.assignments })
  }
})

function isStructuredValue(value) {
  return value != null && typeof value === 'object'
}

function valueAsEditorText(value) {
  if (value != null && typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return ''
    }
  }
  return value != null ? String(value) : ''
}

function rowHint(row) {
  if (normalizeAssignScope(row.scope) === 'out') {
    return '出参 out：建议 name=body（或 status/headers）；value 可为 JSON 结构，叶子支持 {{in.x}}'
  }
  return assignOpCategory(row.op) === 'step'
    ? '步长可为数字；缺省为变量不存在时的起始值'
    : '支持字面量、{{in.x}} / {{flow.x}}；结构值请先选作用域 out 或粘贴 JSON'
}

const rows = computed(() => {
  const list = props.node?.data?.assignments || []
  return list.length ? list : [emptyAssignment()]
})

function commitAssignments(assignments) {
  const normalized = assignments.map((a) => ({ ...a }))
  const data = { assignments: normalized }
  normalizeAssignNodeData(data)
  patchNodeData(props.node.id, { assignments: data.assignments })
}

function onTextInput(index, field, event) {
  onFieldInput(index, field, event.target.value)
}

function onNumberInput(index, field, event, fallback) {
  onFieldInput(index, field, Number(event.target.value) || fallback)
}

function onFieldInput(index, field, value) {
  const next = rows.value.map((r, i) => (i === index ? { ...r, [field]: value } : { ...r }))
  commitAssignments(next)
}

function onScopeChange(index, event) {
  const scope = event.target.value
  const row = { ...rows.value[index], scope }
  if (scope === 'out') {
    row.op = 'set'
    delete row.step
    delete row.ifMissing
    if (row.name == null || String(row.name).trim() === '') {
      row.name = 'body'
    }
    if (row.value == null || row.value === '') {
      row.value = { code: 'SUCCESS' }
    }
  }
  const next = rows.value.map((r, i) => (i === index ? row : { ...r }))
  commitAssignments(next)
}

function onStructValueChange(index, event) {
  const text = event.target.value
  let value = text
  try {
    value = JSON.parse(text)
  } catch {
    // 保留原文，便于继续编辑
  }
  onFieldInput(index, 'value', value)
}

function onOpChange(index, event) {
  const op = event.target.value
  const row = { ...rows.value[index], op }
  const category = assignOpCategory(op)
  if (category === 'step') {
    row.step = row.step != null ? row.step : 1
    row.ifMissing = row.ifMissing != null ? row.ifMissing : 0
    delete row.value
  } else {
    row.value = row.value != null ? row.value : ''
    delete row.step
    delete row.ifMissing
  }
  const next = rows.value.map((r, i) => (i === index ? row : { ...r }))
  commitAssignments(next)
}

function addRow() {
  commitAssignments([...rows.value.map((r) => ({ ...r })), emptyAssignment()])
}

function removeRow(index) {
  if (rows.value.length <= 1) return
  const next = rows.value.filter((_, i) => i !== index).map((r) => ({ ...r }))
  commitAssignments(next)
}

function applyPreset(assignment) {
  commitAssignments([...rows.value.map((r) => ({ ...r })), { ...assignment }])
}
</script>

<style scoped lang="scss">
.assign-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assign-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  :deep(.btn) {
    height: 28px;
    font-size: 11px;
    padding: 0 10px;
  }
}

.assign-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--pd-divider);

  &:last-of-type {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.assign-row__grid {
  display: grid;
  gap: 6px;
  align-items: start;

  &--value {
    grid-template-columns: 64px 1fr 56px 1fr 28px;
  }

  &--step {
    grid-template-columns: 64px 1fr 56px 72px 72px 28px;
  }

  input,
  select,
  textarea {
    height: 30px;
    padding: 0 8px;
    border: 1px solid var(--pd-border-subtle);
    border-radius: 6px;
    font-size: 12px;
    background: var(--pd-surface-elevated);
    color: var(--pd-text);
    min-width: 0;
  }

  textarea.assign-row__struct {
    height: auto;
    min-height: 72px;
    padding: 6px 8px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    grid-column: 4 / 5;
  }
}

.assign-add {
  align-self: flex-start;
  height: 28px;
  font-size: 11px;
}
</style>
