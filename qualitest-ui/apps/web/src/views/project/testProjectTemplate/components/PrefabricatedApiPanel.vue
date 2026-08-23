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
        :disabled="readOnly"
        class="prefab-api-panel__fields"
        label-width="72px"
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
              <el-input v-model="selectedApi.apiPath" placeholder="如 /login" />
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
        </el-row>
      </el-form>

      <el-tabs v-model="activeJsonTab" class="prefab-api-panel__tabs">
        <el-tab-pane
          v-for="tab in JSON_DRAFT_TAB_LABELS"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        >
          <ScriptSourceEditor
            v-model="jsonDrafts[tab.name]"
            :invalid="!!jsonErrors[tab.name]"
            language="json"
            :min-height="jsonEditorHeight"
            :read-only="readOnly"
            @update:model-value="(val) => onJsonDraftChange(tab.name, val)"
          />
          <p v-if="jsonErrors[tab.name]" class="prefab-api-panel__error">{{ jsonErrors[tab.name] }}</p>
        </el-tab-pane>
      </el-tabs>
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
import ScriptSourceEditor from '@/components/script/ScriptSourceEditor.vue'
import {
  HTTP_METHODS,
  apisToPreviewRows,
  emptyPrefabricatedApi,
  parseApis,
  resolveApiMethod,
  setApiMethod,
} from '../utils/templateForm'
import {
  JSON_DRAFT_TAB_LABELS,
  apiToJsonDrafts,
  applyPrefabJsonDraftChange,
  emptyJsonDraftState,
  validatePrefabApiDrafts,
} from '../utils/prefabApiDrafts'

defineProps({
  readOnly: { type: Boolean, default: false },
})

const apis = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)
const activeJsonTab = ref('request')
const jsonEditorHeight = 200

const { drafts: initialDrafts, errors: initialErrors } = emptyJsonDraftState()
const jsonDrafts = ref({ ...initialDrafts })
const jsonErrors = ref({ ...initialErrors })

const tableRows = computed(() =>
  apisToPreviewRows(apis.value).map((row, index) => ({ ...row, _index: index })),
)

const tableRef = ref(null)

const selectedApi = computed(() => {
  if (selectedIndex.value < 0 || selectedIndex.value >= apis.value.length) return null
  return apis.value[selectedIndex.value]
})

function replaceApisAt(index, next) {
  apis.value = apis.value.map((item, idx) => (idx === index ? next : item))
}

const selectedMethod = computed({
  get() {
    if (!selectedApi.value) return 'GET'
    return resolveApiMethod(selectedApi.value)
  },
  set(method) {
    if (!selectedApi.value || selectedIndex.value < 0) return
    replaceApisAt(selectedIndex.value, setApiMethod(selectedApi.value, method))
    refreshJsonDrafts()
  },
})

const selectedAuthMode = computed({
  get() {
    return String(selectedApi.value?.authConfig?.mode || 'inherit').trim() || 'inherit'
  },
  set(mode) {
    if (!selectedApi.value || selectedIndex.value < 0) return
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      authConfig: {
        ...(selectedApi.value.authConfig || {}),
        mode: String(mode || 'inherit').trim() || 'inherit',
      },
    })
    refreshJsonDrafts()
  },
})

function refreshJsonDrafts() {
  const { drafts, errors } = emptyJsonDraftState()
  if (selectedApi.value) {
    jsonDrafts.value = apiToJsonDrafts(selectedApi.value)
  } else {
    jsonDrafts.value = drafts
  }
  jsonErrors.value = errors
}

function setCurrentTableRow() {
  nextTick(() => {
    const row = tableRows.value.find((item) => item._index === selectedIndex.value)
    tableRef.value?.setCurrentRow(row || undefined)
  })
}

function ensureSelection() {
  if (!apis.value.length) {
    selectedIndex.value = -1
    refreshJsonDrafts()
    setCurrentTableRow()
    return
  }
  if (selectedIndex.value < 0 || selectedIndex.value >= apis.value.length) {
    selectedIndex.value = 0
  }
  refreshJsonDrafts()
  setCurrentTableRow()
}

watch(() => apis.value.length, ensureSelection, { immediate: true })
watch(() => selectedIndex.value, refreshJsonDrafts)

watch(
  () => selectedApi.value,
  (api) => {
    if (!api || activeJsonTab.value !== 'raw' || jsonErrors.value.raw) return
    jsonDrafts.value.raw = JSON.stringify(api, null, 2)
  },
  { deep: true },
)

function handleRowClick(row) {
  if (row?._index == null || row._index < 0) return
  selectedIndex.value = row._index
}

function handleAdd() {
  const next = [...parseApis(apis.value), emptyPrefabricatedApi()]
  apis.value = next
  selectedIndex.value = next.length - 1
  activeJsonTab.value = 'request'
  refreshJsonDrafts()
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
  const next = parseApis(apis.value)
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  apis.value = next
  selectedIndex.value = to
}

function onJsonDraftChange(tab, text) {
  jsonDrafts.value[tab] = text
  const result = applyPrefabJsonDraftChange({
    tab,
    text,
    apis: apis.value,
    selectedIndex: selectedIndex.value,
  })
  jsonErrors.value = result.errors
  if (!result.ok) return

  apis.value = result.apis
  if (result.draftPatch) {
    Object.assign(jsonDrafts.value, result.draftPatch)
  }
  if (tab === 'raw') {
    nextTick(refreshJsonDrafts)
  }
}

function validate() {
  return validatePrefabApiDrafts({
    apis: apis.value,
    jsonDrafts: jsonDrafts.value,
    jsonErrors: jsonErrors.value,
  })
}

defineExpose({ validate })
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
  }

  &__tabs {
    :deep(.el-tabs__content) {
      padding-top: 4px;
    }
  }

  &__error {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--el-color-danger);
  }

  &__empty {
    padding: 24px 0;
  }
}
</style>
