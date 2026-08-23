/**
 * 项目模板表单：matchConfig / apis JSON 与 pathPrefix 文本互转。
 */

import { formatAuthModeLabel, parseJsonMaybe, splitPathLines } from '../../testProject/utils/projectAuthConfig'

function listPathPrefixes(matchConfig) {
  const obj = parseJsonMaybe(matchConfig)
  const list = Array.isArray(obj?.pathPrefix) ? obj.pathPrefix : []
  return list.map((s) => String(s).trim()).filter(Boolean)
}

/** matchConfig JSON → pathPrefix 多行文本 */
export function matchConfigToPathPrefixText(matchConfig) {
  return listPathPrefixes(matchConfig).join('\n')
}

/** pathPrefix 多行文本 → matchConfig JSON 字符串；空则 null */
export function pathPrefixTextToMatchConfig(text) {
  const prefixes = splitPathLines(text)
  if (!prefixes.length) return null
  if (prefixes.some((x) => x === '/')) {
    throw new Error('pathPrefix 禁止使用 "/"')
  }
  return JSON.stringify({ pathPrefix: prefixes })
}

/** apis JSON 字符串美化 */
export function formatApisJson(apis) {
  const obj = parseJsonMaybe(apis)
  if (!Array.isArray(obj)) return '[]'
  return JSON.stringify(obj, null, 2)
}

/** 校验 apis JSON；通过返回格式化字符串，失败抛错 */
export function validateAndFormatApisJson(text) {
  const trimmed = String(text || '').trim()
  if (!trimmed) {
    throw new Error('预制接口不能为空')
  }
  let parsed
  try {
    parsed = JSON.parse(trimmed)
  } catch {
    throw new Error('预制接口须为合法 JSON 数组')
  }
  if (!Array.isArray(parsed) || !parsed.length) {
    throw new Error('预制接口须为非空 JSON 数组')
  }
  return JSON.stringify(parsed)
}

function resolveApiMethod(api) {
  const cfg = parseJsonMaybe(api?.requestConfig)
  return String(cfg?.method || 'GET').trim().toUpperCase() || 'GET'
}

/** apis JSON → 预览表格行 */
export function apisToPreviewRows(apis) {
  const list = parseJsonMaybe(apis)
  if (!Array.isArray(list)) return []
  return list.map((api) => ({
    method: resolveApiMethod(api),
    apiPath: String(api?.apiPath || '').trim(),
    apiName: String(api?.apiName || '').trim(),
    authMode: String(api?.authConfig?.mode || 'inherit').trim() || 'inherit',
    authModeLabel: formatAuthModeLabel(api?.authConfig?.mode),
  }))
}

/** 列表展示：pathPrefix 摘要 */
export function formatPathPrefixSummary(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return '—'
  const text = list.join('、')
  return text.length > 48 ? text.slice(0, 48) + '…' : text
}

/** 勾选列表副标题：pathPrefix 提示 */
export function formatPathPrefixHint(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return ''
  return '匹配 ' + list.join('、')
}

export function emptyTemplateForm() {
  return {
    testProjectTemplateId: undefined,
    templateName: '',
    headerName: 'Authorization',
    headerValueTemplate: 'Bearer {{flow.token}}',
    pathPrefixText: '',
    apisJson: '[]',
    enableStatus: 1,
    sortNum: 0,
    remark: '',
    builtinStatus: 0,
  }
}

/** 详情 → 表单 */
export function templateToForm(row) {
  return {
    testProjectTemplateId: row?.testProjectTemplateId,
    templateName: row?.templateName || '',
    headerName: row?.headerName || 'Authorization',
    headerValueTemplate: row?.headerValueTemplate || '',
    pathPrefixText: matchConfigToPathPrefixText(row?.matchConfig),
    apisJson: formatApisJson(row?.apis),
    enableStatus: row?.enableStatus ?? 1,
    sortNum: row?.sortNum ?? 0,
    remark: row?.remark || '',
    builtinStatus: row?.builtinStatus ?? 0,
  }
}

/** 表单 → 提交体 */
export function formToPayload(form) {
  const matchConfig = pathPrefixTextToMatchConfig(form.pathPrefixText)
  const apis = validateAndFormatApisJson(form.apisJson)
  const payload = {
    templateName: String(form.templateName || '').trim(),
    headerName: String(form.headerName || '').trim(),
    headerValueTemplate: String(form.headerValueTemplate || '').trim(),
    apis,
    enableStatus: form.enableStatus ?? 1,
    sortNum: form.sortNum ?? 0,
    remark: form.remark || '',
  }
  if (matchConfig != null) {
    payload.matchConfig = matchConfig
  }
  if (form.testProjectTemplateId != null && form.testProjectTemplateId !== '') {
    payload.testProjectTemplateId = Number(form.testProjectTemplateId)
  }
  return payload
}
