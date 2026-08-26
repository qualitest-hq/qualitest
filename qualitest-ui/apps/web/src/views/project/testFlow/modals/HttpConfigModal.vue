<template>
  <Teleport to="body">
    <div v-if="visible" class="http-modal-overlay" @click.self="close">
      <div aria-labelledby="httpModalTitle" class="http-modal" role="dialog">
        <div class="http-modal__head">
          <div class="http-modal__head-main">
            <div id="httpModalTitle" class="http-modal__title">HTTP 接口配置</div>
            <div class="http-modal__subtitle">编辑完整有效请求；保存时只写入与资产默认不同的测值；路径跟随所绑 API</div>
          </div>
          <div class="http-modal__actions">
            <button class="btn btn--ghost" type="button" @click="clearOverrides">清除测值覆盖</button>
            <button class="btn btn--ghost" type="button" @click="close">取消</button>
            <button class="btn btn--primary" type="button" @click="save">保存</button>
          </div>
        </div>
        <div class="http-modal__body">
          <aside class="http-modal__apis">
            <div class="http-modal__apis-head">API 列表</div>
            <div class="api-picker__search-wrap">
              <input
                  v-model="apiSearch"
                  class="api-picker__search"
                  placeholder="关键字 / URL / Method"
                  type="search"
              />
            </div>
            <div class="api-picker__meta">{{ apiMeta }}</div>
            <div class="api-picker__list">
              <template v-if="groupedApis.length">
                <template v-for="group in groupedApis" :key="group.name">
                  <div class="api-picker__group">{{ group.name }}</div>
                  <button
                      v-for="api in group.apis"
                      :key="api.testProjectApiId"
                      :class="{ 'is-selected': String(draft?.testProjectApiId) === String(api.testProjectApiId) }"
                      class="api-picker__item"
                      type="button"
                      @click="selectApi(api.testProjectApiId)"
                  >
                    <span :class="getMethodBadgeClass(api.httpMethod)">{{ api.httpMethod || '—' }}</span>
                    <span class="api-picker__item-body">
                      <span class="api-picker__item-name">{{ api.apiName || api.label }}</span>
                      <span class="api-picker__item-path">{{ api.apiPath || '' }}</span>
                    </span>
                  </button>
                </template>
              </template>
              <div v-else class="api-picker__empty">无匹配接口</div>
            </div>
          </aside>
          <main class="http-modal__workbench">
            <template v-if="draft?.testProjectApiId">
              <div class="debug-url-bar">
                <select v-model="draft.requestConfig.method" class="debug-method-select">
                  <option v-for="m in HTTP_METHODS" :key="m" :value="m">{{ m }}</option>
                </select>
                <input
                    v-model="draft.apiPath"
                    class="debug-url-input"
                    placeholder="/api/path"
                    readonly
                    title="路径跟随所绑 API 资产，不可在节点上覆盖"
                    type="text"
                />
              </div>
              <ul v-if="nodeHealthWarnings.length" class="http-modal__health">
                <li v-for="(w, i) in nodeHealthWarnings" :key="i">{{ w.message || w.detail || w.code }}</li>
              </ul>
              <nav class="debug-inner-tablist">
                <button
                    v-for="tab in workbenchTabs"
                    :key="tab.id"
                    :class="{ 'is-active': activeTab === tab.id }"
                    class="debug-inner-tab"
                    type="button"
                    @click="activeTab = tab.id"
                >
                  {{ tab.label }}
                  <span v-if="tab.count > 0" class="debug-tab-count">{{ tab.count }}</span>
                </button>
              </nav>
              <div :class="['debug-inner-panel', { 'debug-inner-panel--body': activeTab === 'body' }]">
                <DebugKvSheet v-if="activeTab === 'headers'" v-model="draft.headerRows" label-key="headers" />
                <DebugKvSheet v-else-if="activeTab === 'query'" v-model="draft.requestConfig.queryParams" label-key="query" />
                <DebugKvSheet v-else-if="activeTab === 'path'" v-model="draft.requestConfig.pathParams" label-key="path" />
                <DebugKvSheet v-else-if="activeTab === 'cookies'" v-model="draft.cookieRows" label-key="cookies" />
                <HttpConfigBodyPanel
                    v-else-if="activeTab === 'body'"
                    ref="bodyPanelRef"
                    v-model="draft.requestConfig.body"
                />
              </div>
            </template>
            <div v-else class="http-modal__empty">
              <div class="http-modal__empty-icon">←</div>
              请从左侧选择项目接口
            </div>
          </main>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
