import {ref, computed, watch} from 'vue'
import {
  readAllDebugUiPrefs,
  VALID_REQUEST_TABS_WITH_SCRIPTS,
  VALID_RESP_TABS,
  writeDebugUiPrefs
} from '@/views/project/testProject/utils/apiDebugUiPrefs'
import {
  buildRequestWorkbenchStateFromDetail,
  defaultDraftRequestConfig,
  emptyKVRow,
  ensureTrailingEmptyRow
} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'
import {useApiDebugTrailingEmptyRows} from '@/views/project/testProject/composables/useApiDebugTrailingEmptyRows'

/**
 * API 调试草稿状态：路径/请求配置/Headers/Cookies/脚本/响应/Body JSON 文本与 binary 等。
 * Body JSON 的 sync/merge/build 由 useApiDebugBodyJson 挂到 syncBodyJsonTextFromDraftRef。
 *
 * @param {object} props
 */
export function useApiDebugDraft(props) {
  /** @type {{ fn: null | (() => void) }} */
  const syncBodyJsonTextFromDraftRef = {fn: null}

  /** 行是否算「有值」（启用且有名称） */
  function namedEnabledRowCount(rows) {
    return (rows || []).filter((r) => r?._enabled !== false && String(r?.name || '').trim()).length
  }

  /**
   * 默认请求页签：优先有内容的 Tab。
   * 顺序 body → query → path → cookies → headers → 前后置脚本；全空则 headers。
   */
  function pickDefaultRequestTab() {
    const body = draftRequestConfig.value?.body
    const bodyMode = String(body?.mode || 'none')
    const bodyHasContent =
        (bodyMode !== 'none' && bodyMode !== '') ||
        namedEnabledRowCount(body?.formData) > 0 ||
        namedEnabledRowCount(body?.urlencoded) > 0 ||
        String(bodyJsonText.value || '').trim().length > 0 ||
        String(body?.text || '').trim().length > 0 ||
        binaryBodyFile.value instanceof File
    if (bodyHasContent) return 'body'
    if (namedEnabledRowCount(draftRequestConfig.value?.queryParams) > 0) return 'query'
    if (namedEnabledRowCount(draftRequestConfig.value?.pathParams) > 0) return 'path'
    if (namedEnabledRowCount(draftCookieRows.value) > 0) return 'cookies'
    if (namedEnabledRowCount(draftHeaderRows.value) > 0) return 'headers'
    if (String(draftPreRequestScript.value || '').trim()) return 'preScript'
    if (String(draftPostRequestScript.value || '').trim()) return 'postScript'
    return 'headers'
  }

  function applySavedDebugUiTabs(detail) {
    const apiId = detail?.testProjectApiId
    const projectId = detail?.testProjectId
    if (apiId == null || projectId == null) {
      activeDebugRequestTab.value = pickDefaultRequestTab()
      activeRespTab.value = 'body'
      return
    }
    const all = readAllDebugUiPrefs(projectId)
    const saved = all[String(apiId)]
    const rt = saved?.requestTab
    const rst = saved?.respTab
    const allowReq = VALID_REQUEST_TABS_WITH_SCRIPTS
    activeDebugRequestTab.value = rt && allowReq.includes(rt) ? rt : pickDefaultRequestTab()
    activeRespTab.value =
        rst && VALID_RESP_TABS.includes(rst) ? rst : 'body'
  }

  let persistDebugUiTimer = null

  function persistCurrentDebugUiTabs() {
    const d = props.apiDetail
    if (!d?.testProjectApiId || d.testProjectId == null) return
    const all = readAllDebugUiPrefs(d.testProjectId)
    all[String(d.testProjectApiId)] = {
      requestTab: activeDebugRequestTab.value,
      respTab: activeRespTab.value
    }
    writeDebugUiPrefs(d.testProjectId, all)
  }

  function schedulePersistDebugUiTabs() {
    if (persistDebugUiTimer != null) clearTimeout(persistDebugUiTimer)
    persistDebugUiTimer = setTimeout(() => {
      persistDebugUiTimer = null
      persistCurrentDebugUiTabs()
    }, 50)
  }

  const activeDebugRequestTab = ref('headers')
  const activeRespTab = ref('body')
  const draftApiPath = ref('')
  const draftRequestConfig = ref(defaultDraftRequestConfig())
  const draftHeaderRows = ref([emptyKVRow()])
  const draftCookieRows = ref([emptyKVRow()])
  const bodyJsonText = ref('')
  /** binary 模式：本地文件仅用于调试发送，不入库 */
  const binaryBodyFile = ref(null)
  const binaryFileInputRef = ref(null)
  /** Body JSON 子页：数据结构 | 原始 JSON 示例 */
  const activeBodyJsonSubTab = ref('schema')
  const draftPreRequestScript = ref('')
  const draftPostRequestScript = ref('')
  const debugResponse = ref(createEmptyDebugResponse())
  const debugSending = ref(false)
  const apiDebugSaving = ref(false)

  /** 调试面板右侧「返回响应」的初始空状态（含 bodyEncoding / bodyBase64） */
  function createEmptyDebugResponse() {
    return {
      sent: false,
      ok: false,
      status: null,
      statusText: '',
      headers: {},
      bodyText: '',
      /** text | base64；裸媒体为 base64 */
      bodyEncoding: 'text',
      /** 裸媒体 Base64，供 Body 区上方预览 */
      bodyBase64: '',
      error: null,
      errorCode: null,
      corsHint: null,
      durationMs: null,
      preScriptError: null,
      postScriptError: null,
      scriptLogs: [],
      scriptTests: []
    }
  }

  function resetDebugResponse() {
    debugResponse.value = createEmptyDebugResponse()
  }

  /** 与最近一次 init 入参为同一对象引用时跳过，避免 keep-alive 切回时重复整表重建 */
  let lastInitDraftFromDetailRef = null

  function initDraftFromApiDetail(detail) {
    resetDebugResponse()
    binaryBodyFile.value = null
    const s = buildRequestWorkbenchStateFromDetail(detail)
    draftApiPath.value = s.draftApiPath
    draftRequestConfig.value = s.draftRequestConfig
    draftHeaderRows.value = s.draftHeaderRows
    draftCookieRows.value = s.draftCookieRows
    draftPreRequestScript.value = s.draftPreRequestScript
    draftPostRequestScript.value = s.draftPostRequestScript
    syncBodyJsonTextFromDraftRef.fn?.()
    applySavedDebugUiTabs(detail)
    lastInitDraftFromDetailRef = detail
  }

  function resetDebugWorkbench() {
    draftApiPath.value = ''
    binaryBodyFile.value = null
    draftRequestConfig.value = defaultDraftRequestConfig()
    draftHeaderRows.value = [emptyKVRow()]
    draftCookieRows.value = [emptyKVRow()]
    bodyJsonText.value = ''
    activeBodyJsonSubTab.value = 'schema'
    draftPreRequestScript.value = ''
    draftPostRequestScript.value = ''
    resetDebugResponse()
    activeDebugRequestTab.value = 'headers'
    activeRespTab.value = 'body'
  }

  const debugBodyTabBadge = computed(() => {
    const b = draftRequestConfig.value.body
    if (!b) return 0
    if (b.mode === 'form-data') {
      return (b.formData || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
    }
    if (b.mode === 'x-www-form-urlencoded') {
      return (b.urlencoded || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
    }
    if (b.mode === 'json') return bodyJsonText.value.trim() ? 1 : 0
    if (b.mode === 'text' || b.mode === 'xml') return (b.text || '').trim() ? 1 : 0
    if (b.mode === 'binary') return binaryBodyFile.value instanceof File ? 1 : 0
    return 0
  })

  const debugPathTabBadge = computed(() =>
      (draftRequestConfig.value.pathParams || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
  )
  const debugQueryTabBadge = computed(() =>
      (draftRequestConfig.value.queryParams || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
  )

  function removeRow(arr, index) {
    arr.splice(index, 1)
    if (!arr.length) arr.push(emptyKVRow())
    ensureTrailingEmptyRow(arr)
  }

  useApiDebugTrailingEmptyRows({
    draftHeaderRows,
    draftCookieRows,
    draftRequestConfig,
    ensureTrailingEmptyRow
  })

  watch(
      () => props.apiDetail,
      (d) => {
        if (!d) {
          lastInitDraftFromDetailRef = null
          resetDebugWorkbench()
          return
        }
        if (d === lastInitDraftFromDetailRef) return
        initDraftFromApiDetail(d)
      },
      {immediate: true}
  )

  watch([activeDebugRequestTab, activeRespTab], () => {
    if (!props.apiDetail?.testProjectApiId) return
    schedulePersistDebugUiTabs()
  })

  return {
    syncBodyJsonTextFromDraftRef,
    activeDebugRequestTab,
    activeRespTab,
    draftApiPath,
    draftRequestConfig,
    draftHeaderRows,
    draftCookieRows,
    bodyJsonText,
    binaryBodyFile,
    binaryFileInputRef,
    activeBodyJsonSubTab,
    draftPreRequestScript,
    draftPostRequestScript,
    debugResponse,
    debugSending,
    apiDebugSaving,
    namedEnabledRowCount,
    pickDefaultRequestTab,
    applySavedDebugUiTabs,
    persistCurrentDebugUiTabs,
    schedulePersistDebugUiTabs,
    createEmptyDebugResponse,
    resetDebugResponse,
    initDraftFromApiDetail,
    resetDebugWorkbench,
    removeRow,
    debugPathTabBadge,
    debugQueryTabBadge,
    debugBodyTabBadge
  }
}
