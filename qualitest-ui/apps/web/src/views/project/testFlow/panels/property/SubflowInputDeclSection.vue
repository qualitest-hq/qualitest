<template>
  <div class="subflow-input-decl">
    <div class="field">
      <label>入参声明 inputDecl</label>
      <div class="field__hint">扁平键清单，供父流 subflow.inputs 对照填写；无 query/headers 分节</div>
    </div>
    <div
        v-for="(row, index) in rows"
        :key="index"
        class="subflow-input-decl__row"
    >
      <input
          :value="row.name"
          placeholder="字段名"
          type="text"
          @input="patch(index, { name: ($event.target).value })"
      />
      <select
          :value="normalizeDeclType(row.type)"
          @change="patch(index, { type: ($event.target).value })"
      >
        <option v-for="t in DECL_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
      </select>
      <label class="subflow-input-decl__req">
        <input
            :checked="!!row.required"
            type="checkbox"
            @change="patch(index, { required: ($event.target).checked })"
        />
        必填
      </label>
      <button type="button" class="subflow-input-decl__del" title="删除" @click="remove(index)">×</button>
    </div>
    <button type="button" class="btn btn--ghost" @click="add">+ 入参</button>
  </div>
</template>

<script setup>
/**
 * 子流入口属性：编辑 data.inputDecl。
 * 扁平数组 [{ name, type?, required? }]，只声明父侧应传入的键，不存测值。
 * 父流节点用 data.inputs 按 name 灌值到子流 in。
 */
import { computed } from 'vue'

/** 子流声明可用的类型下拉 */
const DECL_TYPES = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
  { value: 'json', label: 'JSON' },
]

const props = defineProps({
  /** inputDecl 数组；非数组时按空列表展示 */
  modelValue: { type: Array, default: null },
})

const emit = defineEmits(['update:modelValue'])

const rows = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []))

/** 规范化 type：别名 text→string；未知回退 string */
function normalizeDeclType(type) {
  const s = String(type || 'string').trim().toLowerCase()
  if (s === 'text') return 'string'
  return DECL_TYPES.some((t) => t.value === s) ? s : 'string'
}

function emitRows(next) {
  emit('update:modelValue', next)
}

/** 浅拷贝行列表，避免直接改 props */
function clone() {
  return rows.value.map((r) => ({ ...(r || {}) }))
}

/** 追加空声明行 */
function add() {
  emitRows([...clone(), { name: '', type: 'string', required: false }])
}

function remove(index) {
  emitRows(clone().filter((_, i) => i !== index))
}

/** 改某一行的部分字段后整表回写 */
function patch(index, p) {
  const next = clone()
  next[index] = { ...(next[index] || {}), ...p }
  emitRows(next)
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;

.subflow-input-decl__row {
  display: grid;
  grid-template-columns: 1fr 100px auto 28px;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.subflow-input-decl__req {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.subflow-input-decl__del {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary, #666);
}
</style>
