<template>
  <div class="response-config-panel">
    <div v-if="parseErrorFlag && !rawMode" class="resp-parse-alert">
      <el-alert
          :closable="false"
          show-icon
          title="响应配置须为合法 JSON 且包含 responses 数组，已降级为 Raw 编辑"
          type="warning"
      />
      <el-button class="resp-raw-open-btn" size="small" type="primary" @click="openRawFromError">
        打开 Raw 编辑
      </el-button>
    </div>

    <template v-else-if="rawMode">
      <p class="resp-field-desc">直接编辑 responseConfig 完整 JSON（仅 responses[]）</p>
      <el-input v-model="rawText" :rows="16" class="resp-raw-textarea" placeholder="{}" type="textarea"/>
      <div class="resp-raw-actions">
        <el-button size="small" type="primary" @click="applyRawJson">应用并返回可视化</el-button>
        <el-button v-if="!parseErrorFlag" size="small" @click="cancelRawMode">取消</el-button>
      </div>
    </template>

    <template v-else>
      <div class="resp-tab-bar">
        <div class="resp-tab-bar__tabs">
          <el-tabs
              v-model="activeTabId"
              class="resp-el-tabs"
              closable
              type="card"
              @tab-remove="removeResponseTab"
          >
            <el-tab-pane
                v-for="r in local.responses"
                :key="r.id"
                :label="tabLabel(r)"
                :name="r.id"
            />
          </el-tabs>
        </div>
        <el-button class="resp-add-tab-btn" size="small" type="primary" @click="addResponseTab">
          <el-icon class="el-icon--left"><Plus/></el-icon>
          响应
        </el-button>
      </div>

      <div v-if="currentEntry" class="resp-entry-body">
        <div class="resp-meta-grid">
          <div class="resp-meta-grid__fields resp-meta-grid__fields--controls">
            <el-input v-model="currentEntry.name" placeholder="响应名称"/>
            <el-input-number
                v-model="currentEntry.httpStatus"
                :controls="true"
                :max="599"
                :min="100"
                class="resp-status-input"
            />
            <el-select v-model="currentEntry.contentType" class="resp-ct-select" placeholder="类型">
              <el-option label="JSON" value="json"/>
              <el-option label="XML" value="xml"/>
              <el-option label="Binary" value="binary"/>
            </el-select>
          </div>
        </div>

        <div v-if="currentEntry.contentType === 'json'" class="resp-json-body">
          <BodyJsonSchemaTree
              ref="respBodyJsonTreeRef"
              :content-type-hint="'application/json'"
              :model-value="currentEntry.schema"
              :panel-title="'响应结构'"
              :show-example-column="false"
              :virtual-min-flat-rows="36"
              tree-mode="response"
              @open-schema="onRespBodyJsonOpenSchema"
              @update:model-value="onJsonSchemaInput"
          />
        </div>

        <div v-else-if="currentEntry.contentType === 'xml'" class="resp-alt-body resp-xml-body">
          <p class="resp-field-desc">XML 示例或结构说明（文本）</p>
          <el-input
              v-model="currentEntry.xmlText"
              :rows="14"
              class="resp-xml-textarea"
              placeholder="<root>...</root>"
              type="textarea"
          />
        </div>

        <div v-else-if="currentEntry.contentType === 'binary'" class="resp-alt-body resp-binary-body">
          <p class="resp-field-desc">二进制响应无字段树状结构，可填写备注。</p>
          <el-input v-model="currentEntry.binaryNote" placeholder="备注（可选）" size="small"/>
        </div>
      </div>
    </template>
  </div>

  <el-dialog
      v-model="respParamSchemaDialogVisible"
      append-to-body
      class="param-schema-dialog"
      destroy-on-close
      title="参数类型"
      width="520px"
      @closed="onRespParamSchemaDialogClosed"
  >
    <div v-if="respParamSchemaRow" class="param-schema-dialog-inner">
      <div class="param-schema-body">
        <div class="param-schema-main-type-row">
          <el-select
              v-model="respParamSchemaRow.type"
              :class="respParamTypeSelectClass(respParamSchemaRow.type)"
              class="param-schema-type-select"
              default-first-option
              filterable
              placeholder="类型"
              size="small"
          >
            <el-option v-for="t in SCHEMA_JSON_BODY_TYPES" :key="t" :label="t" :value="t"/>
          </el-select>
        </div>
        <div class="param-schema-switches">
          <el-switch v-model="respParamSchemaRow.required" size="small"/>
          <span class="lbl">必需</span>
          <el-switch v-model="respParamSchemaRow.nullable" size="small"/>
          <span class="lbl">允许 NULL</span>
          <el-switch v-model="respParamSchemaRow.deprecated" size="small"/>
          <span class="lbl">废弃</span>
        </div>
        <el-form class="param-schema-form" label-position="top" size="small">
          <el-row v-if="respParamSchemaFormatVisible" :gutter="8">
            <el-col :span="12">
              <el-form-item label="format">
                <el-input v-model="respParamSchemaRow.format" clearable placeholder="如 date、uuid"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="respParamSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in RESP_PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-else :gutter="8">
            <el-col :span="24">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="respParamSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in RESP_PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <template v-if="respParamSchemaStringConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小长度">
                  <el-input-number
                      v-model="respParamSchemaRow.minLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大长度">
                  <el-input-number
                      v-model="respParamSchemaRow.maxLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else-if="respParamSchemaNumberConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小值">
                  <el-input-number v-model="respParamSchemaRow.minValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大值">
                  <el-input-number v-model="respParamSchemaRow.maxValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="param-schema-advanced-label">高级区间</div>
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="exclusiveMinimum">
                  <el-input-number
                      v-model="respParamSchemaRow.exclusiveMinimum"
                      :controls="false"
                      class="param-schema-w100"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="exclusiveMaximum">
                  <el-input-number
                      v-model="respParamSchemaRow.exclusiveMaximum"
                      :controls="false"
                      class="param-schema-w100"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="multipleOf">
                  <el-input-number v-model="respParamSchemaRow.multipleOf" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else-if="respParamSchemaArrayConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小元素数">
                  <el-input-number
                      v-model="respParamSchemaRow.minItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大元素数">
                  <el-input-number
                      v-model="respParamSchemaRow.maxItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-row v-if="!respParamSchemaArrayConstraints" :gutter="8">
            <el-col :span="respParamSchemaStringConstraints ? 12 : 24">
              <el-form-item label="默认值">
                <el-input v-model="respParamSchemaRow.defaultValue" clearable/>
              </el-form-item>
            </el-col>
            <el-col v-if="respParamSchemaStringConstraints" :span="12">
              <el-form-item>
                <template #label>
                  <span>pattern</span>
                  <el-tooltip content="正则约束（常用于 string）" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-input v-model="respParamSchemaRow.pattern" clearable placeholder="^[A-Za-z0-9_-]+"/>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import {Plus, QuestionFilled} from '@element-plus/icons-vue'
