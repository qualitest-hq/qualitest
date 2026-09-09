<template>
  <div class="tpl-prefab-section prefab-api-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制接口</span>
      <div v-if="!readOnly" class="tpl-prefab-section__actions">
        <el-button
          :disabled="!selectedRows.length"
          size="small"
          @click="openBatchGroup"
        >批量改分组</el-button>
        <el-button icon="Plus" size="small" type="primary" @click="handleAdd">新增</el-button>
      </div>
    </div>

    <div class="tpl-prefab-section__body prefab-api-panel__layout">
      <aside class="prefab-api-panel__tree">
        <el-input
          v-model="treeFilterText"
          clearable
          placeholder="过滤目录"
          prefix-icon="Search"
          size="small"
        />
        <div class="prefab-api-panel__tree-toolbar">
          <el-button link size="small" type="primary" @click="clearGroupFilter">全部接口</el-button>
          <span v-if="groupFilterPath" class="prefab-api-panel__filter-hint" :title="groupFilterPath">
            {{ groupFilterPath }}
          </span>
        </div>
        <el-tree
          v-if="apiTree.length"
          ref="treeRef"
          :data="apiTree"
          :filter-node-method="filterTreeNode"
          :props="{ label: 'label', children: 'children' }"
          class="prefab-api-panel__el-tree"
          highlight-current
          node-key="treeNodeKey"
          @node-click="handleTreeNodeClick"
        >
          <template #default="{ node, data }">
            <span class="prefab-api-panel__tree-node">
              <svg-icon
                v-if="data.nodeType !== 'api'"
                class="prefab-api-panel__tree-folder"
                icon-class="folder"
              />
              <span
                v-else
                :class="getApiHttpMethodBadgeClass(data.httpMethod)"
                class="prefab-api-panel__method-badge"
              >{{ data.httpMethod || '—' }}</span>
              <span class="prefab-api-panel__tree-label">{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
        <el-empty
          v-else
          :image-size="48"
          class="prefab-api-panel__tree-empty"
          description="暂无目录"
        />
      </aside>

      <div class="prefab-api-panel__table-wrap">
        <template v-if="allTableRows.length">
          <el-table
            v-if="filteredTableRows.length"
            ref="tableRef"
            :data="filteredTableRows"
            border
            class="tpl-prefab-section__table"
            highlight-current-row
            row-key="_index"
            size="small"
            @row-click="handleRowClick"
            @selection-change="handleSelectionChange"
          >
            <el-table-column v-if="!readOnly" type="selection" width="42" />
            <el-table-column label="方法" prop="method" width="80" />
            <el-table-column label="路径" min-width="120" prop="apiPath" show-overflow-tooltip />
            <el-table-column label="名称" min-width="90" prop="apiName" show-overflow-tooltip />
            <el-table-column label="分组" min-width="120" prop="apiGroupDisplay" show-overflow-tooltip />
            <el-table-column label="鉴权" prop="authModeLabel" width="88" />
            <el-table-column align="center" label="操作" :width="readOnly ? 72 : 200">
              <template #default="scope">
                <el-button
                  link
                  type="primary"
                  @click.stop="openDetail(scope.row._index)"
                >{{ readOnly ? '查看' : '编辑' }}</el-button>
                <template v-if="!readOnly">
                  <el-button
                    link
                    type="danger"
                    @click.stop="handleRemove(scope.row._index)"
                  >删除</el-button>
                  <el-button
                    link
                    type="primary"
                    :disabled="scope.row._index <= 0"
                    @click.stop="handleMove(scope.row._index, -1)"
                  >上移</el-button>
                  <el-button
                    link
                    type="primary"
                    :disabled="scope.row._index >= apis.length - 1"
                    @click.stop="handleMove(scope.row._index, 1)"
                  >下移</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-else
            :image-size="48"
            class="tpl-prefab-section__empty"
            description="当前分组下无接口"
          />
        </template>
        <el-empty
          v-else
          :image-size="56"
          class="tpl-prefab-section__empty"
          description="暂无预制接口，请点击新增"
        />
      </div>
    </div>

    <el-dialog
      v-model="batchGroupVisible"
      append-to-body
      title="批量改分组"
      width="420px"
      @closed="batchGroupPath = ''"
    >
      <el-form label-width="72px" size="small">
        <el-form-item label="分组">
          <el-autocomplete
            v-model="batchGroupPath"
            :fetch-suggestions="queryGroupSuggestions"
            clearable
            placeholder="如 管理端.系统.登录"
            style="width: 100%"
          />
        </el-form-item>
        <p class="prefab-api-panel__hint">将写入选中 {{ selectedRows.length }} 条接口的 apiGroup（点号多级）</p>
      </el-form>
      <template #footer>
        <el-button @click="batchGroupVisible = false">取 消</el-button>
        <el-button type="primary" @click="confirmBatchGroup">确 定</el-button>
      </template>
    </el-dialog>

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
                <span class="prefab-api-panel__text">{{ selectedApiGroupDisplay }}</span>
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
            <el-col :span="12">
              <el-form-item label="上传保护">
                <span class="prefab-api-panel__text">{{ isSyncProtectedOn(selectedApi) ? '开' : '关' }}</span>
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
                <el-autocomplete
                  v-model="selectedApiGroup"
                  :fetch-suggestions="queryGroupSuggestions"
                  clearable
                  placeholder="如 管理端.系统.登录"
                  style="width: 100%"
                />
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
            <el-col :span="12">
              <el-form-item label="上传保护">
                <el-switch
                  v-model="selectedSyncProtected"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                />
                <div class="prefab-api-panel__hint">
                  开启后种子到项目的接口默认受保护，插件导入会跳过
                </div>
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
 * 左侧由 apiGroup 点号路径合成目录树；右侧表格支持过滤与批量改分组。
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
import { getApiHttpMethodBadgeClass } from '@/views/project/testProject/utils/httpMethodMeta'
import {
  DEFAULT_API_GROUP,
  apiGroupMatchesPath,
  collectApiGroupPaths,
  normalizeApiGroup,
  synthesizeTemplateApiCatalog,
} from '../utils/synthesizeTemplateApiTree'

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
const treeRef = ref(null)
const debugTabRef = ref(null)
const responseConfigPanelRef = ref(null)

const treeFilterText = ref('')
const groupFilterPath = ref('')
const selectedRows = ref([])
const batchGroupVisible = ref(false)
const batchGroupPath = ref('')

const apiTree = computed(() => synthesizeTemplateApiCatalog(apis.value).tree)

const allTableRows = computed(() =>
  apisToPreviewRows(apis.value).map((row, index) => ({
    ...row,
    _index: index,
    apiGroupDisplay: normalizeApiGroup(row.apiGroup),
  })),
)

const filteredTableRows = computed(() => {
  if (!groupFilterPath.value) return allTableRows.value
  return allTableRows.value.filter((row) =>
    apiGroupMatchesPath(row.apiGroup, groupFilterPath.value),
  )
})

const existingGroupPaths = computed(() => collectApiGroupPaths(apis.value))

watch(treeFilterText, (val) => {
  treeRef.value?.filter(val)
})

function filterTreeNode(value, data) {
  if (!value) return true
  const kw = String(value).trim().toLowerCase()
  if (!kw) return true
  const label = String(data.label || data.apiName || data.groupName || '').toLowerCase()
  const path = String(data.apiPath || data.groupPath || '').toLowerCase()
  const method = String(data.httpMethod || '').toLowerCase()
  return label.includes(kw) || path.includes(kw) || method.includes(kw)
}

function clearGroupFilter() {
  groupFilterPath.value = ''
  treeRef.value?.setCurrentKey(null)
}

function handleTreeNodeClick(data) {
  if (!data) return
  if (data.nodeType === 'api') {
    const idx =
      typeof data.sourceIndex === 'number' && data.sourceIndex >= 0
        ? data.sourceIndex
        : apis.value.findIndex(
            (api) => String(api?.testProjectApiId || '') === String(data.testProjectApiId),
          )
    if (idx >= 0 && idx < apis.value.length) {
      groupFilterPath.value = normalizeApiGroup(data.groupPath || apis.value[idx]?.apiGroup)
      openDetail(idx)
    }
    return
  }
  if (data.nodeType === 'group') {
    groupFilterPath.value = String(data.groupPath || data.groupName || '').trim()
  }
}

function handleSelectionChange(rows) {
  selectedRows.value = Array.isArray(rows) ? rows : []
}

function queryGroupSuggestions(queryString, cb) {
  const q = String(queryString || '').trim().toLowerCase()
  const list = existingGroupPaths.value
    .filter((p) => !q || p.toLowerCase().includes(q))
    .map((value) => ({ value }))
  cb(list)
}

function openBatchGroup() {
  if (!selectedRows.value.length) return
  batchGroupPath.value = selectedRows.value[0]?.apiGroupDisplay || ''
  batchGroupVisible.value = true
}

function confirmBatchGroup() {
  const path = normalizeApiGroup(batchGroupPath.value)
  const indexes = new Set(selectedRows.value.map((r) => r._index))
  apis.value = parseApis(apis.value).map((api, idx) =>
    indexes.has(idx) ? { ...api, apiGroup: path } : api,
  )
  batchGroupVisible.value = false
  selectedRows.value = []
  nextTick(() => tableRef.value?.clearSelection?.())
}

const selectedApi = computed(() => {
  if (selectedIndex.value < 0 || selectedIndex.value >= apis.value.length) return null
  return apis.value[selectedIndex.value]
})

const selectedApiGroupDisplay = computed(() => {
  if (!selectedApi.value) return '—'
  const raw = String(selectedApi.value.apiGroup || '').trim()
  return raw || DEFAULT_API_GROUP
})

const selectedApiGroup = computed({
  get() {
    return String(selectedApi.value?.apiGroup ?? '')
  },
  set(value) {
    if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      apiGroup: value,
    })
  },
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

const selectedSyncProtected = computed({
  get() {
    return isSyncProtectedOn(selectedApi.value)
  },
  set(on) {
    if (!selectedApi.value || selectedIndex.value < 0 || props.readOnly) return
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      syncProtected: on ? 1 : 0,
    })
  },
})

