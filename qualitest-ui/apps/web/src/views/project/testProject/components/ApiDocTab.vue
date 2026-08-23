<template>
  <div class="api-doc-preview">
    <div class="design-scroll">
      <!-- 概览 -->
      <section class="design-strip">
        <header class="design-strip__title">概览</header>
        <div class="design-strip__body doc-overview-body">
          <div class="doc-overview-hero">
            <h2 class="doc-api-title">{{ apiDetail.apiName || '—' }}</h2>
            <div class="doc-method-path-row">
              <el-tag :class="getDocHttpMethodTagClass(workbench.draftRequestConfig.method)" effect="plain" size="small">
                {{ workbench.draftRequestConfig.method || 'GET' }}
              </el-tag>
              <code class="doc-path-code">{{ workbench.draftApiPath || '—' }}</code>
            </div>
            <div class="doc-meta-chips">
              <span :class="['doc-meta-chip', apiStatusChipClass]">
                <span class="doc-meta-chip__label">状态</span>
                <span class="doc-meta-chip__value">{{ apiStatusLabel }}</span>
              </span>
              <span class="doc-meta-chip">
                <span class="doc-meta-chip__label">协议</span>
                <span class="doc-meta-chip__value">{{ apiDetail.protocolType || '—' }}</span>
              </span>
              <span class="doc-meta-chip">
                <span class="doc-meta-chip__label">分组</span>
                <span class="doc-meta-chip__value">{{ apiDetail.apiGroup || '—' }}</span>
              </span>
              <span v-if="syncTimeLabel" class="doc-meta-chip doc-meta-chip--wide">
                <span class="doc-meta-chip__label">最新同步</span>
                <span class="doc-meta-chip__value">{{ syncTimeLabel }}</span>
              </span>
            </div>
          </div>
          <p v-if="apiDetail.apiDescription" class="doc-desc">{{ apiDetail.apiDescription }}</p>
        </div>
      </section>

      <!-- 请求参数（仅展示有内容的分块） -->
      <section class="design-strip">
        <header class="design-strip__title">请求参数</header>
        <div class="design-strip__body doc-req-body">
          <template v-if="!hasRequestParamsContent">
            <div class="doc-empty-block">
              <p class="doc-empty-block__title">暂无请求参数</p>
              <p class="doc-empty-block__desc">可在「设计」或「调试」中配置 Headers、Query、Body 等</p>
            </div>
          </template>
          <template v-else>
            <div v-if="showDeclaredHeadersSection" class="doc-subblock">
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">声明 Headers（契约）</span>
                <span class="doc-subblock-count">{{ declaredHeaderRows.length }}</span>
              </h3>
              <p class="doc-subblock-hint">描述请求头参数定义，不直接作为调试发出的请求头。</p>
              <div class="doc-kv-table-wrap">
                <DocPreviewDataTable :rows="declaredHeaderRows" variant="kv5"/>
              </div>
            </div>
            <div v-if="showHeadersSection" class="doc-subblock">
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">调试 Headers（发出）</span>
                <span class="doc-subblock-count">{{ headersRows.length }}</span>
              </h3>
              <p class="doc-subblock-hint">实际调试/跑流发送时使用的请求头键值。</p>
              <div class="doc-kv-table-wrap">
                <DocPreviewDataTable :rows="headersRows" variant="kv4"/>
              </div>
            </div>
            <div v-if="showCookiesSection" class="doc-subblock">
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">Cookies</span>
                <span class="doc-subblock-count">{{ cookiesRows.length }}</span>
              </h3>
              <div class="doc-kv-table-wrap">
                <DocPreviewDataTable :rows="cookiesRows" variant="kv4"/>
              </div>
            </div>
            <div v-if="showPathSection" class="doc-subblock">
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">Path</span>
                <span class="doc-subblock-count">{{ pathRows.length }}</span>
              </h3>
              <div class="doc-kv-table-wrap">
                <DocPreviewDataTable :rows="pathRows" variant="kv5"/>
              </div>
            </div>
            <div v-if="showQuerySection" class="doc-subblock">
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">Query</span>
                <span class="doc-subblock-count">{{ queryRows.length }}</span>
              </h3>
              <div class="doc-kv-table-wrap">
                <DocPreviewDataTable :rows="queryRows" variant="kv5"/>
              </div>
            </div>

            <div
                v-if="showBodySection"
                class="doc-subblock"
                :class="{ 'doc-subblock--body': showBodyTopDivider }"
            >
              <h3 class="doc-subblock-title">
                <span class="doc-subblock-title__text">Body</span>
              </h3>
              <template v-if="bodyMode === 'form-data'">
                <p class="doc-body-hint"><span class="doc-mime-pill">multipart/form-data</span></p>
                <div class="doc-kv-table-wrap">
                  <DocPreviewDataTable :rows="formDataRows" variant="kv5"/>
                </div>
              </template>
              <template v-else-if="bodyMode === 'x-www-form-urlencoded'">
                <p class="doc-body-hint"><span class="doc-mime-pill">application/x-www-form-urlencoded</span></p>
                <div class="doc-kv-table-wrap">
                  <DocPreviewDataTable :rows="urlencodedRows" variant="kv5"/>
                </div>
              </template>
              <template v-else-if="bodyMode === 'json'">
                <p class="doc-body-hint"><span class="doc-mime-pill">application/json</span></p>
                <div class="doc-json-split">
                  <div class="doc-json-split__col doc-json-panel">
                    <p class="doc-col-label">数据结构</p>
                    <div class="doc-schema-table-wrap doc-schema-table-wrap--expand">
                      <DocPreviewDataTable
                          v-if="requestJsonFlatRows.length"
                          expand-flow
                          :rows="requestJsonFlatRows"
                          table-class="doc-schema-table"
                          variant="schema"
                      />
                      <p v-else class="doc-empty-hint">未配置结构</p>
                    </div>
                  </div>
                  <div class="doc-json-split__col doc-json-panel">
                    <p class="doc-col-label">示例</p>
                    <pre class="doc-json-pre doc-json-pre--expand">{{ requestJsonExampleText || '（无示例）' }}</pre>
                  </div>
                </div>
              </template>
              <template v-else-if="bodyMode === 'xml' || bodyMode === 'text'">
                <p class="doc-body-hint">
                  <span class="doc-mime-pill">{{ bodyMode === 'xml' ? 'application/xml' : 'text/plain' }}</span>
                </p>
                <pre class="doc-json-pre doc-json-pre--expand">{{ bodyTextContent }}</pre>
              </template>
              <template v-else-if="bodyMode === 'binary'">
                <p class="doc-body-hint"><span class="doc-mime-pill">binary</span></p>
                <p class="doc-readonly-p">{{ binaryDescription }}</p>
              </template>
              <template v-else>
                <p class="doc-empty-hint">未知 Body 模式</p>
              </template>
            </div>
          </template>
        </div>
      </section>

      <!-- 返回响应 -->
      <section class="design-strip design-strip--last">
        <header class="design-strip__title">返回响应</header>
        <div class="design-strip__body doc-resp-body">
          <template v-if="responseParse.parseError">
            <div class="doc-resp-body-padded">
              <el-alert :closable="false" show-icon title="响应配置 JSON 无法解析" type="warning"/>
              <pre v-if="responseRawSnippet" class="doc-json-pre doc-json-pre--expand doc-json-pre--warn">{{ responseRawSnippet }}</pre>
            </div>
          </template>
          <template v-else>
            <nav aria-label="响应状态" class="doc-rsp-tablist" role="tablist">
              <button
                  v-for="r in responseList"
                  :key="r.id"
                  :aria-selected="activeResponseId === r.id"
                  :class="[
                    'doc-rsp-tab',
                    httpStatusBadgeClass(r.httpStatus),
                    { active: activeResponseId === r.id }
                  ]"
                  role="tab"
                  type="button"
                  @click="activeResponseId = r.id"
              >
                <span class="doc-rsp-tab-code">{{ r.httpStatus }}</span>
                <span class="doc-rsp-tab-name">{{ r.name }}</span>
              </button>
            </nav>
            <div v-if="currentResponse" class="doc-rsp-panel" role="tabpanel">
              <div class="doc-resp-summary">
                <span :class="['doc-status-badge', httpStatusBadgeClass(currentResponse.httpStatus)]">
                  {{ currentResponse.httpStatus }}
                </span>
                <span class="doc-mime-pill doc-mime-pill--compact">{{ responseContentTypeLabel(currentResponse) }}</span>
                <span v-if="currentResponse.name" class="doc-resp-name">{{ currentResponse.name }}</span>
              </div>
              <template v-if="currentResponse.contentType === 'json'">
                <div class="doc-json-split">
                  <div class="doc-json-split__col doc-json-panel">
                    <p class="doc-col-label">数据结构</p>
                    <div class="doc-schema-table-wrap doc-schema-table-wrap--expand">
                      <DocPreviewDataTable
                          v-if="currentResponseFlatRows.length"
                          expand-flow
                          :rows="currentResponseFlatRows"
                          table-class="doc-schema-table"
                          variant="schema"
                      />
                      <p v-else class="doc-empty-hint doc-empty-hint--inline">未配置结构</p>
                    </div>
                  </div>
                  <div class="doc-json-split__col doc-json-panel">
                    <p class="doc-col-label">示例</p>
                    <pre class="doc-json-pre doc-json-pre--expand">{{ currentResponseExampleText || '（无示例）' }}</pre>
                  </div>
                </div>
              </template>
              <template v-else-if="currentResponse.contentType === 'xml'">
                <pre class="doc-json-pre doc-json-pre--expand">{{ currentResponse.xmlText?.trim() || '（空）' }}</pre>
              </template>
              <template v-else-if="currentResponse.contentType === 'binary'">
                <p class="doc-readonly-p">{{ currentResponse.binaryNote?.trim() || '（无备注）' }}</p>
              </template>
            </div>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import {getDocHttpMethodTagClass} from '@/views/project/testProject/utils/httpMethodMeta'
