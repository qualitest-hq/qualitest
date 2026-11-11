import { isExternal } from '@/utils/validate'

/**
 * 把库内路径转成页面可展示的地址。
 * - 空串、blob 预览地址：原样返回
 * - http(s) / mailto / tel 等外链：原样返回（含对象存储返回的完整 CDN 地址）
 * - 已带前端代理前缀的：不再重复拼接
 * - 其它相对路径：前面补上 VITE_APP_BASE_API
 *
 * @param {string} path 库内路径或完整 URL
 * @returns {string}
 */
export function resolveResourceUrl(path) {
  if (!path) {
    return ''
  }
  const trimmed = String(path).trim()
  if (!trimmed || trimmed.startsWith('blob:')) {
    return trimmed
  }
  if (isExternal(trimmed)) {
    return trimmed
  }
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  if (baseUrl && trimmed.startsWith(baseUrl)) {
    return trimmed
  }
  return baseUrl + trimmed
}

/**
 * 把上传组件里的展示地址收成适合入库的值。
 * - blob 临时预览：丢弃（返回空串）
 * - 外链完整 URL：原样保留，方便对象存储 CDN 地址入库
 * - 带前端代理前缀的绝对地址：去掉前缀，只存相对路径
 * - 已经是相对路径：原样返回
 *
 * @param {string} url 展示用地址或接口返回的 fileName/url
 * @returns {string}
 */
export function toStoredPath(url) {
  if (!url) {
    return ''
  }
  const trimmed = String(url).trim()
  if (!trimmed || trimmed.startsWith('blob:')) {
    return ''
  }
  if (isExternal(trimmed)) {
    return trimmed
  }
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  if (baseUrl && trimmed.startsWith(baseUrl)) {
    return trimmed.slice(baseUrl.length)
  }
  return trimmed
}