/**
 * HTTP 节点接口配置弹窗。
 * 编辑时展示完整有效请求；保存时只落盘 requestValueOverrides 差分，不写 requestConfig / apiPath。
 * 路径输入框只读，展示所绑 API 资产路径。
 * 打开后若本节点有语义健康告警（孤儿测值、抽取路径等），在工作台顶部列出。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { getTestProjectApi, getTestProjectApiTree } from '@/api/project/testProjectApi'

import DebugKvSheet from '../components/DebugKvSheet.vue'
import HttpConfigBodyPanel from '../components/HttpConfigBodyPanel.vue'
import { useFlowNodes } from '../composables/useFlowNodes'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { isUrlencodedBodyMode } from '@/views/project/testProject/utils/bodyModeUtils'
import {
  applyWorkbenchToNodeData,
  buildWorkbenchFromApiAndNode,
  buildWorkbenchFromApiDetail,
  countFilledRows,
  HTTP_METHODS,
} from '../utils/httpWorkbenchUtils'
import { updateSummary } from '../utils/nodeDataUtils'
import { getMethodBadgeClass } from '../constants/flowConfig'

const props = defineProps({
  visible: { type: Boolean, default: false },
  nodeId: { type: String, default: '' },
})

const emit = defineEmits(['update:visible'])

const store = useFlowCanvasStore()
const apiHealth = useApiHealthStore()
const { replaceNodeData } = useFlowNodes()

/** 当前弹窗节点上的语义健康告警 */
const nodeHealthWarnings = computed(() => apiHealth.warningsForNode(props.nodeId))

const draft = ref(null)
/** 打开/换绑 API 时的资产有效配置，保存时用作差分基线 */
const assetBaseline = ref(null)
const activeTab = ref('headers')
const apiSearch = ref('')
const apiTree = ref([])
const bodyPanelRef = ref(null)

function flattenApiTree(nodes, groupPath = '') {
  const out = []
  ;(nodes || []).forEach((n) => {
    if (n.nodeType === 'api' || n.testProjectApiId) {
      out.push({
        testProjectApiId: n.testProjectApiId,
        apiName: n.apiName || n.label,
        apiPath: n.apiPath,
        httpMethod: n.httpMethod,
        groupPath: groupPath || n.groupPath || '未分组',
      })
    } else if (n.children?.length) {
      const g = n.label || n.groupName || groupPath
      out.push(...flattenApiTree(n.children, g))
    }
  })
  return out
}

const allApis = computed(() => flattenApiTree(apiTree.value))

const filteredApis = computed(() => {
  const kw = apiSearch.value.trim().toLowerCase()
  if (!kw) return allApis.value
  return allApis.value.filter((a) => {
    const name = (a.apiName || '').toLowerCase()
    const path = (a.apiPath || '').toLowerCase()
    const method = (a.httpMethod || '').toLowerCase()
    const group = (a.groupPath || '').toLowerCase()
    return name.includes(kw) || path.includes(kw) || method.includes(kw) || group.includes(kw)
  })
})

const groupedApis = computed(() => {
  const map = new Map()
  filteredApis.value.forEach((api) => {
    const g = api.groupPath || '未分组'
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(api)
  })
  return [...map.entries()].map(([name, apis]) => ({ name, apis }))
})

const apiMeta = computed(() => {
  const total = allApis.value.length
  const filtered = filteredApis.value.length
  return apiSearch.value.trim() ? `匹配 ${filtered} / ${total} 个接口` : `共 ${total} 个接口`
})

