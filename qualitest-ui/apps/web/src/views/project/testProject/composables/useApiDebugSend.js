import {computed} from 'vue'
import {executeDebugRequest} from '@/transport/debugTransport'
import {runPostScript, runPreScript} from '@/views/project/testProject/composables/useApiDebugScript'
import {
  ensureHttpSchemeForRequest,
  resolveEnvBaseUrlForRequest
} from '@/views/project/testProject/utils/envConfigUtils'
import {rowsToKeyValueObject} from '@/views/project/testProject/composables/useApiDebugPersist'
import {listTestProjectAsset} from '@/api/project/testProjectAsset'
import {buildDebugFlowContext} from '@/utils/flow/flowContextBuilder'
import {pickHeadersForSend} from '@/views/project/testProject/utils/debugManagedAuthRow'
import {
  collectUnresolvedPlaceholders,
  resolvePlaceholderInString
} from '@/views/project/testProject/utils/debugPlaceholderResolve'

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
    const headers = {...pickHeadersForSend(draftHeaderRows.value)}
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

    return {fullUrl, method, headers, data}
  }

  /** 前置脚本之后再取素材，把 url / headers / body 里的 {{env/asset/flow}} 换成当前值。 */
  async function resolveSendPlaceholders(built) {
    const projectId = props.apiDetail?.testProjectId
    let assetEntries = []
    if (projectId != null) {
      try {
        const res = await listTestProjectAsset({testProjectId: projectId})
        assetEntries = res?.rows ?? res?.data ?? []
      } catch {
        assetEntries = []
      }
    }
    const env = props.envList.find((e) => String(e.testProjectEnvId) === String(props.testProjectEnvId))
    const ctx = buildDebugFlowContext({
      envUrl: env?.envUrl,
      envVariables: env?.envVariables,
      assetEntries,
      flow: {...(scriptState.value.variables ?? {})}
    })
    const headers = {}
    for (const [name, value] of Object.entries(built.headers || {})) {
      headers[resolvePlaceholderInString(name, ctx)] = resolvePlaceholderInString(value, ctx)
    }
    const data = resolveRequestData(built.data, ctx)
    const texts = [
      built.fullUrl,
      ...Object.keys(built.headers || {}),
      ...Object.values(built.headers || {}),
      typeof built.data === 'string' ? built.data : dataPreview(built.data)
    ]
    return {
      ...built,
      fullUrl: resolvePlaceholderInString(built.fullUrl, ctx),
      headers,
      data,
      unresolvedPlaceholders: collectUnresolvedPlaceholders(texts, ctx)
    }
  }

  function dataPreview(data) {
    if (data == null || typeof data === 'string') return data ?? ''
    if (typeof FormData !== 'undefined' && data instanceof FormData) return ''
    if (typeof File !== 'undefined' && data instanceof File) return ''
    try {
      return JSON.stringify(data)
    } catch {
      return ''
    }
  }

  function resolveRequestData(data, ctx) {
    if (data == null || typeof data !== 'string' && typeof data !== 'object') return data
    if (typeof data === 'string') return resolvePlaceholderInString(data, ctx)
    if (typeof FormData !== 'undefined' && data instanceof FormData) return data
    if (typeof File !== 'undefined' && data instanceof File) return data
    try {
      return JSON.parse(resolvePlaceholderInString(JSON.stringify(data), ctx))
    } catch {
      return data
    }
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
      sendBuilt = await resolveSendPlaceholders(sendBuilt)

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
        scriptTests: [],
        unresolvedPlaceholders: sendBuilt.unresolvedPlaceholders || []
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
