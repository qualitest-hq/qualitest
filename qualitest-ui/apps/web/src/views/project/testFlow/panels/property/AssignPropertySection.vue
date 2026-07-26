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
        <input
            :value="row.name"
            placeholder="pollAttempt"
            type="text"
            @input="onTextInput(index, 'name', $event)"
        />
        <select
            :value="row.op"
            @change="onOpChange(index, $event)"
        >
          <option v-for="op in ASSIGN_OPS" :key="op.value" :value="op.value">{{ op.label }}</option>
        </select>
        <template v-if="assignOpCategory(row.op) === 'value'">
          <input
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
        <button
            class="assign-row__del"
            title="删除"
            type="button"
            @click="removeRow(index)"
        >
          ×
        </button>
      </div>
      <div class="field__hint">{{ rowHint(row.op) }}</div>
    </div>

    <button class="btn btn--ghost assign-add" type="button" @click="addRow">+ 添加赋值</button>
    <div class="field__hint">作用域固定为 flow · 可用于轮询计数、标志位等</div>

    <SnapshotPropertySection :node="node" />
  </div>
</template>

<script setup>
/**
 * Assign 节点属性区：编辑 assignments 列表，支持预设、运算符切换与增删行。
 * 变更经 patchNodeData 写回节点 data 并刷新 summary。
 */
import { computed } from 'vue'

import {
  assignOpCategory,
  ASSIGN_OPS,
  emptyAssignment,
  normalizeAssignNodeData,
} from '@/utils/flow/assign'

import { ASSIGN_PRESETS } from '../../constants/nodeTypes'
import { useFlowNodes } from '../../composables/useFlowNodes'
import SnapshotPropertySection from './SnapshotPropertySection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

const valuePlaceholder = '0 / {{flow.x}}'

/** 按运算符类别返回行内提示文案 */
function rowHint(op) {
  return assignOpCategory(op) === 'step'
    ? '步长可为数字；缺省为变量不存在时的起始值'
    : '支持字面量、{{flow.x}} 或 SpEL 表达式'
}

/** 当前节点的 assignments，至少保留一行供编辑 */
const rows = computed(() => {
  const list = props.node?.data?.assignments || []
  return list.length ? list : [emptyAssignment()]
})

/** 将完整 assignments 写回节点并规范化字段 */
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

/** 切换运算符时按类别补全 value 或 step/ifMissing 字段 */
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
  align-items: center;

  &--value {
    grid-template-columns: 1fr 56px 1fr 28px;
  }

  &--step {
    grid-template-columns: 1fr 56px 72px 72px 28px;
  }

  input,
  select {
    height: 30px;
    padding: 0 8px;
    border: 1px solid var(--pd-border-subtle);
    border-radius: 6px;
    font-size: 12px;
    background: var(--pd-surface-elevated);
    color: var(--pd-text);
    min-width: 0;
  }
}

.assign-row__del {
  width: 28px;
  height: 28px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface);
  color: var(--pd-text-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0;

  &:hover {
    border-color: #ef4444;
    color: #ef4444;
    background: #fef2f2;
  }
}

.assign-add {
  align-self: flex-start;
  height: 28px;
  font-size: 11px;
}
</style>
