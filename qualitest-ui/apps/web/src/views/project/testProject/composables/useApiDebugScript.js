import { ref, watch } from 'vue'
import { executePostRequestScript, executePreRequestScript } from '@/api/test/apiRequestScript'
import { buildDebugFlowContext } from '@/utils/flow/flowContextBuilder'
import { normalizeDebugBodySpec } from '@/transport/debugBodySpec'

const STORAGE_PREFIX = 'qualitest-debug-api-script-'

function readState(projectId) {
  if (projectId == null) {
    return { variables: {}, globals: {}, environmentOverlay: {} }
  }
  try {
    const raw = sessionStorage.getItem(`${STORAGE_PREFIX}${projectId}`)
    if (!raw) {
      return { variables: {}, globals: {}, environmentOverlay: {} }
    }
    const o = JSON.parse(raw)
    return {
      variables: o?.variables && typeof o.variables === 'object' ? o.variables : {},
      globals: o?.globals && typeof o.globals === 'object' ? o.globals : {},
      environmentOverlay:
        o?.environmentOverlay && typeof o.environmentOverlay === 'object' ? o.environmentOverlay : {}
    }
  } catch {
    return { variables: {}, globals: {}, environmentOverlay: {} }
  }
}

function writeState(projectId, state) {
  if (projectId == null) return
  try {
    sessionStorage.setItem(
      `${STORAGE_PREFIX}${projectId}`,
      JSON.stringify({
        variables: state.variables ?? {},
        globals: state.globals ?? {},
        environmentOverlay: state.environmentOverlay ?? {}
      })
    )
  } catch {
    /* ignore */
  }
}

/**
 * @typedef {object} ApiScriptExecuteResult
 * @property {boolean} success
 * @property {string|null} [errorCode]
 * @property {string|null} [errorMessage]
 * @property {string[]} [logs]
 * @property {Array<{name:string,passed:boolean,message?:string}>} [tests]
 * @property {object} [request]
 * @property {Record<string, unknown>} [variables]
 * @property {Record<string, unknown>} [globals]
 * @property {Record<string, unknown>} [environment]
 */

/**
 * API 调试脚本会话状态与 pre/post 执行编排。
 *
 * @param {() => { testProjectId?: string|number|null, testProjectEnvId?: string|number|null, envList?: Array }} getOptions
 */
export function useApiDebugScript(getOptions) {
  const scriptState = ref(readState(null))

  function resolveProjectId() {
    const opts = typeof getOptions === 'function' ? getOptions() : getOptions
    return opts?.testProjectId ?? null
  }

  function loadState() {
    scriptState.value = readState(resolveProjectId())
  }

  function persistState() {
    writeState(resolveProjectId(), scriptState.value)
  }

  /** 合并当前选中环境的 env 与脚本 overlay */
  function buildEnvironmentMap() {
    const opts = typeof getOptions === 'function' ? getOptions() : getOptions
    const env = opts?.envList?.find(
      (e) => String(e.testProjectEnvId) === String(opts?.testProjectEnvId)
    )
    const ctx = buildDebugFlowContext({
      envUrl: env?.envUrl,
      envVariables: env?.envVariables
    })
    return {
      ...ctx.env,
      ...(scriptState.value.environmentOverlay ?? {})
    }
  }

  function applyServerState(result) {
    if (!result || typeof result !== 'object') return
    if (result.variables && typeof result.variables === 'object') {
      scriptState.value.variables = { ...result.variables }
    }
    if (result.globals && typeof result.globals === 'object') {
      scriptState.value.globals = { ...result.globals }
    }
    if (result.environment && typeof result.environment === 'object') {
      const opts = typeof getOptions === 'function' ? getOptions() : getOptions
      const env = opts?.envList?.find(
        (e) => String(e.testProjectEnvId) === String(opts?.testProjectEnvId)
      )
      const baseCtx = buildDebugFlowContext({
        envUrl: env?.envUrl,
        envVariables: env?.envVariables
      })
      const overlay = {}
      for (const [key, value] of Object.entries(result.environment)) {
        if (baseCtx.env[key] !== value) {
          overlay[key] = value
        }
      }
      scriptState.value.environmentOverlay = overlay
    }
    persistState()
  }

  function resetScriptState() {
    scriptState.value = { variables: {}, globals: {}, environmentOverlay: {} }
    persistState()
  }

  watch(
    () => {
      const opts = typeof getOptions === 'function' ? getOptions() : getOptions
      return opts?.testProjectId
    },
    () => loadState(),
    { immediate: true }
  )

  return {
    scriptState,
    loadState,
    persistState,
    buildEnvironmentMap,
    applyServerState,
    resetScriptState
  }
}

