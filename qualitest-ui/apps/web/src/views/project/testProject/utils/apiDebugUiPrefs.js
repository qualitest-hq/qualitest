/** localStorage 中调试 UI 页签记忆的版本号 */
export const DEBUG_UI_PREFS_VER = 1

export const VALID_REQUEST_TABS = ['headers', 'query', 'body', 'path', 'cookies']
export const VALID_REQUEST_TABS_WITH_SCRIPTS = [...VALID_REQUEST_TABS, 'preScript', 'postScript']
export const VALID_RESP_TABS = ['body', 'headers', 'tests']

/** @typedef {'browser' | 'browser-java-forward'} DebugHttpTransportPref */
export const VALID_HTTP_TRANSPORTS = ['browser', 'browser-java-forward']

/**
 * @param {string|number|null|undefined} testProjectId
 * @returns {DebugHttpTransportPref}
 */
export function readDebugHttpTransportPref(testProjectId) {
  const map = readAllDebugUiPrefs(testProjectId)
  const v = map.httpTransport
  return VALID_HTTP_TRANSPORTS.includes(v) ? v : 'browser'
}

/**
 * @param {string|number|null|undefined} testProjectId
 * @param {DebugHttpTransportPref} transport
 */
export function writeDebugHttpTransportPref(testProjectId, transport) {
  if (!VALID_HTTP_TRANSPORTS.includes(transport)) {
    return
  }
  const map = readAllDebugUiPrefs(testProjectId)
  map.httpTransport = transport
  writeDebugUiPrefs(testProjectId, map)
}

export function debugUiPrefsStorageKey(testProjectId) {
  return `qualitest.apiDebugUiPrefs.v${DEBUG_UI_PREFS_VER}.${testProjectId}`
}

export function readAllDebugUiPrefs(testProjectId) {
  if (testProjectId == null) return {}
  try {
    const raw = localStorage.getItem(debugUiPrefsStorageKey(testProjectId))
    if (!raw) return {}
    const o = JSON.parse(raw)
    return typeof o === 'object' && o !== null && !Array.isArray(o) ? o : {}
  } catch {
    return {}
  }
}

export function writeDebugUiPrefs(testProjectId, map) {
  if (testProjectId == null) return
  try {
    localStorage.setItem(debugUiPrefsStorageKey(testProjectId), JSON.stringify(map))
  } catch {
    /* 无配额等情况下忽略 */
  }
}
