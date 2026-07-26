<template>
  <div class="debug-assert-editor">
    <div class="debug-assert-head">
      <span>断言规则（{{ modelValue.length }}）</span>
      <el-button link type="primary" @click="addRow">+ 添加</el-button>
    </div>
    <div v-for="(row, index) in modelValue" :key="index" class="debug-assert-row">
      <el-input
          v-model="row.left"
          class="debug-assert-field debug-assert-left"
          clearable
          placeholder="http.body.data.code / flow.token"
      />
      <el-select v-model="row.operator" class="debug-assert-field debug-assert-op">
        <el-option v-for="o in COND_OPERATORS" :key="o.value" :label="o.label" :value="o.value"/>
      </el-select>
      <el-input
          v-if="row.operator !== 'exists'"
          v-model="row.right"
          class="debug-assert-field debug-assert-right"
          clearable
          placeholder="期望值 / {{flow.x}}"
      />
      <span v-else class="debug-assert-exists-hint">（存在性检查）</span>
      <el-button
          :disabled="modelValue.length <= 1"
          link
          type="danger"
          @click="removeRow(index)"
      >删除
      </el-button>
    </div>
    <p class="debug-assert-hint">多条规则为 AND；断言失败不阻断继续调试</p>
  </div>
</template>

<script setup>
/** assert 节点 data.rules[] 列表编辑器，供测试流画布属性面板使用 */
import { COND_OPERATORS, emptyCompareRule } from '@/utils/flow/compareRule'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue'])

function addRow() {
  emit('update:modelValue', [...props.modelValue, emptyCompareRule()])
}

function removeRow(index) {
  const next = props.modelValue.slice()
  next.splice(index, 1)
  emit('update:modelValue', next.length ? next : [emptyCompareRule()])
}

if (!props.modelValue.length) {
  emit('update:modelValue', [{ left: 'http.body.data.code', operator: 'eq', right: '0' }])
}
</script>

<style scoped>
.debug-assert-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.debug-assert-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.debug-assert-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.debug-assert-field {
  flex: 1 1 140px;
}

.debug-assert-left {
  flex: 2 1 200px;
}

.debug-assert-op {
  max-width: 130px;
  flex: 0 1 130px;
}

.debug-assert-exists-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.debug-assert-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
</style>
