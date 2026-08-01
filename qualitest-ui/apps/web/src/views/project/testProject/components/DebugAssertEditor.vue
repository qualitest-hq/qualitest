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
          placeholder="http.body.data.code / $.data.items[?(@.id==1)]"
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
      <div v-if="trialPreview(row.left)" class="debug-assert-trial">
        {{ trialPreview(row.left) }}
      </div>
    </div>
    <p class="debug-assert-hint">
      多条规则为 AND；左值用 http.body…（可用过滤器）；extract 方言 $.… 会规范为 http.body…
      <template v-if="!hasTrialBody"> · 选中一次含 HTTP 响应的 Run 后可试算左值</template>
    </p>
  </div>
</template>

<script setup>
/**
 * assert / condition 的 rules（或 conditions）列表编辑器。
 * 支持运算符下拉；有 trialBody 时对 http.body / $ 左值即时试算。
 */
import { computed } from 'vue'
import { COND_OPERATORS, emptyCompareRule } from '@/utils/flow/compareRule'
import { previewAssertLeft } from '@/views/project/testFlow/utils/jsonPathTrial'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** 最近一次 HTTP 响应 body，用于左值试算 */
  trialBody: { type: [Object, Array, String, Number, Boolean], default: undefined },
})

const emit = defineEmits(['update:modelValue'])

const hasTrialBody = computed(() => props.trialBody !== undefined && props.trialBody !== null)

function addRow() {
  emit('update:modelValue', [...props.modelValue, emptyCompareRule()])
}

function removeRow(index) {
  const next = props.modelValue.slice()
  next.splice(index, 1)
  emit('update:modelValue', next.length ? next : [emptyCompareRule()])
}

/** 当前行 left 的试算文案；无结果时为空 */
function trialPreview(left) {
  return previewAssertLeft(props.trialBody, left)
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

.debug-assert-trial {
  flex: 1 1 100%;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--el-color-primary);
  word-break: break-all;
}

.debug-assert-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
</style>
