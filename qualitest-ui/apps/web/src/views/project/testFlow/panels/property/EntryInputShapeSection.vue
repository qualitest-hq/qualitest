<template>
  <div class="entry-input-shape">
    <div class="field">
      <label>入参形状 inputShape（无值声明）</label>
      <div class="field__hint">body / query / headers 字段名、类型、是否必填；开放 HTTP 不符时平台 400</div>
    </div>
    <div v-for="sec in sections" :key="sec.key" class="entry-input-shape__block">
      <div class="entry-input-shape__title">{{ sec.label }}</div>
      <div
          v-for="(row, index) in rows(sec.key)"
          :key="`${sec.key}-${index}`"
          class="entry-input-shape__row"
      >
        <input
            :value="row.name"
            placeholder="字段名"
            type="text"
            @input="onFieldText(sec.key, index, 'name', $event)"
        />
        <select :value="normalizeInputFieldType(row.type)" @change="onFieldType(sec.key, index, $event)">
          <option v-for="t in INPUT_FIELD_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <label class="entry-input-shape__req">
          <input
              :checked="!!row.required"
              type="checkbox"
              @change="onFieldRequired(sec.key, index, $event)"
          />
          必填
        </label>
        <button type="button" class="entry-input-shape__del" title="删除" @click="removeRow(sec.key, index)">×</button>
      </div>
      <button type="button" class="btn btn--ghost" @click="addRow(sec.key)">+ {{ sec.label }} 字段</button>
    </div>
  </div>
</template>

<script setup>
/**
 * 入口入参形状编辑区：分别维护 Body / Query / Headers 三组字段声明，
 * 每个字段包含名称、类型、是否必填；只声明结构，不填具体值。
 */
import { computed } from 'vue'

import {
  INPUT_FIELD_TYPES,
  normalizeInputFieldType,
} from '@/utils/flow/inputFields'

const props = defineProps({
  /** 当前入参形状：{ body: [], query: [], headers: [] }，每项为 { name, type, required } */
  modelValue: { type: Object, default: null },
})

/** 字段表变化时抛出完整的新入参形状 */
const emit = defineEmits(['update:modelValue'])

/** 可编辑的三个字段分组：key 对应入参形状属性名，label 为界面展示名 */
const sections = [
  { key: 'body', label: 'Body' },
  { key: 'query', label: 'Query' },
  { key: 'headers', label: 'Headers' },
]

/** 规范化后的当前入参形状；未传值或类型不对时返回三组均为空的默认结构 */
const shape = computed(() => {
  const v = props.modelValue
  if (v && typeof v === 'object') return v
  return { body: [], query: [], headers: [] }
})

/** 取指定分组的字段行列表，非数组时按空列表处理 */
function rows(key) {
  const list = shape.value[key]
  return Array.isArray(list) ? list : []
}

/** 向父组件提交新的入参形状 */
function emitShape(next) {
  emit('update:modelValue', next)
}

/** 复制当前三组字段后交给 mutator 修改，再整体提交，避免直接改动原对象 */
function patchShape(mutator) {
  const base = { body: [], query: [], headers: [] }
  const cur = shape.value
  base.body = Array.isArray(cur.body) ? [...cur.body] : []
  base.query = Array.isArray(cur.query) ? [...cur.query] : []
  base.headers = Array.isArray(cur.headers) ? [...cur.headers] : []
  mutator(base)
  emitShape(base)
}

/** 在指定分组末尾新增一个空字段（默认文本类型、非必填） */
function addRow(key) {
  patchShape((s) => {
    s[key] = [...s[key], { name: '', type: 'text', required: false }]
  })
}

/** 删除指定分组中第 index 行字段 */
function removeRow(key, index) {
  patchShape((s) => {
    s[key] = s[key].filter((_, i) => i !== index)
  })
}

/** 文本输入框变化：把输入值写入指定分组、指定行的 field 属性（如字段名） */
function onFieldText(key, index, field, e) {
  const val = e.target.value
  patchShape((s) => {
    const row = { ...(s[key][index] || {}) }
    row[field] = val
    s[key] = s[key].map((r, i) => (i === index ? row : r))
  })
}

/** 类型下拉框变化：更新指定行的字段类型 */
function onFieldType(key, index, e) {
  const val = e.target.value
  patchShape((s) => {
    const row = { ...(s[key][index] || {}) }
    row.type = val
    s[key] = s[key].map((r, i) => (i === index ? row : r))
  })
}

/** 必填复选框变化：更新指定行的是否必填 */
function onFieldRequired(key, index, e) {
  patchShape((s) => {
    const row = { ...(s[key][index] || {}) }
    row.required = e.target.checked
    s[key] = s[key].map((r, i) => (i === index ? row : r))
  })
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;

.entry-input-shape__block {
  margin-bottom: 12px;
}

.entry-input-shape__title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}

.entry-input-shape__row {
  display: grid;
  grid-template-columns: 1fr 100px auto 28px;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.entry-input-shape__req {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.entry-input-shape__del {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary, #666);
}
</style>