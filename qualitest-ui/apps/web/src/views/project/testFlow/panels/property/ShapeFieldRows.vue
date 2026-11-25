<template>
  <div class="shape-field-rows" :style="{ marginLeft: depth ? `${depth * 12}px` : '0' }">
    <div v-for="(row, index) in localRows" :key="rowKey(row, index)" class="shape-field-rows__item">
      <div class="shape-field-rows__row" :class="{ 'shape-field-rows__row--nestable': nestable }">
        <input
            :value="row.name"
            placeholder="字段名"
            type="text"
            @input="patchRow(index, { name: ($event.target).value })"
        />
        <select
            :value="normalizeShapeType(row.type)"
            @change="onType(index, ($event.target).value)"
        >
          <option v-for="t in SHAPE_FIELD_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <label class="shape-field-rows__req">
          <input
              :checked="!!row.required"
              type="checkbox"
              @change="patchRow(index, { required: ($event.target).checked })"
          />
          必填
        </label>
        <!-- nestable 时预留「+子」列，无按钮也占位，避免删列错位 -->
        <span v-if="nestable" class="shape-field-rows__child-slot">
          <button
              v-if="canHaveChildren(row.type)"
              type="button"
              class="btn btn--ghost shape-field-rows__child-btn"
              title="添加子字段"
              @click="addChild(index)"
          >
            +子
          </button>
        </span>
        <button type="button" class="shape-field-rows__del" title="删除" @click="removeRow(index)">×</button>
      </div>
      <!-- 有 children 时递归渲染下一层，缩进由 depth 控制 -->
      <ShapeFieldRows
          v-if="nestable && canHaveChildren(row.type) && Array.isArray(row.children) && row.children.length"
          :rows="row.children"
          :depth="depth + 1"
          nestable
          @change="(kids) => patchRow(index, { children: kids })"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * inputShape 字段行编辑器。
 * nestable=true：用于 body，可给 object/json 增删 children 并递归编辑。
 * nestable=false：用于 query/headers，只编辑一层 name/type/required。
 */
import { computed } from 'vue'

import { SHAPE_FIELD_TYPES, canHaveChildren, normalizeShapeType } from './shapeFieldTypes'

const props = defineProps({
  /** 当前层字段数组 */
  rows: { type: Array, default: () => [] },
  /** 嵌套深度，用于左边距缩进 */
  depth: { type: Number, default: 0 },
  /** 是否允许挂 children 并显示「+子」 */
  nestable: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

const localRows = computed(() => (Array.isArray(props.rows) ? props.rows : []))

function rowKey(row, index) {
  return `${props.depth}-${index}-${row?.name ?? ''}`
}

function emitRows(next) {
  emit('change', next)
}

/** 深拷贝当前层，避免就地改 props */
function cloneRows() {
  try {
    return structuredClone(localRows.value)
  } catch {
    return JSON.parse(JSON.stringify(localRows.value || []))
  }
}

/** 合并改某一行后整层回写 */
function patchRow(index, patch) {
  const next = cloneRows()
  next[index] = { ...(next[index] || {}), ...patch }
  emitRows(next)
}

/**
 * 改类型：不可嵌套时清掉 children；可嵌套且尚无 children 时补空数组。
 */
function onType(index, type) {
  const next = cloneRows()
  const row = { ...(next[index] || {}), type }
  if (!canHaveChildren(type)) {
    delete row.children
  } else if (!Array.isArray(row.children)) {
    row.children = []
  }
  next[index] = row
  emitRows(next)
}

/**
 * 给当前行追加一个空子字段；若当前类型不可嵌套则自动改成 object。
 */
function addChild(index) {
  const next = cloneRows()
  const row = { ...(next[index] || {}) }
  const kids = Array.isArray(row.children) ? [...row.children] : []
  kids.push({ name: '', type: 'text', required: false })
  row.children = kids
  if (!canHaveChildren(row.type)) {
    row.type = 'object'
  }
  next[index] = row
  emitRows(next)
}

function removeRow(index) {
  emitRows(localRows.value.filter((_, i) => i !== index))
}
</script>

<style scoped lang="scss">
.shape-field-rows__row {
  display: grid;
  grid-template-columns: 1fr 88px auto 28px;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;

  &--nestable {
    grid-template-columns: 1fr 88px auto auto 28px;
  }
}

.shape-field-rows__req {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.shape-field-rows__child-btn {
  padding: 2px 6px;
  font-size: 12px;
}

.shape-field-rows__del {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary, #666);
}
</style>
