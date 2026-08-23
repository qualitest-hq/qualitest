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
      v-if="tableRows.length"
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
      <el-table-column align="center" label="操作" width="72">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click.stop="openDetail(scope.row._index)"
          >{{ readOnly ? '查看' : '编辑' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-else
      :image-size="56"
      class="prefab-api-panel__empty"
      description="暂无预制接口，请点击新增"
    />

    <el-dialog
      v-model="detailVisible"
      :before-close="beforeDetailClose"
      :close-on-click-modal="false"
      :title="detailTitle"
      :z-index="10000"
      append-to-body
      class="prefab-api-detail-dialog"
      destroy-on-close
      top="4vh"
      width="80vw"
    >
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
      <template #footer>
        <div class="prefab-api-panel__dialog-footer">
          <el-button v-if="!readOnly" type="primary" @click="confirmDetail">确 定</el-button>
          <el-button @click="closeDetail">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 预制接口面板：编辑模板的 templateApis。
 * 列表预览 + 详情抽屉（请求 / 响应 / 鉴权 / 测值等）；登录口常用 authConfig.mode=none。
 */
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
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
import { peelTestValuesFromStructure } from '@/views/project/testProject/utils/peelTestValueConfig'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
})

const { proxy } = getCurrentInstance()

const apis = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)
const detailVisible = ref(false)
const responseConfigText = ref('')
/** 确定已成功 flush 时跳过 before-close 再 flush */
const skipFlushOnClose = ref(false)

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

const detailTitle = computed(() => {
  const prefix = props.readOnly ? '查看预制接口' : '编辑预制接口'
  const api = selectedApi.value
  if (!api) return prefix
  const name = String(api.apiName || '').trim()
  if (name) return `${prefix} · ${name}`
  const method = resolveApiMethod(api)
  const path = String(api.apiPath || '').trim()
  if (method || path) return `${prefix} · ${method} ${path}`.trim()
  return prefix
})

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

/** 把当前工作台请求/响应草稿拆测值后写回选中的预制接口 */
function flushWorkbenchToModel() {
  if (props.readOnly) {
    return { ok: true, message: '' }
  }
  if (selectedIndex.value < 0 || !selectedApi.value) {
    return { ok: true, message: '' }
  }
  // 弹框未挂载 / destroy-on-close 后无工作台，无需 flush
  if (!debugTabRef.value) {
    return { ok: true, message: '' }
  }

  responseConfigPanelRef.value?.flushPendingModelEmit?.()

  const respParsed = parseResponseConfigEditorText(responseConfigText.value)
  if (!respParsed.ok) {
    return { ok: false, message: respParsed.error || '响应配置无效' }
  }

  const part = debugTabRef.value?.buildPersistPayload?.(respParsed.value)
  if (!part) {
    return { ok: false, message: '无法读取请求配置' }
  }
  const applied = applyWorkbenchPersistToPrefab(selectedApi.value, part)
  if (!applied.ok) {
    return { ok: false, message: applied.error || '请求配置无效' }
  }

  replaceApisAt(selectedIndex.value, applied.api)
  return { ok: true, message: '' }
}

function ensureSelection() {
  if (!apis.value.length) {
    selectedIndex.value = -1
    detailVisible.value = false
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
  selectedIndex.value = row._index
  setCurrentTableRow()
}

function openDetail(index) {
  if (index == null || index < 0 || index >= apis.value.length) return
  if (detailVisible.value && index !== selectedIndex.value) {
    const flushed = flushWorkbenchToModel()
    if (!flushed.ok) {
      proxy.$modal.msgError(flushed.message)
      return
    }
  }
  selectedIndex.value = index
  syncResponseTextFromApi()
  setCurrentTableRow()
  detailVisible.value = true
}

function closeDetail() {
  detailVisible.value = false
}

function confirmDetail() {
  const flushed = flushWorkbenchToModel()
  if (!flushed.ok) {
    proxy.$modal.msgError(flushed.message)
    return
  }
  skipFlushOnClose.value = true
  detailVisible.value = false
}

/** 关闭前 flush 工作台（refs 尚在）；失败则拦截关闭 */
function beforeDetailClose(done) {
  if (skipFlushOnClose.value) {
    skipFlushOnClose.value = false
    done()
    return
  }
  const flushed = flushWorkbenchToModel()
  if (!flushed.ok) {
    proxy.$modal.msgError(flushed.message)
    return
  }
  done()
}

function handleAdd() {
  if (detailVisible.value) {
    const flushed = flushWorkbenchToModel()
    if (!flushed.ok) {
      proxy.$modal.msgError(flushed.message)
      return
    }
  }
  const next = [...parseApis(apis.value), emptyPrefabricatedApi()]
  apis.value = next
  const newIndex = next.length - 1
  selectedIndex.value = newIndex
  syncResponseTextFromApi()
  setCurrentTableRow()
  // 等表格行渲染后再打开，避免与 length watch 同拍导致弹框未挂载
  nextTick(() => {
    detailVisible.value = true
  })
}

function handleRemove() {
  if (selectedIndex.value < 0) return
  if (detailVisible.value) {
    detailVisible.value = false
  }
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
  if (detailVisible.value) {
    const flushed = flushWorkbenchToModel()
    if (!flushed.ok) {
      proxy.$modal.msgError(flushed.message)
      return
    }
  }
  const next = parseApis(apis.value)
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  apis.value = next
  selectedIndex.value = to
  setCurrentTableRow()
}

/** 响应 JSON 编辑即时：拆 example 进测值，结构只留定义 */
function onResponseConfigTextChange(text) {
  if (props.readOnly) return
  responseConfigText.value = text
  if (selectedIndex.value < 0 || !selectedApi.value) return
  const parsed = parseResponseConfigEditorText(text)
  if (!parsed.ok) return
  const peeled = peelTestValuesFromStructure(
    selectedApi.value.requestConfig,
    parsed.value,
    selectedApi.value.testValueConfig,
  )
  replaceApisAt(selectedIndex.value, {
    ...selectedApi.value,
    requestConfig: peeled.requestConfig,
    responseConfig: peeled.responseConfig,
    testValueConfig: peeled.testValueConfig,
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
    padding: 0 4px;
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
    height: clamp(320px, 50vh, 640px);
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

  &__dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