import BodyJsonSchemaTree from './BodyJsonSchemaTree.vue'
import {supportsFormat} from '@/views/project/testProject/utils/fieldTypeConstraints'
import {
  bodyJsonSchemaNodeToParamRow,
  paramRowMergeIntoBodyJsonNode
} from '@/views/project/testProject/utils/bodyJsonSchemaParamBridge'
import {SCHEMA_JSON_BODY_TYPES} from '@/views/project/testProject/utils/jsonSchemaTree'
import {
  createDefaultResponseEntry,
  createEmptyResponseConfig,
  parseResponseConfigInput,
  serializeResponseConfig
} from '@/views/project/testProject/utils/responseConfig'
import {computed, getCurrentInstance, markRaw, nextTick, onBeforeUnmount, reactive, ref, watch} from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const {proxy} = getCurrentInstance()

const local = reactive(createEmptyResponseConfig())
const activeTabId = ref('')
const rawMode = ref(false)
const rawText = ref('')
const parseErrorFlag = ref(false)
let syncingFromParent = false

/** 入库用的 schema/example 不参与深度响应式，减轻大 JSON 树编辑时的依赖收集与 watch 触发成本 */
function freezeLargeFieldsInLocal() {
  for (const r of local.responses) {
    if (r.schema != null && typeof r.schema === 'object') {
      r.schema = markRaw(r.schema)
    }
    if (r.example != null && typeof r.example === 'object' && !Array.isArray(r.example)) {
      r.example = markRaw(r.example)
    }
  }
}

const respBodyJsonTreeRef = ref(null)
const respParamSchemaDialogVisible = ref(false)
const respParamSchemaRow = ref(null)
const respBodyJsonSchemaEditTarget = ref(null)

const RESP_PARAM_SCHEMA_BEHAVIOR = [
  {label: 'Read/Write', value: 'readWrite'},
  {label: 'Read only', value: 'readOnly'},
  {label: 'Write only', value: 'writeOnly'}
]

const respParamSchemaStringConstraints = computed(() => {
  const r = respParamSchemaRow.value
  if (!r) return false
  const t = String(r.type || '').toLowerCase()
  return t === 'string' || t === 'file' || t === 'any'
})

const respParamSchemaNumberConstraints = computed(() => {
  const r = respParamSchemaRow.value
  if (!r) return false
  const t = String(r.type || '').toLowerCase()
  return t === 'integer' || t === 'number'
})

