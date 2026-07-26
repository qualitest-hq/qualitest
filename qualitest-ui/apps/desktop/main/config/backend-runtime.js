/** @type {string} */
let backendBaseUrl = ''

export function getRuntimeBackendBaseUrl() {
  return backendBaseUrl
}

/**
 * @param {string} url
 */
export function setRuntimeBackendBaseUrl(url) {
  backendBaseUrl = url
}
