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
          placeholder="http.body.data.code / nodes.<节点ID>.http.body.xxx"
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
      <div
          v-if="trials[index]?.text"
          class="debug-assert-trial"
          :class="{ 'debug-assert-trial--miss': trials[index].miss }"
      >
        {{ trials[index].text }}
        <span v-if="trials[index].miss && looksLikeSchemaItems(row.left)" class="debug-assert-trial-tip">
          · 数组请用 data[0]/data[*]/data[?…]，不要写 JSON Schema 关键字 .items
        </span>
      </div>
    </div>
    <p class="debug-assert-hint">
      多条规则为 AND；左值用 http.body…（最近一次 HTTP）或 nodes.节点ID.http.body…；extract 方言 $.… 会规范为 http.body…
      <template v-if="trialSource === 'run'"> · 试算来源：当前选中 Run 的 HTTP 响应</template>
      <template v-else-if="trialSource === 'api-example'"> · 试算来源：上游接口响应示例</template>
      <template v-else-if="!hasTrialBody"> · 绑定上游接口或选中含 HTTP 响应的 Run 后可试算左值</template>
    </p>
  </div>
</template>

<script setup>
/**
 * 断言 / 条件规则列表编辑器。
 *
 * - 编辑 left / operator / right；exists 时隐藏右值
 * - 有 trialBody（或 resolveNodeBody）时对 http.body… / nodes.<id>.http.body… 即时试算
 * - 未命中（空数组、路径未命中、解析失败）标红；左值误含 .items 时追加写法提示
 * - trialSource 标明试算数据来自 Run 还是接口响应示例
 */
import { computed } from 'vue'
import { COND_OPERATORS, emptyCompareRule } from '@/utils/flow/compareRule'
import {
  containsJsonSchemaItemsSegment,
  isTrialMissPreview,
  previewAssertLeft,
} from '@/views/project/testFlow/utils/jsonPathTrial'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** 默认试算用响应体：选中 Run 的最近 HTTP body，或接口 responseConfig.example */
  trialBody: { type: [Object, Array, String, Number, Boolean], default: undefined },
  /** 试算数据来源文案：run | api-example | null */
  trialSource: { type: String, default: null },
  /** 按画布节点 id 取该节点在 Run 中的 HTTP body；nodes.<id>.http… 左值优先用此函数 */
  resolveNodeBody: { type: Function, default: undefined },
})

const emit = defineEmits(['update:modelValue'])

const hasTrialBody = computed(() => props.trialBody !== undefined && props.trialBody !== null)

/** 每行试算文案与是否未命中；随规则 left、trialBody 变化统一计算 */
const trials = computed(() =>
  (props.modelValue || []).map((row) => {
    const text = previewAssertLeft(props.trialBody, row?.left, {
      resolveNodeBody: props.resolveNodeBody,
    })
    return { text, miss: isTrialMissPreview(text) }
  }),
)

function addRow() {
  emit('update:modelValue', [...props.modelValue, emptyCompareRule()])
}

function removeRow(index) {
  const next = props.modelValue.slice()
  next.splice(index, 1)
  emit('update:modelValue', next.length ? next : [emptyCompareRule()])
}

/** 左值路径是否误含 JSON Schema 数组关键字 items（如 data.items.qty） */
function looksLikeSchemaItems(left) {
  return containsJsonSchemaItemsSegment(String(left ?? ''))
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

/* 试算未命中：空数组、路径未命中、解析失败 */
.debug-assert-trial--miss {
  color: var(--el-color-danger);
}

.debug-assert-trial-tip {
  display: inline;
  font-family: inherit;
}

.debug-assert-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
</style>