function isSyncProtectedOn(api) {
  const v = api?.syncProtected
  return v === 1 || v === true || v === '1'
}

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
    const row = allTableRows.value.find((item) => item._index === selectedIndex.value)
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
  // 详情关闭时规范化分组路径，便于目录树稳定
  if (selectedApi.value && selectedIndex.value >= 0 && !props.readOnly) {
    replaceApisAt(selectedIndex.value, {
      ...selectedApi.value,
      apiGroup: normalizeApiGroup(selectedApi.value.apiGroup),
    })
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
  const seedGroup = groupFilterPath.value || ''
  const next = [
    ...parseApis(apis.value),
    { ...emptyPrefabricatedApi(), apiGroup: seedGroup },
  ]
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

function handleRemove(index) {
  if (props.readOnly || index == null || index < 0 || index >= apis.value.length) return
  if (detailVisible.value) {
    detailVisible.value = false
  }
  const next = parseApis(apis.value)
  next.splice(index, 1)
  apis.value = next
  if (selectedIndex.value === index) {
    selectedIndex.value = next.length ? Math.min(index, next.length - 1) : -1
  } else if (selectedIndex.value > index) {
    selectedIndex.value -= 1
  }
  ensureSelection()
}

function handleMove(from, delta) {
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

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';
</style>

<style scoped lang="scss">
.prefab-api-panel {
  width: 100%;

  &__layout {
    display: flex;
    gap: 12px;
    align-items: stretch;
    min-height: 220px;
  }

  &__tree {
    flex: 0 0 300px;
    max-width: 300px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 8px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    background: var(--el-fill-color-blank);
    min-height: 200px;
    max-height: 420px;
  }

  &__tree-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 22px;
  }

  &__filter-hint {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__el-tree {
    flex: 1;
    overflow: auto;
    background: transparent;

    :deep(.el-tree-node__content) {
      height: 30px;
      border-radius: 4px;
    }
  }

  &__tree-empty {
    padding: 12px 0;
  }

  &__tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    overflow: hidden;
    width: 100%;
  }

  &__tree-folder {
    flex-shrink: 0;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }

  &__tree-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
  }

  &__method-badge {
    flex-shrink: 0;
    min-width: 32px;
    padding: 0 3px;
    text-align: center;
    font-size: 10px;
    font-weight: 700;
    font-family: ui-monospace, Consolas, monospace;
    line-height: 1.5;
    border-radius: 2px;
    border: 1px solid var(--el-border-color);
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);

    &.is-GET {
      color: #67c23a;
      border-color: #c2e7b0;
      background: #f0f9eb;
    }

    &.is-POST {
      color: #e6a23c;
      border-color: #f5dab1;
      background: #fdf6ec;
    }

    &.is-PUT {
      color: #409eff;
      border-color: #b3d8ff;
      background: #ecf5ff;
    }

    &.is-DELETE {
      color: #f56c6c;
      border-color: #fbc4c4;
      background: #fef0f0;
    }
  }

  &__table-wrap {
    flex: 1;
    min-width: 0;
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

@media (max-width: 900px) {
  .prefab-api-panel__layout {
    flex-direction: column;
  }

  .prefab-api-panel__tree {
    flex: none;
    max-width: none;
    max-height: 240px;
  }
}
</style>
