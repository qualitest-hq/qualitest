import fs from 'node:fs'
import path from 'node:path'
import {app} from 'electron'

export const DEFAULT_BACKEND_BASE_URL = 'http://127.0.0.1:8080'

const CONFIG_FILE_NAME = 'backend-config.json'

function getConfigFilePath() {
  return path.join(app.getPath('userData'), CONFIG_FILE_NAME)
}

/**
 * @param {string} input
 * @returns {string}
 */
export function normalizeBackendBaseUrl(input) {
  let s = String(input || '').trim()
  if (!s) {
    throw new Error('后端地址不能为空')
  }
  if (!/^https?:\/\//i.test(s)) {
    s = `http://${s}`
  }
  let parsed
  try {
    parsed = new URL(s)
  } catch {
    throw new Error('后端地址格式不正确')
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('仅支持 http 或 https')
  }
  return parsed.origin.replace(/\/$/, '')
}

function readConfigFile() {
  const filePath = getConfigFilePath()
  try {
    if (!fs.existsSync(filePath)) {
      return null
    }
    const raw = fs.readFileSync(filePath, 'utf8')
    const data = JSON.parse(raw)
    if (data && typeof data.backendBaseUrl === 'string' && data.backendBaseUrl.trim()) {
      return normalizeBackendBaseUrl(data.backendBaseUrl)
    }
  } catch (e) {
    console.warn('[qualitest-desktop] 读取后端配置失败:', e)
  }
  return null
}

/**
 * 解析启动时使用的后端地址：环境变量 > 本地配置 > 默认
 * @returns {string}
 */
export function resolveInitialBackendBaseUrl() {
  const fromEnv = process.env.QUALITEST_BACKEND_BASE_URL
  if (fromEnv && String(fromEnv).trim()) {
    try {
      return normalizeBackendBaseUrl(fromEnv)
    } catch (e) {
      console.warn('[qualitest-desktop] 环境变量 QUALITEST_BACKEND_BASE_URL 无效:', e)
    }
  }
  return readConfigFile() || DEFAULT_BACKEND_BASE_URL
}

/**
 * @param {string} backendBaseUrl
 */
export function saveBackendBaseUrl(backendBaseUrl) {
  const normalized = normalizeBackendBaseUrl(backendBaseUrl)
  const filePath = getConfigFilePath()
  fs.mkdirSync(path.dirname(filePath), {recursive: true})
  fs.writeFileSync(
    filePath,
    JSON.stringify({backendBaseUrl: normalized}, null, 2),
    'utf8'
  )
  return normalized
}