const respParamSchemaArrayConstraints = computed(() => {
  const r = respParamSchemaRow.value
  if (!r) return false
  return String(r.type || '').toLowerCase() === 'array'
})

const respParamSchemaFormatVisible = computed(() => {
  const r = respParamSchemaRow.value
  if (!r) return false
  return supportsFormat(r.type)
})

function respParamTypeSelectClass(type) {
  const raw = type == null || type === '' ? 'string' : String(type)
  const safe = raw.toLowerCase().replace(/[^a-z0-9]/g, '') || 'custom'
  return ['param-type-select', `is-type-${safe}`]
}

function onRespBodyJsonOpenSchema(node) {
  if (!node || typeof node !== 'object') return
  const t = String(node.type || '').toLowerCase()
  if (t === 'object') {
    proxy?.$modal?.msgInfo?.(
        'object 请在树中编辑子节点与类型列；标量 / array 字段可使用齿轮打开高级设置。'
    )
    return
  }
  respBodyJsonSchemaEditTarget.value = node
  respParamSchemaRow.value = bodyJsonSchemaNodeToParamRow(node)
  respParamSchemaDialogVisible.value = true
}

function onRespParamSchemaDialogClosed() {
  if (respBodyJsonSchemaEditTarget.value && respParamSchemaRow.value) {
    paramRowMergeIntoBodyJsonNode(respBodyJsonSchemaEditTarget.value, respParamSchemaRow.value)
    respBodyJsonSchemaEditTarget.value = null
    nextTick(() => respBodyJsonTreeRef.value?.emitSchema?.())
  }
  respParamSchemaRow.value = null
}

const currentEntry = computed(() => local.responses.find((r) => r.id === activeTabId.value) || null)

function tabLabel(r) {
  const code = r.httpStatus ?? 200
  const name = (r.name || '成功').trim()
  return `${name} (${code})`
}

function onJsonSchemaInput(v) {
  const r = currentEntry.value
  if (!r) return
  r.schema = v != null && typeof v === 'object' ? markRaw(v) : v
}

function emitSerialized() {
  try {
    emit('update:modelValue', serializeResponseConfig(local))
  } catch (e) {
    console.error(e)
  }
}

watch(rawText, () => {
  if (syncingFromParent || !rawMode.value) return
  emit('update:modelValue', rawText.value)
})

watch(
    () => props.modelValue,
    (s) => {
      if (rawMode.value) {
        const cur = String(s ?? '')
        const rt = String(rawText.value ?? '')
        if (cur === rt) return
      }
      syncingFromParent = true
      parseErrorFlag.value = false
      const r = parseResponseConfigInput(s)
      if (r.parseError) {
        parseErrorFlag.value = true
        rawText.value = typeof s === 'string' ? s : String(s ?? '')
        rawMode.value = true
        nextTick(() => {
          syncingFromParent = false
        })
        return
      }
      Object.assign(local, r.bundle)
      freezeLargeFieldsInLocal()
      const ids = local.responses.map((x) => x.id)
      if (!activeTabId.value || !ids.includes(activeTabId.value)) {
        activeTabId.value = local.responses[0]?.id || ''
      }
      nextTick(() => {
        syncingFromParent = false
      })
    },
    {immediate: true}
)

/** 深度监听 local：防抖后序列化到 v-model（手写 timer，兼容无 .cancel 的 debounce 实现） */
const EMIT_SERIAL_DEBOUNCE_MS = 140
let emitSerializedTimer = null

function clearEmitSerializedTimer() {
  if (emitSerializedTimer != null) {
    clearTimeout(emitSerializedTimer)
    emitSerializedTimer = null
  }
}

function scheduleEmitSerializedDebounced() {
  if (syncingFromParent) return
  if (rawMode.value) return
  clearEmitSerializedTimer()
  emitSerializedTimer = setTimeout(() => {
    emitSerializedTimer = null
    emitSerialized()
  }, EMIT_SERIAL_DEBOUNCE_MS)
}

watch(
    local,
    () => {
      scheduleEmitSerializedDebounced()
    },
    {deep: true}
)

onBeforeUnmount(() => {
  clearEmitSerializedTimer()
  if (!syncingFromParent && !rawMode.value) emitSerialized()
})

function addResponseTab() {
  const ne = createDefaultResponseEntry()
  local.responses.push(ne)
  activeTabId.value = ne.id
}

function removeResponseTab(name) {
  if (local.responses.length <= 1) {
    proxy.$modal?.msgWarning?.('至少保留一条响应')
    return
  }
  const id = name
  const i = local.responses.findIndex((r) => r.id === id)
  if (i < 0) return
  local.responses.splice(i, 1)
  if (activeTabId.value === id) {
    activeTabId.value = local.responses[0]?.id || ''
  }
}

