<template>
  <div class="http-config-body">
    <div class="body-mode-row">
      <el-radio-group v-model="bodyMode" class="body-mode-group" size="small">
        <el-radio-button v-for="opt in BODY_MODES" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <DebugKvSheet
        v-if="bodyMode === 'urlencoded'"
        v-model="urlencodedRows"
        label-key="urlencoded"
    />

    <template v-else-if="bodyMode === 'json'">
      <div v-if="hasJsonSchema" class="body-json-json-shell">
        <el-tabs v-model="activeJsonTab" class="body-json-inner-tabs">
          <el-tab-pane label="数据结构" lazy name="schema">
            <BodyJsonSchemaTree
                compact
                :show-example-column="false"
                :virtual-min-flat-rows="28"
                :model-value="jsonSchema"
                @update:model-value="setJsonSchema"
            />
          </el-tab-pane>
          <el-tab-pane label="请求示例" lazy name="raw">
            <el-input
                v-model="bodyJsonText"
                :autosize="{ minRows: 12, maxRows: 22 }"
                class="debug-body-raw"
                placeholder="JSON"
                type="textarea"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
      <el-input
          v-else
          v-model="bodyJsonText"
          :autosize="{ minRows: 12, maxRows: 22 }"
          class="debug-body-raw"
          placeholder="JSON"
          type="textarea"
      />
    </template>

    <div v-else class="debug-body-placeholder">
      <p class="debug-body-placeholder-title">Body 为 none</p>
      <p class="debug-body-placeholder-desc">无需请求体或在上方的 mode 中选择类型</p>
    </div>
  </div>
</template>

<script setup>
/** HTTP 配置弹窗 Body 区，交互对齐项目测试面板 ApiDebugTab */
import { computed, ref, watch } from 'vue'

import BodyJsonSchemaTree from '@/views/project/testProject/components/BodyJsonSchemaTree.vue'
import { isEmptyObjectSchema } from '@/views/project/testProject/utils/jsonSchemaTree'

import DebugKvSheet from './DebugKvSheet.vue'
import { ensureBodyShape } from '../utils/httpWorkbenchUtils'

const props = defineProps({
  modelValue: { type: Object, required: true },
})

const emit = defineEmits(['update:modelValue'])

const BODY_MODES = [
  { label: 'none', value: 'none' },
  { label: 'urlencoded', value: 'urlencoded' },
  { label: 'JSON', value: 'json' },
]

const bodyJsonText = ref('')
const activeJsonTab = ref('raw')

const body = computed({
  get() {
    return ensureBodyShape(props.modelValue)
  },
  set(val) {
    emit('update:modelValue', val)
  },
})

const bodyMode = computed({
  get() {
    return body.value.mode || 'none'
  },
  set(mode) {
    emit('update:modelValue', { ...body.value, mode })
  },
})

const urlencodedRows = computed({
  get() {
    return body.value.urlencoded || []
  },
  set(rows) {
    emit('update:modelValue', { ...body.value, urlencoded: rows })
  },
})

const jsonSchema = computed(() => body.value.json?.schema ?? null)

const hasJsonSchema = computed(() => {
  const schema = body.value.json?.schema
  return !!schema && !isEmptyObjectSchema(schema)
})

function setJsonSchema(schema) {
  const json = { ...(body.value.json || {}), schema }
  emit('update:modelValue', { ...body.value, json })
}

function syncBodyJsonTextFromBody() {
  const ex = body.value.json?.example
  if (ex == null || ex === '') {
    bodyJsonText.value = ''
  } else if (typeof ex === 'string') {
    bodyJsonText.value = ex
  } else {
    try {
      bodyJsonText.value = JSON.stringify(ex, null, 2)
    } catch {
      bodyJsonText.value = String(ex)
    }
  }
}