const workbenchTabs = computed(() => {
  if (!draft.value) return []
  const d = draft.value
  const body = d.requestConfig.body
  let bodyCount = 0
  if (body.mode === 'json' && String(body.json?.example || '').trim()) bodyCount = 1
  if (isUrlencodedBodyMode(body.mode)) bodyCount = countFilledRows(body.urlencoded)
  // form-data：按已填行数显示 Body 标签角标
  if (body.mode === 'form-data' || body.mode === 'formData' || body.mode === 'multipart') {
    bodyCount = countFilledRows(body.formData)
  }
  return [
    { id: 'headers', label: 'Headers', count: countFilledRows(d.headerRows) },
    { id: 'query', label: 'Query', count: countFilledRows(d.requestConfig.queryParams) },
    { id: 'body', label: 'Body', count: bodyCount },
    { id: 'path', label: 'Path', count: countFilledRows(d.requestConfig.pathParams) },
    { id: 'cookies', label: 'Cookies', count: countFilledRows(d.cookieRows) },
  ]
})


async function loadApiTree() {
  if (store.canvasMode === 'template') {
    apiTree.value = store.templateApiTree || []
    return
  }
  if (!store.testProjectId) return
  try {
    const res = await getTestProjectApiTree({ testProjectId: store.testProjectId })
    apiTree.value = res?.data ?? res ?? []
  } catch {
    apiTree.value = []
  }
}

async function selectApi(apiId) {
  try {
    let detail
    if (store.canvasMode === 'template') {
      const found = (store.templateApiCatalog || []).find(
        (e) => String(e.syntheticId) === String(apiId),
      )
      detail = found?.api ?? null
      if (!detail) {
        ElMessage.error('未找到预制接口')
        return
      }
    } else {
      const res = await getTestProjectApi(apiId)
      detail = res?.data ?? res
    }
    const baseline = buildWorkbenchFromApiDetail(detail)
    assetBaseline.value = baseline
    const node = store.nodes.find((n) => n.id === props.nodeId)
    const nodeData = node?.data
      ? { ...node.data, testProjectApiId: String(apiId), apiPath: detail.apiPath || node.data.apiPath }
      : { testProjectApiId: String(apiId), apiPath: detail.apiPath || '' }
    draft.value = buildWorkbenchFromApiAndNode(detail, nodeData)
    // 换绑 API 时以资产路径为准展示
    draft.value.apiPath = baseline.apiPath
    activeTab.value = 'headers'
  } catch {
    ElMessage.error('加载接口详情失败')
  }
}

async function openForNode(nodeId) {
  const node = store.nodes.find((n) => n.id === nodeId)
  if (!node) return
  apiSearch.value = ''
  activeTab.value = 'headers'
  loadApiTree()

  const apiId = node.data?.testProjectApiId
  if (!apiId) {
    draft.value = buildWorkbenchFromApiAndNode(null, node.data || {})
    assetBaseline.value = buildWorkbenchFromApiDetail(null)
    return
  }
  try {
    let detail
    if (store.canvasMode === 'template') {
      const found = (store.templateApiCatalog || []).find(
        (e) => String(e.syntheticId) === String(apiId),
      )
      detail = found?.api ?? null
      if (!detail) {
        // 无合成 id 时仍可用节点本地 apiPath 打开
        draft.value = buildWorkbenchFromApiAndNode(null, node.data || {})
        assetBaseline.value = buildWorkbenchFromApiDetail(null)
        return
      }
    } else {
      const res = await getTestProjectApi(apiId)
      detail = res?.data ?? res
    }
    assetBaseline.value = buildWorkbenchFromApiDetail(detail)
    draft.value = buildWorkbenchFromApiAndNode(detail, node.data || {})
  } catch {
    draft.value = buildWorkbenchFromApiAndNode(null, node.data || {})
    assetBaseline.value = buildWorkbenchFromApiDetail(null)
    ElMessage.warning('加载接口详情失败，已用节点本地数据打开')
  }
}