function openRawFromError() {
  rawMode.value = true
}

function cancelRawMode() {
  rawMode.value = false
  const r = parseResponseConfigInput(props.modelValue)
  if (!r.parseError) {
    syncingFromParent = true
    Object.assign(local, r.bundle)
    freezeLargeFieldsInLocal()
    const ids = local.responses.map((x) => x.id)
    if (!ids.includes(activeTabId.value)) {
      activeTabId.value = local.responses[0]?.id || ''
    }
    nextTick(() => {
      syncingFromParent = false
    })
  }
}

function applyRawJson() {
  const t = (rawText.value || '').trim()
  if (!t) {
    syncingFromParent = true
    Object.assign(local, createEmptyResponseConfig())
    freezeLargeFieldsInLocal()
    activeTabId.value = local.responses[0]?.id || ''
    parseErrorFlag.value = false
    rawMode.value = false
    nextTick(() => {
      syncingFromParent = false
      emitSerialized()
    })
    return
  }
  try {
    JSON.parse(t)
    const r = parseResponseConfigInput(t)
    if (r.parseError) throw new Error('parse')
    syncingFromParent = true
    Object.assign(local, r.bundle)
    freezeLargeFieldsInLocal()
    activeTabId.value = local.responses[0]?.id || ''
    parseErrorFlag.value = false
    rawMode.value = false
    nextTick(() => {
      syncingFromParent = false
      emitSerialized()
      proxy.$modal?.msgSuccess?.('已应用')
    })
  } catch {
    proxy.$modal?.msgError?.('JSON 格式无效')
  }
}

defineExpose({
  /** 保存前显式取字符串（与 v-model 一致） */
  getSerialized: () => serializeResponseConfig(local),
  /** 是否处于 Raw 模式（保存时可提示） */
  isRawMode: () => rawMode.value,
  /** 取消防抖并立即把当前可视化状态写回 v-model（保存前调用） */
  flushPendingModelEmit() {
    clearEmitSerializedTimer()
    if (!syncingFromParent && !rawMode.value) emitSerialized()
  }
})
</script>

<style lang="scss" scoped>
.response-config-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 120px;
}

.resp-parse-alert {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.resp-raw-open-btn {
  flex-shrink: 0;
}

.resp-field-desc {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--pd-text-muted, #5a6b86);
  line-height: 1.45;
}

.resp-raw-textarea {
  font-family: ui-monospace, Consolas, 'Courier New', monospace;
  font-size: 12px;
}

.resp-raw-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.resp-tab-bar {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  margin: 0;
  min-height: 0;
}

.resp-tab-bar__tabs {
  flex: 1;
  min-width: 0;

  :deep(.el-tabs) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__header) {
    margin: 0 !important;
  }

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: 0 !important;
  }

  :deep(.el-tabs__nav-scroll) {
    padding-bottom: 0;
  }

  /* 卡片 Tab 默认带圆角，与调试/设计区通栏直角一致 */
  :deep(.resp-el-tabs.el-tabs--card) {
    .el-tabs__header {
      border-radius: 0;
    }

    .el-tabs__nav {
      border-radius: 0;
    }

    .el-tabs__item {
      border-radius: 0 !important;
    }
  }
}

.resp-add-tab-btn {
  flex-shrink: 0;
  height: 32px;
  padding: 0 12px;
}

.resp-entry-body {
  border: 1px solid var(--pd-divider, #dbe8f4);
  border-radius: 0;
  overflow: hidden;
  background: var(--pd-surface-elevated, #fff);
}

.resp-meta-grid {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  padding: 10px 10px;
  background: var(--pd-bg-sunken, #e9f2fc);
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
}

.resp-meta-grid__fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;

  .el-input {
    max-width: 220px;
  }

  &--controls {
    :deep(.el-input__wrapper) {
      min-height: 36px;
      padding-left: 11px;
      padding-right: 11px;
    }

    :deep(.el-input__inner) {
      font-size: 14px;
    }

    :deep(.el-select__wrapper) {
      min-height: 36px;
      font-size: 14px;
    }

    :deep(.el-input-number .el-input__wrapper) {
      min-height: 36px;
    }
  }
}

.resp-status-input {
  width: 128px;
}

.resp-ct-select {
  width: 128px;
}

.resp-json-body {
  border: none;
  border-radius: 0;
  overflow: hidden;
}

.resp-alt-body {
  padding: 10px 12px 12px;
}

.resp-xml-body,
.resp-binary-body {
  .resp-field-desc {
    margin-top: 0;
  }
}

.resp-xml-textarea {
  width: 100%;
  font-family: ui-monospace, Consolas, 'Courier New', monospace;
  font-size: 12px;
}
</style>