/** 保存前将文本框内容写回 body.json.example；非法 JSON 返回 false */
function applyJsonTextBeforeSave() {
  if (bodyMode.value !== 'json') return true
  const text = bodyJsonText.value.trim()
  const json = { ...(body.value.json || {}), schema: body.value.json?.schema ?? null }
  if (!text) {
    json.example = ''
    emit('update:modelValue', { ...body.value, json })
    return true
  }
  try {
    json.example = JSON.parse(text)
    emit('update:modelValue', { ...body.value, json })
    return true
  } catch {
    return false
  }
}

watch(
  () => props.modelValue,
  () => syncBodyJsonTextFromBody(),
  { immediate: true, deep: true },
)

watch(bodyMode, () => {
  syncBodyJsonTextFromBody()
})

defineExpose({ applyJsonTextBeforeSave })
</script>

<style scoped lang="scss">
.http-config-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;

  :deep(.debug-kv-sheet) {
    flex: 1;
    min-height: 0;
    padding: 6px 6px 0;
  }
}

.body-mode-row {
  flex-shrink: 0;
  margin: 0;
  padding: 4px 6px 6px;
  overflow-x: auto;
  background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.5));
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
}

.body-mode-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  max-width: 100%;
  border: none;
  background: transparent;

  :deep(.el-radio-button) {
    margin: 0 !important;
  }

  :deep(.el-radio-button__inner) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 28px;
    padding: 4px 11px;
    border-radius: 6px !important;
    border: 1px solid var(--pd-border-muted, #d6e6f5) !important;
    box-shadow: none !important;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.3;
    color: var(--pd-text-muted, #5a6b86);
    background: rgba(255, 255, 255, 0.92);
    transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  }

  :deep(.el-radio-button:first-child .el-radio-button__inner),
  :deep(.el-radio-button:last-child .el-radio-button__inner) {
    border-radius: 6px !important;
  }

  :deep(.el-radio-button:not(.is-active):hover .el-radio-button__inner) {
    color: var(--pd-text, #0f172a);
    border-color: var(--pd-border-subtle, #c5d8ec) !important;
    background: var(--pd-surface-elevated, #fff);
  }

  :deep(.el-radio-button.is-active .el-radio-button__inner) {
    color: #fff !important;
    background: var(--pd-primary, #0b6edc) !important;
    border-color: var(--pd-primary, #0b6edc) !important;
  }
}

.body-json-json-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0 6px 6px;
}

.body-json-inner-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;

  :deep(.el-tabs__header) {
    margin: 0 0 4px;
    flex-shrink: 0;
  }

  :deep(.el-tabs__nav-wrap) {
    padding: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    padding: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

.debug-body-raw {
  width: 100%;
  max-width: 100%;
  padding: 6px;
  box-sizing: border-box;
  font-family: ui-monospace, Consolas, 'Courier New', monospace;
  font-size: 12px;

  :deep(.el-textarea) {
    width: 100%;
  }

  :deep(.el-textarea__inner) {
    font-family: inherit;
    line-height: 1.5;
    border-radius: 6px;
    padding: 10px 12px;
    color: var(--pd-text, #0f172a);
    background: var(--pd-surface-elevated, #fff);
    border: 1px solid var(--pd-divider, #dbe8f4);
    box-shadow: none;
    resize: none;

    &::placeholder {
      color: var(--pd-text-muted, #5a6b86);
      opacity: 0.85;
    }

    &:hover {
      border-color: var(--pd-border-subtle, #c5d8ec);
    }

    &:focus {
      border-color: var(--pd-primary, #0b6edc);
      box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
    }
  }
}

.http-config-body > .debug-body-raw {
  flex: 1;
  min-height: 0;
  padding: 6px;
}

.debug-body-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 16px 6px 20px;
  text-align: center;
}

.debug-body-placeholder-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text-muted, #5a6b86);
}

.debug-body-placeholder-desc {
  margin: 0;
  font-size: 12px;
  color: var(--pd-text-muted, #5a6b86);
  line-height: 1.5;
}
</style>