function close() {
  emit('update:visible', false)
  draft.value = null
  assetBaseline.value = null
}

function clearOverrides() {
  if (!draft.value || !assetBaseline.value) {
    ElMessage.warning('请先选择项目接口')
    return
  }
  // 测值恢复为资产默认；保留当前 headers/cookies
  const restored = JSON.parse(JSON.stringify(assetBaseline.value))
  restored.headerRows = draft.value.headerRows
  restored.cookieRows = draft.value.cookieRows
  draft.value = restored
  ElMessage.success('已恢复为资产默认测值（保存后生效）')
}

function save() {
  if (!draft.value?.testProjectApiId) {
    ElMessage.warning('请先选择项目接口')
    return
  }
  if (draft.value.requestConfig.body?.mode === 'json' && !bodyPanelRef.value?.applyJsonTextBeforeSave()) {
    ElMessage.warning('JSON Body 格式不正确')
    activeTab.value = 'body'
    return
  }
  const node = store.nodes.find((n) => n.id === props.nodeId)
  if (!node) {
    close()
    return
  }
  const data = { ...node.data }
  applyWorkbenchToNodeData(data, draft.value, assetBaseline.value)
  data.callMode = 'project'
  updateSummary('http', data)
  replaceNodeData(props.nodeId, data)
  ElMessage.success('HTTP 节点参数已更新')
  close()
}

watch(
  () => [props.visible, props.nodeId],
  ([vis, id]) => {
    if (vis && id) openForNode(id)
  },
)
</script>

<style lang="scss">
@use '../styles/flowCanvasTokens.scss' as flow;

/* 弹窗挂载在 body，需在此定义 --pd-* 变量与 .btn */
.http-modal-overlay {
  @include flow.flow-pd-modal-vars;
  --pd-bg-page: #e6f0fb;

  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.45);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  color: var(--pd-text);

  @include flow.flow-pd-buttons-nested(30px, 11px);

  .btn {
    font-family: inherit;
  }
}

.http-modal {
  width: min(1120px, calc(100vw - 32px));
  height: min(760px, calc(100vh - 32px));
  display: flex;
  flex-direction: column;
  background: var(--pd-surface-elevated);
  border: 1px solid var(--pd-border-muted);
  border-radius: var(--pd-radius);
  box-shadow: 0 20px 50px rgba(20, 60, 120, 0.18);
  overflow: hidden;
}

.http-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--pd-divider);
  background: var(--pd-gradient-panel-head);
  flex-shrink: 0;
}

.http-modal__head-main {
  min-width: 0;
}

.http-modal__title {
  font-weight: 600;
  font-size: 14px;
  color: var(--pd-text);
  line-height: 1.3;
}

.http-modal__subtitle {
  margin-top: 2px;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.4;
}

.http-modal__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.http-modal__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.http-modal__apis {
  width: 268px;
  flex-shrink: 0;
  border-right: 1px solid var(--pd-divider);
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--pd-surface);
}

.http-modal__apis-head {
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text);
  border-bottom: 1px solid var(--pd-divider);
  background: var(--pd-gradient-panel-head);
  flex-shrink: 0;
}

.api-picker__search-wrap {
  padding: 8px 10px 0;
  flex-shrink: 0;
}

.api-picker__search {
  width: 100%;
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface-elevated);
  font-size: 12px;
  font-family: inherit;
  color: var(--pd-text);
  box-sizing: border-box;
  transition: box-shadow 0.15s, border-color 0.15s;

  &::placeholder {
    color: var(--pd-text-muted);
  }

  &:focus {
    outline: none;
    border-color: var(--pd-primary);
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
  }
}

.api-picker__meta {
  padding: 4px 10px 2px;
  font-size: 10px;
  color: var(--pd-text-muted);
  flex-shrink: 0;
}