import {buildRequestWorkbenchStateFromDetail} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'
import {parseResponseConfigInput} from '@/views/project/testProject/utils/responseConfig'
import {
  createEmptyRootSchema,
  flattenSchemaUiRows,
  isEmptyObjectSchema,
  schemaJsonToUiRoot
} from '@/views/project/testProject/utils/jsonSchemaTree'
import {parseTime} from '@/utils/qualitest'
import DocPreviewDataTable from './DocPreviewDataTable.vue'

const props = defineProps({
  apiDetail: {
    type: Object,
    required: true
  }
})

function filterDocKvRows(rows) {
  if (!Array.isArray(rows)) return []
  return rows.filter((row) => {
    if (!row || row._enabled === false) return false
    if (row._file) return true
    const n = String(row.name ?? '').trim()
    const v = String(row.value ?? '').trim()
    const d = String(row.description ?? '').trim()
    return !!(n || v || d)
  })
}

function mapKvRowsForTable(rows) {
  return filterDocKvRows(rows).map((r) => ({
    name: String(r.name ?? '').trim() || '—',
    type: String(r.type ?? '').trim() || '—',
    required: !!r.required,
    description: String(r.description ?? '').trim(),
    value: String(r.value ?? '').trim()
  }))
}

function formatJsonPretty(val) {
  if (val == null || val === '') return ''
  if (typeof val === 'string') {
    const t = val.trim()
    if (!t) return ''
    try {
      return JSON.stringify(JSON.parse(t), null, 2)
    } catch {
      return val
    }
  }
  try {
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

function responseContentTypeLabel(r) {
  if (!r) return ''
  const ct = String(r.contentType || 'json').toLowerCase()
  if (ct === 'json') return 'application/json'
  if (ct === 'xml') return 'application/xml'
  if (ct === 'binary') return 'binary'
  return ct
}

function buildSchemaFlatTableRows(schema, treeMode) {
  if (!schema || isEmptyObjectSchema(schema)) return []
  const root = schemaJsonToUiRoot(schema || createEmptyRootSchema(), {
    mergeExampleIntoMock: false,
    treeMode
  })
  const flat = flattenSchemaUiRows(root, {
    hideRootObjectShell:
        treeMode === 'response' && String(root?.type || '').toLowerCase() === 'object',
    unwrapOuterDataWrapper: treeMode === 'response'
  })
  return flat.map(({node, depth}) => {
    const isRoot =
        depth === 0 &&
        String(node?.type || '').toLowerCase() === 'object' &&
        !(String(node?.key || '').trim())
    const fieldLabel = isRoot ? 'object' : String(node?.key ?? '').trim() || '—'
    return {
      depth,
      fieldLabel,
      type: String(node?.type ?? '').trim() || '—',
      required: node?.required === true,
      description: String(node?.description ?? '').trim()
    }
  })
}

const workbench = computed(() => buildRequestWorkbenchStateFromDetail(props.apiDetail))

const apiStatusLabel = computed(() => {
  const s = props.apiDetail?.apiStatus
  return s === '0' || s === 0 ? '禁用' : '启用'
})

const apiStatusChipClass = computed(() => {
  const s = props.apiDetail?.apiStatus
  return s === '0' || s === 0 ? 'is-disabled' : 'is-enabled'
})

function httpStatusBadgeClass(status) {
  const code = parseInt(String(status ?? ''), 10)
  if (Number.isNaN(code)) return 'is-unknown'
  if (code >= 200 && code < 300) return 'is-success'
  if (code >= 300 && code < 400) return 'is-redirect'
  if (code >= 400 && code < 500) return 'is-client'
  if (code >= 500) return 'is-server'
  return 'is-unknown'
}

const syncTimeLabel = computed(() => {
  const v = props.apiDetail?.lastSyncTime
  if (v == null || v === '') return ''
  return parseTime(v, '{y}-{m}-{d} {h}:{i}:{s}') || String(v)
})

const declaredHeaderRows = computed(() =>
    mapKvRowsForTable(workbench.value.draftRequestConfig.declaredHeaders)
)
const headersRows = computed(() => mapKvRowsForTable(workbench.value.draftHeaderRows))
const cookiesRows = computed(() => mapKvRowsForTable(workbench.value.draftCookieRows))
const pathRows = computed(() => mapKvRowsForTable(workbench.value.draftRequestConfig.pathParams))
const queryRows = computed(() => mapKvRowsForTable(workbench.value.draftRequestConfig.queryParams))

const bodyMode = computed(() => String(workbench.value.draftRequestConfig.body?.mode || 'none'))

const formDataRows = computed(() => mapKvRowsForTable(workbench.value.draftRequestConfig.body.formData))
const urlencodedRows = computed(() => mapKvRowsForTable(workbench.value.draftRequestConfig.body.urlencoded))

const bodyTextContent = computed(() => String(workbench.value.draftRequestConfig.body?.text ?? ''))

const binaryDescription = computed(() => {
  const d = workbench.value.draftRequestConfig.body?.binary?.description
  return d != null ? String(d) : ''
})

const requestJsonFlatRows = computed(() => {
  const schema = workbench.value.draftRequestConfig.body?.json?.schema
  return buildSchemaFlatTableRows(schema, 'body')
})

const requestJsonExampleText = computed(() =>
    formatJsonPretty(workbench.value.draftRequestConfig.body?.json?.example)
)

const showDeclaredHeadersSection = computed(() => declaredHeaderRows.value.length > 0)
const showHeadersSection = computed(() => headersRows.value.length > 0)
const showCookiesSection = computed(() => cookiesRows.value.length > 0)
const showPathSection = computed(() => pathRows.value.length > 0)
const showQuerySection = computed(() => queryRows.value.length > 0)

const hasJsonBodyExample = computed(() => {
  const ex = workbench.value.draftRequestConfig.body?.json?.example
  return !!formatJsonPretty(ex).trim()
})

/** Body：mode 为 none 不展示；其余模式仅在有实质内容时展示 */
const showBodySection = computed(() => {
  const mode = bodyMode.value
  if (mode === 'none') return false
  if (mode === 'form-data') return formDataRows.value.length > 0
  if (mode === 'x-www-form-urlencoded') return urlencodedRows.value.length > 0
  if (mode === 'json') {
    return requestJsonFlatRows.value.length > 0 || hasJsonBodyExample.value
  }
  if (mode === 'xml' || mode === 'text') return !!bodyTextContent.value.trim()
  if (mode === 'binary') return !!binaryDescription.value.trim()
  return true
})

const hasRequestParamsContent = computed(
    () =>
        showDeclaredHeadersSection.value ||
        showHeadersSection.value ||
        showCookiesSection.value ||
        showPathSection.value ||
        showQuerySection.value ||
        showBodySection.value
)

/** Body 前有 Query/Path 等分块时显示顶部分隔线 */
const showBodyTopDivider = computed(
    () =>
        showBodySection.value &&
        (showDeclaredHeadersSection.value ||
            showHeadersSection.value ||
            showCookiesSection.value ||
            showPathSection.value ||
            showQuerySection.value)
)

const responseParse = computed(() => parseResponseConfigInput(props.apiDetail?.responseConfig))

const responseBundle = computed(() =>
    responseParse.value.parseError ? null : responseParse.value.bundle
)

const responseList = computed(() => responseBundle.value?.responses || [])

const responseRawSnippet = computed(() => {
  const raw = props.apiDetail?.responseConfig
  if (raw == null) return ''
  return typeof raw === 'string' ? raw : JSON.stringify(raw)
})

const activeResponseId = ref('')

watch(
    responseList,
    (list) => {
      if (!list.length) {
        activeResponseId.value = ''
        return
      }
      if (!list.some((r) => r.id === activeResponseId.value)) {
        activeResponseId.value = list[0].id
      }
    },
    {immediate: true}
)

const currentResponse = computed(() =>
    responseList.value.find((r) => r.id === activeResponseId.value) || null
)

const currentResponseFlatRows = computed(() => {
  const r = currentResponse.value
  if (!r || r.contentType !== 'json') return []
  return buildSchemaFlatTableRows(r.schema, 'response')
})

const currentResponseExampleText = computed(() => {
  const r = currentResponse.value
  if (!r || r.contentType !== 'json') return ''
  return formatJsonPretty(r.example)
})
</script>

<style lang="scss" scoped>
.api-doc-preview {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--pd-surface-elevated, #fff);
}

.design-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-gutter: stable;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.design-strip {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);

  &--last {
    border-bottom: none;
  }
}

.design-strip__title {
  flex-shrink: 0;
  margin: 0;
  padding: 6px 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text, #0f172a);
  background: var(--pd-bg-toolbar, #d0e2f4);
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.design-strip__body {
  background: var(--pd-surface-elevated, #fff);
}

.doc-overview-body {
  padding: 14px 12px 16px;
}

.doc-overview-hero {
  padding: 14px 14px 12px;
  border-radius: var(--pd-radius-sm, 8px);
  background: linear-gradient(180deg, rgba(233, 242, 252, 0.72) 0%, rgba(251, 253, 255, 0.95) 100%);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  box-shadow: var(--pd-shadow-card, 0 1px 2px rgba(20, 60, 120, 0.05));
}

.doc-api-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--pd-text, #0f172a);
  line-height: 1.35;
}

.doc-method-path-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.doc-path-code {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--pd-text, #0f172a);
  background: rgba(255, 255, 255, 0.88);
  padding: 7px 12px;
  border-radius: var(--pd-radius-sm, 8px);
  border: 1px solid var(--pd-border-subtle, #c5d8ec);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.6) inset;
  word-break: break-all;
}

.doc-meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.doc-meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  color: var(--pd-text-muted, #5a6b86);

  &--wide .doc-meta-chip__value {
    font-variant-numeric: tabular-nums;
  }

  &.is-enabled {
    border-color: color-mix(in srgb, #059669 35%, var(--pd-border-muted));
    background: color-mix(in srgb, #ecfdf5 70%, #fff);

    .doc-meta-chip__value {
      color: #047857;
      font-weight: 600;
    }
  }

  &.is-disabled {
    border-color: color-mix(in srgb, #94a3b8 40%, var(--pd-border-muted));
    background: color-mix(in srgb, #f1f5f9 80%, #fff);

    .doc-meta-chip__value {
      color: #64748b;
      font-weight: 600;
    }
  }
}

.doc-meta-chip__label {
  font-weight: 500;
  opacity: 0.9;
}

.doc-meta-chip__value {
  color: var(--pd-text, #334155);
  font-weight: 500;
}

.doc-meta-label {
  color: var(--pd-text-muted, #5a6b86);
  font-weight: 500;
}

.doc-desc {
  margin: 14px 0 0;
  padding: 10px 12px;
  font-size: var(--pd-font-body, 13px);
  line-height: 1.6;
  color: var(--pd-text, #334155);
  white-space: pre-wrap;
  background: var(--pd-bg-sunken, #e9f2fc);
  border-radius: var(--pd-radius-sm, 8px);
  border-left: 3px solid var(--pd-primary, #0b6edc);
}

.doc-req-body {
  padding: 12px 12px 16px;
}

/* 响应 Tab 与分区标题贴齐，避免标题下出现白条 */
.doc-resp-body {
  padding: 0 0 16px;
}

.doc-resp-body-padded {
  padding: 12px 12px 0;
}

.doc-subblock {
  margin-bottom: 18px;
  padding: 12px 12px 14px;
  border-radius: var(--pd-radius-sm, 8px);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  background: var(--pd-surface, #fbfdff);

  &:last-child {
    margin-bottom: 0;
  }

  &--body {
    margin-top: 4px;
    border-top: none;
    padding-top: 12px;
  }
}

.doc-subblock-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
  font-size: 13px;
  font-weight: 600;
  color: var(--pd-text, #0f172a);

  &::before {
    content: '';
    width: 3px;
    height: 14px;
    border-radius: 2px;
    background: var(--pd-primary, #0b6edc);
    flex-shrink: 0;
  }
}

.doc-subblock-title__text {
  flex: 1;
  min-width: 0;
}

.doc-subblock-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-tab, #334c6e);
  background: rgba(11, 110, 220, 0.1);
}

.doc-subblock-hint {
  margin: -4px 0 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.doc-body-hint {
  margin: 0 0 10px;
}

.doc-mime-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  font-family: ui-monospace, Consolas, monospace;
  letter-spacing: 0.02em;
  color: var(--pd-text-tab, #334c6e);
  background: var(--pd-bg-sunken, #e9f2fc);
  border: 1px solid var(--pd-border-muted, #d6e6f5);

  &--compact {
    font-size: 11px;
    padding: 2px 8px;
  }
}

.doc-empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 20px;
  text-align: center;
  border-radius: var(--pd-radius-sm, 8px);
  border: 1px dashed var(--pd-border-subtle, #c5d8ec);
  background: color-mix(in srgb, var(--pd-bg-sunken, #e9f2fc) 55%, transparent);
}

.doc-empty-block__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text-muted, #5a6b86);
}

.doc-empty-block__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pd-text-muted, #5a6b86);
  max-width: 320px;
}

.doc-empty-hint {
  margin: 0;
  font-size: 13px;
  color: var(--pd-text-muted, #5a6b86);

  &--inline {
    padding: 12px;
    text-align: center;
    border-radius: 6px;
    background: var(--pd-bg-sunken, #e9f2fc);
  }
}

.doc-readonly-p {
  margin: 0;
  padding: 10px 12px;
  font-size: var(--pd-font-body, 13px);
  color: var(--pd-text, #334155);
  line-height: 1.55;
  border-radius: var(--pd-radius-sm, 8px);
  background: var(--pd-bg-sunken, #e9f2fc);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
}

.doc-kv-table-wrap,
.doc-schema-table-wrap {
  border-radius: var(--pd-radius-sm, 8px);
  overflow: hidden;

  &--expand {
    overflow: visible;

    :deep(.el-table__body-wrapper) {
      max-height: none !important;
      overflow: visible !important;
    }

    :deep(.el-scrollbar__wrap) {
      max-height: none !important;
      overflow: visible !important;
    }
  }
  border: 1px solid var(--pd-border-subtle, #c5d8ec);
  background: var(--pd-surface-elevated, #fff);
  box-shadow: 0 1px 2px rgba(20, 60, 120, 0.04);

  :deep(.el-table) {
    --el-table-border-color: var(--pd-divider, #dbe8f4);
    --el-table-header-bg-color: transparent;
    --el-table-row-hover-bg-color: rgba(11, 110, 220, 0.06);
    --el-table-tr-bg-color: var(--pd-surface-elevated, #fff);
    --el-table-bg-color: var(--pd-surface-elevated, #fff);
    font-size: 12px;
  }

  :deep(.el-table__header-wrapper th.el-table__cell) {
    background: var(--pd-gradient-panel-head, linear-gradient(180deg, #fafcff 0%, #f0f6fc 100%));
    color: var(--pd-text-muted, #5a6b86);
    font-weight: 600;
    font-size: 12px;
    letter-spacing: 0.02em;
    padding: 10px 0;
  }

  :deep(.el-table__body-wrapper td.el-table__cell) {
    padding: 9px 0;
    color: var(--pd-text, #334155);
  }

  :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
    background: rgba(233, 242, 252, 0.35);
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }
}

.doc-json-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
  min-height: 0;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.doc-json-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 10px 12px;
  border-radius: var(--pd-radius-sm, 8px);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  background: var(--pd-surface-elevated, #fff);
  box-shadow: 0 1px 2px rgba(20, 60, 120, 0.04);
}

.doc-json-split__col {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-col-label {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text, #0f172a);
  letter-spacing: 0.02em;
}

.doc-json-pre {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.5;
  font-family: ui-monospace, Consolas, monospace;
  background: var(--pd-bg-sunken, #e9f2fc);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--pd-text, #0f172a);

  &--expand {
    flex: none;
    min-height: 0;
    max-height: none;
    overflow: visible;
  }

  &--warn {
    margin-top: 10px;
  }
}

.doc-rsp-tablist {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 2px;
  margin: 0 0 14px;
  padding: 4px 8px 0;
  border-bottom: 1px solid var(--pd-border-subtle, var(--pd-divider, #dbe8f4));
  background: var(--pd-gradient-tabstrip, var(--pd-bg-page));
  flex-shrink: 0;
}

.doc-rsp-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 0 14px;
  height: 36px;
  border: 1px solid transparent;
  border-bottom: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--pd-font-tab, 14px);
  font-weight: 500;
  color: var(--pd-text-muted, #5a6b86);
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  transition:
    color 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;

  &:hover:not(.active) {
    color: var(--pd-text-tab, #334c6e);
    background: rgba(255, 255, 255, 0.6);
    border-color: var(--pd-border-muted, #d6e6f5);
  }

  &.active {
    color: var(--pd-primary);
    font-weight: 600;
    background: var(--pd-surface-elevated);
    border-color: var(--pd-border-subtle, #c5d8ec);
    border-bottom: none;
    margin-bottom: -1px;
    z-index: 1;
    box-shadow: 0 1px 3px rgba(20, 60, 120, 0.06);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 3px;
      background: var(--pd-primary);
      border-radius: 0;
      z-index: 2;
    }
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--pd-primary) 45%, transparent);
    outline-offset: 1px;
  }
}

.doc-rsp-tab-code {
  font-family: ui-monospace, Consolas, monospace;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.doc-rsp-tab-name {
  font-weight: 500;
  opacity: 0.88;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-rsp-tab.is-success .doc-rsp-tab-code {
  color: #047857;
}

.doc-rsp-tab.is-redirect .doc-rsp-tab-code {
  color: #1d4ed8;
}

.doc-rsp-tab.is-client .doc-rsp-tab-code {
  color: #b45309;
}

.doc-rsp-tab.is-server .doc-rsp-tab-code {
  color: #b91c1c;
}

.doc-rsp-tab.active .doc-rsp-tab-code {
  color: inherit;
}

.doc-rsp-panel {
  min-height: 0;
  padding: 0 12px;
}

.doc-resp-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  margin-bottom: 14px;
  padding: 8px 10px;
  border-radius: var(--pd-radius-sm, 8px);
  background: var(--pd-bg-sunken, #e9f2fc);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
}

.doc-status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, Consolas, monospace;

  &.is-success {
    color: #047857;
    background: #ecfdf5;
    border: 1px solid #6ee7b7;
  }

  &.is-redirect {
    color: #1d4ed8;
    background: #eff6ff;
    border: 1px solid #93c5fd;
  }

  &.is-client {
    color: #b45309;
    background: #fffbeb;
    border: 1px solid #fcd34d;
  }

  &.is-server {
    color: #b91c1c;
    background: #fef2f2;
    border: 1px solid #fca5a5;
  }

  &.is-unknown {
    color: #475569;
    background: #f8fafc;
    border: 1px solid #cbd5e1;
  }
}

.doc-resp-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--pd-text, #334155);
}

.doc-method-tag {
  font-weight: 600;
  border-width: 1px;

  &.is-GET {
    color: #0b6edc;
    border-color: #93c5fd;
    background: rgba(11, 110, 220, 0.06);
  }

  &.is-POST {
    color: #059669;
    border-color: #6ee7b7;
    background: rgba(5, 150, 105, 0.06);
  }

  &.is-PUT {
    color: #d97706;
    border-color: #fcd34d;
    background: rgba(217, 119, 6, 0.08);
  }

  &.is-PATCH {
    color: #7c3aed;
    border-color: #c4b5fd;
    background: rgba(124, 58, 237, 0.06);
  }

  &.is-DELETE {
    color: #dc2626;
    border-color: #fca5a5;
    background: rgba(220, 38, 38, 0.06);
  }

  &.is-HEAD,
  &.is-OPTIONS {
    color: #64748b;
    border-color: #cbd5e1;
    background: rgba(100, 116, 139, 0.06);
  }

  &.is-other {
    color: #475569;
    border-color: #cbd5e1;
  }
}
</style>
