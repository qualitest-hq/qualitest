<template>
  <div class="prefab-api-panel">
    <div class="prefab-api-panel__toolbar">
      <span class="prefab-api-panel__title">预制接口</span>
      <div v-if="!readOnly" class="prefab-api-panel__actions">
        <el-button icon="Plus" size="small" type="primary" @click="handleAdd">新增</el-button>
        <el-button
          :disabled="selectedIndex < 0"
          icon="Delete"
          size="small"
          @click="handleRemove"
        >删除</el-button>
        <el-button
          :disabled="selectedIndex <= 0"
          icon="Top"
          size="small"
          @click="handleMove(-1)"
        >上移</el-button>
        <el-button
          :disabled="selectedIndex < 0 || selectedIndex >= apis.length - 1"
          icon="Bottom"
          size="small"
          @click="handleMove(1)"
        >下移</el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="tableRows"
      border
      class="prefab-api-panel__table"
      highlight-current-row
      row-key="_index"
      size="small"
      @row-click="handleRowClick"
    >
      <el-table-column label="方法" prop="method" width="80" />
      <el-table-column label="路径" min-width="140" prop="apiPath" show-overflow-tooltip />
      <el-table-column label="名称" min-width="100" prop="apiName" show-overflow-tooltip />
      <el-table-column label="鉴权" prop="authModeLabel" width="88" />
    </el-table>

    <div v-if="selectedIndex >= 0 && selectedApi" class="prefab-api-panel__detail">
      <el-form
        v-if="readOnly"
        class="prefab-api-panel__fields prefab-api-panel__fields--readonly"
        label-width="88px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="名称">
              <span class="prefab-api-panel__text">{{ selectedApi.apiName || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路径">
              <span class="prefab-api-panel__text">{{ selectedApi.apiPath || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分组">
              <span class="prefab-api-panel__text">{{ selectedApi.apiGroup || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方法">
              <span class="prefab-api-panel__text">{{ selectedMethod }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权">
              <span class="prefab-api-panel__text">{{ selectedAuthModeLabel }}</span>
            </el-form-item>
          </el-col>
          <template v-if="selectedAuthMode === 'override'">
            <el-col :span="12">
              <el-form-item label="自定义头名">
                <span class="prefab-api-panel__text">{{ selectedAuthHeaderName || '—' }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="头值模板">
                <span class="prefab-api-panel__text">{{ selectedAuthValueTemplate || '—' }}</span>
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="24">
            <el-form-item label="描述">
              <span class="prefab-api-panel__text">{{ selectedApi.apiDescription || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="造流提示">
              <span class="prefab-api-panel__text prefab-api-panel__text--pre">{{ selectedDesignHintsText || '—' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-form
        v-else
        class="prefab-api-panel__fields"
        label-width="88px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="名称">
              <el-input v-model="selectedApi.apiName" placeholder="如 登录" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路径">
              <el-input
                :model-value="selectedApi.apiPath"
                placeholder="如 /login"
                @update:model-value="onFormPathChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分组">
              <el-input v-model="selectedApi.apiGroup" placeholder="如 系统.登录" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方法">
              <el-select v-model="selectedMethod" style="width: 100%">
                <el-option
                  v-for="item in HTTP_METHODS"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权">
              <el-select v-model="selectedAuthMode" style="width: 100%">
                <el-option label="免登录" value="none" />
                <el-option label="继承" value="inherit" />
                <el-option label="自定义" value="override" />
              </el-select>
            </el-form-item>
          </el-col>
          <template v-if="selectedAuthMode === 'override'">
            <el-col :span="12">
              <el-form-item label="自定义头名">
                <el-input v-model="selectedAuthHeaderName" placeholder="Authorization" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="头值模板">
                <el-input
                  v-model="selectedAuthValueTemplate"
                  placeholder="Bearer {{flow.token}}"
                />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input
                v-model="selectedApi.apiDescription"
                :rows="2"
                placeholder="接口说明（可选）"
                type="textarea"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="造流提示">
              <el-input
                v-model="selectedDesignHintsText"
                :rows="2"
                placeholder="每行一条；人机可维护"
                type="textarea"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="prefab-api-panel__section-title">
        <span>请求设计</span>
        <span v-if="readOnly" class="prefab-api-panel__hint">只读浏览，可切换 Tab 查看</span>
      </div>
      <div
        class="prefab-api-panel__workbench"
        :class="{ 'prefab-api-panel__chrome--readonly': readOnly }"
      >
        <ApiDebugTab
          :key="'dbg-' + selectedIndex"
          ref="debugTabRef"
          :api-detail="workbenchDetail"
          :embed-in-design="true"
          :env-list="[]"
          :test-project-env-id="null"
        />
      </div>

      <div class="prefab-api-panel__section-title">
        <span>响应配置</span>
        <span v-if="readOnly" class="prefab-api-panel__hint">只读浏览</span>
      </div>
      <div
        class="prefab-api-panel__response"
        :class="{ 'prefab-api-panel__chrome--readonly': readOnly }"
      >
        <ResponseConfigPanel
          :key="'resp-' + selectedIndex"
          ref="responseConfigPanelRef"
          :model-value="responseConfigText"
          @update:model-value="onResponseConfigTextChange"
        />
      </div>
    </div>

    <el-empty
      v-else
      :image-size="56"
      class="prefab-api-panel__empty"
      description="暂无预制接口，请点击新增"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import ApiDebugTab from '@/views/project/testProject/components/ApiDebugTab.vue'
import ResponseConfigPanel from '@/views/project/testProject/components/ResponseConfigPanel.vue'
import {
  HTTP_METHODS,
  apisToPreviewRows,
  emptyPrefabricatedApi,
  parseApis,
  resolveApiMethod,
  setApiMethod,
} from '../utils/templateForm'
import { formatAuthModeLabel } from '@/views/project/testProject/utils/projectAuthConfig'
import {
  applyWorkbenchPersistToPrefab,
  designHintsFromText,
  designHintsToText,
  patchAuthConfigMode,
  prefabToWorkbenchDetail,
  parseResponseConfigEditorText,
  readAuthOverrideHeader,
  responseConfigToEditorText,
} from '../utils/prefabApiWorkbench'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
})

const apis = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)
const responseConfigText = ref('')

const tableRef = ref(null)
const debugTabRef = ref(null)
const responseConfigPanelRef = ref(null)

const tableRows = computed(() =>
  apisToPreviewRows(apis.value).map((row, index) => ({ ...row, _index: index })),
)

const selectedApi = computed(() => {
  if (selectedIndex.value < 0 || selectedIndex.value >= apis.value.length) return null
  return apis.value[selectedIndex.value]
})

const workbenchDetail = computed(() => prefabToWorkbenchDetail(selectedApi.value))

function replaceApisAt(index, next) {
  apis.value = apis.value.map((item, idx) => (idx === index ? next : item))
}

function syncResponseTextFromApi() {
  responseConfigText.value = responseConfigToEditorText(selectedApi.value?.responseConfig)
}

const selectedMethod = computed({
  get() {
    if (!selectedApi.value) return 'GET'
    return resolveApiMethod(selectedApi.value)
  },
  set(method) {
    if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
    replaceApisAt(selectedIndex.value, setApiMethod(selectedApi.value, method))
  },
})

const selectedAuthMode = computed({
  get() {
    return String(selectedApi.value?.authConfig?.mode || 'inherit').trim() || 'inherit'
  },
  set(mode) {
    if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
    const override = readAuthOverrideHeader(selectedApi.value.authConfig)
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      authConfig: patchAuthConfigMode(selectedApi.value.authConfig, mode, override),
    })
  },
})

const selectedAuthModeLabel = computed(() => formatAuthModeLabel(selectedAuthMode.value))

function patchSelectedAuthOverride(partial) {
  if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
  const override = {
    ...readAuthOverrideHeader(selectedApi.value.authConfig),
    ...partial,
  }
  replaceApisAt(selectedIndex.value, {
    ...selectedApi.value,
    authConfig: patchAuthConfigMode(selectedApi.value.authConfig, 'override', override),
  })
}

const selectedAuthHeaderName = computed({
  get() {
    return readAuthOverrideHeader(selectedApi.value?.authConfig).headerName
  },
  set(name) {
    patchSelectedAuthOverride({ headerName: name })
  },
})

const selectedAuthValueTemplate = computed({
  get() {
    return readAuthOverrideHeader(selectedApi.value?.authConfig).valueTemplate
  },
  set(valueTemplate) {
    patchSelectedAuthOverride({ valueTemplate })
  },
})

const selectedDesignHintsText = computed({
  get() {
    return designHintsToText(selectedApi.value?.designHints)
  },
  set(text) {
    if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      designHints: designHintsFromText(text),
    })
  },
})

function onFormPathChange(path) {
  if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
  replaceApisAt(selectedIndex.value, {
    ...selectedApi.value,
    apiPath: path,
  })
}

function setCurrentTableRow() {
  nextTick(() => {
    const row = tableRows.value.find((item) => item._index === selectedIndex.value)
    tableRef.value?.setCurrentRow(row || undefined)
  })
}

function flushWorkbenchToModel() {
  if (props.readOnly) {
    return { ok: true, message: '' }
  }
  if (selectedIndex.value < 0 || !selectedApi.value) {
    return { ok: true, message: '' }
  }

  responseConfigPanelRef.value?.flushPendingModelEmit?.()

  const part = debugTabRef.value?.buildPersistPayload?.()
  if (!part) {
    return { ok: false, message: '无法读取请求配置' }
  }
  const applied = applyWorkbenchPersistToPrefab(selectedApi.value, part)
  if (!applied.ok) {
    return { ok: false, message: applied.error || '请求配置无效' }
  }

  let nextApi = applied.api
  const respParsed = parseResponseConfigEditorText(responseConfigText.value)
  if (!respParsed.ok) {
    return { ok: false, message: respParsed.error || '响应配置无效' }
  }
  nextApi = { ...nextApi, responseConfig: respParsed.value }
  replaceApisAt(selectedIndex.value, nextApi)
  return { ok: true, message: '' }
}

function ensureSelection() {
  if (!apis.value.length) {
    selectedIndex.value = -1
    syncResponseTextFromApi()
    setCurrentTableRow()
    return
  }
  if (selectedIndex.value < 0 || selectedIndex.value >= apis.value.length) {
    selectedIndex.value = 0
  }
  syncResponseTextFromApi()
  setCurrentTableRow()
}

watch(() => apis.value.length, ensureSelection, { immediate: true })

watch(
  () => selectedIndex.value,
  () => {
    syncResponseTextFromApi()
  },
)

function handleRowClick(row) {
  if (row?._index == null || row._index < 0) return
  if (row._index === selectedIndex.value) return
  flushWorkbenchToModel()
  selectedIndex.value = row._index
  setCurrentTableRow()
}

function handleAdd() {
  flushWorkbenchToModel()
  const next = [...parseApis(apis.value), emptyPrefabricatedApi()]
  apis.value = next
  selectedIndex.value = next.length - 1
  syncResponseTextFromApi()
}

function handleRemove() {
  if (selectedIndex.value < 0) return
  const next = parseApis(apis.value)
  next.splice(selectedIndex.value, 1)
  apis.value = next
  if (selectedIndex.value >= next.length) {
    selectedIndex.value = next.length - 1
  }
  ensureSelection()
}

function handleMove(delta) {
  const from = selectedIndex.value
  const to = from + delta
  if (from < 0 || to < 0 || to >= apis.value.length) return
  flushWorkbenchToModel()
  const next = parseApis(apis.value)
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  apis.value = next
  selectedIndex.value = to
}

function onResponseConfigTextChange(text) {
  if (props.readOnly) return
  responseConfigText.value = text
  if (selectedIndex.value < 0 || !selectedApi.value) return
  const parsed = parseResponseConfigEditorText(text)
  if (!parsed.ok) return
  replaceApisAt(selectedIndex.value, {
    ...selectedApi.value,
    responseConfig: parsed.value,
  })
}

function flushAndValidate() {
  const flushed = flushWorkbenchToModel()
  if (!flushed.ok) {
    return { valid: false, message: flushed.message }
  }
  if (!Array.isArray(apis.value) || !apis.value.length) {
    return { valid: false, message: '预制接口不能为空' }
  }
  return { valid: true, message: '' }
}

defineExpose({ flushAndValidate })
</script>

<style scoped lang="scss">
.prefab-api-panel {
  width: 100%;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__table {
    width: 100%;
    margin-bottom: 12px;
  }

  &__detail {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-fill-color-blank);
  }

  &__fields {
    margin-bottom: 8px;

    &--readonly {
      :deep(.el-form-item) {
        margin-bottom: 10px;
      }
    }
  }

  &__text {
    display: inline-block;
    min-height: 22px;
    line-height: 22px;
    font-size: 13px;
    color: var(--el-text-color-primary);
    word-break: break-all;

    &--pre {
      white-space: pre-wrap;
    }
  }

  &__section-title {
    margin: 12px 0 8px;
    padding: 6px 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-light);
    border-radius: 4px;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__hint {
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &__workbench {
    display: flex;
    flex-direction: column;
    height: clamp(320px, 44vh, 600px);
    min-height: 320px;
    margin-bottom: 8px;
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    background: var(--el-fill-color-blank);

    :deep(.api-debug-workbench) {
      flex: 1;
      min-height: 0;
      height: 100%;
    }
  }

  &__response {
    margin-bottom: 8px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    overflow: hidden;
  }

  /* 只读：可点 Tab / 滚动查看；禁掉输入与按钮编辑 */
  &__chrome--readonly {
    :deep(input),
    :deep(textarea),
    :deep(.el-input),
    :deep(.el-textarea),
    :deep(.el-select),
    :deep(.el-checkbox),
    :deep(.el-radio),
    :deep(.el-button),
    :deep(.el-input-number),
    :deep(.el-switch) {
      pointer-events: none;
    }
  }

  &__empty {
    padding: 24px 0;
  }
}
</style>