.api-picker__list {
  flex: 1;
  overflow: auto;
  padding: 2px 6px 8px;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
  }
}

.api-picker__group {
  padding: 8px 6px 2px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: var(--pd-text-muted);
}

.api-picker__item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  padding: 6px 7px;
  margin-bottom: 1px;
  border: 1px solid transparent;
  border-radius: var(--pd-radius-sm);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    background: color-mix(in srgb, var(--pd-primary-soft) 65%, #fff);
    border-color: color-mix(in srgb, var(--pd-primary) 18%, transparent);
  }

  &.is-selected {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
    box-shadow: var(--pd-shadow-card);
  }
}

.api-picker__item-body {
  min-width: 0;
}

.api-picker__item-name {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text);
  line-height: 1.35;
}

.api-picker__item-path {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--pd-text-muted);
  font-family: ui-monospace, Consolas, monospace;
  line-height: 1.4;
  word-break: break-all;
}

.api-picker__empty {
  padding: 24px 16px;
  text-align: center;
  color: var(--pd-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.http-modal__workbench {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  padding: 10px 12px;
  overflow: hidden;
  background: var(--pd-surface-elevated);
}

.http-modal__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
  color: var(--pd-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.http-modal__empty-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  border: 1px dashed var(--pd-border-subtle);
  background: var(--pd-bg-sunken);
  font-size: 16px;
  color: var(--pd-text-muted);
}

.debug-url-bar {
  display: flex;
  gap: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: var(--pd-radius-sm);
  background: var(--pd-bg-sunken);
  flex-shrink: 0;
}

.http-modal__health {
  /* 本节点语义告警条：孤儿测值、抽取路径等 */
  margin: 0 0 8px;
  padding: 8px 10px 8px 24px;
  border-radius: 6px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #a16207;
  font-size: 12px;
  line-height: 1.45;
  flex-shrink: 0;
}

.debug-method-select {
  width: 88px;
  flex-shrink: 0;
  height: 30px;
  padding: 0 7px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface-elevated);
  font-size: 11px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--pd-text);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:focus {
    outline: none;
    border-color: var(--pd-primary);
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
  }
}

.debug-url-input {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface-elevated);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--pd-text);
  transition: border-color 0.15s, box-shadow 0.15s;

  &::placeholder {
    color: var(--pd-text-muted);
  }

  &:focus {
    outline: none;
    border-color: var(--pd-primary);
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
  }
}

.debug-inner-tablist {
  display: flex;
  gap: 0;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--pd-divider);
  flex-wrap: wrap;
  flex-shrink: 0;
}

.debug-inner-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  color: var(--pd-text-muted);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--pd-text);
  }

  &.is-active {
    color: var(--pd-primary);
    border-bottom-color: var(--pd-primary);
  }
}

.debug-tab-count {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--pd-primary) 12%, #fff);
  color: var(--pd-primary);
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  .debug-inner-tab.is-active & {
    background: var(--pd-primary);
    color: #fff;
  }
}

.debug-inner-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: var(--pd-radius-sm);
  background: var(--pd-surface);

  &--body {
    padding: 0;
    gap: 0;
  }
}

.field__hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 10px;
  text-align: center;
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}

.method-badge {
  flex-shrink: 0;
  min-width: 36px;
  padding: 1px 4px;
  text-align: center;
  font-size: 10px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
  line-height: 1.5;
  border-radius: 2px;
  background: var(--pd-bg-sunken);
  color: #606266;
  border: 1px solid var(--pd-border-muted);

  &.is-GET { color: #67c23a; border-color: #c2e7b0; background: #f0f9eb; }
  &.is-POST { color: #e6a23c; border-color: #f5dab1; background: #fdf6ec; }
  &.is-PUT { color: #409eff; border-color: #b3d8ff; background: #ecf5ff; }
  &.is-PATCH { color: #909399; border-color: #dcdfe6; background: #f4f4f5; }
  &.is-DELETE { color: #f56c6c; border-color: #fbc4c4; background: #fef0f0; }
}
</style>
