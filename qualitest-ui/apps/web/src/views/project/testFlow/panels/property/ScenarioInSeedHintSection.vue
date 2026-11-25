<template>
  <div class="scenario-inseed-hint">
    <div class="field">
      <label>入参键提示 inSeedKeys（可选）</label>
      <div class="field__hint">只声明场景建议填写的键名；具体测值在右侧场景 inSeed 里填。缺值不阻止开跑</div>
    </div>
    <div
        v-for="(row, index) in rows"
        :key="index"
        class="scenario-inseed-hint__row"
    >
      <input
          :value="row.name"
          placeholder="键名"
          type="text"
          @input="patch(index, { name: ($event.target).value })"
      />
      <select
          :value="normalizeType(row.type)"
          @change="patch(index, { type: ($event.target).value })"
      >
        <option v-for="t in KEY_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
      </select>
      <label class="scenario-inseed-hint__req">
        <input
            :checked="!!row.required"
            type="checkbox"
            @change="patch(index, { required: ($event.target).checked })"
        />
        必填
      </label>
      <button type="button" class="scenario-inseed-hint__del" title="删除" @click="remove(index)">×</button>
    </div>
    <button type="button" class="btn btn--ghost" @click="add">+ 键提示</button>
  </div>
</template>

<script setup>
/**
 * 场景入口属性：编辑可选 data.inSeedKeys。
 * 扁平数组 [{ name, type?, required? }]，只提示各场景 inSeed 宜填哪些键，不存测值。
 * 跑前场景面板会按此列表补空行，并把 required=true 的键标 *；缺值不拦开跑。
 */
import { computed } from 'vue'

/** 键提示可用的类型下拉 */
const KEY_TYPES = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
  { value: 'json', label: 'JSON' },
]

const props = defineProps({
  /** inSeedKeys 数组；非数组时按空列表展示 */
  modelValue: { type: Array, default: null },
})

const emit = defineEmits(['update:modelValue'])

const rows = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []))

/** 规范化 type：别名 text→string；未知回退 string */
function normalizeType(type) {
  const s = String(type || 'string').trim().toLowerCase()
  if (s === 'text') return 'string'
  return KEY_TYPES.some((t) => t.value === s) ? s : 'string'
}

/** 浅拷贝行列表，避免直接改 props */
function clone() {
  return rows.value.map((r) => ({ ...(r || {}) }))
}

function emitRows(next) {
  emit('update:modelValue', next)
}

/** 追加空提示行 */
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

.scenario-inseed-hint__row {
  display: grid;
  grid-template-columns: 1fr 100px auto 28px;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.scenario-inseed-hint__req {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.scenario-inseed-hint__del {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary, #666);
}
</style>