/**
 * @param {object} built
 * @param {string} built.fullUrl
 * @param {string} built.method
 * @param {Record<string, string>} built.headers
 * @param {unknown} [built.data]
 */
export async function buildScriptRequestSnapshot(built) {
  const body = await normalizeDebugBodySpec(built.method, built.data)
  return {
    method: built.method,
    url: built.fullUrl,
    headers: { ...(built.headers || {}) },
    body: body ?? { kind: 'none' }
  }
}

/**
 * @param {object} built
 * @param {object|null|undefined} request
 */
export async function applyScriptRequestToBuilt(built, request) {
  if (!request || typeof request !== 'object') {
    return built
  }
  const next = { ...built, headers: { ...(built.headers || {}) } }
  if (request.method) {
    next.method = String(request.method).toUpperCase()
  }
  if (request.url) {
    next.fullUrl = String(request.url)
  }
  if (request.headers && typeof request.headers === 'object') {
    next.headers = {}
    for (const [key, value] of Object.entries(request.headers)) {
      if (key) next.headers[key] = value != null ? String(value) : ''
    }
  }
  const body = request.body
  if (body && typeof body === 'object') {
    next.data = bodySpecToData(body, next.method, next.headers)
  }
  return next
}

function bodySpecToData(body, method, headers) {
  const kind = body.kind || 'none'
  if (['GET', 'HEAD'].includes(String(method).toUpperCase()) || kind === 'none') {
    return undefined
  }
  switch (kind) {
    case 'json':
      if (body.json != null && typeof body.json === 'object') {
        return body.json
      }
      if (body.raw != null) {
        try {
          return JSON.parse(String(body.raw))
        } catch {
          return body.raw
        }
      }
      return {}
    case 'urlencoded': {
      const params = new URLSearchParams()
      const fields = body.fields
      if (Array.isArray(fields)) {
        for (const row of fields) {
          if (Array.isArray(row) && row[0]) {
            params.append(String(row[0]), row[1] != null ? String(row[1]) : '')
          }
        }
      } else if (body.raw) {
        return String(body.raw)
      }
      if (!headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
      }
      return params.toString()
    }
    case 'raw':
    case 'text':
    case 'xml':
      return body.raw != null ? String(body.raw) : ''
    default:
      return body.raw != null ? String(body.raw) : undefined
  }
}

function unwrapApiResult(res) {
  if (res && typeof res === 'object' && 'success' in res) {
    return res
  }
  if (res && typeof res === 'object' && res.data && typeof res.data === 'object' && 'success' in res.data) {
    return res.data
  }
  return res
}

/**
 * @param {string} source
 * @param {object} built
 * @param {{ variables: object, globals: object, environment: object }} state
 * @returns {Promise<{ built: object, result: ApiScriptExecuteResult|null, error: string|null }>}
 */
export async function runPreScript(source, built, state) {
  if (!source || !String(source).trim()) {
    return { built, result: null, error: null }
  }
  try {
    const request = await buildScriptRequestSnapshot(built)
    const raw = await executePreRequestScript({
      source,
      variables: state.variables ?? {},
      environment: state.environment ?? {},
      globals: state.globals ?? {},
      request
    })
    const result = unwrapApiResult(raw)
    if (!result?.success) {
      return {
        built,
        result,
        error: result?.errorMessage || '前置脚本执行失败'
      }
    }
    const nextBuilt = await applyScriptRequestToBuilt(built, result.request)
    return { built: nextBuilt, result, error: null }
  } catch (e) {
    return {
      built,
      result: null,
      error: e?.message || String(e)
    }
  }
}

/**
 * @param {string} source
 * @param {object} built
 * @param {object} debugResponse
 * @param {{ variables: object, globals: object, environment: object }} state
 */
export async function runPostScript(source, built, debugResponse, state) {
  if (!source || !String(source).trim()) {
    return { result: null, error: null }
  }
  try {
    const request = await buildScriptRequestSnapshot(built)
    const raw = await executePostRequestScript({
      source,
      variables: state.variables ?? {},
      environment: state.environment ?? {},
      globals: state.globals ?? {},
      request,
      response: {
        status: debugResponse.status,
        code: debugResponse.status,
        statusText: debugResponse.statusText,
        headers: debugResponse.headers ?? {},
        bodyText: debugResponse.bodyText ?? '',
        durationMs: debugResponse.durationMs
      }
    })
    const result = unwrapApiResult(raw)
    if (!result?.success) {
      return {
        result,
        error: result?.errorMessage || '后置脚本执行失败'
      }
    }
    return { result, error: null }
  } catch (e) {
    return {
      result: null,
      error: e?.message || String(e)
    }
  }
}
