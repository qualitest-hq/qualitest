import {computed} from 'vue'
import {executeDebugRequest} from '@/transport/debugTransport'
import {runPostScript, runPreScript} from '@/views/project/testProject/composables/useApiDebugScript'
import {
  ensureHttpSchemeForRequest,
  resolveEnvBaseUrlForRequest
} from '@/views/project/testProject/utils/envConfigUtils'
import {rowsToKeyValueObject} from '@/views/project/testProject/composables/useApiDebugPersist'

/**
 * API 调试发送：组装 URL/选项、执行请求与前后置脚本。
 *
 * @param {object} props
 * @param {ReturnType<typeof import('./useApiDebugDraft').useApiDebugDraft>} draft
 * @param {ReturnType<typeof import('./useApiDebugBodyJson').useApiDebugBodyJson>} bodyJson
 * @param {object} proxy
 * @param {{ scriptState: import('vue').Ref, buildEnvironmentMap: Function, applyServerState: Function }} script
 */
export function useApiDebugSend(props, draft, bodyJson, proxy, script) {
  const {
    draftApiPath,
    draftRequestConfig,
    draftHeaderRows,
    draftCookieRows,
    binaryBodyFile,
    draftPreRequestScript,
    draftPostRequestScript,
    debugResponse,
    debugSending,
    createEmptyDebugResponse
  } = draft
  const {buildJsonBodyDataForSend} = bodyJson
  const {scriptState, buildEnvironmentMap, applyServerState} = script

  const debugScriptTestsBadge = computed(() => (debugResponse.value.scriptTests || []).length)

  function formatResponseHeaders(h) {
    if (!h || typeof h !== 'object') return ''
    try {
      const flat = {}
      for (const k of Object.keys(h)) {
        flat[k] = h[k]
      }
      return JSON.stringify(flat, null, 2)
    } catch {
      return String(h)
    }
  }

  function buildSendUrlAndOptions() {
    const envId = props.testProjectEnvId
    const env = props.envList.find((e) => String(e.testProjectEnvId) === String(envId))
    const envBase = resolveEnvBaseUrlForRequest(env?.envUrl)
    if (!envBase) {
      return {error: '请先选择环境'}
    }
    let path = (draftApiPath.value || '').trim()
    if (!path.startsWith('/')) path = '/' + path

    let urlPath = path
    for (const row of draftRequestConfig.value.pathParams || []) {
      if (row._enabled === false) continue
      const n = (row.name || '').trim()
      if (!n) continue
      const val = row.value ?? ''
      urlPath = urlPath.split(`{${n}}`).join(encodeURIComponent(val))
    }

    const qs = new URLSearchParams()
    for (const row of draftRequestConfig.value.queryParams || []) {
      if (row._enabled === false) continue
      const k = (row.name || '').trim()
      if (!k) continue
      qs.append(k, row.value ?? '')
    }
    const qStr = qs.toString()
    const base = ensureHttpSchemeForRequest(envBase).replace(/\/$/, '')
    const fullUrl = base + urlPath + (qStr ? `?${qStr}` : '')

    const method = (draftRequestConfig.value.method || 'GET').toUpperCase()
    const headers = {...rowsToKeyValueObject(draftHeaderRows.value)}
    const cookieStr = Object.entries(rowsToKeyValueObject(draftCookieRows.value))
        .map(([k, v]) => `${k}=${v}`)
        .join('; ')
    if (cookieStr) {
      headers.Cookie = [headers.Cookie, cookieStr].filter(Boolean).join('; ')
    }

    const body = draftRequestConfig.value.body
    const mode = body?.mode || 'none'
    let data = undefined

    if (!['GET', 'HEAD'].includes(method)) {
      switch (mode) {
        case 'json': {
          const builtJson = buildJsonBodyDataForSend()
          if (builtJson.error) return {error: builtJson.error}
          data = builtJson.data
          if (!headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = 'application/json'
          }
          break
        }
        case 'form-data': {
          const fd = new FormData()
          for (const row of body.formData || []) {
            if (row._enabled === false) continue
            const k = (row.name || '').trim()
            if (!k) continue
            const t = String(row.type || '').toLowerCase()
            if (t === 'file' && row._file instanceof File) {
              fd.append(k, row._file, row._file.name)
            } else {
              fd.append(k, row.value ?? '')
            }
          }
          data = fd
          // 必须清掉声明头里的 Content-Type，否则 axios 会按 application/json 序列化，FormData 变成普通 JSON
          for (const hk of Object.keys(headers)) {
            if (hk.toLowerCase() === 'content-type') delete headers[hk]
          }
          break
        }
        case 'x-www-form-urlencoded': {
          const p = new URLSearchParams()
          for (const row of body.urlencoded || []) {
            if (row._enabled === false) continue
            const k = (row.name || '').trim()
            if (!k) continue
            p.append(k, row.value ?? '')
          }
          data = p.toString()
          if (!headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
          }
          break
        }
        case 'xml':
        case 'text':
          data = body.text ?? ''
          if (mode === 'xml' && !headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = 'application/xml'
          }
          if (mode === 'text' && !headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = 'text/plain'
          }
          break
        case 'binary': {
          const f = binaryBodyFile.value
          if (!(f instanceof File)) {
            return {error: 'binary 请求体请先选择本地文件'}
          }
          data = f
          if (!headers['Content-Type'] && !headers['content-type']) {
            const ct = f.type && String(f.type).trim() ? f.type : 'application/octet-stream'
            headers['Content-Type'] = ct
          }
          break
        }
        default:
          data = undefined
      }
    }

    return {fullUrl, method, headers, data, testProjectApiId: props.apiDetail?.testProjectApiId}
  }

  async function handleDebugSend() {
    const built = buildSendUrlAndOptions()
    if (built.error) {
      if (built.error === '请先选择环境') {
        proxy.$modal.msgWarning(built.error)
      } else {
        proxy.$modal.msgError(built.error)
      }
      return
    }
    let sendBuilt = built
    const scriptSession = {
      variables: {...(scriptState.value.variables ?? {})},
      globals: {...(scriptState.value.globals ?? {})},
      environment: buildEnvironmentMap()
    }
    debugSending.value = true
    debugResponse.value = createEmptyDebugResponse()
    try {
      const preScript = draftPreRequestScript.value ?? props.apiDetail?.preRequestScript ?? ''
      const preOutcome = await runPreScript(preScript, sendBuilt, scriptSession)
      if (preOutcome.error) {
        debugResponse.value = {
          ...debugResponse.value,
          sent: true,
          ok: false,
          error: preOutcome.error,
          preScriptError: preOutcome.error,
          scriptLogs: preOutcome.result?.logs ?? []
        }
        return
      }
      if (preOutcome.result) {
        applyServerState(preOutcome.result)
        scriptSession.variables = {...(scriptState.value.variables ?? {})}
        scriptSession.globals = {...(scriptState.value.globals ?? {})}
        scriptSession.environment = buildEnvironmentMap()
      }
      sendBuilt = preOutcome.built

      const {fullUrl, method, headers, data} = sendBuilt
      const result = await executeDebugRequest(
          {fullUrl, method, headers, data},
          {transportMode: props.httpTransportMode}
      )
      debugResponse.value = {
        sent: true,
        ok: result.ok,
        status: result.status,
        statusText: result.statusText || '',
        headers: result.headers || {},
        bodyText: result.bodyText,
        bodyEncoding: result.bodyEncoding || 'text',
        bodyBase64: result.bodyBase64 || '',
        error: result.error,
        errorCode: result.errorCode,
        corsHint: result.corsHint || null,
        durationMs: result.durationMs,
        preScriptError: null,
        postScriptError: null,
        scriptLogs: preOutcome.result?.logs ?? [],
        scriptTests: []
      }

      const postScript = draftPostRequestScript.value ?? props.apiDetail?.postRequestScript ?? ''
      const postOutcome = await runPostScript(postScript, sendBuilt, debugResponse.value, scriptSession)
      if (postOutcome.result) {
        applyServerState(postOutcome.result)
        const logs = [
          ...(debugResponse.value.scriptLogs ?? []),
          ...(postOutcome.result.logs ?? [])
        ]
        debugResponse.value.scriptLogs = logs
        debugResponse.value.scriptTests = postOutcome.result.tests ?? []
      }
      if (postOutcome.error) {
        debugResponse.value.postScriptError = postOutcome.error
        debugResponse.value.ok = false
        if (!debugResponse.value.error) {
          debugResponse.value.error = postOutcome.error
        }
      }
      if ((debugResponse.value.scriptTests ?? []).some((t) => t && t.passed === false)) {
        debugResponse.value.ok = false
      }
    } catch (err) {
      debugResponse.value = {
        ...debugResponse.value,
        sent: true,
        ok: false,
        status: debugResponse.value.status,
        statusText: debugResponse.value.statusText || '',
        headers: debugResponse.value.headers || {},
        bodyText: debugResponse.value.bodyText ?? '',
        error: err.message || String(err),
        errorCode: debugResponse.value.errorCode,
        corsHint: debugResponse.value.corsHint,
        durationMs: debugResponse.value.durationMs
      }
      console.error(err)
    } finally {
      debugSending.value = false
    }
  }

  return {
    buildSendUrlAndOptions,
    handleDebugSend,
    formatResponseHeaders,
    debugScriptTestsBadge
  }
}
