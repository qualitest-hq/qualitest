<template>
  <div class="debug-extract-editor">
    <div class="debug-extract-head">
      <span>提取项（{{ modelValue.length }}）</span>
      <el-button link type="primary" @click="addRow">+ 添加</el-button>
    </div>
    <div v-if="!modelValue.length" class="debug-extract-empty">
      从本步骤 HTTP 响应提取变量，写入 flow / env / asset
    </div>
    <div v-for="(row, index) in modelValue" :key="index" class="debug-extract-row">
      <div class="debug-extract-row__toolbar">
        <el-select v-model="row.scope" class="debug-extract-scope" @change="onScopeChange(row)">
          <el-option v-for="s in EXTRACT_SCOPES" :key="s.value" :label="s.label" :value="s.value"/>
        </el-select>
        <el-select v-model="row.from" class="debug-extract-from">
          <el-option v-for="o in EXTRACT_FROM_OPTIONS" :key="o.value" :label="o.label" :value="o.value"/>
        </el-select>
        <el-button
            :disabled="modelValue.length <= 1"
            class="debug-extract-del"
            link
            type="danger"
            @click="removeRow(index)"
        >删除
        </el-button>
      </div>
      <el-input
          v-model="row.expr"
          :placeholder="exprPlaceholder(row.from)"
          class="debug-extract-expr"
          clearable
      />
      <div :class="['debug-extract-row__dest', isSimpleScope(row.scope) ? 'is-single' : 'is-pair']">
        <template v-if="isSimpleScope(row.scope)">
          <el-input v-model="row.name" clearable placeholder="变量名"/>
        </template>
        <template v-else>
          <el-input v-model="row.entryKey" clearable placeholder="素材 key"/>
          <el-input v-model="row.fieldPath" clearable placeholder="字段路径"/>
        </template>
      </div>
    </div>
    <p class="debug-extract-hint">flow → {{flowHint}} · env/asset 写入后可用 {{scopeHint}}</p>
  </div>
</template>

<script setup>
/** http 节点 data.extracts[] 列表编辑器，供测试流画布属性面板使用 */
import { EXTRACT_FROM_OPTIONS, EXTRACT_SCOPES, emptyExtractTarget } from '@/utils/flow/extract'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** 为 false 时不自动塞入空行（测试流画布：无提取项即显示空态） */
  autoSeedRow: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue'])

const flowHint = '{{flow.name}}'
const scopeHint = '{{env.key}} / {{asset.key.field}}'

function isSimpleScope(scope) {
  return scope === 'flow' || scope === 'env'
}

function exprPlaceholder(from) {
  if (from === 'body') return '$.data.token'
  if (from === 'header') return 'X-Request-Id'
  if (from === 'status') return '（无需填写）'
  if (from === 'regex') return '正则（暂未支持执行）'
  return ''
}

function onScopeChange(row) {
  if (row.scope === 'asset') {
    row.name = row.name || ''
  }
}

function addRow() {
  emit('update:modelValue', [...props.modelValue, emptyExtractTarget()])
}

function removeRow(index) {
  const next = props.modelValue.slice()
  next.splice(index, 1)
  if (!next.length) {
    emit('update:modelValue', props.autoSeedRow ? [emptyExtractTarget()] : [])
    return
  }
  emit('update:modelValue', next)
}

if (!props.modelValue.length && props.autoSeedRow) {
  emit('update:modelValue', [emptyExtractTarget()])
}
</script>

<style scoped>
.debug-extract-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.debug-extract-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.debug-extract-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.debug-extract-row__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.debug-extract-scope {
  width: 118px;
  flex-shrink: 0;
}

.debug-extract-from {
  flex: 1;
  min-width: 0;
}

.debug-extract-expr {
  width: 100%;
}

.debug-extract-row__dest {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.debug-extract-row__dest.is-single {
  grid-template-columns: 1fr;
}

.debug-extract-row__dest.is-pair {
  grid-template-columns: 1fr 1fr;
}

.debug-extract-del {
  flex-shrink: 0;
  margin-left: auto;
}

.debug-extract-empty,
.debug-extract-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0;
  line-height: 1.45;
}
</style>
