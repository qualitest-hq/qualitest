<template>
  <div class="input-editor">
    <div class="field">
      <label>提示文案</label>
      <input
          :value="node.data.prompt ?? ''"
          placeholder="请填写后继续"
          type="text"
          @input="onPromptInput"
      />
    </div>

    <div
        v-for="(row, index) in rows"
        :key="index"
        class="input-field-row"
    >
      <div class="input-field-row__grid">
        <input
            :value="row.name"
            placeholder="变量名 captchaCode"
            title="写入 flow 的变量名"
            type="text"
            @input="onFieldText(index, 'name', $event)"
        />
        <input
            :value="row.label"
            placeholder="标签"
            type="text"
            @input="onFieldText(index, 'label', $event)"
        />
        <select :value="normalizeInputFieldType(row.type)" @change="onFieldType(index, $event)">
          <option v-for="t in INPUT_FIELD_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <label class="input-field-row__req">
          <input
              :checked="!!row.required"
              type="checkbox"
              @change="onFieldRequired(index, $event)"
          />
          必填
        </label>
        <button class="input-field-row__del" title="删除" type="button" @click="removeRow(index)">×</button>
      </div>
      <div class="input-field-row__extra">
        <input
            :value="row.placeholder ?? ''"
            placeholder="placeholder"
            type="text"
            @input="onFieldText(index, 'placeholder', $event)"
        />
        <input
            v-if="normalizeInputFieldType(row.type) !== 'boolean' && !requiresOptions(row.type)"
            :value="defaultValueText(row)"
            placeholder="默认值"
            type="text"
            @input="onFieldDefault(index, $event)"
        />
      </div>
      <div v-if="requiresOptions(row.type)" class="input-field-row__options">
        <div
            v-for="(opt, oi) in row.options || []"
            :key="oi"
            class="input-option-row"
        >
          <input
              :value="opt.label"
              placeholder="选项标签"
              type="text"
              @input="onOptionText(index, oi, 'label', $event)"
          />
          <input
              :value="opt.value"
              placeholder="value"
              type="text"
              @input="onOptionText(index, oi, 'value', $event)"
          />
          <button class="input-field-row__del" type="button" @click="removeOption(index, oi)">×</button>
        </div>
        <button class="btn btn--ghost" type="button" @click="addOption(index)">+ 选项</button>
      </div>
    </div>

    <button class="btn btn--ghost" type="button" @click="addRow">+ 添加字段</button>
    <div class="field__hint">运行时写入 flow.*；验证码等可从上一步 HTTP 看图后在此填写</div>
  </div>
</template>

<script setup>
/**
 * Input 节点属性区。
 * 编辑 data.prompt，以及 data.fields[]（name / label / type / required / placeholder / defaultValue / options）。
 * 变更经 patchNodeData 写回并刷新 summary。
 */
import { computed, onMounted } from 'vue'

import {
  defaultInputFields,
  emptyInputField,
  INPUT_FIELD_TYPES,
  normalizeInputFieldType,
  requiresOptions,
} from '@/utils/flow/inputFields'

import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

onMounted(() => {
  const list = props.node?.data?.fields
  if (!Array.isArray(list) || !list.length) {
    patchNodeData(props.node.id, { fields: defaultInputFields() })
  }
})

const rows = computed(() => {
  const list = props.node?.data?.fields
  return Array.isArray(list) && list.length ? list : [emptyInputField()]
})

function defaultValueText(row) {
  const v = row?.defaultValue
  if (v == null) return ''
  if (Array.isArray(v)) return v.join(',')
  return String(v)
}

function commit(fields) {
  patchNodeData(props.node.id, { fields })
}

function cloneRows() {
  return rows.value.map((r) => ({
    ...r,
    options: Array.isArray(r.options) ? r.options.map((o) => ({ ...o })) : [],
  }))
}

function onPromptInput(e) {
  patchNodeData(props.node.id, { prompt: e.target.value })
}

function onFieldText(index, key, e) {
  const next = cloneRows()
  next[index] = { ...next[index], [key]: e.target.value }
  commit(next)
}

function onFieldType(index, e) {
  const next = cloneRows()
  const type = normalizeInputFieldType(e.target.value)
  next[index] = {
    ...next[index],
    type,
    options: requiresOptions(type)
      ? (next[index].options?.length ? next[index].options : [{ label: '', value: '' }])
      : [],
  }
  commit(next)
}

function onFieldRequired(index, e) {
  const next = cloneRows()
  next[index] = { ...next[index], required: !!e.target.checked }
  commit(next)
}

function onFieldDefault(index, e) {
  const next = cloneRows()
  next[index] = { ...next[index], defaultValue: e.target.value }
  commit(next)
}

function onOptionText(index, oi, key, e) {
  const next = cloneRows()
  const options = [...(next[index].options || [])]
  options[oi] = { ...options[oi], [key]: e.target.value }
  next[index] = { ...next[index], options }
  commit(next)
}

function addOption(index) {
  const next = cloneRows()
  const options = [...(next[index].options || []), { label: '', value: '' }]
  next[index] = { ...next[index], options }
  commit(next)
}

function removeOption(index, oi) {
  const next = cloneRows()
  const options = [...(next[index].options || [])]
  options.splice(oi, 1)
  next[index] = { ...next[index], options }
  commit(next)
}

function addRow() {
  commit([...cloneRows(), emptyInputField()])
}

function removeRow(index) {
  const next = cloneRows()
  next.splice(index, 1)
  commit(next.length ? next : [emptyInputField()])
}
</script>

<style scoped>
.input-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-field-row {
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter, #e5e7eb);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-field-row__grid {
  display: grid;
  grid-template-columns: 1fr 1fr minmax(90px, auto) auto auto;
  gap: 6px;
  align-items: center;
}

.input-field-row__extra {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.input-field-row__options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.input-option-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 6px;
}

.input-field-row__req {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.input-field-row__del {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #94a3b8;
}

.input-field-row__del:hover {
  color: #dc2626;
}

.input-field-row input,
.input-field-row select {
  min-width: 0;
}
</style>
